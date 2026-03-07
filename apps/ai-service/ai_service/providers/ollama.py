from __future__ import annotations

import json
from collections.abc import AsyncIterator

import httpx

from ..routing import scene_params


class OllamaClient:
    def __init__(self, base_url: str) -> None:
        self._base_url = base_url

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
        async with httpx.AsyncClient(timeout=timeout_seconds) as client:
            response = await client.post(f"{self._base_url}/api/generate", json=payload)
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
        async with (
            httpx.AsyncClient(timeout=timeout_seconds) as client,
            client.stream(
                "POST",
                f"{self._base_url}/api/generate",
                json=payload,
            ) as response,
        ):
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
        async with httpx.AsyncClient(timeout=45.0) as client:
            response = await client.post(f"{self._base_url}/api/embeddings", json=payload)
            response.raise_for_status()
            data = response.json()
        embedding = data.get("embedding", [])
        if not isinstance(embedding, list):
            raise ValueError("invalid embedding payload")
        return [float(x) for x in embedding]
