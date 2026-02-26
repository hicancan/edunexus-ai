import hashlib
import json
import logging
import os
import re
import time
from pathlib import Path
from typing import Any, Iterable, cast

import httpx
import numpy as np
from docx import Document as DocxDocument
from dotenv import load_dotenv
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


SERVICE_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SERVICE_DIR.parent.parent
load_dotenv(PROJECT_ROOT / ".env", override=False)
load_dotenv(SERVICE_DIR / ".env", override=False)

app = FastAPI(title="edunexus-ai-service", version="1.0.0")
logger = logging.getLogger("edunexus.ai")
logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))

LLM_PROVIDER = os.getenv("LLM_PROVIDER", "auto")
OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://127.0.0.1:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "deepseek-r1:8b")
OLLAMA_COMPLEX_MODEL = os.getenv("OLLAMA_COMPLEX_MODEL", OLLAMA_MODEL)
GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY", "")
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", GOOGLE_API_KEY)
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.0-flash")
GEMINI_COMPLEX_MODEL = os.getenv("GEMINI_COMPLEX_MODEL", "gemini-1.5-pro")
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
OPENAI_BASE_URL = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
OPENAI_COMPLEX_MODEL = os.getenv("OPENAI_COMPLEX_MODEL", "gpt-4.1")
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1")
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")
DEEPSEEK_COMPLEX_MODEL = os.getenv("DEEPSEEK_COMPLEX_MODEL", "deepseek-reasoner")
QDRANT_URL = os.getenv("QDRANT_URL", "http://127.0.0.1:6333")
EMBEDDING_DIM = 128

qdrant = QdrantClient(url=QDRANT_URL, check_compatibility=False)


class ChatReq(BaseModel):
    student_id: str
    session_id: str
    message: str
    teacher_id: str | None = None
    scene: str = "chat_rag"
    trace_id: str | None = None


class WrongAnalyzeReq(BaseModel):
    question: str
    correct_answer: str
    student_answer: str
    concept_tags: list[str] = Field(default_factory=list)
    teacher_suggestion: str | None = None
    scene: str = "wrong_analysis"
    trace_id: str | None = None


class GenerateQuestionsReq(BaseModel):
    subject: str
    difficulty: str = "MEDIUM"
    count: int = 5
    concept_tags: list[str] = Field(default_factory=list)
    wrong_context: list[dict[str, Any]] = Field(default_factory=list)
    teacher_suggestions: list[dict[str, Any]] = Field(default_factory=list)
    scene: str = "ai_question"
    trace_id: str | None = None


class GeneratePlanReq(BaseModel):
    topic: str
    grade_level: str
    duration_mins: int
    scene: str = "lesson_plan"
    trace_id: str | None = None


class KbIngestReq(BaseModel):
    document_id: str
    teacher_id: str
    filename: str
    file_path: str
    trace_id: str | None = None


class KbDeleteReq(BaseModel):
    document_id: str


@app.on_event("startup")
def startup() -> None:
    ensure_collection()
    logger.info(
        "ai_service startup llm_provider=%s auto_policy=simple->ollama,complex->deepseek deepseek_key_configured=%s",
        LLM_PROVIDER,
        bool(DEEPSEEK_API_KEY),
    )


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


def sanitize_prompt_input(text: str, limit: int = 3000) -> str:
    safe = (text or "").replace("```", "").strip()
    return safe[:limit]


def build_chat_prompt(message: str, context_text: str) -> str:
    return (
        "你是 EduNexus AI 教学助手。仅使用给定上下文回答；若上下文不足，明确说明不确定。"
        "请使用 Markdown，且引用来源文件名。\n\n"
        f"上下文:\n{context_text}\n\n"
        f"问题: {sanitize_prompt_input(message, 1200)}\n"
    )


