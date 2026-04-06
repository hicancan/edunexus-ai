from __future__ import annotations

import httpx

from ..routing import scene_params

_DEFAULT_POOL_LIMITS = httpx.Limits(max_connections=10, max_keepalive_connections=5)


class GeminiClient:
    def __init__(self, api_key: str) -> None:
        self._api_key = api_key
        self._client = httpx.AsyncClient(
            limits=_DEFAULT_POOL_LIMITS,
            timeout=httpx.Timeout(120.0, connect=10.0),
        )

    async def complete(
        self, prompt: str, model: str, scene: str, *, timeout_seconds: float
    ) -> tuple[str, dict[str, int]]:
        if not self._api_key:
            raise RuntimeError("missing gemini api key")
        cfg = scene_params(scene)
        payload = {
            "contents": [{"parts": [{"text": prompt}]}],
            "generationConfig": {
                "temperature": cfg["temperature"],
                "topP": cfg["top_p"],
                "maxOutputTokens": int(cfg["max_tokens"]),
            },
        }
        url = (
            f"https://generativelanguage.googleapis.com/v1beta/models/"
            f"{model}:generateContent?key={self._api_key}"
        )
        try:
            response = await self._client.post(url, json=payload, timeout=timeout_seconds)
            response.raise_for_status()
            data = response.json()
        except Exception as ex:
            # M-06: 脱敏——确保异常堆栈中不包含 API Key
            sanitized_url = (
                f"https://generativelanguage.googleapis.com/v1beta/models/"
                f"{model}:generateContent?key=***"
            )
            raise RuntimeError(
                f"gemini call failed: {type(ex).__name__} url={sanitized_url}"
            ) from None
        raw_usage = data.get("usageMetadata", {})
        usage = {
            "prompt_tokens": raw_usage.get("promptTokenCount", 0) or 0,
            "completion_tokens": raw_usage.get("candidatesTokenCount", 0) or 0,
        }
        candidates = data.get("candidates", [])
        if not candidates:
            return "", usage
        parts = candidates[0].get("content", {}).get("parts", [])
        return "".join(str(part.get("text", "")) for part in parts), usage

    async def aclose(self) -> None:
        await self._client.aclose()
