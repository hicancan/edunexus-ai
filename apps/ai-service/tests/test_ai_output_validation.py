import pytest
from pydantic import ValidationError

from ai_service.models import AIQuestionItem, WrongAnalyzeResponse


def test_ai_question_item_accepts_schema_compliant_items() -> None:
    item = AIQuestionItem.model_validate(
        {
            "question_type": "SINGLE_CHOICE",
            "content": "题干",
            "options": {"A": "1", "B": "2", "C": "3", "D": "4"},
            "correct_answer": "A",
            "explanation": "解释",
            "knowledge_points": ["知识点"],
        }
    )
    assert item.question_type == "SINGLE_CHOICE"
    assert item.correct_answer == "A"


def test_ai_question_item_rejects_incomplete_options() -> None:
    with pytest.raises(ValidationError):
        AIQuestionItem.model_validate(
            {
                "question_type": "SINGLE_CHOICE",
                "content": "题干",
                "options": {"A": "1", "B": "2"},
                "correct_answer": "A",
                "explanation": "解释",
                "knowledge_points": ["知识点"],
            }
        )


def test_ai_question_item_allows_short_answer_without_options() -> None:
    item = AIQuestionItem.model_validate(
        {
            "question_type": "SHORT_ANSWER",
            "content": "请写出牛顿第二定律",
            "options": {},
            "correct_answer": "F=ma",
            "explanation": "定义式",
            "knowledge_points": ["牛顿第二定律"],
        }
    )
    assert item.question_type == "SHORT_ANSWER"
    assert item.options == {}


def test_wrong_analyze_response_rejects_invalid_steps_range() -> None:
    with pytest.raises(ValidationError):
        WrongAnalyzeResponse.model_validate(
            {
                "encourage": "继续加油",
                "concept": "受力分析",
                "steps": ["只给一步"],
                "rootCause": "概念混淆",
                "nextPractice": "再做三题",
            }
        )


def test_ai_question_item_remaps_true_false_to_single_choice() -> None:
    """TRUE_FALSE is not accepted by Java — the Python model remaps it to SINGLE_CHOICE."""
    item = AIQuestionItem.model_validate(
        {
            "question_type": "TRUE_FALSE",
            "content": "牛顿第一定律也称惯性定律",
            "options": {"A": "正确", "B": "错误", "C": "不确定", "D": "以上都不是"},
            "correct_answer": "A",
            "explanation": "惯性定律就是牛顿第一定律",
            "knowledge_points": ["牛顿第一定律"],
        }
    )
    assert item.question_type == "SINGLE_CHOICE"


def test_ai_question_item_remaps_essay_to_short_answer() -> None:
    """ESSAY / OPEN_ENDED are remapped to SHORT_ANSWER."""
    for variant in ("ESSAY", "OPEN_ENDED"):
        item = AIQuestionItem.model_validate(
            {
                "question_type": variant,
                "content": "请阐述牛顿第二定律的含义",
                "options": {},
                "correct_answer": "F=ma",
                "explanation": "力等于质量乘加速度",
                "knowledge_points": ["牛顿第二定律"],
            }
        )
        assert item.question_type == "SHORT_ANSWER"


def test_ai_question_item_rejects_unknown_type() -> None:
    """Question types not in the remap table or allowed set should raise ValidationError."""
    with pytest.raises(ValidationError, match="unsupported"):
        AIQuestionItem.model_validate(
            {
                "question_type": "FILL_IN_THE_BLANK",
                "content": "题干",
                "options": {"A": "1", "B": "2", "C": "3", "D": "4"},
                "correct_answer": "A",
                "explanation": "解释",
                "knowledge_points": ["知识点"],
            }
        )
