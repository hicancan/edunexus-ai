from ai_service.config import Settings
from ai_service.routing import model_for_scene, provider_candidates


def _settings(provider: str = "auto") -> Settings:
    return Settings(
        app_name="svc",
        app_version="1.0.0",
        log_level="INFO",
        llm_provider=provider,
        ollama_base_url="http://127.0.0.1:11434",
        ollama_embed_model="qwen3-embedding:0.6b",
        ollama_model="qwen3:4b",
        ollama_rag_model="qwen3:8b",
        ollama_complex_model="deepseek-r1:8b",
        gemini_api_key="",
        gemini_model="gemini-2.0-flash",
        gemini_complex_model="gemini-1.5-pro",
        openai_api_key="",
        openai_base_url="https://api.openai.com/v1",
        openai_model="gpt-4o-mini",
        openai_complex_model="gpt-4.1",
        openai_embed_model="text-embedding-3-small",
        deepseek_api_key="sk-test",
        deepseek_base_url="https://api.deepseek.com/v1",
        deepseek_model="deepseek-chat",
        deepseek_complex_model="deepseek-reasoner",
        qdrant_url="http://127.0.0.1:6333",
        qdrant_api_key="",
        qdrant_collection="knowledge_chunks",
        embedding_dim=1024,
        service_token="token",
        py_env_name="edunexus-ai",
        python_runner="uv",
        enforce_conda_env=False,
    )


def test_auto_provider_candidates_prefers_ollama() -> None:
    settings = _settings(provider="auto")
    candidates = provider_candidates(settings, "chat_rag")
    assert candidates[0] == "ollama"
    assert "deepseek" in candidates


def test_scene_model_mapping_matches_contract() -> None:
    settings = _settings(provider="auto")
    assert model_for_scene(settings, "ollama", "chat_rag") == "qwen3:8b"
    assert model_for_scene(settings, "ollama", "ai_question") == "deepseek-r1:8b"
