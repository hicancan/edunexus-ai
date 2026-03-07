from __future__ import annotations

import grpc

from ..ai_service_pb2 import ExerciseAnalysisResponse
from ..ai_service_pb2_grpc import ExerciseAnalysisServiceServicer
from ..config import Settings
from ..errors import InternalServiceError
from ..llm import LLMService
from ..prompts import wrong_analysis_prompt
from ..repair import complete_with_json_repair
from .base import abort_internal_error, require_internal_auth


class ExerciseAnalysisServicer(ExerciseAnalysisServiceServicer):
    def __init__(self, llm_service: LLMService, settings: Settings) -> None:
        self.llm = llm_service
        self.settings = settings

    async def Analyze(self, request, context: grpc.aio.ServicerContext):
        metadata_trace, _ = await require_internal_auth(context, self.settings, require_trace=True)
        trace_id = request.trace_id or metadata_trace

        prompt = wrong_analysis_prompt(
            question=request.question,
            user_answer=request.user_answer,
            correct_answer=request.correct_answer,
            knowledge_points=list(request.knowledge_points),
            teacher_suggestion=request.teacher_suggestion,
        )
        try:
            parsed, _ = await complete_with_json_repair(
                self.llm,
                prompt,
                scene="wrong_analysis",
                trace_id=trace_id,
                repair_prompt_fn=lambda text: (
                    "将下面文本转换为合法 JSON 对象，"
                    "字段为 encourage/concept/steps/rootCause/nextPractice：\n" + text
                ),
            )
            if parsed is None:
                await context.abort(grpc.StatusCode.INTERNAL, "Output is not valid JSON")
            return ExerciseAnalysisResponse(
                encourage=parsed.get("encourage", ""),
                concept=parsed.get("concept", ""),
                steps=parsed.get("steps", []),
                root_cause=parsed.get("rootCause", ""),
                next_practice=parsed.get("nextPractice", ""),
            )
        except InternalServiceError as error:
            await abort_internal_error(context, error)
