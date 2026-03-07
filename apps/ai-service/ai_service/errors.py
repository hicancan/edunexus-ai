from __future__ import annotations

from dataclasses import dataclass


@dataclass(slots=True)
class InternalServiceError(Exception):
    status_code: int
    code: str
    message: str

    def __str__(self) -> str:
        return f"{self.code}: {self.message}"


def auth_failed() -> InternalServiceError:
    return InternalServiceError(401, "INTERNAL_AUTH_FAILED", "invalid service token")


def dependency_error(message: str = "dependency unavailable") -> InternalServiceError:
    return InternalServiceError(503, "INTERNAL_DEPENDENCY", message)


def timeout_error(message: str = "upstream timeout") -> InternalServiceError:
    return InternalServiceError(504, "INTERNAL_TIMEOUT", message)


def model_unavailable(message: str = "model unavailable") -> InternalServiceError:
    return InternalServiceError(503, "INTERNAL_MODEL_UNAVAILABLE", message)


def output_invalid(message: str = "model output invalid") -> InternalServiceError:
    return InternalServiceError(502, "INTERNAL_OUTPUT_INVALID", message)


def bad_request(message: str) -> InternalServiceError:
    return InternalServiceError(400, "VALIDATION_FIELD", message)
