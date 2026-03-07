from __future__ import annotations

import grpc

from ..ai_service_pb2 import LessonPlanGenerateResponse
from ..ai_service_pb2_grpc import LessonPlanServiceServicer
from ..config import Settings
from ..errors import InternalServiceError
from ..llm import LLMService
from ..prompts import (
    has_valid_plan_structure,
    lesson_plan_prompt,
    lesson_plan_repair_prompt,
    sanitize_lesson_plan_markdown,
)
from .base import abort_internal_error, require_internal_auth

GENERATION_TIMEOUT_SECONDS = 40.0
GENERATION_PROVIDER_TIMEOUT_SECONDS = 28.0
REPAIR_TIMEOUT_SECONDS = 24.0
REPAIR_PROVIDER_TIMEOUT_SECONDS = 18.0


class LessonPlanServicer(LessonPlanServiceServicer):
    def __init__(self, llm_service: LLMService, settings: Settings) -> None:
        self.llm = llm_service
        self.settings = settings

    async def Generate(self, request, context: grpc.aio.ServicerContext):
        metadata_trace, _ = await require_internal_auth(context, self.settings, require_trace=True)
        trace_id = request.trace_id or metadata_trace

        prompt = lesson_plan_prompt(request.topic, request.grade_level, request.duration_mins)
        try:
            result = await self.llm.complete(
                prompt,
                scene="lesson_plan",
                trace_id=trace_id,
                timeout_budget_override=GENERATION_TIMEOUT_SECONDS,
                provider_timeout_cap_override=GENERATION_PROVIDER_TIMEOUT_SECONDS,
                max_attempts=1,
            )
            final_result = result
            content = sanitize_lesson_plan_markdown(result.text.strip())
            if not has_valid_plan_structure(content, request.duration_mins):
                repaired = await self.llm.complete(
                    lesson_plan_repair_prompt(content, request.duration_mins),
                    scene="lesson_plan",
                    trace_id=trace_id,
                    timeout_budget_override=REPAIR_TIMEOUT_SECONDS,
                    provider_timeout_cap_override=REPAIR_PROVIDER_TIMEOUT_SECONDS,
                    max_attempts=1,
                )
                final_result = repaired
                content = sanitize_lesson_plan_markdown(repaired.text.strip())
                if not has_valid_plan_structure(content, request.duration_mins):
                    await context.abort(
                        grpc.StatusCode.INTERNAL,
                        "Lesson plan structure is incomplete",
                    )
            return LessonPlanGenerateResponse(
                content_md=content,
                provider=final_result.provider,
                model=final_result.model,
                latency_ms=final_result.latency_ms,
            )
        except InternalServiceError as error:
            await abort_internal_error(context, error)
