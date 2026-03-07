from __future__ import annotations

import asyncio
import logging
import time
from collections.abc import AsyncIterator
from dataclasses import dataclass

from .config import Settings
from .errors import model_unavailable, timeout_error
from .providers import GeminiClient, OllamaClient, OpenAICompatClient
from .routing import provider_candidates, route_decision

logger = logging.getLogger("edunexus.ai.llm")

SCENE_TIMEOUT_SECONDS: dict[str, float] = {
    "chat_rag": 25.0,
    "wrong_analysis": 30.0,
    "ai_question": 120.0,
    "lesson_plan": 60.0,
}

SCENE_RETRY_DELAY_SECONDS: dict[str, float] = {
    "chat_rag": 0.5,
    "wrong_analysis": 0.8,
    "ai_question": 1.0,
    "lesson_plan": 1.2,
}

SCENE_PROVIDER_TIMEOUT_CAP_SECONDS: dict[str, float] = {
    "chat_rag": 12.0,
    "wrong_analysis": 18.0,
    "ai_question": 40.0,
    "lesson_plan": 28.0,
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
        self._ollama = OllamaClient(settings.ollama_base_url)
        self._openai = OpenAICompatClient(settings.openai_base_url, settings.openai_api_key or "")
        self._deepseek = OpenAICompatClient(
            settings.deepseek_base_url, settings.deepseek_api_key or ""
        )
        self._gemini = GeminiClient(settings.gemini_api_key or "")

    def _scene_timeout_seconds(self, scene: str) -> float:
        if scene == "chat_rag":
            return self.settings.chat_rag_timeout_seconds
        if scene == "wrong_analysis":
            return self.settings.wrong_analysis_timeout_seconds
        if scene == "ai_question":
            return self.settings.ai_question_timeout_seconds
        if scene == "lesson_plan":
            return self.settings.lesson_plan_timeout_seconds
        return SCENE_TIMEOUT_SECONDS.get(scene, 45.0)

    async def complete(
        self,
        prompt: str,
        scene: str,
        trace_id: str,
        *,
        hit_kb: bool = False,
        chunk_ids: list[str] | None = None,
        timeout_budget_override: float | None = None,
        provider_timeout_cap_override: float | None = None,
        max_attempts: int = 2,
    ) -> LLMResult:
        candidates = provider_candidates(self.settings, scene)
        if not candidates:
            raise model_unavailable("no provider available")

        timeout_budget = timeout_budget_override or self._scene_timeout_seconds(scene)
        provider_timeout_cap = (
            provider_timeout_cap_override
            or SCENE_PROVIDER_TIMEOUT_CAP_SECONDS.get(scene, timeout_budget)
        )
        deadline = time.perf_counter() + timeout_budget
        retry_base_delay = SCENE_RETRY_DELAY_SECONDS.get(scene, 1.0)
        timeout_seen = False
        error_messages: list[str] = []
        started = time.perf_counter()

        for provider in candidates:
            decision = route_decision(self.settings, provider, scene)

            text = ""
            usage: dict[str, int] = {}
            for attempt in range(max_attempts):
                remaining = deadline - time.perf_counter()
                if remaining <= 0:
                    timeout_seen = True
                    error_messages.append(
                        f"{provider}:timeout:budget_exhausted trace_id={trace_id}"
                    )
                    break

                request_timeout = max(1.0, min(timeout_budget, remaining, provider_timeout_cap))
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
                except TimeoutError:
                    text, usage = "", {}
                    timeout_seen = True
                    error_messages.append(
                        f"{provider}:timeout:attempt={attempt + 1} trace_id={trace_id}"
                    )
                    if attempt >= max_attempts - 1:
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
                except Exception as ex:
                    error_messages.append(
                        f"{provider}:error:attempt={attempt + 1}:{ex} trace_id={trace_id}"
                    )
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

    async def stream(
        self,
        prompt: str,
        scene: str,
        trace_id: str,
        *,
        hit_kb: bool = False,
        chunk_ids: list[str] | None = None,
    ) -> AsyncIterator[str]:
        candidates = provider_candidates(self.settings, scene)
        if not candidates:
            raise model_unavailable("no provider available")

        timeout_budget = self._scene_timeout_seconds(scene)
        provider_timeout_cap = SCENE_PROVIDER_TIMEOUT_CAP_SECONDS.get(scene, timeout_budget)
        deadline = time.perf_counter() + timeout_budget
        retry_base_delay = SCENE_RETRY_DELAY_SECONDS.get(scene, 1.0)
        timeout_seen = False
        error_messages: list[str] = []
        started = time.perf_counter()

        for provider in candidates:
            decision = route_decision(self.settings, provider, scene)

            for attempt in range(2):
                remaining = deadline - time.perf_counter()
                if remaining <= 0:
                    timeout_seen = True
                    error_messages.append(
                        f"{provider}:timeout:budget_exhausted trace_id={trace_id}"
                    )
                    break

                request_timeout = max(1.0, min(timeout_budget, remaining, provider_timeout_cap))
                emitted = False
                try:
                    if provider == "ollama":
                        async for delta in self._ollama.stream(
                            prompt,
                            decision.model,
                            scene,
                            timeout_seconds=request_timeout,
                        ):
                            if not delta:
                                continue
                            emitted = True
                            yield delta
                    else:
                        text, _usage = await asyncio.wait_for(
                            self._complete_by_provider(
                                provider,
                                decision.model,
                                scene,
                                prompt,
                                timeout_seconds=request_timeout,
                            ),
                            timeout=request_timeout,
                        )
                        cleaned = text.strip()
                        if cleaned:
                            emitted = True
                            yield cleaned
                        else:
                            error_messages.append(f"{provider}:empty_response trace_id={trace_id}")

                    if emitted:
                        logger.info(
                            "llm_stream_call provider=%s model=%s scene=%s latency_ms=%s hit_kb=%s chunk_ids=%s trace_id=%s",
                            provider,
                            decision.model,
                            scene,
                            int((time.perf_counter() - started) * 1000),
                            hit_kb,
                            chunk_ids or [],
                            trace_id,
                        )
                        return

                    error_messages.append(f"{provider}:empty_stream trace_id={trace_id}")
                    break
                except TimeoutError:
                    timeout_seen = True
                    error_messages.append(
                        f"{provider}:timeout:attempt={attempt + 1} trace_id={trace_id}"
                    )
                    if attempt >= 1:
                        break

                    delay = retry_base_delay * (attempt + 1)
                    if deadline - time.perf_counter() <= delay:
                        break
                    logger.warning(
                        "llm_stream_timeout_retry provider=%s model=%s scene=%s attempt=%s delay=%s trace_id=%s",
                        provider,
                        decision.model,
                        scene,
                        attempt + 1,
                        delay,
                        trace_id,
                    )
                    await asyncio.sleep(delay)
                except Exception as ex:
                    error_messages.append(
                        f"{provider}:error:attempt={attempt + 1}:{ex} trace_id={trace_id}"
                    )
                    break

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
            vector = await self._ollama.embed(cleaned, self.settings.ollama_embed_model)
            return self._normalize_dimension(vector)
        except Exception as ex:
            errors.append(f"ollama:{ex}")

        if self.settings.openai_api_key:
            try:
                vector = await self._openai.embed(cleaned, self.settings.openai_embed_model)
                return self._normalize_dimension(vector)
            except Exception as ex:
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
            return await self._ollama.complete(
                prompt, model, scene, timeout_seconds=timeout_seconds
            )
        if provider == "openai":
            return await self._openai.complete(
                prompt, model, scene, timeout_seconds=timeout_seconds
            )
        if provider == "deepseek":
            return await self._deepseek.complete(
                prompt, model, scene, timeout_seconds=timeout_seconds
            )
        if provider == "gemini":
            return await self._gemini.complete(
                prompt, model, scene, timeout_seconds=timeout_seconds
            )
        raise ValueError(f"unsupported provider: {provider}")