def build_wrong_analysis_prompt(req: WrongAnalyzeReq) -> str:
    return (
        "你是资深老师，请输出：鼓励语、概念拆解、分步解题、错因定位、改进建议。\n"
        f"题目: {sanitize_prompt_input(req.question, 1200)}\n"
        f"正确答案: {sanitize_prompt_input(req.correct_answer, 300)}\n"
        f"学生答案: {sanitize_prompt_input(req.student_answer, 300)}\n"
        f"知识点: {','.join(req.concept_tags)}\n"
        f"教师建议: {sanitize_prompt_input(req.teacher_suggestion or '无', 800)}"
    )


def build_generate_questions_prompt(req: GenerateQuestionsReq, weak_points: str, teacher_notes: str) -> str:
    return (
        "请生成 JSON 数组题目，字段包含 content/options/correct_answer/explanation/knowledge_points/question_type。"
        f"学科:{sanitize_prompt_input(req.subject, 120)} 难度:{sanitize_prompt_input(req.difficulty, 20)} 数量:{req.count} 知识点:{req.concept_tags}\n"
        f"学生薄弱点:{sanitize_prompt_input(weak_points, 1200)}\n"
        f"教师建议:{sanitize_prompt_input(teacher_notes, 1200)}\n"
        "仅返回 JSON，不要额外文本。"
    )


def build_plan_prompt(req: GeneratePlanReq) -> str:
    return (
        f"请生成教学主题为《{sanitize_prompt_input(req.topic, 120)}》的教案，适用于{sanitize_prompt_input(req.grade_level, 60)}，总时长{req.duration_mins}分钟。"
        "输出 Markdown，包含教学目标、重难点、教学流程（带时间分配）、作业。"
    )


@app.post("/chat")
async def chat(req: ChatReq) -> dict[str, Any]:
    context_chunks = await retrieve_context(req.message, teacher_id=req.teacher_id)
    if not context_chunks:
        return {
            "answer": "抱歉，课堂资料不足，当前无法给出可靠答案。请先让教师补充相关资料后再试。",
            "citations": [],
        }
    prompt_chunks = context_chunks[:3]
    context_text = "\n\n".join([f"[{c['filename']}] {c['text']}" for c in prompt_chunks])
    prompt = build_chat_prompt(req.message, context_text)
    try:
        answer = await call_llm(
            prompt,
            scene=req.scene,
            hit_kb=bool(context_chunks),
            chunk_ids=[str(c.get("chunk_index")) for c in context_chunks],
            trace_id=req.trace_id,
            routing_input=req.message,
        )
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
    prompt = build_wrong_analysis_prompt(req)
    analysis = await call_llm(prompt, scene=req.scene, trace_id=req.trace_id)
    return {"analysis": analysis}


@app.post("/questions/generate")
async def generate_questions(req: GenerateQuestionsReq) -> dict[str, Any]:
    weak_points = summarize_wrong_context(req.wrong_context)
    teacher_notes = summarize_teacher_suggestions(req.teacher_suggestions)
    prompt = build_generate_questions_prompt(req, weak_points, teacher_notes)
    text = await call_llm(prompt, scene=req.scene, trace_id=req.trace_id)
    questions = parse_json_array(text)
    questions = validate_questions_payload(questions)
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
    prompt = build_plan_prompt(req)
    try:
        content = await call_llm(prompt, scene=req.scene, trace_id=req.trace_id)
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
    if not teacher_id:
        return []
    vector = embed_text(question)
    try:
        flt = Filter(must=[FieldCondition(key="teacher_id", match=MatchValue(value=teacher_id))])
        query_points = getattr(qdrant, "query_points", None)
        if callable(query_points):
            query_result = query_points(
                collection_name="knowledge_chunks",
                query=vector,
                query_filter=flt,
                limit=5,
            )
            points_raw = getattr(query_result, "points", query_result)
        else:
            legacy_search = getattr(qdrant, "search")
            points_raw = legacy_search(
                collection_name="knowledge_chunks",
                query_vector=vector,
                query_filter=flt,
                limit=5,
            )
        if isinstance(points_raw, list):
            points = points_raw
        else:
            points = list(cast(Iterable[Any], points_raw))
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


