import datetime

from google.protobuf import timestamp_pb2 as _timestamp_pb2
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class JobType(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    JOB_TYPE_UNSPECIFIED: _ClassVar[JobType]
    JOB_TYPE_DOCUMENT_PARSE: _ClassVar[JobType]
    JOB_TYPE_DOCUMENT_CHUNK: _ClassVar[JobType]
    JOB_TYPE_DOCUMENT_EMBED: _ClassVar[JobType]
    JOB_TYPE_DOCUMENT_UPSERT_QDRANT: _ClassVar[JobType]
    JOB_TYPE_DOCUMENT_MARK_READY: _ClassVar[JobType]

class JobStatus(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    JOB_STATUS_UNSPECIFIED: _ClassVar[JobStatus]
    JOB_STATUS_PENDING: _ClassVar[JobStatus]
    JOB_STATUS_RUNNING: _ClassVar[JobStatus]
    JOB_STATUS_SUCCEEDED: _ClassVar[JobStatus]
    JOB_STATUS_FAILED: _ClassVar[JobStatus]
    JOB_STATUS_DEAD_LETTER: _ClassVar[JobStatus]
JOB_TYPE_UNSPECIFIED: JobType
JOB_TYPE_DOCUMENT_PARSE: JobType
JOB_TYPE_DOCUMENT_CHUNK: JobType
JOB_TYPE_DOCUMENT_EMBED: JobType
JOB_TYPE_DOCUMENT_UPSERT_QDRANT: JobType
JOB_TYPE_DOCUMENT_MARK_READY: JobType
JOB_STATUS_UNSPECIFIED: JobStatus
JOB_STATUS_PENDING: JobStatus
JOB_STATUS_RUNNING: JobStatus
JOB_STATUS_SUCCEEDED: JobStatus
JOB_STATUS_FAILED: JobStatus
JOB_STATUS_DEAD_LETTER: JobStatus

class DocumentParsePayload(_message.Message):
    __slots__ = ("file_url", "file_type")
    FILE_URL_FIELD_NUMBER: _ClassVar[int]
    FILE_TYPE_FIELD_NUMBER: _ClassVar[int]
    file_url: str
    file_type: str
    def __init__(self, file_url: _Optional[str] = ..., file_type: _Optional[str] = ...) -> None: ...

class DocumentChunkPayload(_message.Message):
    __slots__ = ("chunk_size", "chunk_overlap")
    CHUNK_SIZE_FIELD_NUMBER: _ClassVar[int]
    CHUNK_OVERLAP_FIELD_NUMBER: _ClassVar[int]
    chunk_size: int
    chunk_overlap: int
    def __init__(self, chunk_size: _Optional[int] = ..., chunk_overlap: _Optional[int] = ...) -> None: ...

class DocumentEmbedPayload(_message.Message):
    __slots__ = ("model_name", "embedding_dim")
    MODEL_NAME_FIELD_NUMBER: _ClassVar[int]
    EMBEDDING_DIM_FIELD_NUMBER: _ClassVar[int]
    model_name: str
    embedding_dim: int
    def __init__(self, model_name: _Optional[str] = ..., embedding_dim: _Optional[int] = ...) -> None: ...

class DocumentUpsertQdrantPayload(_message.Message):
    __slots__ = ("collection_name",)
    COLLECTION_NAME_FIELD_NUMBER: _ClassVar[int]
    collection_name: str
    def __init__(self, collection_name: _Optional[str] = ...) -> None: ...

class DocumentMarkReadyPayload(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class JobPayload(_message.Message):
    __slots__ = ("job_id", "job_type", "trace_id", "business_id", "attempt", "parse_payload", "chunk_payload", "embed_payload", "upsert_payload", "mark_ready_payload", "created_at", "timeout_at")
    JOB_ID_FIELD_NUMBER: _ClassVar[int]
    JOB_TYPE_FIELD_NUMBER: _ClassVar[int]
    TRACE_ID_FIELD_NUMBER: _ClassVar[int]
    BUSINESS_ID_FIELD_NUMBER: _ClassVar[int]
    ATTEMPT_FIELD_NUMBER: _ClassVar[int]
    PARSE_PAYLOAD_FIELD_NUMBER: _ClassVar[int]
    CHUNK_PAYLOAD_FIELD_NUMBER: _ClassVar[int]
    EMBED_PAYLOAD_FIELD_NUMBER: _ClassVar[int]
    UPSERT_PAYLOAD_FIELD_NUMBER: _ClassVar[int]
    MARK_READY_PAYLOAD_FIELD_NUMBER: _ClassVar[int]
    CREATED_AT_FIELD_NUMBER: _ClassVar[int]
    TIMEOUT_AT_FIELD_NUMBER: _ClassVar[int]
    job_id: str
    job_type: JobType
    trace_id: str
    business_id: str
    attempt: int
    parse_payload: DocumentParsePayload
    chunk_payload: DocumentChunkPayload
    embed_payload: DocumentEmbedPayload
    upsert_payload: DocumentUpsertQdrantPayload
    mark_ready_payload: DocumentMarkReadyPayload
    created_at: _timestamp_pb2.Timestamp
    timeout_at: _timestamp_pb2.Timestamp
    def __init__(self, job_id: _Optional[str] = ..., job_type: _Optional[_Union[JobType, str]] = ..., trace_id: _Optional[str] = ..., business_id: _Optional[str] = ..., attempt: _Optional[int] = ..., parse_payload: _Optional[_Union[DocumentParsePayload, _Mapping]] = ..., chunk_payload: _Optional[_Union[DocumentChunkPayload, _Mapping]] = ..., embed_payload: _Optional[_Union[DocumentEmbedPayload, _Mapping]] = ..., upsert_payload: _Optional[_Union[DocumentUpsertQdrantPayload, _Mapping]] = ..., mark_ready_payload: _Optional[_Union[DocumentMarkReadyPayload, _Mapping]] = ..., created_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., timeout_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...

class JobStatusReportRequest(_message.Message):
    __slots__ = ("job_id", "status", "error_message", "retry_count", "reported_at", "trace_id", "chunks")
    JOB_ID_FIELD_NUMBER: _ClassVar[int]
    STATUS_FIELD_NUMBER: _ClassVar[int]
    ERROR_MESSAGE_FIELD_NUMBER: _ClassVar[int]
    RETRY_COUNT_FIELD_NUMBER: _ClassVar[int]
    REPORTED_AT_FIELD_NUMBER: _ClassVar[int]
    TRACE_ID_FIELD_NUMBER: _ClassVar[int]
    CHUNKS_FIELD_NUMBER: _ClassVar[int]
    job_id: str
    status: JobStatus
    error_message: str
    retry_count: int
    reported_at: _timestamp_pb2.Timestamp
    trace_id: str
    chunks: int
    def __init__(self, job_id: _Optional[str] = ..., status: _Optional[_Union[JobStatus, str]] = ..., error_message: _Optional[str] = ..., retry_count: _Optional[int] = ..., reported_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., trace_id: _Optional[str] = ..., chunks: _Optional[int] = ...) -> None: ...

class JobStatusReportResponse(_message.Message):
    __slots__ = ("success",)
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    success: bool
    def __init__(self, success: bool = ...) -> None: ...
