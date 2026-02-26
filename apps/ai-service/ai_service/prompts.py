from __future__ import annotations

import json
import re
from typing import Any


PROMPT_INJECTION_PATTERNS = (
    r"(?i)ignore\s+all\s+previous\s+instructions?",
    r"(?i)ignore\s+previous\s+instructions?",
    r"(?i)system\s+prompt",
    r"(?i)developer\s+message",
    r"忽略(所有)?(之前|上文)指令",
)


def sanitize_text(text: str, limit: int = 3000) -> str:
    compact = re.sub(r"\s+", " ", (text or "").replace("```", "")).strip()
    for pattern in PROMPT_INJECTION_PATTERNS:
        compact = re.sub(pattern, "[filtered]", compact)
    return compact[:limit]


def uncertain_answer() -> str:
    return "抱歉，课堂资料不足，当前无法给出可靠答案。请先让教师补充相关资料后再试。"


def chat_prompt(message: str, context_text: str, history: list[dict[str, str]] | None = None) -> str:
    history_lines: list[str] = []
    for row in (history or [])[-8:]:
        role = sanitize_text(row.get("role", "USER"), 20).upper()
        content = sanitize_text(row.get("content", ""), 600)
        if not content:
            continue
        history_lines.append(f"{role}: {content}")

    conversation = "\n".join(history_lines) if history_lines else "无"
    return (
        "你是 EduNexus AI 教学助手。\n"
        "规则：\n"
        "1) 仅能依据 <Context> 回答；\n"
        "2) 若上下文不足，必须明确说“课堂资料不足”；\n"
        "3) 回答使用 Markdown，可用 LaTeX；\n"
        "4) 回答末尾必须给出来源文件名引用（如 [physics_ch3.pdf]）。\n\n"
        f"<History>\n{conversation}\n</History>\n\n"
        f"<Context>\n{context_text}\n</Context>\n\n"
        f"<Question>{sanitize_text(message, 1200)}</Question>"
    )


def wrong_analysis_prompt(
    question: str,
    user_answer: str,
    correct_answer: str,
    knowledge_points: list[str],
    teacher_suggestion: str | None,
) -> str:
    kp = [sanitize_text(x, 80) for x in knowledge_points if sanitize_text(x, 80)]
    kp_text = ", ".join(kp) if kp else "未提供"
    return (
        "你是一位有耐心的资深老师。请仅输出 JSON 对象，不要代码块，不要额外文本。\n"
        "JSON 字段必须包含：encourage, concept, steps, rootCause, nextPractice。\n"
        "其中 steps 必须是字符串数组，长度 2-6。\n\n"
        f"题目：{sanitize_text(question, 1600)}\n"
        f"学生答案：{sanitize_text(user_answer, 500)}\n"
        f"标准答案：{sanitize_text(correct_answer, 500)}\n"
        f"知识点：{kp_text}\n"
        f"教师建议：{sanitize_text(teacher_suggestion or '无', 800)}"
    )


def aiq_prompt(
    subject: str,
    difficulty: str,
    count: int,
    concept_tags: list[str],
    weakness_profile: list[dict[str, Any]],
    teacher_suggestions: list[dict[str, Any]],
) -> str:
    return (
        "你是资深命题老师。请基于输入生成个性化习题。\n"
        "必须只输出 JSON 数组，禁止 Markdown 代码块。\n"
        "每题字段必须包含：content, options, correct_answer, explanation, knowledge_points。\n"
        "options 必须至少有 A/B/C/D 四个选项。\n"
        "knowledge_points 必须是字符串数组。\n\n"
        f"subject={sanitize_text(subject, 80)}\n"
        f"difficulty={sanitize_text(difficulty, 20)}\n"
        f"count={count}\n"
        f"concept_tags={json.dumps(concept_tags, ensure_ascii=False)}\n"
        f"weakness_profile={json.dumps(weakness_profile[:20], ensure_ascii=False)}\n"
        f"teacher_suggestions={json.dumps(teacher_suggestions[:10], ensure_ascii=False)}"
    )


def aiq_repair_prompt(raw_output: str, count: int) -> str:
    return (
        "将下面内容修复为合法 JSON 数组，不要输出任何解释。\n"
        "每个元素必须包含 content/options/correct_answer/explanation/knowledge_points。\n"
        f"题目数量必须为 {count}。\n"
        f"原始输出：{sanitize_text(raw_output, 8000)}"
    )


def lesson_plan_prompt(topic: str, grade_level: str, duration_mins: int) -> str:
    return (
        "请输出 Markdown 教案，严格包含以下四个二级标题：\n"
        "## 1. 教学目标\n"
        "## 2. 重难点\n"
        "## 3. 教学流程（含时间分配）\n"
        "## 4. 作业与评估\n"
        "不要省略任何章节。\n\n"
        f"主题：{sanitize_text(topic, 120)}\n"
        f"年级：{sanitize_text(grade_level, 60)}\n"
        f"总时长：{duration_mins} 分钟"
    )


def lesson_plan_repair_prompt(raw_output: str) -> str:
    return (
        "将以下教案改写为符合固定章节结构的 Markdown。\n"
        "必须包含：\n"
        "## 1. 教学目标\n"
        "## 2. 重难点\n"
        "## 3. 教学流程（含时间分配）\n"
        "## 4. 作业与评估\n"
        "仅输出 Markdown 正文。\n\n"
        f"原始输出：\n{sanitize_text(raw_output, 10000)}"
    )


def has_required_plan_sections(content: str) -> bool:
    normalized = content.replace("\r", "")
    required = [
        "## 1. 教学目标",
        "## 2. 重难点",
        "## 3. 教学流程（含时间分配）",
        "## 4. 作业与评估",
    ]
    return all(section in normalized for section in required)