def chat_complexity_level(question_or_prompt: str) -> str:
    hard_markers = [
        "推导",
        "证明",
        "分析",
        "论证",
        "比较",
        "为什么",
        "如何设计",
        "步骤",
        "复杂",
        "derive",
        "prove",
        "analyze",
        "reason",
        "compare",
    ]
    if len(question_or_prompt) > 220:
        return "complex"
    lowered = question_or_prompt.lower()
    if any(marker in question_or_prompt or marker in lowered for marker in hard_markers):
        return "complex"
    return "simple"


def provider_available(provider: str) -> bool:
    if provider == "gemini":
        return bool(GEMINI_API_KEY or GOOGLE_API_KEY)
    if provider == "openai":
        return bool(OPENAI_API_KEY)
    if provider == "deepseek":
        return bool(DEEPSEEK_API_KEY)
    if provider == "ollama":
        return True
    return False


def auto_primary_provider(scene: str, prompt: str) -> str:
    complexity = chat_complexity_level(prompt)
    if complexity == "complex" and provider_available("deepseek"):
        return "deepseek"
    return "ollama"


def route_model(provider: str, scene: str, prompt: str) -> str:
    if provider == "auto":
        provider = auto_primary_provider(scene, prompt)
    complexity = chat_complexity_level(prompt) if scene == "chat_rag" else "simple"
    if provider == "gemini":
        if scene == "chat_rag":
            return GEMINI_COMPLEX_MODEL if complexity == "complex" else GEMINI_MODEL
        return GEMINI_MODEL
    if provider == "ollama":
        if scene == "chat_rag":
            return OLLAMA_COMPLEX_MODEL if complexity == "complex" else OLLAMA_MODEL
        return OLLAMA_MODEL
    if provider == "openai":
        if scene == "chat_rag":
            return OPENAI_COMPLEX_MODEL if complexity == "complex" else OPENAI_MODEL
        return OPENAI_MODEL
    if provider == "deepseek":
        if scene == "chat_rag":
            return DEEPSEEK_COMPLEX_MODEL if complexity == "complex" else DEEPSEEK_MODEL
        return DEEPSEEK_MODEL
    return OLLAMA_MODEL


def route_provider_candidates(provider: str, scene: str, prompt: str) -> list[str]:
    known = ["ollama", "deepseek", "gemini", "openai"]
    selected = provider.lower()
    if selected == "auto":
        primary = auto_primary_provider(scene, prompt)
        ordered = [primary] + [p for p in known if p != primary]
        return [p for p in ordered if provider_available(p)]

    ordered = [selected] + [p for p in known if p != selected]
    return [p for p in ordered if provider_available(p) or p == selected]


