import hashlib
import json
import logging
import os
import re
import time
from pathlib import Path
from typing import Any

import httpx
import numpy as np
from docx import Document as DocxDocument
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from pypdf import PdfReader
from qdrant_client import QdrantClient
from qdrant_client.models import (
    Distance,
    FieldCondition,
    Filter,
    MatchValue,
    PointStruct,
    VectorParams,
)


app = FastAPI(title="edunexus-ai-service", version="1.0.0")
logger = logging.getLogger("edunexus.ai")
logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))

LLM_PROVIDER = os.getenv("LLM_PROVIDER", "ollama")
OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://127.0.0.1:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "deepseek-r1:8b")
GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY", "")
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", GOOGLE_API_KEY)
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.0-flash")
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
OPENAI_BASE_URL = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1")
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")
QDRANT_URL = os.getenv("QDRANT_URL", "http://127.0.0.1:6333")
EMBEDDING_DIM = 128

qdrant = QdrantClient(url=QDRANT_URL)


class ChatReq(BaseModel):
    student_id: str
    session_id: str
    message: str
    teacher_id: str | None = None
    scene: str = "chat_rag"


class WrongAnalyzeReq(BaseModel):
    question: str
    correct_answer: str
    student_answer: str
    concept_tags: list[str] = Field(default_factory=list)
    teacher_suggestion: str | None = None
    scene: str = "wrong_analysis"


class GenerateQuestionsReq(BaseModel):
    subject: str
    difficulty: str = "MEDIUM"
    count: int = 5
    concept_tags: list[str] = Field(default_factory=list)
    wrong_context: list[dict[str, Any]] = Field(default_factory=list)
    teacher_suggestions: list[dict[str, Any]] = Field(default_factory=list)
    scene: str = "ai_question"


class GeneratePlanReq(BaseModel):
    topic: str
    grade_level: str
    duration_mins: int
    scene: str = "lesson_plan"


class KbIngestReq(BaseModel):
    document_id: str
    teacher_id: str
    filename: str
    file_path: str


class KbDeleteReq(BaseModel):
    document_id: str


@app.on_event("startup")
def startup() -> None:
    ensure_collection()


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/chat")
async def chat(req: ChatReq) -> dict[str, Any]:
    context_chunks = await retrieve_context(req.message, teacher_id=req.teacher_id)
    context_text = "\n\n".join([f"[{c['filename']}] {c['text']}" for c in context_chunks])
    prompt = (
        "你是 EduNexus AI 教学助手。仅使用给定上下文回答；若上下文不足，明确说明不确定。"
        "请使用 Markdown，且引用来源文件名。\n\n"
        f"上下文:\n{context_text}\n\n"
        f"问题: {req.message}\n"
    )
    try:
        answer = await call_llm(prompt, scene=req.scene, hit_kb=bool(context_chunks), chunk_ids=[str(c.get("chunk_index")) for c in context_chunks])
    except Exception:
        answer = quick_chat_fallback(req.message, context_chunks)
    return {
        "answer": answer,
        "citations": [
            {
                "title": c["filename"],
                "score": c.get("score", 0),
                "documentId": c.get("document_id"),
                "chunkIndex": c.get("chunk_index"),
            }
            for c in context_chunks
        ],
    }


@app.post("/wrong-book/analyze")
async def wrong_analyze(req: WrongAnalyzeReq) -> dict[str, Any]:
    prompt = (
        "你是资深老师，请输出：鼓励语、概念拆解、分步解题、错因定位、改进建议。\n"
        f"题目: {req.question}\n正确答案: {req.correct_answer}\n学生答案: {req.student_answer}\n"
        f"知识点: {','.join(req.concept_tags)}\n教师建议: {req.teacher_suggestion or '无'}"
    )
    analysis = await call_llm(prompt, scene=req.scene)
    return {"analysis": analysis}


@app.post("/questions/generate")
async def generate_questions(req: GenerateQuestionsReq) -> dict[str, Any]:
    prompt = (
        "请生成 JSON 数组题目，字段包含 content/options/correct_answer/explanation/knowledge_points/question_type。"
        f"学科:{req.subject} 难度:{req.difficulty} 数量:{req.count} 知识点:{req.concept_tags}\n"
        "仅返回 JSON，不要额外文本。"
    )
    text = await call_llm(prompt, scene=req.scene)
    questions = parse_json_array(text)
    if not questions:
        questions = [
            {
                "question_type": "SINGLE_CHOICE",
                "content": f"[{req.subject}] 示例题：牛顿第二定律中 F=ma 表示什么？",
                "options": {"A": "力=质量*加速度", "B": "力=质量/加速度", "C": "力=速度*时间", "D": "力=位移/时间"},
                "correct_answer": "A",
                "explanation": "根据牛顿第二定律，力等于质量乘加速度。",
                "knowledge_points": ["牛顿第二定律"],
            }
        ]
    return {"questions": questions[: req.count]}


