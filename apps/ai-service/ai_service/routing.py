from __future__ import annotations

from dataclasses import dataclass

from .config import Settings


SCENE_PARAMS: dict[str, dict[str, float | int]] = {
    "chat_rag": {"temperature": 0.2, "max_tokens": 1200, "top_p": 0.9},
    "wrong_analysis": {"temperature": 0.3, "max_tokens": 1400, "top_p": 0.95},
    "ai_question": {"temperature": 0.7, "max_tokens": 1800, "top_p": 0.95},
    "lesson_plan": {"temperature": 0.7, "max_tokens": 2200, "top_p": 0.95},
}

COMPLEX_SCENES = {"wrong_analysis", "ai_question", "lesson_plan"}


@dataclass(frozen=True, slots=True)
class RouteDecision:
    provider: str
    model: str
    reason: str


def scene_params(scene: str) -> dict[str, float | int]:
    return SCENE_PARAMS.get(scene, SCENE_PARAMS["chat_rag"])


def provider_available(settings: Settings, provider: str) -> bool:
    if provider == "ollama":
        return bool(settings.ollama_base_url)
    if provider == "deepseek":
        return bool(settings.deepseek_api_key)
    if provider == "openai":
        return bool(settings.openai_api_key)
    if provider == "gemini":
        return bool(settings.gemini_api_key)
    return False


def model_for_scene(settings: Settings, provider: str, scene: str) -> str:
    if provider == "ollama":
        if scene == "chat_rag":
            return settings.ollama_rag_model
        if scene in COMPLEX_SCENES:
            return settings.ollama_complex_model
        return settings.ollama_model

    if provider == "deepseek":
        return settings.deepseek_complex_model if scene in COMPLEX_SCENES else settings.deepseek_model

    if provider == "openai":
        return settings.openai_complex_model if scene in COMPLEX_SCENES else settings.openai_model

    if provider == "gemini":
        return settings.gemini_complex_model if scene in COMPLEX_SCENES else settings.gemini_model

    return settings.ollama_model


def provider_candidates(settings: Settings, scene: str) -> list[str]:
    selected = settings.llm_provider
    if selected != "auto":
        ordered = [selected, "ollama", "deepseek", "openai", "gemini"]
    else:
        ordered = ["ollama", "deepseek", "openai", "gemini"]

    seen: set[str] = set()
    out: list[str] = []
    for candidate in ordered:
        if candidate in seen:
            continue
        seen.add(candidate)
        if provider_available(settings, candidate):
            out.append(candidate)
    return out


def route_decision(settings: Settings, provider: str, scene: str) -> RouteDecision:
    model = model_for_scene(settings, provider, scene)
    if scene == "chat_rag" and provider == "ollama":
        reason = "RAG 场景优先使用本地 Ollama 主力模型"
    elif scene in COMPLEX_SCENES:
        reason = "复杂生成任务使用高推理模型"
    else:
        reason = "默认场景模型路由"
    return RouteDecision(provider=provider, model=model, reason=reason)
