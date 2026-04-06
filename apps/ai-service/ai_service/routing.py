from __future__ import annotations

from dataclasses import dataclass

from .config import Settings

SCENE_PARAMS: dict[str, dict[str, float | int]] = {
    "chat_rag": {"temperature": 0.2, "max_tokens": 1200, "top_p": 0.9},
    "wrong_analysis": {"temperature": 0.3, "max_tokens": 1400, "top_p": 0.95},
    "ai_question": {"temperature": 0.55, "max_tokens": 1200, "top_p": 0.9},
    "ai_question_large": {"temperature": 0.45, "max_tokens": 4200, "top_p": 0.9},
    "teacher_suggestion": {"temperature": 0.35, "max_tokens": 1200, "top_p": 0.9},
    "lesson_plan": {"temperature": 0.3, "max_tokens": 1800, "top_p": 0.9},
    "socratic_probe": {"temperature": 0.25, "max_tokens": 600, "top_p": 0.9},
    "knowledge_topology": {"temperature": 0.2, "max_tokens": 2000, "top_p": 0.9},
    "intervention_sandbox": {"temperature": 0.3, "max_tokens": 1500, "top_p": 0.9},
}

STRUCTURED_SCENES = {
    "ai_question", "ai_question_large", "teacher_suggestion", "lesson_plan",
    "knowledge_topology", "intervention_sandbox",
}
REASONING_SCENES = {"wrong_analysis"}
SCENE_PROVIDER_PREFERENCE: dict[str, list[str]] = {
    "chat_rag": ["ollama", "deepseek", "openai", "gemini"],
    "ai_question": ["ollama", "deepseek", "openai", "gemini"],
    "ai_question_large": ["deepseek", "ollama", "openai", "gemini"],
    "wrong_analysis": ["deepseek", "ollama", "openai", "gemini"],
    "teacher_suggestion": ["deepseek", "ollama", "openai", "gemini"],
    "lesson_plan": ["deepseek", "ollama", "openai", "gemini"],
    "socratic_probe": ["ollama", "deepseek", "openai", "gemini"],
    "knowledge_topology": ["deepseek", "ollama", "openai", "gemini"],
    "intervention_sandbox": ["deepseek", "ollama", "openai", "gemini"],
}


@dataclass(frozen=True, slots=True)
class RouteDecision:
    provider: str
    model: str
    reason: str


def normalize_runtime_strategy(raw_strategy: str | None) -> str:
    if not raw_strategy:
        return "云边端协同"
    normalized = raw_strategy.strip().lower()
    if "全云" in normalized or "cloud" in normalized:
        return "全云推理"
    if "边侧" in normalized or "edge" in normalized:
        return "边侧优先"
    if "云边" in normalized or "hybrid" in normalized:
        return "云边端协同"
    return raw_strategy.strip()


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
    strategy = normalize_runtime_strategy(settings.runtime_strategy)
    default_order = SCENE_PROVIDER_PREFERENCE.get(
        _scene, ["ollama", "deepseek", "openai", "gemini"]
    )
    if selected != "auto":
        ordered = [selected]
    elif strategy == "全云推理":
        ordered = [candidate for candidate in default_order if candidate != "ollama"]
    elif strategy == "边侧优先":
        ordered = ["ollama", *default_order]
    else:
        ordered = default_order

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
    elif scene == "ai_question" and provider == "ollama":
        reason = "课堂轻量练习优先使用边侧模型，兼顾低时延与连续交互"
    elif scene == "ai_question_large" and provider == "deepseek":
        reason = "大批量练习生成优先上云，保证复杂生成质量与稳定性"
    elif scene == "teacher_suggestion" and provider == "ollama":
        reason = "教师建议草案使用本地结构化模型，优先保证格式稳定与可确认性"
    elif scene == "teacher_suggestion" and provider == "deepseek":
        reason = "教师建议属于复杂教学支持任务，优先使用云侧主模型"
    elif scene == "lesson_plan" and provider == "ollama":
        reason = "教案生成在云侧主模型不可用时回退到本地结构化模型"
    elif scene == "lesson_plan" and provider == "deepseek":
        reason = "教案生成优先使用云侧主模型，保证复杂生成质量"
    elif scene == "wrong_analysis" and provider == "deepseek":
        reason = "错因归因优先使用云侧高推理模型"
    elif scene == "socratic_probe" and provider == "ollama":
        reason = "苏格拉底追问使用边侧模型，保证课堂低延迟交互"
    elif scene == "socratic_probe" and provider == "deepseek":
        reason = "苏格拉底追问回退至云侧模型"
    elif scene == "knowledge_topology" and provider == "deepseek":
        reason = "知识拓扑推断需要跨概念关联能力，优先使用云侧主模型"
    elif scene == "knowledge_topology" and provider == "ollama":
        reason = "知识拓扑推断回退至本地结构化模型"
    elif scene == "intervention_sandbox" and provider == "deepseek":
        reason = "干预策略推演需要综合推理能力，优先使用云侧主模型"
    elif scene == "intervention_sandbox" and provider == "ollama":
        reason = "干预策略推演回退至本地结构化模型"
    elif scene in REASONING_SCENES:
        reason = "诊断分析任务使用高推理模型"
    elif scene in STRUCTURED_SCENES:
        reason = "结构化生成任务优先使用更稳的非推理模型"
    else:
        reason = "默认场景模型路由"
    return RouteDecision(provider=provider, model=model, reason=reason)
