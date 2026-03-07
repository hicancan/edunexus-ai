from __future__ import annotations

import pytest

from ai_service.config import Settings, validate_runtime_policy


def _settings(**overrides: object) -> Settings:
    base = {
        "app_name": "svc",
        "app_version": "1.0.0",
        "log_level": "INFO",
        "llm_provider": "auto",
        "ollama_base_url": "http://127.0.0.1:11434",
        "ollama_embed_model": "qwen3-embedding:0.6b",
        "ollama_model": "qwen3:4b",
        "ollama_rag_model": "qwen3:8b",
        "ollama_structured_model": "qwen3:8b",
        "ollama_lesson_plan_model": "qwen3:8b",
        "ollama_complex_model": "deepseek-r1:8b",
        "gemini_api_key": "",
        "gemini_model": "gemini-2.0-flash",
        "gemini_structured_model": "gemini-2.0-flash",
        "gemini_complex_model": "gemini-1.5-pro",
        "openai_api_key": "",
        "openai_base_url": "https://api.openai.com/v1",
        "openai_model": "gpt-4o-mini",
        "openai_structured_model": "gpt-4o-mini",
        "openai_complex_model": "gpt-4.1",
        "openai_embed_model": "text-embedding-3-small",
        "deepseek_api_key": "",
        "deepseek_base_url": "https://api.deepseek.com/v1",
        "deepseek_model": "deepseek-chat",
        "deepseek_structured_model": "deepseek-chat",
        "deepseek_complex_model": "deepseek-reasoner",
        "qdrant_url": "http://127.0.0.1:6333",
        "qdrant_api_key": "",
        "qdrant_collection": "knowledge_chunks",
        "embedding_dim": 1024,
        "java_grpc_url": "localhost:9090",
        "service_token": "token",
        "python_runner": "uv",
        "chat_rag_timeout_seconds": 25.0,
        "wrong_analysis_timeout_seconds": 30.0,
        "ai_question_timeout_seconds": 120.0,
        "lesson_plan_timeout_seconds": 60.0,
    }
    base.update(overrides)
    return Settings(**base)


def test_validate_runtime_policy_accepts_uv_runner() -> None:
    validate_runtime_policy(_settings())


def test_validate_runtime_policy_rejects_non_uv_runner() -> None:
    with pytest.raises(RuntimeError, match="PYTHON_RUNNER"):
        validate_runtime_policy(_settings(python_runner="pip"))