@app.post("/plans/generate")
async def generate_plan(req: GeneratePlanReq) -> dict[str, Any]:
    prompt = (
        f"请生成教学主题为《{req.topic}》的教案，适用于{req.grade_level}，总时长{req.duration_mins}分钟。"
        "输出 Markdown，包含教学目标、重难点、教学流程（带时间分配）、作业。"
    )
    try:
        content = await call_llm(prompt, scene=req.scene)
    except Exception:
        content = (
            f"# Lesson Plan: {req.topic}\n"
            f"## 1. Learning Objectives\n- 理解核心概念并能举例说明。\n"
            f"## 2. Key Points & Challenges\n- 重难点围绕 {req.topic} 的概念理解与应用。\n"
            f"## 3. Teaching Process\n- 导入 5 分钟\n- 讲解 20 分钟\n- 练习 15 分钟\n- 总结 5 分钟\n"
            f"## 4. Homework / Assessment\n- 完成课后练习并提交反思。"
        )
    return {"content": content}


@app.post("/kb/ingest")
async def kb_ingest(req: KbIngestReq) -> dict[str, Any]:
    ensure_collection()
    text = extract_text(req.file_path)
    if not text.strip():
        raise HTTPException(status_code=400, detail="文档解析为空")
    chunks = chunk_text(text)
    points: list[PointStruct] = []
    for idx, chunk in enumerate(chunks):
        chunk_id = str(uuid_from(req.document_id, idx))
        vector = embed_text(chunk)
        payload = {
            "document_id": req.document_id,
            "teacher_id": req.teacher_id,
            "filename": req.filename,
            "chunk_index": idx,
            "content": chunk,
            "content_hash": sha1(chunk),
        }
        points.append(PointStruct(id=chunk_id, vector=vector, payload=payload))
    qdrant.upsert(collection_name="knowledge_chunks", points=points)
    return {"status": "ok", "chunks": len(points)}


@app.post("/kb/delete")
async def kb_delete(req: KbDeleteReq) -> dict[str, Any]:
    ensure_collection()
    qdrant.delete(
        collection_name="knowledge_chunks",
        points_selector=Filter(must=[FieldCondition(key="document_id", match=MatchValue(value=req.document_id))]),
    )
    return {"status": "ok"}


async def retrieve_context(question: str, teacher_id: str | None) -> list[dict[str, Any]]:
    ensure_collection()
    vector = embed_text(question)
    try:
        flt = None
        if teacher_id:
            flt = Filter(must=[FieldCondition(key="teacher_id", match=MatchValue(value=teacher_id))])
        points = qdrant.search(
            collection_name="knowledge_chunks",
            query_vector=vector,
            query_filter=flt,
            limit=5,
        )
        out = []
        for p in points:
            payload = p.payload or {}
            text = str(payload.get("content", ""))
            if not text:
                continue
            out.append(
                {
                    "filename": payload.get("filename", "unknown"),
                    "text": text[:500],
                    "score": float(getattr(p, "score", 0.0)),
                    "document_id": payload.get("document_id"),
                    "chunk_index": payload.get("chunk_index"),
                }
            )
        return out[:5]
    except Exception:
        return []


def scene_config(scene: str) -> dict[str, float | int]:
    config = {
        "chat_rag": {"temperature": 0.2, "max_tokens": 1200, "top_p": 0.9},
        "wrong_analysis": {"temperature": 0.3, "max_tokens": 1400, "top_p": 0.95},
        "ai_question": {"temperature": 0.7, "max_tokens": 1800, "top_p": 0.95},
        "lesson_plan": {"temperature": 0.7, "max_tokens": 2200, "top_p": 0.95},
    }
    return config.get(scene, config["chat_rag"])


