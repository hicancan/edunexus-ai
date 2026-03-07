from __future__ import annotations

from dataclasses import dataclass

from .config import Settings

SCENE_PARAMS: dict[str, dict[str, float | int]] = {
    "chat_rag": {"temperature": 0.2, "max_tokens": 1200, "top_p": 0.9},
    "wrong_analysis": {"temperature": 0.3, "max_tokens": 1400, "top_p": 0.95},
    "ai_question": {"temperature": 0.55, "max_tokens": 1200, "top_p": 0.9},
    "lesson_plan": {"temperature": 0.35, "max_tokens": 1100, "top_p": 0.9},
}

STRUCTURED_SCENES = {"ai_question", "lesson_plan"}
REASONING_SCENES = {"wrong_analysis"}


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


# doc/08-AI与RAG策略 §3.1: chat_rag 场景说明
# 当前统一使用 ollama_rag_model (qwen3:8b) 用于所有 RAG 对话。
# 文档中描述的"轻量问答" vs "深度 RAG" 分级
# 可在后续版本中通过 ChatRequest.complexity 字段实现。
# 当前设计决策：统一使用 8b 保证回答质量，资源换质量。
def model_for_scene(settings: Settings, provider: str, scene: str) -> str:
    if provider == "ollama":
        if scene == "chat_rag":
            return settings.ollama_rag_model
        if scene == "lesson_plan":
            return settings.ollama_lesson_plan_model
        if scene in STRUCTURED_SCENES:
            return settings.ollama_structured_model
        if scene in REASONING_SCENES:
            return settings.ollama_complex_model
        return settings.ollama_model

    if provider == "deepseek":
        if scene in STRUCTURED_SCENES:
            return settings.deepseek_structured_model
        if scene in REASONING_SCENES:
            return settings.deepseek_complex_model
        return settings.deepseek_model

    if provider == "openai":
        if scene in STRUCTURED_SCENES:
            return settings.openai_structured_model
        if scene in REASONING_SCENES:
            return settings.openai_complex_model
        return settings.openai_model

    if provider == "gemini":
        if scene in STRUCTURED_SCENES:
            return settings.gemini_structured_model
        if scene in REASONING_SCENES:
            return settings.gemini_complex_model
        return settings.gemini_model

    return settings.ollama_model


def provider_candidates(settings: Settings, _scene: str) -> list[str]:
    selected = settings.llm_provider
    ordered = [selected] if selected != "auto" else ["ollama", "deepseek", "openai", "gemini"]

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
    elif scene == "lesson_plan" and provider == "ollama":
        reason = "教案生成优先使用本地结构化模型，控制延迟并稳定版式"
    elif scene in REASONING_SCENES:
        reason = "诊断分析任务使用高推理模型"
    elif scene in STRUCTURED_SCENES:
        reason = "结构化生成任务优先使用更稳的非推理模型"
    else:
        reason = "默认场景模型路由"
    return RouteDecision(provider=provider, model=model, reason=reason)
