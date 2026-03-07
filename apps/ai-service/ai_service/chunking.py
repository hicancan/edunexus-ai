from __future__ import annotations

import hashlib
import re


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
