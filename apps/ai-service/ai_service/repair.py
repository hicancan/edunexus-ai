from __future__ import annotations

from collections.abc import Callable
from typing import Any

from .utils import parse_json_array, parse_json_object


async def complete_with_json_repair(
    llm: Any,
    prompt: str,
    scene: str,
    trace_id: str,
    *,
    expect_array: bool = False,
    repair_prompt_fn: Callable[[str], str] | None = None,
) -> tuple[list[Any] | dict[str, Any] | None, Any]:
    """Call LLM, parse JSON, repair once on failure.

    Returns (parsed_value, llm_result) where llm_result is the LLMResult
    from whichever call succeeded (original or repaired).
    """
    result = await llm.complete(prompt, scene=scene, trace_id=trace_id)
    parsed: list[Any] | dict[str, Any] | None = (
        parse_json_array(result.text) if expect_array else parse_json_object(result.text)
    )

    empty = parsed is None or (expect_array and isinstance(parsed, list) and not parsed)
    if empty and repair_prompt_fn is not None:
        repaired = await llm.complete(repair_prompt_fn(result.text), scene=scene, trace_id=trace_id)
        parsed = (
            parse_json_array(repaired.text) if expect_array else parse_json_object(repaired.text)
        )
        return parsed, repaired

    return parsed, result
