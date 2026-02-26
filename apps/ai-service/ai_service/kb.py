from __future__ import annotations

import hashlib
import logging
import re
import uuid
from pathlib import Path
from typing import Any, Iterable, cast

from docx import Document as DocxDocument
from pypdf import PdfReader
from qdrant_client import QdrantClient
from qdrant_client.models import Distance, FieldCondition, Filter, MatchValue, PointStruct, VectorParams

from .config import Settings
from .errors import bad_request, dependency_error
from .models import KbDeleteRequest, KbIngestRequest


logger = logging.getLogger("edunexus.ai.kb")


class KnowledgeBaseService:
    def __init__(self, settings: Settings, embedder: Any) -> None:
        self.settings = settings
        self._embedder = embedder
        self._qdrant = QdrantClient(
            url=settings.qdrant_url,
            api_key=settings.qdrant_api_key or None,
            check_compatibility=False,
        )

    def ensure_collection(self) -> None:
        try:
            existing = self._qdrant.get_collections().collections
            names = {item.name for item in existing}
            if self.settings.qdrant_collection in names:
                return
            self._qdrant.create_collection(
                collection_name=self.settings.qdrant_collection,
                vectors_config=VectorParams(size=self.settings.embedding_dim, distance=Distance.COSINE),
            )
        except Exception as ex:  # noqa: BLE001
            raise dependency_error(f"qdrant unavailable: {ex}") from ex

    async def ingest(self, req: KbIngestRequest) -> dict[str, Any]:
        self.ensure_collection()
        if not Path(req.file_path).exists():
            raise bad_request("document file not found")
        text = extract_text(req.file_path)
        if not text.strip():
            raise bad_request("document content is empty")

        chunks = chunk_text(text)
        if not chunks:
            raise bad_request("document chunks are empty")

        points: list[PointStruct] = []
        for idx, chunk in enumerate(chunks):
            content_hash = sha1(chunk)
            point_id = str(uuid.uuid5(uuid.NAMESPACE_DNS, f"{req.document_id}:{idx}:{content_hash}"))
            vector = await self._embedder(chunk)
            payload: dict[str, Any] = {
                "document_id": req.document_id,
                "teacher_id": req.teacher_id,
                "filename": req.filename,
                "chunk_index": idx,
                "content": chunk,
                "content_hash": content_hash,
            }
            if req.class_id:
                payload["class_id"] = req.class_id
            points.append(PointStruct(id=point_id, vector=vector, payload=payload))

        try:
            self._qdrant.upsert(collection_name=self.settings.qdrant_collection, points=points)
        except Exception as ex:  # noqa: BLE001
            raise dependency_error(f"qdrant upsert failed: {ex}") from ex

        return {"status": "ok", "chunks": len(points)}

    def delete(self, req: KbDeleteRequest) -> dict[str, Any]:
        self.ensure_collection()
        flt = Filter(must=[FieldCondition(key="document_id", match=MatchValue(value=req.document_id))])
        try:
            self._qdrant.delete(collection_name=self.settings.qdrant_collection, points_selector=flt)
        except Exception as ex:  # noqa: BLE001
            raise dependency_error(f"qdrant delete failed: {ex}") from ex
        return {"status": "ok"}

    async def retrieve(self, question: str, teacher_id: str | None, class_id: str | None, top_k: int = 5) -> list[dict[str, Any]]:
        self.ensure_collection()
        if not teacher_id and not class_id:
            return []

        vector = await self._embedder(question)
        conditions: list[Any] = []
        if teacher_id:
            conditions.append(FieldCondition(key="teacher_id", match=MatchValue(value=teacher_id)))
        if class_id:
            conditions.append(FieldCondition(key="class_id", match=MatchValue(value=class_id)))
        query_filter = Filter(must=cast(Any, conditions))

        try:
            query_points = getattr(self._qdrant, "query_points", None)
            if callable(query_points):
                result = query_points(
                    collection_name=self.settings.qdrant_collection,
                    query=vector,
                    query_filter=query_filter,
                    limit=top_k,
                )
                points_raw = getattr(result, "points", result)
            else:
                search_points = getattr(cast(Any, self._qdrant), "search", None)
                if not callable(search_points):
                    raise RuntimeError("qdrant search API unavailable")
                points_raw = search_points(
                    collection_name=self.settings.qdrant_collection,
                    query_vector=vector,
                    query_filter=query_filter,
                    limit=top_k,
                )
        except Exception as ex:  # noqa: BLE001
            logger.warning("qdrant search failed: %s", ex)
            return []

        points = points_raw if isinstance(points_raw, list) else list(cast(Iterable[Any], points_raw))
        out: list[dict[str, Any]] = []
        for point in points:
            payload = getattr(point, "payload", {}) or {}
            content = str(payload.get("content", "")).strip()
            if not content:
                continue
            out.append(
                {
                    "document_id": payload.get("document_id"),
                    "filename": payload.get("filename", "unknown"),
                    "chunk_index": payload.get("chunk_index"),
                    "content": content,
                    "score": float(getattr(point, "score", 0.0)),
                }
            )
        return out[:top_k]


