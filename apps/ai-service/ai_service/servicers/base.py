from __future__ import annotations

import grpc

from ..config import Settings
from ..errors import InternalServiceError

_STATUS_CODE_MAP: dict[int, grpc.StatusCode] = {
    400: grpc.StatusCode.INVALID_ARGUMENT,
    401: grpc.StatusCode.UNAUTHENTICATED,
    403: grpc.StatusCode.PERMISSION_DENIED,
    404: grpc.StatusCode.NOT_FOUND,
    429: grpc.StatusCode.RESOURCE_EXHAUSTED,
    502: grpc.StatusCode.INTERNAL,
    503: grpc.StatusCode.UNAVAILABLE,
    504: grpc.StatusCode.DEADLINE_EXCEEDED,
}


def _metadata_dict(context: grpc.aio.ServicerContext) -> dict[str, str]:
    out: dict[str, str] = {}
    metadata = context.invocation_metadata()
    if metadata is None:
        return out
    for key, value in metadata:
        out[key.lower()] = value
    return out


async def require_internal_auth(
    context: grpc.aio.ServicerContext,
    settings: Settings,
    *,
    require_trace: bool,
) -> tuple[str, str]:
    metadata = _metadata_dict(context)
    token = metadata.get("x-service-token", "")
    if token != settings.service_token:
        await context.abort(grpc.StatusCode.UNAUTHENTICATED, "invalid service token")

    trace_id = (metadata.get("x-trace-id", "") or "").strip()
    if require_trace and not trace_id:
        await context.abort(grpc.StatusCode.INVALID_ARGUMENT, "X-Trace-Id is required")

    idem_key = (metadata.get("idempotency-key", "") or "").strip()
    return trace_id, idem_key


async def abort_internal_error(
    context: grpc.aio.ServicerContext,
    error: InternalServiceError,
) -> None:
    status = _STATUS_CODE_MAP.get(error.status_code, grpc.StatusCode.INTERNAL)
    await context.abort(status, error.message)