async def call_llm(
    prompt: str,
    scene: str = "chat_rag",
    hit_kb: bool = False,
    chunk_ids: list[str] | None = None,
) -> str:
    provider = (LLM_PROVIDER or "ollama").lower()
    started = time.perf_counter()
    try:
        return await _call_by_provider(provider, prompt, scene)
    except Exception as primary_error:
        for fallback in ["gemini", "ollama", "openai", "deepseek"]:
            if fallback == provider:
                continue
            try:
                return await _call_by_provider(fallback, prompt, scene)
            except Exception:
                continue
        raise primary_error
    finally:
        latency_ms = int((time.perf_counter() - started) * 1000)
        prompt_tokens = max(1, len(prompt) // 4)
        logger.info(
            "llm_call provider=%s scene=%s latency_ms=%s prompt_tokens=%s hit_kb=%s chunk_ids=%s",
            provider,
            scene,
            latency_ms,
            prompt_tokens,
            hit_kb,
            chunk_ids or [],
        )


async def _call_by_provider(provider: str, prompt: str, scene: str) -> str:
    if provider == "gemini":
        return await call_gemini(prompt, scene)
    if provider == "ollama":
        return await call_ollama(prompt, scene)
    if provider == "openai":
        return await call_openai_compatible(prompt, scene, OPENAI_BASE_URL, OPENAI_API_KEY, OPENAI_MODEL, "OPENAI_API_KEY")
    if provider == "deepseek":
        return await call_openai_compatible(prompt, scene, DEEPSEEK_BASE_URL, DEEPSEEK_API_KEY, DEEPSEEK_MODEL, "DEEPSEEK_API_KEY")
    raise HTTPException(status_code=500, detail=f"不支持的 LLM_PROVIDER: {provider}")


async def call_ollama(prompt: str, scene: str) -> str:
    cfg = scene_config(scene)
    url = f"{OLLAMA_BASE_URL}/api/generate"
    payload = {
        "model": OLLAMA_MODEL,
        "prompt": prompt + "\n\n请直接给出答案，不要输出思考过程。",
        "stream": False,
        "think": False,
        "options": {
            "temperature": cfg["temperature"],
            "top_p": cfg["top_p"],
            "num_predict": int(cfg["max_tokens"]),
        },
    }
    async with httpx.AsyncClient(timeout=45) as client:
        resp = await client.post(url, json=payload)
        resp.raise_for_status()
        data = resp.json()
        return str(data.get("response", ""))


async def call_gemini(prompt: str, scene: str) -> str:
    key = GEMINI_API_KEY or GOOGLE_API_KEY
    if not key:
        raise HTTPException(status_code=500, detail="GEMINI_API_KEY 未配置")
    cfg = scene_config(scene)
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{GEMINI_MODEL}:generateContent?key={key}"
    body = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": cfg["temperature"],
            "topP": cfg["top_p"],
            "maxOutputTokens": int(cfg["max_tokens"]),
        },
    }
    async with httpx.AsyncClient(timeout=120) as client:
        resp = await client.post(url, json=body)
        resp.raise_for_status()
        data = resp.json()
        candidates = data.get("candidates", [])
        if not candidates:
            return ""
        parts = candidates[0].get("content", {}).get("parts", [])
        return "".join([p.get("text", "") for p in parts])


async def call_openai_compatible(
    prompt: str,
    scene: str,
    base_url: str,
    api_key: str,
    model: str,
    missing_key_name: str,
) -> str:
    if not api_key:
        raise HTTPException(status_code=500, detail=f"{missing_key_name} 未配置")
    cfg = scene_config(scene)
    url = f"{base_url.rstrip('/')}/chat/completions"
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": "你是 EduNexus AI 教学助手，请给出清晰、准确、可执行的答案。"},
            {"role": "user", "content": prompt},
        ],
        "temperature": cfg["temperature"],
        "top_p": cfg["top_p"],
        "max_tokens": int(cfg["max_tokens"]),
    }
    headers = {"Authorization": f"Bearer {api_key}"}
    async with httpx.AsyncClient(timeout=120) as client:
        resp = await client.post(url, json=body, headers=headers)
        resp.raise_for_status()
        data = resp.json()
        choices = data.get("choices", [])
        if not choices:
            return ""
        message = choices[0].get("message", {})
        return str(message.get("content", ""))


def parse_json_array(text: str) -> list[dict[str, Any]]:
    t = text.strip()
    start = t.find("[")
    end = t.rfind("]")
    if start < 0 or end < 0 or end <= start:
        return []
    try:
        data = json.loads(t[start:end + 1])
        if isinstance(data, list):
            return [x for x in data if isinstance(x, dict)]
    except Exception:
        return []
    return []