def extract_text(file_path: str) -> str:
    path = Path(file_path)
    if not path.exists():
        return ""
    suffix = path.suffix.lower()
    if suffix == ".pdf":
        return extract_pdf(path)
    if suffix in {".docx", ".doc"}:
        return extract_docx(path)
    return path.read_text(encoding="utf-8", errors="ignore")


def extract_pdf(path: Path) -> str:
    try:
        reader = PdfReader(str(path))
        return "\n".join((page.extract_text() or "") for page in reader.pages)
    except Exception:  # noqa: BLE001
        return ""


def extract_docx(path: Path) -> str:
    try:
        document = DocxDocument(str(path))
        return "\n".join(row.text for row in document.paragraphs if row.text)
    except Exception:  # noqa: BLE001
        return ""


def chunk_text(text: str, chunk_size: int = 700, overlap: int = 100) -> list[str]:
    """语义感知切片：优先按段落 → 句号 → 字符级兜底。保留段落结构。"""
    if not text or not text.strip():
        return []

    # 按双换行拆段落（保留段落结构，不压缩为单行）
    paragraphs = re.split(r"\n{2,}", text.strip())
    paragraphs = [re.sub(r"[ \t]+", " ", p).strip() for p in paragraphs if p.strip()]

    chunks: list[str] = []
    buffer = ""

    for para in paragraphs:
        candidate = f"{buffer}\n\n{para}".strip() if buffer else para
        if len(candidate) <= chunk_size:
            buffer = candidate
            continue

        # 当前 buffer 已满，先保存
        if buffer:
            chunks.append(buffer)

        # 段落本身超长 → 按句号切分
        if len(para) > chunk_size:
            sentences = re.split(r"(?<=[。！？.!?])\s*", para)
            sent_buffer = ""
            for sent in sentences:
                sent = sent.strip()
                if not sent:
                    continue
                candidate = f"{sent_buffer}{sent}" if not sent_buffer else f"{sent_buffer} {sent}"
                if len(candidate) <= chunk_size:
                    sent_buffer = candidate
                else:
                    if sent_buffer:
                        chunks.append(sent_buffer)
                    # 单句仍然超长 → 字符级兜底
                    if len(sent) > chunk_size:
                        start = 0
                        while start < len(sent):
                            end = min(len(sent), start + chunk_size)
                            chunks.append(sent[start:end])
                            if end >= len(sent):
                                break
                            start = max(0, end - overlap)
                        sent_buffer = ""
                    else:
                        sent_buffer = sent
            buffer = sent_buffer
        else:
            buffer = para

    if buffer:
        chunks.append(buffer)

    return chunks


def sha1(text: str) -> str:
    return hashlib.sha1(text.encode("utf-8", errors="ignore")).hexdigest()
