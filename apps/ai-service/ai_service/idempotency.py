from __future__ import annotations

import threading
import time
from dataclasses import dataclass
from typing import Any

from .errors import bad_request


@dataclass(slots=True)
class _Entry:
    request_hash: str
    response: dict[str, Any]
    expires_at: float


class IdempotencyStore:
    def __init__(self, ttl_seconds: int = 24 * 3600) -> None:
        self._ttl_seconds = ttl_seconds
        self._items: dict[str, _Entry] = {}
        self._lock = threading.Lock()

    def _cleanup(self, now: float) -> None:
        expired = [key for key, entry in self._items.items() if entry.expires_at <= now]
        for key in expired:
            self._items.pop(key, None)

    def get(self, scope: str, idem_key: str, request_hash: str) -> dict[str, Any] | None:
        token = idem_key.strip()
        if not token:
            return None
        now = time.time()
        full_key = f"{scope}:{token}"
        with self._lock:
            self._cleanup(now)
            entry = self._items.get(full_key)
            if not entry:
                return None
            if entry.request_hash != request_hash:
                raise bad_request("Idempotency-Key reused with different payload")
            return dict(entry.response)

    def set(self, scope: str, idem_key: str, request_hash: str, response: dict[str, Any]) -> None:
        token = idem_key.strip()
        if not token:
            return
        now = time.time()
        full_key = f"{scope}:{token}"
        with self._lock:
            self._cleanup(now)
            self._items[full_key] = _Entry(
                request_hash=request_hash,
                response=dict(response),
                expires_at=now + self._ttl_seconds,
            )
