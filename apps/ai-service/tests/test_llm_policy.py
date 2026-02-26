from __future__ import annotations

from ai_service.llm import SCENE_RETRY_DELAY_SECONDS, SCENE_TIMEOUT_SECONDS


def test_scene_timeout_baseline_matches_internal_contract() -> None:
    assert SCENE_TIMEOUT_SECONDS == {
        "chat_rag": 25.0,
        "wrong_analysis": 30.0,
        "ai_question": 45.0,
        "lesson_plan": 60.0,
    }


def test_scene_retry_delay_baseline_matches_internal_contract() -> None:
    assert SCENE_RETRY_DELAY_SECONDS == {
        "chat_rag": 0.5,
        "wrong_analysis": 0.8,
        "ai_question": 1.0,
        "lesson_plan": 1.2,
    }
