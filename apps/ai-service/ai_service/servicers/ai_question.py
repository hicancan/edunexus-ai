from __future__ import annotations

import json
from typing import Any

import grpc
from pydantic import ValidationError

from ..ai_service_pb2 import AiQuestionGenerateResponse, GeneratedQuestion
from ..ai_service_pb2_grpc import AiQuestionServiceServicer
from ..config import Settings
from ..errors import InternalServiceError
from ..llm import LLMService
from ..models import AIQuestionItem
from ..prompts import aiq_prompt, aiq_repair_prompt
from ..repair import complete_with_json_repair
from .base import abort_internal_error, require_internal_auth


def _validate_questions(rows: list[dict[str, Any]], count: int) -> list[GeneratedQuestion]:
    """Validate and convert LLM-generated question dicts.

    Returns as many valid questions as possible (up to `count`).
    An empty list means the LLM produced nothing usable.
    """
    output: list[GeneratedQuestion] = []
    for row in rows:
        try:
            question = AIQuestionItem.model_validate(row)
        except ValidationError:
            continue
        output.append(
            GeneratedQuestion(
                question_type=question.question_type,
                content=question.content,
                options=question.options,
                correct_answer=question.correct_answer,
                explanation=question.explanation,
                knowledge_points=question.knowledge_points,
            )
        )
        if len(output) >= count:
            break
    return output  # return whatever is valid; caller decides if it's enough


class AiQuestionServicer(AiQuestionServiceServicer):
    def __init__(self, llm_service: LLMService, settings: Settings) -> None:
        self.llm = llm_service
        self.settings = settings

    async def Generate(self, request, context: grpc.aio.ServicerContext):
        metadata_trace, _ = await require_internal_auth(context, self.settings, require_trace=True)
        trace_id = request.trace_id or metadata_trace

        # weakness_profile and teacher_suggestions arrive as JSON strings
        # (serialized by Java's AiQuestionService.toJson()); parse them back.
        def _parse_list(raw: str) -> list[Any]:
            if not raw:
                return []
            try:
                parsed = json.loads(raw)
                return parsed if isinstance(parsed, list) else []
            except Exception:
                return []

        prompt = aiq_prompt(
            subject=request.subject,
            difficulty=request.difficulty,
            count=request.count,
            concept_tags=list(request.concept_tags),
            weakness_profile=_parse_list(request.weakness_profile),
            teacher_suggestions=_parse_list(request.teacher_suggestions),
        )
        try:
            parsed_array, result = await complete_with_json_repair(
                self.llm,
                prompt,
                scene="ai_question",
                trace_id=trace_id,
                expect_array=True,
                repair_prompt_fn=lambda text: aiq_repair_prompt(text, request.count),
            )
            questions = _validate_questions(parsed_array or [], request.count)
            if not questions:
                await context.abort(
                    grpc.StatusCode.INTERNAL,
                    "AI question output invalid after repair",
                )
                return AiQuestionGenerateResponse()
            return AiQuestionGenerateResponse(
                questions=questions,
                router_decision=result.reason,
            )
        except InternalServiceError as error:
            await abort_internal_error(context, error)
