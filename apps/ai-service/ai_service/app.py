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
        logger.info("ai_service startup provider=%s", settings.llm_provider)

        import asyncio

        from .grpc_server import serve_grpc

        grpc_task = asyncio.create_task(
            serve_grpc(llm_service, kb_service, worker, idempotency_store, settings)
        )

        yield

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

    return app


app = create_app()
