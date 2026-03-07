from __future__ import annotations

import time

import grpc

from ..ai_service_pb2 import ChatResponse, ChatStreamResponse, Citation
from ..ai_service_pb2_grpc import RagChatServiceServicer
from ..config import Settings
from ..errors import InternalServiceError
from ..kb import KnowledgeBaseService
from ..llm import LLMService
from ..prompts import chat_prompt, uncertain_answer
from .base import abort_internal_error, require_internal_auth


class RagChatServicer(RagChatServiceServicer):
    def __init__(
        self,
        llm_service: LLMService,
        kb_service: KnowledgeBaseService,
        settings: Settings,
    ) -> None:
        self.llm = llm_service
        self.kb = kb_service
        self.settings = settings

    async def Chat(self, request, context: grpc.aio.ServicerContext):
        metadata_trace, _ = await require_internal_auth(context, self.settings, require_trace=True)
        trace_id = request.trace_id or metadata_trace
        teacher_id = request.teacher_scope.teacher_id if request.HasField("teacher_scope") else ""
        class_id = request.teacher_scope.class_id if request.HasField("teacher_scope") else ""
        started = time.perf_counter()

        contexts = await self.kb.retrieve(
            request.message, teacher_id=teacher_id, class_id=class_id, top_k=5
        )
        contexts = [ctx for ctx in contexts if ctx.get("score", 0.0) >= 0.35]

        citations = [
            Citation(
                document_id=row.get("document_id", ""),
                filename=row.get("filename", "unknown"),
                chunk_index=row.get("chunk_index", 0),
                content=row.get("content", ""),
                score=row.get("score", 0.0),
            )
            for row in contexts[:3]
        ]

        if not contexts:
            latency_ms = int((time.perf_counter() - started) * 1000)
            return ChatResponse(
                answer=uncertain_answer(),
                citations=[],
                provider=self.settings.llm_provider,
                model="",
                token_usage=ChatResponse.TokenUsage(prompt=0, completion=0),
                latency_ms=latency_ms,
            )

        context_for_prompt = "\n\n".join(
            f"[{row['filename']}] {row['content'][:550]}" for row in contexts[:3]
        )
        history = [{"role": msg.role, "content": msg.content} for msg in request.context.history]
        prompt = chat_prompt(request.message, context_for_prompt, history)

        try:
            result = await self.llm.complete(
                prompt,
                scene="chat_rag",
                trace_id=trace_id,
                hit_kb=True,
                chunk_ids=[
                    f"{row.get('document_id')}:{row.get('chunk_index')}" for row in contexts[:3]
                ],
            )
            return ChatResponse(
                answer=result.text,
                citations=citations,
                provider=result.provider,
                model=result.model,
                token_usage=ChatResponse.TokenUsage(
                    prompt=result.prompt_tokens,
                    completion=result.completion_tokens,
                ),
                latency_ms=result.latency_ms,
            )
        except InternalServiceError as error:
            await abort_internal_error(context, error)

    async def ChatStream(self, request, context: grpc.aio.ServicerContext):
        metadata_trace, _ = await require_internal_auth(context, self.settings, require_trace=True)
        trace_id = request.trace_id or metadata_trace
        teacher_id = request.teacher_scope.teacher_id if request.HasField("teacher_scope") else ""
        class_id = request.teacher_scope.class_id if request.HasField("teacher_scope") else ""

        contexts = await self.kb.retrieve(
            request.message, teacher_id=teacher_id, class_id=class_id, top_k=5
        )
        contexts = [ctx for ctx in contexts if ctx.get("score", 0.0) >= 0.35]

        citations = [
            Citation(
                document_id=row.get("document_id", ""),
                filename=row.get("filename", "unknown"),
                chunk_index=row.get("chunk_index", 0),
                content=row.get("content", ""),
                score=row.get("score", 0.0),
            )
            for row in contexts[:3]
        ]

        if not contexts:
            yield ChatStreamResponse(delta=uncertain_answer(), citations=[])
            return

        context_for_prompt = "\n\n".join(
            f"[{row['filename']}] {row['content'][:550]}" for row in contexts[:3]
        )
        history = [{"role": msg.role, "content": msg.content} for msg in request.context.history]
        prompt = chat_prompt(request.message, context_for_prompt, history)

        yield ChatStreamResponse(delta="", citations=citations)

        try:
            emitted = False
            async for delta in self.llm.stream(
                prompt,
                scene="chat_rag",
                trace_id=trace_id,
                hit_kb=True,
                chunk_ids=[
                    f"{row.get('document_id')}:{row.get('chunk_index')}" for row in contexts[:3]
                ],
            ):
                if not delta:
                    continue
                emitted = True
                yield ChatStreamResponse(delta=delta, citations=[])

            if not emitted:
                yield ChatStreamResponse(delta=uncertain_answer(), citations=[])
        except InternalServiceError as error:
            await abort_internal_error(context, error)
