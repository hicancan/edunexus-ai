from __future__ import annotations

import logging
import uuid
from collections.abc import Iterable
from pathlib import Path
from typing import Any, cast

from qdrant_client import QdrantClient
from qdrant_client.models import (
    Distance,
    FieldCondition,
    Filter,
    MatchValue,
    PointStruct,
    VectorParams,
)

from .chunking import chunk_text, sha1
from .config import Settings
from .errors import bad_request, dependency_error
from .extraction import extract_text_from_bytes
from .models import KbDeleteRequest, KbIngestRequest

logger = logging.getLogger("edunexus.ai.kb")

IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg", ".webp"}


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
                vectors_config=VectorParams(
                    size=self.settings.embedding_dim, distance=Distance.COSINE
                ),
            )
        except Exception as ex:
            raise dependency_error(f"qdrant unavailable: {ex}") from ex

    def extract_and_chunk(self, req: KbIngestRequest) -> list[str]:
        if not req.file_content:
            raise bad_request("document file is empty")

        suffix = Path(req.filename).suffix.lower()
        if suffix in IMAGE_SUFFIXES:
            raise bad_request("image document is not supported for knowledge ingest")

        text = extract_text_from_bytes(req.filename, req.file_content)

        if not text.strip():
            raise bad_request("document content is empty")

        chunks = chunk_text(text)
        if not chunks:
            raise bad_request("document chunks are empty")

        return chunks

    async def embed_and_upsert_chunks(
        self, document_id: str, chunks: list[str], req: KbIngestRequest
    ) -> int:
        self.ensure_collection()

        points: list[PointStruct] = []
        for idx, chunk in enumerate(chunks):
            content_hash = sha1(chunk)
            point_id = str(uuid.uuid5(uuid.NAMESPACE_DNS, f"{document_id}:{idx}:{content_hash}"))
            vector = await self._embedder(chunk)

            payload: dict[str, Any] = {
                "document_id": document_id,
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
        except Exception as ex:
            raise dependency_error(f"qdrant upsert failed: {ex}") from ex

        return len(points)

    def delete(self, req: KbDeleteRequest) -> dict[str, Any]:
        self.ensure_collection()
        flt = Filter(
            must=[FieldCondition(key="document_id", match=MatchValue(value=req.document_id))]
        )
        try:
            self._qdrant.delete(
                collection_name=self.settings.qdrant_collection, points_selector=flt
            )
        except Exception as ex:
            raise dependency_error(f"qdrant delete failed: {ex}") from ex
        return {"status": "ok"}

    async def retrieve(
        self,
        question: str,
        teacher_id: str | None,
        class_id: str | None,
        top_k: int = 5,
    ) -> list[dict[str, Any]]:
        self.ensure_collection()
        if not teacher_id or not class_id:
            return []

        vector = await self._embedder(question)
        conditions: list[Any] = [
            FieldCondition(key="teacher_id", match=MatchValue(value=teacher_id)),
            FieldCondition(key="class_id", match=MatchValue(value=class_id)),
        ]
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
        except Exception as ex:
            logger.warning("qdrant search failed: %s", ex)
            return []

        points = (
            points_raw if isinstance(points_raw, list) else list(cast(Iterable[Any], points_raw))
        )
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