async def call_llm(
    prompt: str,
    scene: str = "chat_rag",
    hit_kb: bool = False,
    chunk_ids: list[str] | None = None,
    trace_id: str | None = None,
    routing_input: str | None = None,
) -> str:
    provider = (LLM_PROVIDER or "ollama").lower()
    routing_basis = routing_input or prompt
    candidates = route_provider_candidates(provider, scene, routing_basis)
    if not candidates:
        raise HTTPException(status_code=500, detail="没有可用的模型提供方，请检查 API Key 配置")

    used_provider = candidates[0]
    used_model = route_model(used_provider, scene, routing_basis)
    output = ""
    started = time.perf_counter()
    errors: list[str] = []
    try:
        for idx, candidate in enumerate(candidates):
            try:
                output, used_model = await _call_by_provider(candidate, prompt, scene, routing_basis)
                used_provider = candidate
                return output
            except Exception as ex:
                errors.append(f"{candidate}:{ex}")
                if idx == len(candidates) - 1:
                    raise ex
                continue
        raise HTTPException(status_code=500, detail="LLM 调用失败")
    finally:
        latency_ms = int((time.perf_counter() - started) * 1000)
        prompt_tokens = max(1, len(prompt) // 4)
        completion_tokens = max(1, len(output) // 4) if output else 0
        logger.info(
            "llm_call provider=%s model=%s scene=%s latency_ms=%s prompt_tokens=%s completion_tokens=%s hit_kb=%s chunk_ids=%s trace_id=%s candidates=%s errors=%s",
            used_provider,
            used_model,
            scene,
            latency_ms,
            prompt_tokens,
            completion_tokens,
            hit_kb,
            chunk_ids or [],
            trace_id or "",
            candidates,
            errors,
        )


async def _call_by_provider(provider: str, prompt: str, scene: str, routing_basis: str | None = None) -> tuple[str, str]:
    model = route_model(provider, scene, routing_basis or prompt)
    if provider == "gemini":
        return await call_gemini(prompt, scene, model), model
    if provider == "ollama":
        return await call_ollama(prompt, scene, model), model
    if provider == "openai":
        return (
            await call_openai_compatible(prompt, scene, OPENAI_BASE_URL, OPENAI_API_KEY, model, "OPENAI_API_KEY"),
            model,
        )
    if provider == "deepseek":
        return (
            await call_openai_compatible(prompt, scene, DEEPSEEK_BASE_URL, DEEPSEEK_API_KEY, model, "DEEPSEEK_API_KEY"),
            model,
        )
    raise HTTPException(status_code=500, detail=f"不支持的 LLM_PROVIDER: {provider}")


async def call_ollama(prompt: str, scene: str, model: str) -> str:
    cfg = scene_config(scene)
    url = f"{OLLAMA_BASE_URL}/api/generate"
    payload = {
        "model": model,
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


async def call_gemini(prompt: str, scene: str, model: str) -> str:
    key = GEMINI_API_KEY or GOOGLE_API_KEY
    if not key:
        raise HTTPException(status_code=500, detail="GEMINI_API_KEY 未配置")
    cfg = scene_config(scene)
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}"
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


def summarize_wrong_context(wrong_context: list[dict[str, Any]]) -> str:
    if not wrong_context:
        return "无明显薄弱点"
    tags: list[str] = []
    for row in wrong_context[:20]:
        points = row.get("knowledge_points")
        if isinstance(points, list):
            for p in points:
                if isinstance(p, str) and p.strip():
                    tags.append(p.strip())
        elif isinstance(points, str) and points.strip():
            tags.append(points.strip())
    if not tags:
        return "无明显薄弱点"
    uniq = []
    for t in tags:
        if t not in uniq:
            uniq.append(t)
    return ", ".join(uniq[:8])


def summarize_teacher_suggestions(suggestions: list[dict[str, Any]]) -> str:
    if not suggestions:
        return "无"
    out: list[str] = []
    for row in suggestions[:8]:
        txt = row.get("suggestion")
        if isinstance(txt, str) and txt.strip():
            out.append(txt.strip())
    return "；".join(out) if out else "无"


def validate_questions_payload(items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    valid: list[dict[str, Any]] = []
    for item in items:
        if not isinstance(item, dict):
            continue
        content = str(item.get("content", "")).strip()
        if not content:
            continue
        options = item.get("options")
        if not isinstance(options, dict) or not options:
            continue
        normalized_options: dict[str, str] = {}
        for key, value in options.items():
            k = str(key).strip().upper()
            if not k:
                continue
            normalized_options[k] = str(value).strip()
        if not normalized_options:
            continue
        correct_answer = str(item.get("correct_answer", "")).strip().upper()
        if correct_answer not in normalized_options:
            first = next(iter(normalized_options.keys()))
            correct_answer = first
        question_type = str(item.get("question_type", "SINGLE_CHOICE")).strip().upper()
        if question_type not in {"SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "SHORT_ANSWER"}:
            question_type = "SINGLE_CHOICE"
        explanation = str(item.get("explanation", "")).strip() or "参考标准解法逐步分析。"
        knowledge_points = item.get("knowledge_points", [])
        if isinstance(knowledge_points, list):
            kp = [str(x).strip() for x in knowledge_points if str(x).strip()]
        else:
            kp = []
        if not kp:
            kp = ["基础概念"]
        valid.append(
            {
                "question_type": question_type,
                "content": content,
                "options": normalized_options,
                "correct_answer": correct_answer,
                "explanation": explanation,
                "knowledge_points": kp,
            }
        )
    return valid


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
    flat = np.asarray(vec, dtype=np.float32).reshape(-1)
    return [float(x) for x in flat]


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
