from __future__ import annotations

import json
import logging
import time
from contextlib import asynccontextmanager
from typing import Any

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from .config import load_settings, validate_runtime_policy
from .errors import InternalServiceError, auth_failed, output_invalid
from .idempotency import IdempotencyStore
from .kb import KnowledgeBaseService
from .llm import LLMService
from .models import (
    AIQGenerateRequest,
    ChatRequest,
    KbDeleteRequest,
    KbIngestRequest,
    LessonPlanRequest,
    WrongAnalyzeRequest,
    WrongAnalyzeResponse,
)
from .prompts import (
    aiq_prompt,
    aiq_repair_prompt,
    chat_prompt,
    has_required_plan_sections,
    lesson_plan_prompt,
    lesson_plan_repair_prompt,
    uncertain_answer,
    wrong_analysis_prompt,
)
from .utils import parse_json_array, parse_json_object, stable_json_hash


logger = logging.getLogger("edunexus.ai")

PUBLIC_PATHS = {"/health", "/docs", "/openapi.json", "/redoc"}
INTERNAL_OPEN_PATHS = {"/internal/v1/ping"}


def create_app() -> FastAPI:
    settings = load_settings()
    logging.basicConfig(level=settings.log_level)

    llm_service = LLMService(settings)
    kb_service = KnowledgeBaseService(settings, llm_service.embed)
    idempotency_store = IdempotencyStore()

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        validate_runtime_policy(settings)
        try:
            kb_service.ensure_collection()
        except InternalServiceError as ex:
            logger.warning("startup dependency warning code=%s message=%s", ex.code, ex.message)
        logger.info("ai_service startup provider=%s", settings.llm_provider)
        yield

    app = FastAPI(title=settings.app_name, version=settings.app_version, lifespan=lifespan)
    app.state.settings = settings
    app.state.llm_service = llm_service
    app.state.kb_service = kb_service
    app.state.idempotency_store = idempotency_store

    @app.exception_handler(InternalServiceError)
    async def internal_error_handler(request: Request, exc: InternalServiceError) -> JSONResponse:
        trace_id = request.headers.get("X-Trace-Id", "")
        return JSONResponse(
            status_code=exc.status_code,
            content={"code": exc.code, "message": exc.message, "traceId": trace_id},
        )

    @app.exception_handler(ValidationError)
    async def validation_error_handler(request: Request, exc: ValidationError) -> JSONResponse:
        trace_id = request.headers.get("X-Trace-Id", "")
        return JSONResponse(
            status_code=400,
            content={"code": "VALIDATION_FIELD", "message": str(exc), "traceId": trace_id},
        )

    @app.middleware("http")
    async def internal_auth_middleware(request: Request, call_next):
        path = request.url.path
        if path in PUBLIC_PATHS:
            return await call_next(request)
        if request.method == "OPTIONS":
            return await call_next(request)

        if path.startswith("/internal/v1/"):
            token = request.headers.get("X-Service-Token", "")
            if token != settings.service_token:
                error = auth_failed()
                return JSONResponse(status_code=error.status_code, content={"code": error.code, "message": error.message})

            if path not in INTERNAL_OPEN_PATHS and not request.headers.get("X-Trace-Id", "").strip():
                return JSONResponse(
                    status_code=400,
                    content={"code": "VALIDATION_PARAM", "message": "X-Trace-Id is required"},
                )

        return await call_next(request)

    @app.get("/health")
    async def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/internal/v1/ping")
    async def internal_ping() -> dict[str, str]:
        return {"status": "ok"}

    @app.post("/internal/v1/rag/chat")
    async def rag_chat(req: ChatRequest, request: Request) -> dict[str, Any]:
        trace_id = request.headers.get("X-Trace-Id") or req.trace_id or ""
        teacher_id, class_id = req.scope()
        started = time.perf_counter()

        contexts = await kb_service.retrieve(req.message, teacher_id=teacher_id, class_id=class_id, top_k=5)
        citations = _build_citations(contexts)

        if not contexts:
            latency_ms = int((time.perf_counter() - started) * 1000)
            return {
                "answer": uncertain_answer(),
                "citations": [],
                "provider": settings.llm_provider,
                "model": "",
                "tokenUsage": {"prompt": 0, "completion": 0},
                "latencyMs": latency_ms,
            }

        context_for_prompt = "\n\n".join(
            f"[{row['filename']}] {row['content'][:550]}" for row in contexts[:3]
        )
        history = []
        if req.context:
            for row in req.context.history:
                history.append({"role": row.role, "content": row.content})
        prompt = chat_prompt(req.message, context_for_prompt, history)

        result = await llm_service.complete(
            prompt,
            scene="chat_rag",
            trace_id=trace_id,
            hit_kb=True,
            chunk_ids=[f"{row.get('document_id')}:{row.get('chunk_index')}" for row in contexts[:3]],
        )

        return {
            "answer": result.text,
            "citations": citations,
            "provider": result.provider,
            "model": result.model,
            "tokenUsage": {"prompt": result.prompt_tokens, "completion": result.completion_tokens},
            "latencyMs": result.latency_ms,
        }

    @app.post("/internal/v1/exercise/analyze")
    async def analyze(req: WrongAnalyzeRequest, request: Request) -> dict[str, Any]:
        trace_id = request.headers.get("X-Trace-Id") or req.trace_id or ""
        prompt = wrong_analysis_prompt(
            question=req.question,
            user_answer=req.user_answer,
            correct_answer=req.correct_answer,
            knowledge_points=req.knowledge_points,
            teacher_suggestion=req.teacher_suggestion,
        )
        result = await llm_service.complete(prompt, scene="wrong_analysis", trace_id=trace_id)
        parsed = parse_json_object(result.text)
        if parsed is None:
            repaired = await llm_service.complete(
                "将下面文本转换为合法 JSON 对象，字段为 encourage/concept/steps/rootCause/nextPractice：\n"
                + result.text,
                scene="wrong_analysis",
                trace_id=trace_id,
            )
            parsed = parse_json_object(repaired.text)

        if parsed is None:
            raise output_invalid("wrong analysis output is not valid JSON")

        normalized = _validate_wrong_analysis(parsed)
        return normalized.model_dump()

    @app.post("/internal/v1/aiq/generate")
    async def generate_aiq(req: AIQGenerateRequest, request: Request) -> dict[str, Any]:
        trace_id = request.headers.get("X-Trace-Id") or req.trace_id or ""
        prompt = aiq_prompt(
            subject=req.subject,
            difficulty=req.difficulty,
            count=req.count,
            concept_tags=req.concept_tags,
            weakness_profile=req.weakness_profile,
            teacher_suggestions=req.teacher_suggestions,
        )
        result = await llm_service.complete(prompt, scene="ai_question", trace_id=trace_id)

        questions = _validate_questions(parse_json_array(result.text), req.count)
        if not questions:
            repaired = await llm_service.complete(
                aiq_repair_prompt(result.text, req.count),
                scene="ai_question",
                trace_id=trace_id,
            )
            questions = _validate_questions(parse_json_array(repaired.text), req.count)
            if not questions:
                raise output_invalid("ai question output invalid after repair")

        return {
            "questions": questions,
            "routerDecision": {
                "provider": result.provider,
                "model": result.model,
                "reason": result.reason,
                "latencyMs": result.latency_ms,
            },
        }

    @app.post("/internal/v1/lesson-plans/generate")
    async def generate_lesson_plan(req: LessonPlanRequest, request: Request) -> dict[str, Any]:
        trace_id = request.headers.get("X-Trace-Id") or req.trace_id or ""
        prompt = lesson_plan_prompt(req.topic, req.grade_level, req.duration_mins)
        result = await llm_service.complete(prompt, scene="lesson_plan", trace_id=trace_id)

        content = result.text.strip()
        if not has_required_plan_sections(content):
            repaired = await llm_service.complete(
                lesson_plan_repair_prompt(content),
                scene="lesson_plan",
                trace_id=trace_id,
            )
            content = repaired.text.strip()
            if not has_required_plan_sections(content):
                raise output_invalid("lesson plan sections are incomplete")

        return {
            "contentMd": content,
            "provider": result.provider,
            "model": result.model,
            "latencyMs": result.latency_ms,
        }

    @app.post("/internal/v1/kb/ingest")
    async def kb_ingest(req: KbIngestRequest, request: Request) -> dict[str, Any]:
        idem_key = request.headers.get("Idempotency-Key", "").strip()
        payload = req.model_dump(mode="json")
        request_hash = stable_json_hash(payload)
        if idem_key:
            replay = idempotency_store.get("kb.ingest", idem_key, request_hash)
            if replay is not None:
                replay["code"] = "INTERNAL_IDEMPOTENT_REPLAY"
                return replay

        result = await kb_service.ingest(req)
        if idem_key:
            idempotency_store.set("kb.ingest", idem_key, request_hash, result)
        return result

    @app.post("/internal/v1/kb/delete")
    async def kb_delete(req: KbDeleteRequest, request: Request) -> dict[str, Any]:
        idem_key = request.headers.get("Idempotency-Key", "").strip()
        payload = req.model_dump(mode="json")
        request_hash = stable_json_hash(payload)
        if idem_key:
            replay = idempotency_store.get("kb.delete", idem_key, request_hash)
            if replay is not None:
                replay["code"] = "INTERNAL_IDEMPOTENT_REPLAY"
                return replay

        result = kb_service.delete(req)
        if idem_key:
            idempotency_store.set("kb.delete", idem_key, request_hash, result)
        return result

    return app


