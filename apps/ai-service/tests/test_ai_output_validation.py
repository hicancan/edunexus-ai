import pytest

from ai_service.app import _validate_questions, _validate_wrong_analysis
from ai_service.errors import InternalServiceError


def test_validate_questions_accepts_schema_compliant_items() -> None:
    rows = [
        {
            "content": "题干",
            "options": {"A": "1", "B": "2", "C": "3", "D": "4"},
            "correct_answer": "A",
            "explanation": "解释",
            "knowledge_points": ["知识点"],
        }
    ]
    out = _validate_questions(rows, 1)
    assert len(out) == 1
    assert out[0]["correct_answer"] == "A"


def test_validate_questions_requires_exact_count() -> None:
    rows = [
        {
            "content": "题干",
            "options": {"A": "1", "B": "2", "C": "3", "D": "4"},
            "correct_answer": "A",
            "explanation": "解释",
            "knowledge_points": ["知识点"],
        }
    ]
    out = _validate_questions(rows, 2)
    assert out == []


def test_validate_questions_rejects_incomplete_items() -> None:
    rows = [
        {
            "content": "题干",
            "options": {"A": "1", "B": "2"},
            "correct_answer": "A",
            "explanation": "解释",
            "knowledge_points": ["知识点"],
        }
    ]
    out = _validate_questions(rows, 1)
    assert out == []


def test_validate_wrong_analysis_rejects_invalid_steps_range() -> None:
    with pytest.raises(InternalServiceError) as ex:
        _validate_wrong_analysis(
            {
                "encourage": "继续加油",
                "concept": "受力分析",
                "steps": ["只给一步"],
                "rootCause": "概念混淆",
                "nextPractice": "再做三题",
            }
        )
    assert ex.value.code == "INTERNAL_OUTPUT_INVALID"
