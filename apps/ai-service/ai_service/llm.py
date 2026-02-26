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

SCENE_TIMEOUT_SECONDS: dict[str, float] = {
    "chat_rag": 25.0,
    "wrong_analysis": 30.0,
    "ai_question": 45.0,
    "lesson_plan": 60.0,
}

SCENE_RETRY_DELAY_SECONDS: dict[str, float] = {
    "chat_rag": 0.5,
    "wrong_analysis": 0.8,
    "ai_question": 1.0,
    "lesson_plan": 1.2,
}


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

        timeout_budget = SCENE_TIMEOUT_SECONDS.get(scene, 45.0)
        deadline = time.perf_counter() + timeout_budget
        retry_base_delay = SCENE_RETRY_DELAY_SECONDS.get(scene, 1.0)
        timeout_seen = False
        error_messages: list[str] = []
        started = time.perf_counter()

        for provider in candidates:
            decision = route_decision(self.settings, provider, scene)

            text = ""
            for attempt in range(2):
                remaining = deadline - time.perf_counter()
                if remaining <= 0:
                    timeout_seen = True
                    error_messages.append(f"{provider}:timeout:budget_exhausted trace_id={trace_id}")
                    break

                request_timeout = max(1.0, min(timeout_budget, remaining))
                try:
                    text, usage = await asyncio.wait_for(
                        self._complete_by_provider(
                            provider,
                            decision.model,
                            scene,
                            prompt,
                            timeout_seconds=request_timeout,
                        ),
                        timeout=request_timeout,
                    )
                    break
                except asyncio.TimeoutError:
                    text, usage = "", {}
                    timeout_seen = True
                    error_messages.append(f"{provider}:timeout:attempt={attempt + 1} trace_id={trace_id}")
                    if attempt >= 1:
                        break

                    delay = retry_base_delay * (attempt + 1)
                    if deadline - time.perf_counter() <= delay:
                        break
                    logger.warning(
                        "llm_timeout_retry provider=%s model=%s scene=%s attempt=%s delay=%s trace_id=%s",
                        provider,
                        decision.model,
                        scene,
                        attempt + 1,
                        delay,
                        trace_id,
                    )
                    await asyncio.sleep(delay)
                except Exception as ex:  # noqa: BLE001
                    error_messages.append(f"{provider}:error:attempt={attempt + 1}:{ex} trace_id={trace_id}")
                    text, usage = "", {}
                    break

            cleaned_text = text.strip()
            if not cleaned_text:
                continue

            latency_ms = int((time.perf_counter() - started) * 1000)
            result = LLMResult(
                text=cleaned_text,
                provider=provider,
                model=decision.model,
                reason=decision.reason,
                latency_ms=latency_ms,
                prompt_tokens=usage.get("prompt_tokens") or max(1, len(prompt) // 4),
                completion_tokens=usage.get("completion_tokens") or max(1, len(cleaned_text) // 4),
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
            raise timeout_error(f"all provider calls timed out trace_id={trace_id}")
        details = "; ".join(error_messages[-4:]) if error_messages else "all provider calls failed"
        raise model_unavailable(f"{details} trace_id={trace_id}")

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
        action = "truncate" if len(vector) > target else "zero_pad"
        logger.warning(
            "embedding_dimension_mismatch expected=%d actual=%d action=%s",
            target,
            len(vector),
            action,
        )
        if len(vector) > target:
            return vector[:target]
        return vector + [0.0 for _ in range(target - len(vector))]

    async def _complete_by_provider(
        self,
        provider: str,
        model: str,
        scene: str,
        prompt: str,
        *,
        timeout_seconds: float,
    ) -> tuple[str, dict[str, int]]:
        if provider == "ollama":
            return await self._call_ollama(prompt, model, scene, timeout_seconds=timeout_seconds)
        if provider == "openai":
            return await self._call_openai_compatible(
                base_url=self.settings.openai_base_url,
                api_key=self.settings.openai_api_key,
                model=model,
                prompt=prompt,
                scene=scene,
                timeout_seconds=timeout_seconds,
            )
        if provider == "deepseek":
            return await self._call_openai_compatible(
                base_url=self.settings.deepseek_base_url,
                api_key=self.settings.deepseek_api_key,
                model=model,
                prompt=prompt,
                scene=scene,
                timeout_seconds=timeout_seconds,
            )
        if provider == "gemini":
            return await self._call_gemini(prompt, model, scene, timeout_seconds=timeout_seconds)
        raise ValueError(f"unsupported provider: {provider}")

    async def _call_ollama(self, prompt: str, model: str, scene: str, *, timeout_seconds: float) -> tuple[str, dict[str, int]]:
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
        async with httpx.AsyncClient(timeout=timeout_seconds) as client:
            response = await client.post(f"{self.settings.ollama_base_url}/api/generate", json=payload)
            response.raise_for_status()
            data = response.json()
        usage = {
            "prompt_tokens": data.get("prompt_eval_count", 0) or 0,
            "completion_tokens": data.get("eval_count", 0) or 0,
        }
        return str(data.get("response", "")), usage

    async def _call_openai_compatible(
        self,
        *,
        base_url: str,
        api_key: str,
        model: str,
        prompt: str,
        scene: str,
        timeout_seconds: float,
    ) -> tuple[str, dict[str, int]]:
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
        async with httpx.AsyncClient(timeout=timeout_seconds) as client:
            response = await client.post(f"{base_url}/chat/completions", json=payload, headers=headers)
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

    async def _call_gemini(self, prompt: str, model: str, scene: str, *, timeout_seconds: float) -> tuple[str, dict[str, int]]:
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
        try:
            async with httpx.AsyncClient(timeout=timeout_seconds) as client:
                response = await client.post(url, json=payload)
                response.raise_for_status()
                data = response.json()
        except Exception as ex:
            # M-06: 脱敏——确保异常堆栈中不包含 API Key
            sanitized_url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key=***"
            raise RuntimeError(f"gemini call failed: {type(ex).__name__} url={sanitized_url}") from None
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
