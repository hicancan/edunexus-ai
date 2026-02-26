from __future__ import annotations

import asyncio
import logging
import time
from dataclasses import dataclass

import httpx

from .config import Settings
from .errors import model_unavailable, timeout_error
from .routing import provider_candidates, route_decision, scene_params


logger = logging.getLogger("edunexus.ai.llm")


@dataclass(frozen=True, slots=True)
class LLMResult:
    text: str
    provider: str
    model: str
    reason: str
    latency_ms: int
    prompt_tokens: int
    completion_tokens: int


class LLMService:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings

    async def complete(self, prompt: str, scene: str, trace_id: str, *, hit_kb: bool = False, chunk_ids: list[str] | None = None) -> LLMResult:
        candidates = provider_candidates(self.settings, scene)
        if not candidates:
            raise model_unavailable("no provider available")

        timeout_seen = False
        error_messages: list[str] = []
        started = time.perf_counter()

        for provider in candidates:
            decision = route_decision(self.settings, provider, scene)
            try:
                text = await self._complete_by_provider(provider, decision.model, scene, prompt)
            except (httpx.ReadTimeout, httpx.ConnectTimeout, asyncio.TimeoutError) as ex:
                timeout_seen = True
                error_messages.append(f"{provider}:timeout:{ex}")
                continue
            except Exception as ex:  # noqa: BLE001
                error_messages.append(f"{provider}:error:{ex}")
                continue

            latency_ms = int((time.perf_counter() - started) * 1000)
            result = LLMResult(
                text=text.strip(),
                provider=provider,
                model=decision.model,
                reason=decision.reason,
                latency_ms=latency_ms,
                prompt_tokens=max(1, len(prompt) // 4),
                completion_tokens=max(1, len(text) // 4) if text else 0,
            )
            logger.info(
                "llm_call provider=%s model=%s scene=%s latency_ms=%s prompt_tokens=%s completion_tokens=%s hit_kb=%s chunk_ids=%s trace_id=%s",
                result.provider,
                result.model,
                scene,
                result.latency_ms,
                result.prompt_tokens,
                result.completion_tokens,
                hit_kb,
                chunk_ids or [],
                trace_id,
            )
            return result

        if timeout_seen:
            raise timeout_error("all provider calls timed out")
        message = "; ".join(error_messages[-4:]) if error_messages else "all provider calls failed"
        raise model_unavailable(message)

    async def embed(self, text: str) -> list[float]:
        cleaned = text.strip()
        if not cleaned:
            return [0.0 for _ in range(self.settings.embedding_dim)]

        errors: list[str] = []
        try:
            vector = await self._embed_with_ollama(cleaned)
            return self._normalize_dimension(vector)
        except Exception as ex:  # noqa: BLE001
            errors.append(f"ollama:{ex}")

        if self.settings.openai_api_key:
            try:
                vector = await self._embed_with_openai(cleaned)
                return self._normalize_dimension(vector)
            except Exception as ex:  # noqa: BLE001
                errors.append(f"openai:{ex}")

        raise model_unavailable("embedding unavailable: " + "; ".join(errors[-3:]))

    def _normalize_dimension(self, vector: list[float]) -> list[float]:
        target = self.settings.embedding_dim
        if len(vector) == target:
            return vector
        if len(vector) > target:
            return vector[:target]
        return vector + [0.0 for _ in range(target - len(vector))]

    async def _complete_by_provider(self, provider: str, model: str, scene: str, prompt: str) -> str:
        if provider == "ollama":
            return await self._call_ollama(prompt, model, scene)
        if provider == "openai":
            return await self._call_openai_compatible(
                base_url=self.settings.openai_base_url,
                api_key=self.settings.openai_api_key,
                model=model,
                prompt=prompt,
                scene=scene,
            )
        if provider == "deepseek":
            return await self._call_openai_compatible(
                base_url=self.settings.deepseek_base_url,
                api_key=self.settings.deepseek_api_key,
                model=model,
                prompt=prompt,
                scene=scene,
            )
        if provider == "gemini":
            return await self._call_gemini(prompt, model, scene)
        raise ValueError(f"unsupported provider: {provider}")

    async def _call_ollama(self, prompt: str, model: str, scene: str) -> str:
        cfg = scene_params(scene)
        payload = {
            "model": model,
            "prompt": prompt,
            "stream": False,
            "options": {
                "temperature": cfg["temperature"],
                "top_p": cfg["top_p"],
                "num_predict": int(cfg["max_tokens"]),
            },
        }
        async with httpx.AsyncClient(timeout=45.0) as client:
            response = await client.post(f"{self.settings.ollama_base_url}/api/generate", json=payload)
            response.raise_for_status()
            data = response.json()
        return str(data.get("response", ""))

    async def _call_openai_compatible(self, *, base_url: str, api_key: str, model: str, prompt: str, scene: str) -> str:
        if not api_key:
            raise RuntimeError("missing api key")
        cfg = scene_params(scene)
        payload = {
            "model": model,
            "messages": [
                {"role": "system", "content": "你是 EduNexus AI 教学助手，请给出清晰且可执行的答案。"},
                {"role": "user", "content": prompt},
            ],
            "temperature": cfg["temperature"],
            "top_p": cfg["top_p"],
            "max_tokens": int(cfg["max_tokens"]),
        }
        headers = {"Authorization": f"Bearer {api_key}"}
        async with httpx.AsyncClient(timeout=45.0) as client:
            response = await client.post(f"{base_url}/chat/completions", json=payload, headers=headers)
            response.raise_for_status()
            data = response.json()
        choices = data.get("choices", [])
        if not choices:
            return ""
        message = choices[0].get("message", {})
        return str(message.get("content", ""))

    async def _call_gemini(self, prompt: str, model: str, scene: str) -> str:
        key = self.settings.gemini_api_key
        if not key:
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
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}"
        async with httpx.AsyncClient(timeout=45.0) as client:
            response = await client.post(url, json=payload)
            response.raise_for_status()
            data = response.json()
        candidates = data.get("candidates", [])
        if not candidates:
            return ""
        parts = candidates[0].get("content", {}).get("parts", [])
        return "".join(str(part.get("text", "")) for part in parts)

    async def _embed_with_ollama(self, text: str) -> list[float]:
        payload = {"model": self.settings.ollama_embed_model, "prompt": text}
        async with httpx.AsyncClient(timeout=45.0) as client:
            response = await client.post(f"{self.settings.ollama_base_url}/api/embeddings", json=payload)
            response.raise_for_status()
            data = response.json()
        embedding = data.get("embedding", [])
        if not isinstance(embedding, list):
            raise ValueError("invalid embedding payload")
        return [float(x) for x in embedding]

    async def _embed_with_openai(self, text: str) -> list[float]:
        payload = {
            "model": self.settings.openai_embed_model,
            "input": text,
        }
        headers = {"Authorization": f"Bearer {self.settings.openai_api_key}"}
        async with httpx.AsyncClient(timeout=45.0) as client:
            response = await client.post(f"{self.settings.openai_base_url}/embeddings", json=payload, headers=headers)
            response.raise_for_status()
            data = response.json()
        rows = data.get("data", [])
        if not rows:
            raise ValueError("empty embedding response")
        vector = rows[0].get("embedding", [])
        if not isinstance(vector, list):
            raise ValueError("invalid embedding response")
        return [float(x) for x in vector]
