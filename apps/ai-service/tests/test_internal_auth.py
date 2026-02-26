from fastapi.testclient import TestClient

from ai_service.app import app


client = TestClient(app)


def test_health_is_public() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json().get("status") == "ok"


def test_internal_endpoint_requires_service_token() -> None:
    response = client.get("/internal/v1/ping")
    assert response.status_code == 401
    assert response.json().get("code") == "INTERNAL_AUTH_FAILED"


def test_internal_endpoint_accepts_valid_service_token() -> None:
    response = client.get("/internal/v1/ping", headers={"X-Service-Token": "change-this-in-local-too"})
    assert response.status_code == 200
    assert response.json().get("status") == "ok"
