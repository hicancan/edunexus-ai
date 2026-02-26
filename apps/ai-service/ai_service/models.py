from __future__ import annotations

import json
from typing import Any

from pydantic import AliasChoices, BaseModel, ConfigDict, Field, field_validator, model_validator


def _clean_text(value: Any) -> str:
    return str(value or "").strip()


class TeacherScope(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    teacher_id: str | None = Field(default=None, validation_alias=AliasChoices("teacherId", "teacher_id"))
    class_id: str | None = Field(default=None, validation_alias=AliasChoices("classId", "class_id"))


class ChatContextMessage(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    role: str
    content: str


class ChatContext(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    history: list[ChatContextMessage] = Field(default_factory=list)


class ChatRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    trace_id: str | None = Field(default=None, validation_alias=AliasChoices("traceId", "trace_id"))
    session_id: str = Field(validation_alias=AliasChoices("sessionId", "session_id"))
    student_id: str = Field(validation_alias=AliasChoices("studentId", "student_id"))
    message: str
    stream: bool = False
    scene: str = "chat_rag"

    teacher_scope: TeacherScope | None = Field(default=None, validation_alias=AliasChoices("teacherScope", "teacher_scope"))
    teacher_id: str | None = Field(default=None, validation_alias=AliasChoices("teacherId", "teacher_id"))
    class_id: str | None = Field(default=None, validation_alias=AliasChoices("classId", "class_id"))
    context: ChatContext | None = None

    def scope(self) -> tuple[str | None, str | None]:
        teacher_id = self.teacher_id
        class_id = self.class_id
        if self.teacher_scope is not None:
            teacher_id = teacher_id or self.teacher_scope.teacher_id
            class_id = class_id or self.teacher_scope.class_id
        return teacher_id, class_id


class WrongAnalyzeRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    trace_id: str | None = Field(default=None, validation_alias=AliasChoices("traceId", "trace_id"))
    scene: str = "wrong_analysis"

    question: str
    user_answer: str = Field(validation_alias=AliasChoices("userAnswer", "user_answer", "studentAnswer", "student_answer"))
    correct_answer: str = Field(validation_alias=AliasChoices("correctAnswer", "correct_answer"))
    knowledge_points: list[str] = Field(
        default_factory=list,
        validation_alias=AliasChoices("knowledgePoints", "knowledge_points", "conceptTags", "concept_tags"),
    )
    teacher_suggestion: str | None = Field(default=None, validation_alias=AliasChoices("teacherSuggestion", "teacher_suggestion"))


class AIQGenerateRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    trace_id: str | None = Field(default=None, validation_alias=AliasChoices("traceId", "trace_id"))
    scene: str = "ai_question"

    student_id: str | None = Field(default=None, validation_alias=AliasChoices("studentId", "student_id"))
    count: int = Field(default=5, ge=1, le=20)
    subject: str
    difficulty: str = "MEDIUM"
    concept_tags: list[str] = Field(default_factory=list, validation_alias=AliasChoices("conceptTags", "concept_tags"))
    weakness_profile: list[dict[str, Any]] = Field(
        default_factory=list,
        validation_alias=AliasChoices("weaknessProfile", "weakness_profile", "wrong_context"),
    )
    teacher_suggestions: list[dict[str, Any]] = Field(
        default_factory=list,
        validation_alias=AliasChoices("teacherSuggestions", "teacher_suggestions"),
    )


class LessonPlanRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    trace_id: str | None = Field(default=None, validation_alias=AliasChoices("traceId", "trace_id"))
    scene: str = "lesson_plan"

    topic: str
    grade_level: str = Field(validation_alias=AliasChoices("gradeLevel", "grade_level"))
    duration_mins: int = Field(validation_alias=AliasChoices("durationMins", "duration_mins"), ge=10, le=240)
    teacher_id: str | None = Field(default=None, validation_alias=AliasChoices("teacherId", "teacher_id"))


class KbIngestRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    trace_id: str | None = None
    document_id: str
    teacher_id: str
    class_id: str | None = None
    filename: str
    file_path: str

    @model_validator(mode="before")
    @classmethod
    def _normalize_aliases(cls, value: Any) -> Any:
        if not isinstance(value, dict):
            return value

        alias_map = {
            "traceId": "trace_id",
            "documentId": "document_id",
            "teacherId": "teacher_id",
            "classId": "class_id",
            "filePath": "file_path",
        }
        data = dict(value)
        for source, target in alias_map.items():
            if source in data and target not in data:
                data[target] = data[source]
        return data


class KbDeleteRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    trace_id: str | None = None
    document_id: str

    @model_validator(mode="before")
    @classmethod
    def _normalize_aliases(cls, value: Any) -> Any:
        if not isinstance(value, dict):
            return value

        data = dict(value)
        if "traceId" in data and "trace_id" not in data:
            data["trace_id"] = data["traceId"]
        if "documentId" in data and "document_id" not in data:
            data["document_id"] = data["documentId"]
        return data


class AIQuestionItem(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    content: str
    options: dict[str, str]
    correct_answer: str = Field(validation_alias=AliasChoices("correct_answer", "correctAnswer"))
    explanation: str
    knowledge_points: list[str] = Field(
        validation_alias=AliasChoices("knowledge_points", "knowledgePoints")
    )

    @field_validator("content", "explanation", mode="before")
    @classmethod
    def _required_text(cls, value: Any) -> str:
        cleaned = _clean_text(value)
        if not cleaned:
            raise ValueError("text field cannot be empty")
        return cleaned

    @field_validator("options", mode="before")
    @classmethod
    def _normalize_options(cls, value: Any) -> dict[str, str]:
        options_raw = value
        if isinstance(options_raw, str):
            try:
                options_raw = json.loads(options_raw)
            except json.JSONDecodeError as ex:
                raise ValueError("options must be valid JSON object") from ex
        if not isinstance(options_raw, dict):
            raise ValueError("options must be object")

        normalized: dict[str, str] = {}
        for key, option_value in options_raw.items():
            label = _clean_text(key).upper()
            content = _clean_text(option_value)
            if label and content:
                normalized[label] = content
        return normalized

    @field_validator("correct_answer", mode="before")
    @classmethod
    def _normalize_answer(cls, value: Any) -> str:
        answer = _clean_text(value).upper()
        if not answer:
            raise ValueError("correct_answer cannot be empty")
        return answer

    @field_validator("knowledge_points", mode="before")
    @classmethod
    def _normalize_kp(cls, value: Any) -> list[str]:
        if not isinstance(value, list):
            raise ValueError("knowledge_points must be list")
        out = [_clean_text(item) for item in value]
        return [item for item in out if item]

    @field_validator("options")
    @classmethod
    def _validate_options(cls, value: dict[str, str]) -> dict[str, str]:
        if len(value) < 4:
            raise ValueError("options must contain at least 4 items")
        for label in ("A", "B", "C", "D"):
            if label not in value:
                raise ValueError("options must contain A/B/C/D")
        return value

    @field_validator("knowledge_points")
    @classmethod
    def _validate_kp(cls, value: list[str]) -> list[str]:
        if not value:
            raise ValueError("knowledge_points cannot be empty")
        return value

    @field_validator("correct_answer")
    @classmethod
    def _validate_answer_in_options(cls, value: str, info: Any) -> str:
        options = info.data.get("options") if hasattr(info, "data") else None
        if isinstance(options, dict) and value not in options:
            raise ValueError("correct_answer must exist in options")
        return value


class WrongAnalyzeResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    encourage: str
    concept: str
    steps: list[str]
    rootCause: str = Field(validation_alias=AliasChoices("rootCause", "root_cause"))
    nextPractice: str = Field(validation_alias=AliasChoices("nextPractice", "next_practice"))

    @field_validator("encourage", "concept", "rootCause", "nextPractice", mode="before")
    @classmethod
    def _required_output_text(cls, value: Any) -> str:
        cleaned = _clean_text(value)
        if not cleaned:
            raise ValueError("field cannot be empty")
        return cleaned

    @field_validator("steps", mode="before")
    @classmethod
    def _normalize_steps(cls, value: Any) -> list[str]:
        if isinstance(value, str):
            value = [value]
        if not isinstance(value, list):
            raise ValueError("steps must be list")
        out = [_clean_text(step) for step in value]
        return [step for step in out if step]

    @field_validator("steps")
    @classmethod
    def _validate_steps(cls, value: list[str]) -> list[str]:
        if not 2 <= len(value) <= 6:
            raise ValueError("steps must contain 2-6 items")
        return value
