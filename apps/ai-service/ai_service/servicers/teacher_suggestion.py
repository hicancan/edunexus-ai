from __future__ import annotations

import grpc

from ..ai_service_pb2 import GeneratedTeachingSuggestion, TeachingSuggestionGenerateResponse
from ..ai_service_pb2_grpc import TeachingSuggestionServiceServicer
from ..config import Settings
from ..errors import InternalServiceError
from ..llm import LLMService
from ..models import TeachingSuggestionItem
from ..prompts import teacher_suggestion_prompt, teacher_suggestion_repair_prompt
from ..repair import complete_with_json_repair
from .base import abort_internal_error, require_internal_auth


def _validate_suggestions(
    rows: list[dict], knowledge_points: set[str]
) -> list[GeneratedTeachingSuggestion]:
    output: list[GeneratedTeachingSuggestion] = []
    seen: set[str] = set()
    for row in rows:
        try:
            suggestion = TeachingSuggestionItem.model_validate(row)
        except Exception:
            continue
        if suggestion.knowledge_point not in knowledge_points or suggestion.knowledge_point in seen:
            continue
        seen.add(suggestion.knowledge_point)
        output.append(
            GeneratedTeachingSuggestion(
                knowledge_point=suggestion.knowledge_point,
                suggestion_template=suggestion.suggestion_template,
            )
        )
    return output


class TeachingSuggestionServicer(TeachingSuggestionServiceServicer):
    def __init__(self, llm_service: LLMService, settings: Settings) -> None:
        self.llm = llm_service
        self.settings = settings

    async def Generate(self, request, context: grpc.aio.ServicerContext):
        metadata_trace, _ = await require_internal_auth(context, self.settings, require_trace=True)
        trace_id = request.trace_id or metadata_trace

        candidates = [
            {
                "knowledge_point": candidate.knowledge_point,
                "student_count": candidate.student_count,
                "total_wrong_count": candidate.total_wrong_count,
            }
            for candidate in request.candidates
            if candidate.knowledge_point
        ]
        if not candidates:
            return TeachingSuggestionGenerateResponse(suggestions=[])

        knowledge_points = {item["knowledge_point"] for item in candidates}
        prompt = teacher_suggestion_prompt(candidates)
        try:
            parsed_array, result = await complete_with_json_repair(
                self.llm,
                prompt,
                scene="teacher_suggestion",
                trace_id=trace_id,
                expect_array=True,
                repair_prompt_fn=lambda text: teacher_suggestion_repair_prompt(
                    text, sorted(knowledge_points)
                ),
            )
            suggestions = _validate_suggestions(parsed_array or [], knowledge_points)
            if not suggestions:
                await context.abort(
                    grpc.StatusCode.INTERNAL,
                    "Teacher suggestion output invalid after repair",
                )
                return TeachingSuggestionGenerateResponse()
            return TeachingSuggestionGenerateResponse(
                suggestions=suggestions,
                provider=result.provider,
                model=result.model,
                latency_ms=result.latency_ms,
                router_decision=result.reason,
            )
        except InternalServiceError as error:
            await abort_internal_error(context, error)
