from ai_service.app import _validate_questions


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
