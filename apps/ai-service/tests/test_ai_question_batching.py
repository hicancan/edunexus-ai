from __future__ import annotations

import asyncio
from dataclasses import dataclass

import grpc

from ai_service.ai_service_pb2 import AiQuestionGenerateRequest
from ai_service.config import Settings
from ai_service.servicers.ai_question import AiQuestionServicer


@dataclass
class _FakeResult:
    text: str
    provider: str = "ollama"
    model: str = "qwen3.5:4b"
    reason: str = "test-route"
    latency_ms: int = 12
    prompt_tokens: int = 32
    completion_tokens: int = 64


class _FakeLLM:
    def __init__(self, responses: list[str]) -> None:
        self._responses = responses
        self.prompts: list[str] = []

    async def complete(self, prompt: str, scene: str, trace_id: str):  # pragma: no cover - exercised by test
        self.prompts.append(prompt)
        index = min(len(self.prompts) - 1, len(self._responses) - 1)
        return _FakeResult(text=self._responses[index])


class _FakeContext:
    def __init__(self, token: str = "change-this-in-local-too", trace_id: str = "trace-test") -> None:
        self._metadata = (
            ("x-service-token", token),
            ("x-trace-id", trace_id),
        )

    def invocation_metadata(self):
        return self._metadata

    async def abort(self, code, details):
        raise RuntimeError(f"{code.name}:{details}")


def _settings() -> Settings:
    return Settings(
        app_name="edunexus-ai-service",
        app_version="1.0.0",
        log_level="INFO",
        runtime_strategy="云边端协同",
        llm_provider="ollama",
        ollama_base_url="http://127.0.0.1:11434",
        ollama_embed_model="qwen3-embedding:0.6b",
        ollama_model="qwen3.5:4b",
        ollama_rag_model="qwen3.5:4b",
        ollama_structured_model="qwen3.5:4b",
        ollama_lesson_plan_model="qwen3.5:9b",
        ollama_complex_model="deepseek-r1:8b",
        gemini_api_key="",
        gemini_model="gemini-2.0-flash",
        gemini_structured_model="gemini-2.0-flash",
        gemini_complex_model="gemini-1.5-pro",
        openai_api_key="",
        openai_base_url="https://api.openai.com/v1",
        openai_model="gpt-4o-mini",
        openai_structured_model="gpt-4o-mini",
        openai_complex_model="gpt-4.1",
        openai_embed_model="text-embedding-3-small",
        deepseek_api_key="",
        deepseek_base_url="https://api.deepseek.com/v1",
        deepseek_model="deepseek-chat",
        deepseek_structured_model="deepseek-chat",
        deepseek_complex_model="deepseek-chat",
        qdrant_url="http://127.0.0.1:6333",
        qdrant_api_key="",
        qdrant_collection="knowledge_chunks",
        embedding_dim=1024,
        java_grpc_url="127.0.0.1:9090",
        service_token="change-this-in-local-too",
        python_runner="uv",
        chat_rag_timeout_seconds=25.0,
        wrong_analysis_timeout_seconds=30.0,
        ai_question_timeout_seconds=120.0,
        lesson_plan_timeout_seconds=90.0,
    )


def _question_json(index: int) -> str:
    return (
        "["
        "{"
        f"\"question_type\":\"SINGLE_CHOICE\",\"content\":\"题目{index}A\",\"options\":{{\"A\":\"1\",\"B\":\"2\",\"C\":\"3\",\"D\":\"4\"}},"
        "\"correct_answer\":\"A\",\"explanation\":\"解析\",\"knowledge_points\":[\"牛顿第二定律\"]"
        "},"
        "{"
        f"\"question_type\":\"SINGLE_CHOICE\",\"content\":\"题目{index}B\",\"options\":{{\"A\":\"1\",\"B\":\"2\",\"C\":\"3\",\"D\":\"4\"}},"
        "\"correct_answer\":\"B\",\"explanation\":\"解析\",\"knowledge_points\":[\"牛顿第二定律\"]"
        "},"
        "{"
        f"\"question_type\":\"SHORT_ANSWER\",\"content\":\"题目{index}C\",\"options\":{{}},"
        "\"correct_answer\":\"质量与加速度\",\"explanation\":\"解析\",\"knowledge_points\":[\"牛顿第二定律\"]"
        "},"
        "{"
        f"\"question_type\":\"SINGLE_CHOICE\",\"content\":\"题目{index}D\",\"options\":{{\"A\":\"1\",\"B\":\"2\",\"C\":\"3\",\"D\":\"4\"}},"
        "\"correct_answer\":\"C\",\"explanation\":\"解析\",\"knowledge_points\":[\"牛顿第二定律\"]"
        "}"
        "]"
    )


def test_generate_batches_until_requested_count() -> None:
    llm = _FakeLLM([_question_json(1), _question_json(2)])
    servicer = AiQuestionServicer(llm, _settings())
    request = AiQuestionGenerateRequest(
        trace_id="trace-test",
        student_id="student-1",
        count=8,
        subject="物理",
        difficulty="MEDIUM",
        concept_tags=["牛顿第二定律"],
        weakness_profile="[]",
        teacher_suggestions="[]",
    )

    response = asyncio.run(servicer.Generate(request, _FakeContext()))

    assert len(response.questions) == 8
    assert "existing_questions" in llm.prompts[1]
    assert response.router_decision == "test-route"


def test_generate_fails_when_questions_are_insufficient() -> None:
    llm = _FakeLLM([_question_json(1), "[]", "[]", "[]"])
    servicer = AiQuestionServicer(llm, _settings())
    request = AiQuestionGenerateRequest(
        trace_id="trace-test",
        student_id="student-1",
        count=8,
        subject="物理",
        difficulty="MEDIUM",
        concept_tags=["牛顿第二定律"],
        weakness_profile="[]",
        teacher_suggestions="[]",
    )

    try:
        asyncio.run(servicer.Generate(request, _FakeContext()))
        raise AssertionError("Expected Generate to abort when output is insufficient.")
    except RuntimeError as exc:
        assert grpc.StatusCode.INTERNAL.name in str(exc)
        assert "expected 8, got 4" in str(exc)
