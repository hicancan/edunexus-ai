from __future__ import annotations

import httpx

from ..routing import scene_params


class OpenAICompatClient:
    """OpenAI-compatible client; works for OpenAI and DeepSeek."""

    def __init__(self, base_url: str, api_key: str) -> None:
        self._base_url = base_url
        self._api_key = api_key

    async def complete(
        self, prompt: str, model: str, scene: str, *, timeout_seconds: float
    ) -> tuple[str, dict[str, int]]:
        if not self._api_key:
            raise RuntimeError("missing api key")
        cfg = scene_params(scene)
        payload = {
            "model": model,
            "messages": [
                {
                    "role": "system",
                    "content": "你是 EduNexus AI 教学助手，请给出清晰且可执行的答案。",
                },
                {"role": "user", "content": prompt},
            ],
            "temperature": cfg["temperature"],
            "top_p": cfg["top_p"],
            "max_tokens": int(cfg["max_tokens"]),
        }
        headers = {"Authorization": f"Bearer {self._api_key}"}
        async with httpx.AsyncClient(timeout=timeout_seconds) as client:
            response = await client.post(
                f"{self._base_url}/chat/completions", json=payload, headers=headers
            )
            response.raise_for_status()
            data = response.json()
        raw_usage = data.get("usage", {})
        usage = {
            "prompt_tokens": raw_usage.get("prompt_tokens", 0) or 0,
            "completion_tokens": raw_usage.get("completion_tokens", 0) or 0,
        }
        choices = data.get("choices", [])
        if not choices:
            return "", usage
        message = choices[0].get("message", {})
        return str(message.get("content", "")), usage

    async def embed(self, text: str, model: str) -> list[float]:
        if not self._api_key:
            raise RuntimeError("missing api key")
        payload = {"model": model, "input": text}
        headers = {"Authorization": f"Bearer {self._api_key}"}
        async with httpx.AsyncClient(timeout=45.0) as client:
            response = await client.post(
                f"{self._base_url}/embeddings", json=payload, headers=headers
            )
            response.raise_for_status()
            data = response.json()
        rows = data.get("data", [])
        if not rows:
            raise ValueError("empty embedding response")
        vector = rows[0].get("embedding", [])
        if not isinstance(vector, list):
            raise ValueError("invalid embedding response")
        return [float(x) for x in vector]