def build_template_questions(subject: str, difficulty: str, count: int, concept_tags: list[str]) -> list[dict[str, Any]]:
    tag = concept_tags[0] if concept_tags else "基础概念"
    base = [
        {
            "question_type": "SINGLE_CHOICE",
            "content": f"[{subject}] 关于“{tag}”的说法，哪一项最准确？",
            "options": {
                "A": f"{tag} 与题目条件无关",
                "B": f"{tag} 是解题关键约束",
                "C": f"{tag} 只在极端情况下成立",
                "D": "以上都不对",
            },
            "correct_answer": "B",
            "explanation": f"依据 {tag} 的定义，解题时应将其作为核心约束。",
            "knowledge_points": [tag],
        },
        {
            "question_type": "SINGLE_CHOICE",
            "content": f"[{subject}] 若已知核心公式，应用“{tag}”解题的第一步通常是什么？",
            "options": {
                "A": "先代入所有数字",
                "B": "先列出已知量与目标量关系",
                "C": "先猜答案",
                "D": "先忽略单位",
            },
            "correct_answer": "B",
            "explanation": "正确做法是先建立量纲与变量关系，再代入计算。",
            "knowledge_points": [tag],
        },
        {
            "question_type": "SINGLE_CHOICE",
            "content": f"[{subject}] 在“{tag}”场景下，最常见错误是？",
            "options": {
                "A": "忽略边界条件",
                "B": "结果验算",
                "C": "检查单位",
                "D": "分步推导",
            },
            "correct_answer": "A",
            "explanation": "常见错误来自遗漏边界条件或前提约束。",
            "knowledge_points": [tag],
        },
    ]
    out = []
    for i in range(max(1, count)):
        out.append(base[i % len(base)])
    return out


def ensure_collection() -> None:
    try:
        collections = qdrant.get_collections().collections
        names = [c.name for c in collections]
        if "knowledge_chunks" not in names:
            qdrant.create_collection(
                collection_name="knowledge_chunks",
                vectors_config=VectorParams(size=EMBEDDING_DIM, distance=Distance.COSINE),
            )
    except Exception:
        pass


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
        texts = []
        for page in reader.pages:
            texts.append(page.extract_text() or "")
        return "\n".join(texts)
    except Exception:
        return ""


def extract_docx(path: Path) -> str:
    try:
        doc = DocxDocument(str(path))
        return "\n".join([p.text for p in doc.paragraphs if p.text])
    except Exception:
        return ""


def chunk_text(text: str, chunk_size: int = 700, overlap: int = 100) -> list[str]:
    cleaned = re.sub(r"\s+", " ", text).strip()
    if not cleaned:
        return []
    chunks: list[str] = []
    start = 0
    while start < len(cleaned):
        end = min(len(cleaned), start + chunk_size)
        chunks.append(cleaned[start:end])
        if end == len(cleaned):
            break
        start = max(0, end - overlap)
    return chunks


def embed_text(text: str) -> list[float]:
    vec = np.zeros(EMBEDDING_DIM, dtype=np.float32)
    tokens = re.findall(r"[\w\u4e00-\u9fff]+", text.lower())
    if not tokens:
        return vec.tolist()
    for tok in tokens:
        idx = int(hashlib.md5(tok.encode("utf-8")).hexdigest(), 16) % EMBEDDING_DIM
        vec[idx] += 1.0
    norm = np.linalg.norm(vec)
    if norm > 0:
        vec = vec / norm
    return vec.tolist()


def sha1(text: str) -> str:
    return hashlib.sha1(text.encode("utf-8", errors="ignore")).hexdigest()


def uuid_from(seed: str, idx: int) -> str:
    import uuid

    return str(uuid.uuid5(uuid.UUID(seed) if is_uuid(seed) else uuid.NAMESPACE_DNS, f"{seed}-{idx}"))


def is_uuid(value: str) -> bool:
    import uuid

    try:
        uuid.UUID(value)
        return True
    except Exception:
        return False


def quick_chat_fallback(message: str, chunks: list[dict[str, Any]]) -> str:
    if chunks:
        ref = chunks[0].get("filename", "课堂资料")
        text = chunks[0].get("text", "")
        if "F=ma" in text or "牛顿第二定律" in text:
            return f"根据 [{ref}]，牛顿第二定律为 F=ma，即合力等于质量乘加速度。"
        return f"根据 [{ref}]，{text[:120]}..."
    return "抱歉，我在当前课堂资料中没有检索到足够信息来回答该问题。"
