import datetime

from google.protobuf import timestamp_pb2 as _timestamp_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class ChatRequest(_message.Message):
    __slots__ = ("trace_id", "session_id", "student_id", "teacher_scope", "message", "stream", "context")
    class TeacherScope(_message.Message):
        __slots__ = ("teacher_id", "class_id")
        TEACHER_ID_FIELD_NUMBER: _ClassVar[int]
        CLASS_ID_FIELD_NUMBER: _ClassVar[int]
        teacher_id: str
        class_id: str
        def __init__(self, teacher_id: _Optional[str] = ..., class_id: _Optional[str] = ...) -> None: ...
    class Context(_message.Message):
        __slots__ = ("history",)
        class Message(_message.Message):
            __slots__ = ("role", "content", "timestamp")
            ROLE_FIELD_NUMBER: _ClassVar[int]
            CONTENT_FIELD_NUMBER: _ClassVar[int]
            TIMESTAMP_FIELD_NUMBER: _ClassVar[int]
            role: str
            content: str
            timestamp: _timestamp_pb2.Timestamp
            def __init__(self, role: _Optional[str] = ..., content: _Optional[str] = ..., timestamp: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...
        HISTORY_FIELD_NUMBER: _ClassVar[int]
        history: _containers.RepeatedCompositeFieldContainer[ChatRequest.Context.Message]
        def __init__(self, history: _Optional[_Iterable[_Union[ChatRequest.Context.Message, _Mapping]]] = ...) -> None: ...
    TRACE_ID_FIELD_NUMBER: _ClassVar[int]
    SESSION_ID_FIELD_NUMBER: _ClassVar[int]
    STUDENT_ID_FIELD_NUMBER: _ClassVar[int]
    TEACHER_SCOPE_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    STREAM_FIELD_NUMBER: _ClassVar[int]
    CONTEXT_FIELD_NUMBER: _ClassVar[int]
    trace_id: str
    session_id: str
    student_id: str
    teacher_scope: ChatRequest.TeacherScope
    message: str
    stream: bool
    context: ChatRequest.Context
    def __init__(self, trace_id: _Optional[str] = ..., session_id: _Optional[str] = ..., student_id: _Optional[str] = ..., teacher_scope: _Optional[_Union[ChatRequest.TeacherScope, _Mapping]] = ..., message: _Optional[str] = ..., stream: bool = ..., context: _Optional[_Union[ChatRequest.Context, _Mapping]] = ...) -> None: ...

class Citation(_message.Message):
    __slots__ = ("document_id", "filename", "chunk_index", "content", "score")
    DOCUMENT_ID_FIELD_NUMBER: _ClassVar[int]
    FILENAME_FIELD_NUMBER: _ClassVar[int]
    CHUNK_INDEX_FIELD_NUMBER: _ClassVar[int]
    CONTENT_FIELD_NUMBER: _ClassVar[int]
    SCORE_FIELD_NUMBER: _ClassVar[int]
    document_id: str
    filename: str
    chunk_index: int
    content: str
    score: float
    def __init__(self, document_id: _Optional[str] = ..., filename: _Optional[str] = ..., chunk_index: _Optional[int] = ..., content: _Optional[str] = ..., score: _Optional[float] = ...) -> None: ...

class ChatResponse(_message.Message):
    __slots__ = ("answer", "citations", "provider", "model", "token_usage", "latency_ms", "error_code", "error_message")
    class TokenUsage(_message.Message):
        __slots__ = ("prompt", "completion")
        PROMPT_FIELD_NUMBER: _ClassVar[int]
        COMPLETION_FIELD_NUMBER: _ClassVar[int]
        prompt: int
        completion: int
        def __init__(self, prompt: _Optional[int] = ..., completion: _Optional[int] = ...) -> None: ...
    ANSWER_FIELD_NUMBER: _ClassVar[int]
    CITATIONS_FIELD_NUMBER: _ClassVar[int]
    PROVIDER_FIELD_NUMBER: _ClassVar[int]
    MODEL_FIELD_NUMBER: _ClassVar[int]
    TOKEN_USAGE_FIELD_NUMBER: _ClassVar[int]
    LATENCY_MS_FIELD_NUMBER: _ClassVar[int]
    ERROR_CODE_FIELD_NUMBER: _ClassVar[int]
    ERROR_MESSAGE_FIELD_NUMBER: _ClassVar[int]
    answer: str
    citations: _containers.RepeatedCompositeFieldContainer[Citation]
    provider: str
    model: str
    token_usage: ChatResponse.TokenUsage
    latency_ms: int
    error_code: str
    error_message: str
    def __init__(self, answer: _Optional[str] = ..., citations: _Optional[_Iterable[_Union[Citation, _Mapping]]] = ..., provider: _Optional[str] = ..., model: _Optional[str] = ..., token_usage: _Optional[_Union[ChatResponse.TokenUsage, _Mapping]] = ..., latency_ms: _Optional[int] = ..., error_code: _Optional[str] = ..., error_message: _Optional[str] = ...) -> None: ...

class ChatStreamResponse(_message.Message):
    __slots__ = ("delta", "citations")
    DELTA_FIELD_NUMBER: _ClassVar[int]
    CITATIONS_FIELD_NUMBER: _ClassVar[int]
    delta: str
    citations: _containers.RepeatedCompositeFieldContainer[Citation]
    def __init__(self, delta: _Optional[str] = ..., citations: _Optional[_Iterable[_Union[Citation, _Mapping]]] = ...) -> None: ...

class ExerciseAnalysisRequest(_message.Message):
    __slots__ = ("trace_id", "idempotency_key", "question", "user_answer", "correct_answer", "knowledge_points", "teacher_suggestion")
    TRACE_ID_FIELD_NUMBER: _ClassVar[int]
    IDEMPOTENCY_KEY_FIELD_NUMBER: _ClassVar[int]
    QUESTION_FIELD_NUMBER: _ClassVar[int]
    USER_ANSWER_FIELD_NUMBER: _ClassVar[int]
    CORRECT_ANSWER_FIELD_NUMBER: _ClassVar[int]
    KNOWLEDGE_POINTS_FIELD_NUMBER: _ClassVar[int]
    TEACHER_SUGGESTION_FIELD_NUMBER: _ClassVar[int]
    trace_id: str
    idempotency_key: str
    question: str
    user_answer: str
    correct_answer: str
    knowledge_points: _containers.RepeatedScalarFieldContainer[str]
    teacher_suggestion: str
    def __init__(self, trace_id: _Optional[str] = ..., idempotency_key: _Optional[str] = ..., question: _Optional[str] = ..., user_answer: _Optional[str] = ..., correct_answer: _Optional[str] = ..., knowledge_points: _Optional[_Iterable[str]] = ..., teacher_suggestion: _Optional[str] = ...) -> None: ...

class ExerciseAnalysisResponse(_message.Message):
    __slots__ = ("encourage", "concept", "steps", "root_cause", "next_practice", "error_code", "error_message")
    ENCOURAGE_FIELD_NUMBER: _ClassVar[int]
    CONCEPT_FIELD_NUMBER: _ClassVar[int]
    STEPS_FIELD_NUMBER: _ClassVar[int]
    ROOT_CAUSE_FIELD_NUMBER: _ClassVar[int]
    NEXT_PRACTICE_FIELD_NUMBER: _ClassVar[int]
    ERROR_CODE_FIELD_NUMBER: _ClassVar[int]
    ERROR_MESSAGE_FIELD_NUMBER: _ClassVar[int]
    encourage: str
    concept: str
    steps: _containers.RepeatedScalarFieldContainer[str]
    root_cause: str
    next_practice: str
    error_code: str
    error_message: str
    def __init__(self, encourage: _Optional[str] = ..., concept: _Optional[str] = ..., steps: _Optional[_Iterable[str]] = ..., root_cause: _Optional[str] = ..., next_practice: _Optional[str] = ..., error_code: _Optional[str] = ..., error_message: _Optional[str] = ...) -> None: ...

class AiQuestionGenerateRequest(_message.Message):
    __slots__ = ("trace_id", "idempotency_key", "student_id", "count", "subject", "difficulty", "concept_tags", "weakness_profile", "teacher_suggestions")
    TRACE_ID_FIELD_NUMBER: _ClassVar[int]
    IDEMPOTENCY_KEY_FIELD_NUMBER: _ClassVar[int]
    STUDENT_ID_FIELD_NUMBER: _ClassVar[int]
    COUNT_FIELD_NUMBER: _ClassVar[int]
    SUBJECT_FIELD_NUMBER: _ClassVar[int]
    DIFFICULTY_FIELD_NUMBER: _ClassVar[int]
    CONCEPT_TAGS_FIELD_NUMBER: _ClassVar[int]
    WEAKNESS_PROFILE_FIELD_NUMBER: _ClassVar[int]
    TEACHER_SUGGESTIONS_FIELD_NUMBER: _ClassVar[int]
    trace_id: str
    idempotency_key: str
    student_id: str
    count: int
    subject: str
    difficulty: str
    concept_tags: _containers.RepeatedScalarFieldContainer[str]
    weakness_profile: str
    teacher_suggestions: str
    def __init__(self, trace_id: _Optional[str] = ..., idempotency_key: _Optional[str] = ..., student_id: _Optional[str] = ..., count: _Optional[int] = ..., subject: _Optional[str] = ..., difficulty: _Optional[str] = ..., concept_tags: _Optional[_Iterable[str]] = ..., weakness_profile: _Optional[str] = ..., teacher_suggestions: _Optional[str] = ...) -> None: ...

class GeneratedQuestion(_message.Message):
    __slots__ = ("question_type", "content", "options", "correct_answer", "explanation", "knowledge_points")
    class OptionsEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: str
        def __init__(self, key: _Optional[str] = ..., value: _Optional[str] = ...) -> None: ...
    QUESTION_TYPE_FIELD_NUMBER: _ClassVar[int]
    CONTENT_FIELD_NUMBER: _ClassVar[int]
    OPTIONS_FIELD_NUMBER: _ClassVar[int]
    CORRECT_ANSWER_FIELD_NUMBER: _ClassVar[int]
    EXPLANATION_FIELD_NUMBER: _ClassVar[int]
    KNOWLEDGE_POINTS_FIELD_NUMBER: _ClassVar[int]
    question_type: str
    content: str
    options: _containers.ScalarMap[str, str]
    correct_answer: str
    explanation: str
    knowledge_points: _containers.RepeatedScalarFieldContainer[str]
    def __init__(self, question_type: _Optional[str] = ..., content: _Optional[str] = ..., options: _Optional[_Mapping[str, str]] = ..., correct_answer: _Optional[str] = ..., explanation: _Optional[str] = ..., knowledge_points: _Optional[_Iterable[str]] = ...) -> None: ...

class AiQuestionGenerateResponse(_message.Message):
    __slots__ = ("questions", "router_decision", "error_code", "error_message")
    QUESTIONS_FIELD_NUMBER: _ClassVar[int]
    ROUTER_DECISION_FIELD_NUMBER: _ClassVar[int]
    ERROR_CODE_FIELD_NUMBER: _ClassVar[int]
    ERROR_MESSAGE_FIELD_NUMBER: _ClassVar[int]
    questions: _containers.RepeatedCompositeFieldContainer[GeneratedQuestion]
    router_decision: str
    error_code: str
    error_message: str
    def __init__(self, questions: _Optional[_Iterable[_Union[GeneratedQuestion, _Mapping]]] = ..., router_decision: _Optional[str] = ..., error_code: _Optional[str] = ..., error_message: _Optional[str] = ...) -> None: ...

class TeachingSuggestionCandidate(_message.Message):
    __slots__ = ("knowledge_point", "student_count", "total_wrong_count")
    KNOWLEDGE_POINT_FIELD_NUMBER: _ClassVar[int]
    STUDENT_COUNT_FIELD_NUMBER: _ClassVar[int]
    TOTAL_WRONG_COUNT_FIELD_NUMBER: _ClassVar[int]
    knowledge_point: str
    student_count: int
    total_wrong_count: int
    def __init__(self, knowledge_point: _Optional[str] = ..., student_count: _Optional[int] = ..., total_wrong_count: _Optional[int] = ...) -> None: ...

class GeneratedTeachingSuggestion(_message.Message):
    __slots__ = ("knowledge_point", "suggestion_template")
    KNOWLEDGE_POINT_FIELD_NUMBER: _ClassVar[int]
    SUGGESTION_TEMPLATE_FIELD_NUMBER: _ClassVar[int]
    knowledge_point: str
    suggestion_template: str
    def __init__(self, knowledge_point: _Optional[str] = ..., suggestion_template: _Optional[str] = ...) -> None: ...

class TeachingSuggestionGenerateRequest(_message.Message):
    __slots__ = ("trace_id", "idempotency_key", "teacher_id", "candidates")
    TRACE_ID_FIELD_NUMBER: _ClassVar[int]
    IDEMPOTENCY_KEY_FIELD_NUMBER: _ClassVar[int]
    TEACHER_ID_FIELD_NUMBER: _ClassVar[int]
    CANDIDATES_FIELD_NUMBER: _ClassVar[int]
    trace_id: str
    idempotency_key: str
    teacher_id: str
    candidates: _containers.RepeatedCompositeFieldContainer[TeachingSuggestionCandidate]
    def __init__(self, trace_id: _Optional[str] = ..., idempotency_key: _Optional[str] = ..., teacher_id: _Optional[str] = ..., candidates: _Optional[_Iterable[_Union[TeachingSuggestionCandidate, _Mapping]]] = ...) -> None: ...

class TeachingSuggestionGenerateResponse(_message.Message):
    __slots__ = ("suggestions", "provider", "model", "latency_ms", "router_decision", "error_code", "error_message")
    SUGGESTIONS_FIELD_NUMBER: _ClassVar[int]
    PROVIDER_FIELD_NUMBER: _ClassVar[int]
    MODEL_FIELD_NUMBER: _ClassVar[int]
    LATENCY_MS_FIELD_NUMBER: _ClassVar[int]
    ROUTER_DECISION_FIELD_NUMBER: _ClassVar[int]
    ERROR_CODE_FIELD_NUMBER: _ClassVar[int]
    ERROR_MESSAGE_FIELD_NUMBER: _ClassVar[int]
    suggestions: _containers.RepeatedCompositeFieldContainer[GeneratedTeachingSuggestion]
    provider: str
    model: str
    latency_ms: int
    router_decision: str
    error_code: str
    error_message: str
    def __init__(self, suggestions: _Optional[_Iterable[_Union[GeneratedTeachingSuggestion, _Mapping]]] = ..., provider: _Optional[str] = ..., model: _Optional[str] = ..., latency_ms: _Optional[int] = ..., router_decision: _Optional[str] = ..., error_code: _Optional[str] = ..., error_message: _Optional[str] = ...) -> None: ...

class LessonPlanGenerateRequest(_message.Message):
    __slots__ = ("trace_id", "idempotency_key", "topic", "grade_level", "duration_mins", "teacher_id")
    TRACE_ID_FIELD_NUMBER: _ClassVar[int]
    IDEMPOTENCY_KEY_FIELD_NUMBER: _ClassVar[int]
    TOPIC_FIELD_NUMBER: _ClassVar[int]
    GRADE_LEVEL_FIELD_NUMBER: _ClassVar[int]
    DURATION_MINS_FIELD_NUMBER: _ClassVar[int]
    TEACHER_ID_FIELD_NUMBER: _ClassVar[int]
    trace_id: str
    idempotency_key: str
    topic: str
    grade_level: str
    duration_mins: int
    teacher_id: str
    def __init__(self, trace_id: _Optional[str] = ..., idempotency_key: _Optional[str] = ..., topic: _Optional[str] = ..., grade_level: _Optional[str] = ..., duration_mins: _Optional[int] = ..., teacher_id: _Optional[str] = ...) -> None: ...

class LessonPlanGenerateResponse(_message.Message):
    __slots__ = ("content_md", "provider", "model", "latency_ms", "error_code", "error_message")
    CONTENT_MD_FIELD_NUMBER: _ClassVar[int]
    PROVIDER_FIELD_NUMBER: _ClassVar[int]
    MODEL_FIELD_NUMBER: _ClassVar[int]
    LATENCY_MS_FIELD_NUMBER: _ClassVar[int]
    ERROR_CODE_FIELD_NUMBER: _ClassVar[int]
    ERROR_MESSAGE_FIELD_NUMBER: _ClassVar[int]
    content_md: str
    provider: str
    model: str
    latency_ms: int
    error_code: str
    error_message: str
    def __init__(self, content_md: _Optional[str] = ..., provider: _Optional[str] = ..., model: _Optional[str] = ..., latency_ms: _Optional[int] = ..., error_code: _Optional[str] = ..., error_message: _Optional[str] = ...) -> None: ...

class KbIngestRequest(_message.Message):
    __slots__ = ("trace_id", "idempotency_key", "job_id", "document_id", "teacher_id", "class_id", "filename", "file_type", "file_content")
    TRACE_ID_FIELD_NUMBER: _ClassVar[int]
    IDEMPOTENCY_KEY_FIELD_NUMBER: _ClassVar[int]
    JOB_ID_FIELD_NUMBER: _ClassVar[int]
    DOCUMENT_ID_FIELD_NUMBER: _ClassVar[int]
    TEACHER_ID_FIELD_NUMBER: _ClassVar[int]
    CLASS_ID_FIELD_NUMBER: _ClassVar[int]
    FILENAME_FIELD_NUMBER: _ClassVar[int]
    FILE_TYPE_FIELD_NUMBER: _ClassVar[int]
    FILE_CONTENT_FIELD_NUMBER: _ClassVar[int]
    trace_id: str
    idempotency_key: str
    job_id: str
    document_id: str
    teacher_id: str
    class_id: str
    filename: str
    file_type: str
    file_content: bytes
    def __init__(self, trace_id: _Optional[str] = ..., idempotency_key: _Optional[str] = ..., job_id: _Optional[str] = ..., document_id: _Optional[str] = ..., teacher_id: _Optional[str] = ..., class_id: _Optional[str] = ..., filename: _Optional[str] = ..., file_type: _Optional[str] = ..., file_content: _Optional[bytes] = ...) -> None: ...

class KbIngestResponse(_message.Message):
    __slots__ = ("status", "job_id", "background", "chunks", "error_code", "error_message")
    STATUS_FIELD_NUMBER: _ClassVar[int]
    JOB_ID_FIELD_NUMBER: _ClassVar[int]
    BACKGROUND_FIELD_NUMBER: _ClassVar[int]
    CHUNKS_FIELD_NUMBER: _ClassVar[int]
    ERROR_CODE_FIELD_NUMBER: _ClassVar[int]
    ERROR_MESSAGE_FIELD_NUMBER: _ClassVar[int]
    status: str
    job_id: str
    background: bool
    chunks: int
    error_code: str
    error_message: str
    def __init__(self, status: _Optional[str] = ..., job_id: _Optional[str] = ..., background: bool = ..., chunks: _Optional[int] = ..., error_code: _Optional[str] = ..., error_message: _Optional[str] = ...) -> None: ...

class KbDeleteRequest(_message.Message):
    __slots__ = ("trace_id", "idempotency_key", "document_id")
    TRACE_ID_FIELD_NUMBER: _ClassVar[int]
    IDEMPOTENCY_KEY_FIELD_NUMBER: _ClassVar[int]
    DOCUMENT_ID_FIELD_NUMBER: _ClassVar[int]
    trace_id: str
    idempotency_key: str
    document_id: str
    def __init__(self, trace_id: _Optional[str] = ..., idempotency_key: _Optional[str] = ..., document_id: _Optional[str] = ...) -> None: ...

class KbDeleteResponse(_message.Message):
    __slots__ = ("status", "error_code", "error_message")
    STATUS_FIELD_NUMBER: _ClassVar[int]
    ERROR_CODE_FIELD_NUMBER: _ClassVar[int]
    ERROR_MESSAGE_FIELD_NUMBER: _ClassVar[int]
    status: str
    error_code: str
    error_message: str
    def __init__(self, status: _Optional[str] = ..., error_code: _Optional[str] = ..., error_message: _Optional[str] = ...) -> None: ...