def _build_citations(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for row in rows[:3]:
        out.append(
            {
                "documentId": row.get("document_id"),
                "filename": row.get("filename", "unknown"),
                "chunkIndex": row.get("chunk_index"),
                "content": row.get("content", ""),
                "score": row.get("score", 0.0),
            }
        )
    return out


def _validate_wrong_analysis(payload: dict[str, Any]) -> WrongAnalyzeResponse:
    steps = payload.get("steps", [])
    if isinstance(steps, str):
        steps = [steps]
    if not isinstance(steps, list):
        raise output_invalid("steps must be a list")
    clean_steps = [str(step).strip() for step in steps if str(step).strip()]
    if not clean_steps:
        raise output_invalid("steps cannot be empty")

    try:
        response = WrongAnalyzeResponse(
            encourage=str(payload.get("encourage", "")).strip(),
            concept=str(payload.get("concept", "")).strip(),
            steps=clean_steps,
            rootCause=str(payload.get("rootCause", payload.get("root_cause", ""))).strip(),
            nextPractice=str(payload.get("nextPractice", payload.get("next_practice", ""))).strip(),
        )
        if not response.encourage or not response.concept or not response.rootCause or not response.nextPractice:
            raise output_invalid("wrong analysis fields cannot be empty")
        return response
    except ValidationError as ex:
        raise output_invalid(str(ex)) from ex


def _validate_questions(rows: list[dict[str, Any]], count: int) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for row in rows:
        content = str(row.get("content", "")).strip()
        if not content:
            continue

        options_raw = row.get("options", {})
        if isinstance(options_raw, str):
            try:
                options_raw = json.loads(options_raw)
            except Exception:  # noqa: BLE001
                options_raw = {}
        if not isinstance(options_raw, dict):
            continue

        options: dict[str, str] = {}
        for key, value in options_raw.items():
            option_key = str(key).strip().upper()
            option_value = str(value).strip()
            if option_key and option_value:
                options[option_key] = option_value
        if len(options) < 4:
            continue
        if not all(label in options for label in ("A", "B", "C", "D")):
            continue

        correct_answer = str(row.get("correct_answer", "")).strip().upper()
        if correct_answer not in options:
            continue

        explanation = str(row.get("explanation", "")).strip()
        if not explanation:
            continue

        knowledge_points = row.get("knowledge_points", [])
        if not isinstance(knowledge_points, list):
            continue
        kp = [str(item).strip() for item in knowledge_points if str(item).strip()]
        if not kp:
            continue

        out.append(
            {
                "content": content,
                "options": options,
                "correct_answer": correct_answer,
                "explanation": explanation,
                "knowledge_points": kp,
            }
        )
        if len(out) >= count:
            break
    return out


app = create_app()
