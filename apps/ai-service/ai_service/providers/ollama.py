from __future__ import annotations

import json
from collections.abc import AsyncIterator

import httpx

from ..routing import scene_params

_DEFAULT_POOL_LIMITS = httpx.Limits(max_connections=10, max_keepalive_connections=5)


class OllamaClient:
    def __init__(self, base_url: str) -> None:
        self._base_url = base_url
        self._client = httpx.AsyncClient(
            base_url=base_url,
            limits=_DEFAULT_POOL_LIMITS,
            timeout=httpx.Timeout(120.0, connect=10.0),
        )

    def _build_payload(
        self, prompt: str, model: str, scene: str, *, stream: bool
    ) -> dict[str, object]:
        cfg = scene_params(scene)
        payload: dict[str, object] = {
            "model": model,
            "prompt": prompt,
            "stream": stream,
            "options": {
                "temperature": cfg["temperature"],
                "top_p": cfg["top_p"],
                "num_predict": int(cfg["max_tokens"]),
            },
        }
        if model.lower().startswith("qwen"):
            payload["think"] = False
        return payload

    async def complete(
        self, prompt: str, model: str, scene: str, *, timeout_seconds: float
    ) -> tuple[str, dict[str, int]]:
        payload = self._build_payload(prompt, model, scene, stream=False)
        response = await self._client.post("/api/generate", json=payload, timeout=timeout_seconds)
        response.raise_for_status()
        data = response.json()
        usage = {
            "prompt_tokens": data.get("prompt_eval_count", 0) or 0,
            "completion_tokens": data.get("eval_count", 0) or 0,
        }
        return str(data.get("response", "")), usage

    async def stream(
        self, prompt: str, model: str, scene: str, *, timeout_seconds: float
    ) -> AsyncIterator[str]:
        payload = self._build_payload(prompt, model, scene, stream=True)
        async with self._client.stream(
            "POST",
            "/api/generate",
            json=payload,
            timeout=timeout_seconds,
        ) as response:
            response.raise_for_status()
            async for line in response.aiter_lines():
                raw = line.strip()
                if not raw:
                    continue
                data = json.loads(raw)
                delta = str(data.get("response", ""))
                if delta:
                    yield delta

    async def embed(self, text: str, model: str) -> list[float]:
        payload = {"model": model, "prompt": text}
        response = await self._client.post("/api/embeddings", json=payload, timeout=45.0)
        response.raise_for_status()
        data = response.json()
        embedding = data.get("embedding", [])
        if not isinstance(embedding, list):
            raise ValueError("invalid embedding payload")
        return [float(x) for x in embedding]

    async def aclose(self) -> None:
        await self._client.aclose()
