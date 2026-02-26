from fastapi.testclient import TestClient

from ai_service.app import app


client = TestClient(app)


def test_internal_chat_requires_service_token() -> None:
    response = client.post(
        "/internal/v1/rag/chat",
        json={
            "traceId": "trace-1",
            "sessionId": "s1",
            "studentId": "stu-1",
            "message": "hello",
        },
    )
    assert response.status_code == 401
    assert response.json()["code"] == "INTERNAL_AUTH_FAILED"


def test_internal_chat_requires_trace_id() -> None:
    response = client.post(
        "/internal/v1/rag/chat",
        headers={"X-Service-Token": "change-this-in-local-too"},
        json={
            "sessionId": "s1",
            "studentId": "stu-1",
            "message": "hello",
        },
    )
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_PARAM"


def test_kb_ingest_requires_idempotency_key() -> None:
    response = client.post(
        "/internal/v1/kb/ingest",
        headers={
            "X-Service-Token": "change-this-in-local-too",
            "X-Trace-Id": "trace-1",
        },
        json={
            "documentId": "doc-1",
            "teacherId": "teacher-1",
            "filename": "sample.txt",
            "filePath": "./missing.txt",
        },
    )
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_PARAM"


def test_kb_delete_requires_idempotency_key() -> None:
    response = client.post(
        "/internal/v1/kb/delete",
        headers={
            "X-Service-Token": "change-this-in-local-too",
            "X-Trace-Id": "trace-1",
        },
        json={
            "documentId": "doc-1",
        },
    )
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_PARAM"


def test_kb_ingest_rejects_short_idempotency_key() -> None:
    response = client.post(
        "/internal/v1/kb/ingest",
        headers={
            "X-Service-Token": "change-this-in-local-too",
            "X-Trace-Id": "trace-1",
            "Idempotency-Key": "short",
        },
        json={
            "documentId": "doc-1",
            "teacherId": "teacher-1",
            "filename": "sample.txt",
            "filePath": "./missing.txt",
        },
    )
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_PARAM"


def test_kb_ingest_validates_payload_after_idempotency_check() -> None:
    response = client.post(
        "/internal/v1/kb/ingest",
        headers={
            "X-Service-Token": "change-this-in-local-too",
            "X-Trace-Id": "trace-1",
            "Idempotency-Key": "idem-key-1",
        },
        json={
            "documentId": "doc-1",
            "teacherId": "teacher-1",
            "filename": "sample.txt",
            "filePath": "./missing.txt",
        },
    )
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_FIELD"
