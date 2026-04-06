from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from .config import load_settings, validate_runtime_policy
from .errors import InternalServiceError, auth_failed
from .idempotency import IdempotencyStore
from .kb import KnowledgeBaseService
from .llm import LLMService

logger = logging.getLogger("edunexus.ai")

PUBLIC_PATHS = {"/health", "/docs", "/openapi.json", "/redoc"}
INTERNAL_OPEN_PATHS = {"/internal/v1/ping"}


def create_app() -> FastAPI:
    settings = load_settings()
    logging.basicConfig(level=settings.log_level)

    llm_service = LLMService(settings)
    kb_service = KnowledgeBaseService(settings, llm_service.embed)
    idempotency_store = IdempotencyStore()

    from .worker import AsyncWorker

    worker = AsyncWorker(settings, kb_service)

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        validate_runtime_policy(settings)
        try:
            kb_service.ensure_collection()
        except InternalServiceError as ex:
            logger.warning("startup dependency warning code=%s message=%s", ex.code, ex.message)
        logger.info(
            "ai_service startup provider=%s runtime_strategy=%s",
            settings.llm_provider,
            settings.runtime_strategy,
        )

        import asyncio

        from .grpc_server import serve_grpc

        grpc_task = asyncio.create_task(
            serve_grpc(llm_service, kb_service, worker, idempotency_store, settings)
        )

        yield

        await llm_service.aclose()
        grpc_task.cancel()

    app = FastAPI(title=settings.app_name, version=settings.app_version, lifespan=lifespan)
    app.state.settings = settings
    app.state.llm_service = llm_service
    app.state.kb_service = kb_service
    app.state.idempotency_store = idempotency_store
    app.state.worker = worker

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
            content={
                "code": "VALIDATION_FIELD",
                "message": str(exc),
                "traceId": trace_id,
            },
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
                return JSONResponse(
                    status_code=error.status_code,
                    content={
                        "code": error.code,
                        "message": error.message,
                        "traceId": request.headers.get("X-Trace-Id", ""),
                    },
                )

            if (
                path not in INTERNAL_OPEN_PATHS
                and not request.headers.get("X-Trace-Id", "").strip()
            ):
                return JSONResponse(
                    status_code=400,
                    content={
                        "code": "VALIDATION_PARAM",
                        "message": "X-Trace-Id is required",
                        "traceId": "",
                    },
                )

        return await call_next(request)

    @app.get("/health")
    async def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/internal/v1/ping")
    async def internal_ping() -> dict[str, str]:
        return {"status": "ok"}

    # ── Socratic Scaffold Chain ──────────────────────────────────────────

    @app.post("/internal/v1/socratic-probe")
    async def socratic_probe(request: Request) -> JSONResponse:
        from .prompts import socratic_probe_prompt
        from .repair import complete_with_json_repair

        body = await request.json()
        trace_id = request.headers.get("X-Trace-Id", "")
        round_number = int(body.get("roundNumber", 1))
        prompt = socratic_probe_prompt(
            question=body.get("question", ""),
            user_answer=body.get("userAnswer", ""),
            correct_answer=body.get("correctAnswer", ""),
            knowledge_points=body.get("knowledgePoints", []),
            round_number=round_number,
            student_responses=body.get("studentResponses", []),
        )
        parsed, result = await complete_with_json_repair(
            llm_service,
            prompt,
            scene="socratic_probe",
            trace_id=trace_id,
            repair_prompt_fn=lambda text: (
                "将下面文本转换为合法 JSON 对象：\n" + text
            ),
        )
        return JSONResponse(content={
            "data": parsed or {},
            "roundNumber": round_number,
            "provider": result.provider,
            "model": result.model,
            "reason": result.reason,
            "latencyMs": result.latency_ms,
            "traceId": trace_id,
        })

    # ── Knowledge Topology Explorer ──────────────────────────────────────

    @app.post("/internal/v1/knowledge-topology")
    async def knowledge_topology(request: Request) -> JSONResponse:
        from .prompts import knowledge_topology_prompt
        from .repair import complete_with_json_repair

        body = await request.json()
        trace_id = request.headers.get("X-Trace-Id", "")
        prompt = knowledge_topology_prompt(
            knowledge_points=body.get("knowledgePoints", []),
            mastery_data=body.get("masteryData", []),
        )
        parsed, result = await complete_with_json_repair(
            llm_service,
            prompt,
            scene="knowledge_topology",
            trace_id=trace_id,
            repair_prompt_fn=lambda text: (
                "将下面文本转换为合法 JSON 对象，包含 nodes 和 edges 两个数组字段：\n" + text
            ),
        )
        return JSONResponse(content={
            "data": parsed or {"nodes": [], "edges": []},
            "provider": result.provider,
            "model": result.model,
            "reason": result.reason,
            "latencyMs": result.latency_ms,
            "traceId": trace_id,
        })

    # ── Intervention Sandbox ─────────────────────────────────────────────

    @app.post("/internal/v1/intervention-sandbox")
    async def intervention_sandbox(request: Request) -> JSONResponse:
        from .prompts import intervention_sandbox_prompt
        from .repair import complete_with_json_repair

        body = await request.json()
        trace_id = request.headers.get("X-Trace-Id", "")
        prompt = intervention_sandbox_prompt(
            class_wrong_clusters=body.get("classWrongClusters", []),
            student_count=int(body.get("studentCount", 0)),
        )
        parsed, result = await complete_with_json_repair(
            llm_service,
            prompt,
            scene="intervention_sandbox",
            trace_id=trace_id,
            expect_array=True,
            repair_prompt_fn=lambda text: (
                "将下面文本转换为合法 JSON 数组，每个元素包含"
                " strategy_name/description/target_knowledge_points/"
                "estimated_minutes/estimated_fix_rate/target_student_count/priority：\n" + text
            ),
        )
        return JSONResponse(content={
            "data": parsed or [],
            "provider": result.provider,
            "model": result.model,
            "reason": result.reason,
            "latencyMs": result.latency_ms,
            "traceId": trace_id,
        })

    return app


app = create_app()
