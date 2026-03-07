from __future__ import annotations

import logging

import grpc

from .ai_service_pb2_grpc import (
    add_AiQuestionServiceServicer_to_server,
    add_ExerciseAnalysisServiceServicer_to_server,
    add_KnowledgeBaseServiceServicer_to_server,
    add_LessonPlanServiceServicer_to_server,
    add_RagChatServiceServicer_to_server,
)
from .config import Settings
from .idempotency import IdempotencyStore
from .kb import KnowledgeBaseService
from .llm import LLMService
from .servicers import (
    AiQuestionServicer,
    ExerciseAnalysisServicer,
    KnowledgeBaseServicer,
    LessonPlanServicer,
    RagChatServicer,
)
from .worker import AsyncWorker

logger = logging.getLogger("edunexus.ai.grpc")


async def serve_grpc(
    llm_service: LLMService,
    kb_service: KnowledgeBaseService,
    worker: AsyncWorker,
    idempotency_store: IdempotencyStore,
    settings: Settings,
) -> None:
    server = grpc.aio.server()
    add_RagChatServiceServicer_to_server(
        RagChatServicer(llm_service, kb_service, settings),
        server,
    )
    add_ExerciseAnalysisServiceServicer_to_server(
        ExerciseAnalysisServicer(llm_service, settings),
        server,
    )
    add_LessonPlanServiceServicer_to_server(
        LessonPlanServicer(llm_service, settings),
        server,
    )
    add_AiQuestionServiceServicer_to_server(
        AiQuestionServicer(llm_service, settings),
        server,
    )
    add_KnowledgeBaseServiceServicer_to_server(
        KnowledgeBaseServicer(kb_service, worker, idempotency_store, settings),
        server,
    )

    listen_addr = "[::]:50051"
    server.add_insecure_port(listen_addr)
    logger.info("Starting gRPC Server on %s", listen_addr)
    await server.start()
    await server.wait_for_termination()
