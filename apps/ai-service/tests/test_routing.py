from dataclasses import replace

from ai_service.config import Settings
from ai_service.routing import model_for_scene, provider_candidates


def _settings(provider: str = "auto") -> Settings:
    return Settings(
        app_name="svc",
        app_version="1.0.0",
        log_level="INFO",
        runtime_strategy="云边端协同",
        llm_provider=provider,
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
        deepseek_api_key="sk-test",
        deepseek_base_url="https://api.deepseek.com/v1",
        deepseek_model="deepseek-chat",
        deepseek_structured_model="deepseek-chat",
        deepseek_complex_model="deepseek-reasoner",
        qdrant_url="http://127.0.0.1:6333",
        qdrant_api_key="",
        qdrant_collection="knowledge_chunks",
        embedding_dim=1024,
        java_grpc_url="localhost:9090",
        service_token="token",
        python_runner="uv",
        chat_rag_timeout_seconds=25.0,
        wrong_analysis_timeout_seconds=30.0,
        ai_question_timeout_seconds=120.0,
        lesson_plan_timeout_seconds=60.0,
    )


def test_auto_provider_candidates_prefers_ollama() -> None:
    settings = _settings(provider="auto")
    candidates = provider_candidates(settings, "chat_rag")
    assert candidates[0] == "ollama"
    assert "deepseek" in candidates


def test_large_ai_question_prefers_cloud_provider() -> None:
    settings = _settings(provider="auto")
    candidates = provider_candidates(settings, "ai_question_large")
    assert candidates[0] == "deepseek"
    assert "ollama" in candidates


def test_lesson_plan_prefers_cloud_provider() -> None:
    settings = _settings(provider="auto")
    candidates = provider_candidates(settings, "lesson_plan")
    assert candidates[0] == "deepseek"
    assert "ollama" in candidates


def test_explicit_provider_candidates_are_strict() -> None:
    settings = _settings(provider="ollama")
    candidates = provider_candidates(settings, "chat_rag")
    assert candidates == ["ollama"]


def test_full_cloud_strategy_excludes_ollama() -> None:
    settings = _settings(provider="auto")
    settings = replace(settings, runtime_strategy="全云推理")
    candidates = provider_candidates(settings, "chat_rag")
    assert "ollama" not in candidates
    assert candidates[0] == "deepseek"


def test_edge_priority_strategy_prefers_ollama_even_for_large_generation() -> None:
    settings = _settings(provider="auto")
    settings = replace(settings, runtime_strategy="边侧优先")
    candidates = provider_candidates(settings, "ai_question_large")
    assert candidates[0] == "ollama"
    assert "deepseek" in candidates


def test_scene_model_mapping_matches_contract() -> None:
    settings = _settings(provider="auto")
    assert model_for_scene(settings, "ollama", "chat_rag") == "qwen3.5:4b"
    assert model_for_scene(settings, "ollama", "ai_question") == "qwen3.5:4b"
    assert model_for_scene(settings, "deepseek", "ai_question_large") == "deepseek-chat"
    assert model_for_scene(settings, "ollama", "lesson_plan") == "qwen3.5:9b"
