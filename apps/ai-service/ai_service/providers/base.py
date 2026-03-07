from __future__ import annotations

from collections.abc import AsyncIterator
from typing import Protocol, runtime_checkable


@runtime_checkable
class ProviderClient(Protocol):
    async def complete(
        self,
        prompt: str,
        model: str,
        scene: str,
        *,
        timeout_seconds: float,
    ) -> tuple[str, dict[str, int]]: ...

    def stream(
        self,
        prompt: str,
        model: str,
        scene: str,
        *,
        timeout_seconds: float,
    ) -> AsyncIterator[str]: ...
