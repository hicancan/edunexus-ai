from __future__ import annotations

from ai_service.config import load_settings
from ai_service.llm import (
    SCENE_PROVIDER_TIMEOUT_CAP_SECONDS,
    SCENE_RETRY_DELAY_SECONDS,
    LLMService,
)


def test_scene_timeout_from_settings_matches_defaults() -> None:
    """Timeouts now come from Settings, not a hardcoded dict.
    Verify the LLMService._scene_timeout_seconds method returns the correct
    defaults when no env overrides are present.
    """
    settings = load_settings()
    svc = LLMService(settings)
    assert svc._scene_timeout_seconds("chat_rag") == settings.chat_rag_timeout_seconds
    assert svc._scene_timeout_seconds("wrong_analysis") == settings.wrong_analysis_timeout_seconds
    assert svc._scene_timeout_seconds("ai_question") == settings.ai_question_timeout_seconds
    assert svc._scene_timeout_seconds("ai_question_large") == settings.ai_question_timeout_seconds
    assert svc._scene_timeout_seconds("lesson_plan") == settings.lesson_plan_timeout_seconds
    # unknown scene falls back to 45.0
    assert svc._scene_timeout_seconds("unknown_scene") == 45.0


def test_scene_retry_delay_baseline_matches_internal_contract() -> None:
    assert SCENE_RETRY_DELAY_SECONDS == {
        "chat_rag": 0.5,
        "wrong_analysis": 0.8,
        "ai_question": 1.0,
        "ai_question_large": 1.2,
        "teacher_suggestion": 1.0,
        "lesson_plan": 1.2,
    }


def test_scene_provider_timeout_caps_leave_room_for_fallbacks() -> None:
    assert SCENE_PROVIDER_TIMEOUT_CAP_SECONDS == {
        "chat_rag": 12.0,
        "wrong_analysis": 18.0,
        "ai_question": 40.0,
        "ai_question_large": 75.0,
        "teacher_suggestion": 24.0,
        "lesson_plan": 28.0,
    }
