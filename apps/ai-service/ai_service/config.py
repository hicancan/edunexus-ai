from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv


SERVICE_DIR = Path(__file__).resolve().parent.parent
PROJECT_ROOT = SERVICE_DIR.parent.parent


def load_env_files() -> None:
    load_dotenv(PROJECT_ROOT / ".env", override=False)
    load_dotenv(SERVICE_DIR / ".env", override=False)


@dataclass(frozen=True, slots=True)
class Settings:
    app_name: str
    app_version: str
    log_level: str

    llm_provider: str

    ollama_base_url: str
    ollama_embed_model: str
    ollama_model: str
    ollama_rag_model: str
    ollama_complex_model: str

    gemini_api_key: str
    gemini_model: str
    gemini_complex_model: str

    openai_api_key: str
    openai_base_url: str
    openai_model: str
    openai_complex_model: str
    openai_embed_model: str

    deepseek_api_key: str
    deepseek_base_url: str
    deepseek_model: str
    deepseek_complex_model: str

    qdrant_url: str
    qdrant_api_key: str
    qdrant_collection: str
    embedding_dim: int

    service_token: str

    py_env_provider: str
    py_env_name: str
    python_runner: str


def load_settings() -> Settings:
    load_env_files()
    return Settings(
        app_name="edunexus-ai-service",
        app_version="1.0.0",
        log_level=os.getenv("LOG_LEVEL", "INFO").upper(),
        llm_provider=os.getenv("LLM_PROVIDER", "auto").strip().lower(),
        ollama_base_url=os.getenv("OLLAMA_BASE_URL", "http://127.0.0.1:11434").rstrip("/"),
        ollama_embed_model=os.getenv("OLLAMA_EMBED_MODEL", "qwen3-embedding:0.6b"),
        ollama_model=os.getenv("OLLAMA_MODEL", "qwen3:4b"),
        ollama_rag_model=os.getenv("OLLAMA_RAG_MODEL", "qwen3:8b"),
        ollama_complex_model=os.getenv("OLLAMA_COMPLEX_MODEL", "deepseek-r1:8b"),
        gemini_api_key=(os.getenv("GEMINI_API_KEY", "") or os.getenv("GOOGLE_API_KEY", "")).strip(),
        gemini_model=os.getenv("GEMINI_MODEL", "gemini-2.0-flash"),
        gemini_complex_model=os.getenv("GEMINI_COMPLEX_MODEL", "gemini-1.5-pro"),
        openai_api_key=os.getenv("OPENAI_API_KEY", "").strip(),
        openai_base_url=os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1").rstrip("/"),
        openai_model=os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
        openai_complex_model=os.getenv("OPENAI_COMPLEX_MODEL", "gpt-4.1"),
        openai_embed_model=os.getenv("OPENAI_EMBED_MODEL", "text-embedding-3-small"),
        deepseek_api_key=os.getenv("DEEPSEEK_API_KEY", "").strip(),
        deepseek_base_url=os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1").rstrip("/"),
        deepseek_model=os.getenv("DEEPSEEK_MODEL", "deepseek-chat"),
        deepseek_complex_model=os.getenv("DEEPSEEK_COMPLEX_MODEL", "deepseek-reasoner"),
        qdrant_url=os.getenv("QDRANT_URL", "http://127.0.0.1:6333").rstrip("/"),
        qdrant_api_key=os.getenv("QDRANT_API_KEY", "").strip(),
        qdrant_collection=os.getenv("QDRANT_COLLECTION", "knowledge_chunks"),
        embedding_dim=int(os.getenv("EMBEDDING_DIM", "1024")),
        service_token=os.getenv("AI_SERVICE_TOKEN", "change-this-in-local-too").strip(),
        py_env_provider=os.getenv("PY_ENV_PROVIDER", "conda").strip().lower(),
        py_env_name=os.getenv("PY_ENV_NAME", "edunexus-ai").strip(),
        python_runner=os.getenv("PYTHON_RUNNER", "uv").strip().lower(),
    )


def validate_runtime_policy(settings: Settings) -> None:
    if settings.py_env_provider != "conda":
        raise RuntimeError("PY_ENV_PROVIDER must be conda")
    if settings.py_env_name != "edunexus-ai":
        raise RuntimeError("PY_ENV_NAME must be edunexus-ai")
    if settings.python_runner != "uv":
        raise RuntimeError("PYTHON_RUNNER must be uv")

    active_conda = os.getenv("CONDA_DEFAULT_ENV", "").strip()
    if active_conda != "edunexus-ai":
        raise RuntimeError("Conda env edunexus-ai is required")
