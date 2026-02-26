from __future__ import annotations

from typing import Any

from pydantic import AliasChoices, BaseModel, ConfigDict, Field, model_validator


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
    knowledge_points: list[str] = Field(default_factory=list, validation_alias=AliasChoices("knowledgePoints", "knowledge_points", "conceptTags", "concept_tags"))
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
    weakness_profile: list[dict[str, Any]] = Field(default_factory=list, validation_alias=AliasChoices("weaknessProfile", "weakness_profile", "wrong_context"))
    teacher_suggestions: list[dict[str, Any]] = Field(default_factory=list, validation_alias=AliasChoices("teacherSuggestions", "teacher_suggestions"))


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
    def normalize_aliases(cls, value: Any) -> Any:
        if not isinstance(value, dict):
            return value
        data = dict(value)
        alias_map = {
            "traceId": "trace_id",
            "documentId": "document_id",
            "teacherId": "teacher_id",
            "classId": "class_id",
            "filePath": "file_path",
        }
        for src, dst in alias_map.items():
            if src in data and dst not in data:
                data[dst] = data[src]
        return data


class KbDeleteRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    trace_id: str | None = None
    document_id: str

    @model_validator(mode="before")
    @classmethod
    def normalize_aliases(cls, value: Any) -> Any:
        if not isinstance(value, dict):
            return value
        data = dict(value)
        if "traceId" in data and "trace_id" not in data:
            data["trace_id"] = data["traceId"]
        if "documentId" in data and "document_id" not in data:
            data["document_id"] = data["documentId"]
        return data


class WrongAnalyzeResponse(BaseModel):
    encourage: str
    concept: str
    steps: list[str]
    rootCause: str
    nextPractice: str
