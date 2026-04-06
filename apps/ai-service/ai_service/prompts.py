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


def chat_prompt(
    message: str, context_text: str, history: list[dict[str, str]] | None = None
) -> str:
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
        "4) 回答末尾必须给出来源引用，格式为 [文件名]（如 [physics_ch3.pdf]）。"
        "每条引用单独一行，可用多个引用。\n\n"
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
    existing_questions: list[str] | None = None,
) -> str:
    existing_questions = [
        sanitize_text(item, 180) for item in (existing_questions or []) if sanitize_text(item, 180)
    ]
    existing_text = (
        json.dumps(existing_questions[:12], ensure_ascii=False)
        if existing_questions
        else "[]"
    )
    return (
        "你是资深命题老师。请基于输入生成个性化习题。\n"
        "必须只输出 JSON 数组，禁止 Markdown 代码块。\n"
        "每题必须包含字段：question_type, content, options, correct_answer, explanation, knowledge_points。\n"
        "question_type 只能是以下三种之一：SINGLE_CHOICE（单选）、MULTIPLE_CHOICE（多选）、SHORT_ANSWER（简答）。\n"
        "SINGLE_CHOICE 和 MULTIPLE_CHOICE 的 options 必须是对象，包含 A/B/C/D 四个键。\n"
        "MULTIPLE_CHOICE 的 correct_answer 必须是按字母升序拼接的字符串，例如 AC。\n"
        "SHORT_ANSWER 的 options 填空对象 {}。\n"
        "knowledge_points 必须是非空字符串数组。\n\n"
        "若 existing_questions 非空，表示这些题目已经生成完成。你必须继续补齐剩余题目，"
        "且新题的题干、知识点组合和设问角度都不能与 existing_questions 重复。\n\n"
        f"subject={sanitize_text(subject, 80)}\n"
        f"difficulty={sanitize_text(difficulty, 20)}\n"
        f"count={count}\n"
        f"concept_tags={json.dumps(concept_tags, ensure_ascii=False)}\n"
        f"weakness_profile={json.dumps(weakness_profile[:20], ensure_ascii=False)}\n"
        f"teacher_suggestions={json.dumps(teacher_suggestions[:10], ensure_ascii=False)}\n"
        f"existing_questions={existing_text}"
    )


def aiq_repair_prompt(raw_output: str, count: int) -> str:
    return (
        "将下面内容修复为合法 JSON 数组，不要输出任何解释。\n"
        "每个元素必须包含：question_type, content, options, correct_answer, explanation, knowledge_points。\n"
        "question_type 只能是 SINGLE_CHOICE、MULTIPLE_CHOICE 或 SHORT_ANSWER。\n"
        "SINGLE_CHOICE/MULTIPLE_CHOICE 的 options 必须包含 A/B/C/D 四个键；SHORT_ANSWER 的 options 为 {}。\n"
        "MULTIPLE_CHOICE 的 correct_answer 必须是按字母升序拼接的字符串，例如 AC。\n"
        f"题目数量必须为 {count}。\n"
        f"原始输出：{sanitize_text(raw_output, 8000)}"
    )


def teacher_suggestion_prompt(candidates: list[dict[str, Any]]) -> str:
    return (
        "你是资深教研教师，请基于班级学情生成教师可直接确认的干预建议草案。\n"
        "必须只输出 JSON 数组，禁止 Markdown 代码块或额外说明。\n"
        "数组中的每个元素必须包含字段：knowledge_point, suggestion_template。\n"
        "knowledge_point 必须与输入保持完全一致。\n"
        "suggestion_template 必须是 1-2 句中文教学建议，强调先复讲、再诊断、后分层再练，避免空话和口号。\n"
        "建议要能直接发给学生或用于课堂复讲，不要出现模型、自我说明、提示词等内容。\n\n"
        f"candidates={json.dumps(candidates[:8], ensure_ascii=False)}"
    )


def teacher_suggestion_repair_prompt(raw_output: str, knowledge_points: list[str]) -> str:
    return (
        "将下面内容修复为合法 JSON 数组，不要输出任何解释。\n"
        "每个元素必须包含：knowledge_point, suggestion_template。\n"
        "knowledge_point 必须且只能从以下集合中选择，并保持完全一致："
        f"{json.dumps(knowledge_points, ensure_ascii=False)}。\n"
        "suggestion_template 必须是 1-2 句中文教学建议。\n"
        f"原始输出：{sanitize_text(raw_output, 6000)}"
    )


def lesson_plan_prompt(topic: str, grade_level: str, duration_mins: int) -> str:
    return (
        "你是资深教研组长，请直接输出最终 Markdown 教案。\n"
        "禁止输出思考过程、前言、自我解释、提示词复述，禁止出现“首先”“我需要”“关键点”等分析性句子。\n"
        "请输出 Markdown 教案，严格包含以下四个二级标题：\n"
        "## 1. 教学目标\n"
        "## 2. 重难点\n"
        "## 3. 教学流程（含时间分配）\n"
        "## 4. 作业与评估\n"
        "不要省略任何章节。\n\n"
        "每个章节都要有具体内容，教学流程至少拆成 4 个步骤，并明确标注时间分配。\n\n"
        f"主题：{sanitize_text(topic, 120)}\n"
        f"年级：{sanitize_text(grade_level, 60)}\n"
        f"总时长：{duration_mins} 分钟"
    )


def lesson_plan_repair_prompt(raw_output: str, duration_mins: int) -> str:
    return (
        "将以下教案改写为符合固定章节结构的 Markdown。\n"
        "不要解释修改原因，不要输出任何分析语句，直接输出修复后的正文。\n"
        "必须包含：\n"
        "## 1. 教学目标\n"
        "## 2. 重难点\n"
        "## 3. 教学流程（含时间分配）\n"
        "## 4. 作业与评估\n"
        "教学流程至少保留 4 个步骤，并统一写成 `### 步骤一：标题（X 分钟）` 的格式。\n"
        f"所有步骤时间总和必须严格等于 {duration_mins} 分钟。\n"
        "修正步骤标题中的错字、漏字和多余标点。\n"
        "仅输出 Markdown 正文。\n\n"
        f"原始输出：\n{sanitize_text(raw_output, 10000)}"
    )


def sanitize_lesson_plan_markdown(content: str) -> str:
    normalized = (content or "").replace("\r", "").strip()
    normalized = re.sub(
        r"^###\s*步\s*[,，]?\s*([一二三四五六七八九十0-9])\s*[:：]?",
        r"### 步骤\1：",
        normalized,
        flags=re.MULTILINE,
    )
    normalized = re.sub(
        r"^###\s*步骤\s*([一二三四五六七八九十0-9])\s*[:：]?",
        r"### 步骤\1：",
        normalized,
        flags=re.MULTILINE,
    )
    return normalized


def coerce_lesson_plan_markdown(
    content: str, topic: str, grade_level: str, duration_mins: int
) -> str:
    normalized = sanitize_lesson_plan_markdown(content)
    sections = _split_level2_sections(normalized)
    goals = _find_section_body(sections, ("教学目标", "目标"))
    key_points = _find_section_body(sections, ("重难点", "重点难点", "教学重难点"))
    flow = _find_section_body(sections, ("教学流程", "教学过程", "流程设计"))
    homework = _find_section_body(sections, ("作业与评估", "作业与反馈", "评估与作业"))

    step_blocks = _extract_step_blocks(flow or normalized)
    step_blocks = _ensure_minimum_step_blocks(step_blocks, topic)
    step_durations = _normalize_step_durations(step_blocks, duration_mins)

    lines: list[str] = [
        "## 1. 教学目标",
        *_normalize_section_lines(
            goals,
            [
                f"理解「{topic}」的核心概念、关键条件与常见应用场景。",
                f"能够结合{grade_level}课堂题型完成基础判断、表达或计算。",
                "能在教师支架下复盘易错点，并完成一次针对性再练。",
            ],
        ),
        "",
        "## 2. 重难点",
        *_normalize_section_lines(
            key_points,
            [
                f"重点：梳理「{topic}」的关键概念、公式或判断依据。",
                "难点：把课堂概念迁移到题目条件识别、分析与规范表达中。",
            ],
        ),
        "",
        "## 3. 教学流程（含时间分配）",
    ]

    numerals = ["一", "二", "三", "四", "五", "六"]
    for index, step in enumerate(step_blocks):
        numeral = numerals[index] if index < len(numerals) else str(index + 1)
        lines.append(
            f"### 步骤{numeral}：{step['title']}（{step_durations[index]} 分钟）"
        )
        lines.extend(_normalize_step_lines(step["body"], topic, index))
        lines.append("")

    lines.extend(
        [
            "## 4. 作业与评估",
            *_normalize_section_lines(
                homework,
                [
                    f"完成 1 组围绕「{topic}」的分层练习，并提交错因复盘记录。",
                    "根据课堂反馈记录 1 条仍需教师支架支持的问题，作为下次课前诊断依据。",
                ],
            ),
        ]
    )
    return "\n".join(lines).strip()


def has_required_plan_sections(content: str) -> bool:
    normalized = sanitize_lesson_plan_markdown(content)
    required = [
        "## 1. 教学目标",
        "## 2. 重难点",
        "## 3. 教学流程（含时间分配）",
        "## 4. 作业与评估",
    ]
    return all(section in normalized for section in required)


def extract_lesson_plan_step_durations(content: str) -> list[int]:
    normalized = sanitize_lesson_plan_markdown(content)
    matches = re.findall(
        r"^###\s*步骤[一二三四五六七八九十0-9]+：.*?[（(](\d+)\s*分钟[）)]",
        normalized,
        flags=re.MULTILINE,
    )
    return [int(value) for value in matches]


def has_valid_plan_structure(content: str, duration_mins: int) -> bool:
    if not has_required_plan_sections(content):
        return False
    durations = extract_lesson_plan_step_durations(content)
    return len(durations) >= 4 and sum(durations) == duration_mins


def _split_level2_sections(content: str) -> dict[str, str]:
    sections: dict[str, str] = {}
    matches = list(re.finditer(r"^##\s+(.+)$", content, flags=re.MULTILINE))
    for index, match in enumerate(matches):
        start = match.end()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(content)
        title = re.sub(r"^[0-9.\s、]+", "", match.group(1)).strip()
        sections[title] = content[start:end].strip()
    return sections


def _find_section_body(sections: dict[str, str], aliases: tuple[str, ...]) -> str:
    for title, body in sections.items():
        if any(alias in title for alias in aliases):
            return body
    return ""


def _extract_step_blocks(flow_content: str) -> list[dict[str, str | int | None]]:
    if not flow_content:
        return []
    matches = list(re.finditer(r"^###\s+(.+)$", flow_content, flags=re.MULTILINE))
    steps: list[dict[str, str | int | None]] = []
    for index, match in enumerate(matches):
        start = match.end()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(flow_content)
        heading = match.group(1).strip()
        duration_match = re.search(r"[（(](\d+)\s*分钟[）)]", heading)
        title = re.sub(r"[（(]\d+\s*分钟[）)]", "", heading)
        title = re.sub(r"^步骤[一二三四五六七八九十0-9]+[:：]?", "", title).strip("：: -")
        steps.append(
            {
                "title": title or f"教学步骤{index + 1}",
                "body": flow_content[start:end].strip(),
                "duration": int(duration_match.group(1)) if duration_match else None,
            }
        )
    return steps


def _ensure_minimum_step_blocks(
    step_blocks: list[dict[str, str | int | None]], topic: str
) -> list[dict[str, str | int | None]]:
    default_steps = [
        ("导入与诊断", f"通过贴近课堂的情境或追问，引出「{topic}」的学习任务与已有认知。"),
        ("概念建构", f"围绕「{topic}」梳理核心概念、关键条件与常见误区。"),
        ("分层练习", "结合基础题与迁移题开展分层练习，并针对易错点进行点拨。"),
        ("总结与评估", "回顾本节重点，完成课堂反馈，并布置后续巩固任务。"),
    ]
    normalized = step_blocks[:4]
    while len(normalized) < 4:
        title, body = default_steps[len(normalized)]
        normalized.append({"title": title, "body": body, "duration": None})
    return normalized


def _normalize_step_durations(
    step_blocks: list[dict[str, str | int | None]], duration_mins: int
) -> list[int]:
    provided = [step.get("duration") for step in step_blocks]
    if all(isinstance(value, int) and value > 0 for value in provided):
        bases = [int(value) for value in provided]
    else:
        default_weights = [0.2, 0.35, 0.25, 0.2]
        bases = []
        for index, value in enumerate(provided):
            if isinstance(value, int) and value > 0:
                bases.append(value)
            else:
                weight = default_weights[index] if index < len(default_weights) else 1 / len(step_blocks)
                bases.append(weight)
    return _allocate_duration(duration_mins, bases)


def _allocate_duration(total_minutes: int, bases: list[int | float]) -> list[int]:
    if not bases:
        return []
    total_weight = float(sum(float(base) for base in bases)) or float(len(bases))
    raw = [total_minutes * float(base) / total_weight for base in bases]
    floors = [max(1, int(value)) for value in raw]
    current = sum(floors)

    if current > total_minutes:
        order = sorted(range(len(floors)), key=lambda index: floors[index], reverse=True)
        for index in order:
            while current > total_minutes and floors[index] > 1:
                floors[index] -= 1
                current -= 1
    elif current < total_minutes:
        fractions = sorted(
            range(len(raw)),
            key=lambda index: raw[index] - int(raw[index]),
            reverse=True,
        )
        pointer = 0
        while current < total_minutes:
            target = fractions[pointer % len(fractions)]
            floors[target] += 1
            current += 1
            pointer += 1

    return floors


def _normalize_section_lines(body: str, fallback_lines: list[str]) -> list[str]:
    lines = [line.strip() for line in (body or "").splitlines() if line.strip()]
    if not lines:
        lines = fallback_lines
    return [line if line.startswith(("-", "*")) else f"- {line}" for line in lines[:4]]


def _normalize_step_lines(body: str, topic: str, index: int) -> list[str]:
    fallback_by_index = [
        f"通过 1 个贴近课堂的问题或情境，引导学生聚焦「{topic}」的学习目标。",
        f"结合板书、示例或类比，讲清「{topic}」的关键概念与判断依据。",
        "组织基础到提升的分层练习，并根据学生反馈进行点拨。",
        "回顾本节核心要点，完成课堂反馈并说明课后任务。",
    ]
    lines = [line.strip() for line in (body or "").splitlines() if line.strip()]
    if not lines:
        lines = [fallback_by_index[index] if index < len(fallback_by_index) else fallback_by_index[-1]]
    return [line if line.startswith(("-", "*")) else f"- {line}" for line in lines[:4]]


# ── Socratic Scaffold Chain ──────────────────────────────────────────────


def socratic_probe_prompt(
    question: str,
    user_answer: str,
    correct_answer: str,
    knowledge_points: list[str],
    round_number: int,
    student_responses: list[str],
) -> str:
    kp = [sanitize_text(x, 80) for x in knowledge_points if sanitize_text(x, 80)]
    kp_text = ", ".join(kp) if kp else "未提供"

    prev_dialogue = ""
    for idx, resp in enumerate(student_responses):
        prev_dialogue += f"第{idx + 1}轮学生回答：{sanitize_text(resp, 400)}\n"

    if round_number == 1:
        return (
            "你是一位善于引导学生主动思考的资深教师。\n"
            "学生做错了一道题，但你不能直接告诉他答案或原因。\n"
            "你只能提出一个引导性问题，帮助他重新审视题目条件和自己的思路。\n"
            "问题必须简短（50字以内），必须与该题的关键概念直接相关。\n"
            "仅输出 JSON 对象，字段为 probe_question。\n\n"
            f"题目：{sanitize_text(question, 1600)}\n"
            f"学生答案：{sanitize_text(user_answer, 500)}\n"
            f"标准答案：{sanitize_text(correct_answer, 500)}\n"
            f"知识点：{kp_text}"
        )
    if round_number == 2:
        return (
            "你是一位善于引导学生主动思考的资深教师。\n"
            "上一轮你对学生提出了引导问题，学生已回答。\n"
            "根据学生的回答，判断他的思维偏差方向，并给出一个更聚焦的提示。\n"
            "提示必须简短（80字以内），不能直接揭示答案，但要让学生离正确理解更近一步。\n"
            "仅输出 JSON 对象，字段为 probe_question 和 thinking_direction"
            "（thinking_direction 用20字描述学生当前的思维偏差）。\n\n"
            f"题目：{sanitize_text(question, 1600)}\n"
            f"学生答案：{sanitize_text(user_answer, 500)}\n"
            f"标准答案：{sanitize_text(correct_answer, 500)}\n"
            f"知识点：{kp_text}\n"
            f"{prev_dialogue}"
        )
    # round_number >= 3: final reveal
    return (
        "你是一位善于引导学生主动思考的资深教师。\n"
        "经过两轮追问，现在请给出完整的诊断分析。\n"
        "仅输出 JSON 对象，字段必须包含：\n"
        "summary（对学生思维路径的总结，100字以内），\n"
        "root_cause（错误根因，80字以内），\n"
        "corrected_thinking（正确思路，分步骤字符串数组，2-4步），\n"
        "encouragement（鼓励语，30字以内）。\n\n"
        f"题目：{sanitize_text(question, 1600)}\n"
        f"学生答案：{sanitize_text(user_answer, 500)}\n"
        f"标准答案：{sanitize_text(correct_answer, 500)}\n"
        f"知识点：{kp_text}\n"
        f"{prev_dialogue}"
    )


# ── Knowledge Topology Explorer ──────────────────────────────────────────


def knowledge_topology_prompt(
    knowledge_points: list[str],
    mastery_data: list[dict[str, Any]],
) -> str:
    kp_list = json.dumps(
        [sanitize_text(kp, 80) for kp in knowledge_points if sanitize_text(kp, 80)][:30],
        ensure_ascii=False,
    )
    mastery_json = json.dumps(mastery_data[:30], ensure_ascii=False)
    return (
        "你是一位学科知识图谱专家。请分析以下知识点之间的逻辑关系。\n"
        "仅输出 JSON 对象，包含两个字段：\n"
        "nodes：数组，每个元素包含 id（知识点名称）、level（认知层级：记忆/理解/应用/分析/综合，选一个）。\n"
        "edges：数组，每个元素包含 source、target、relation（三者都是字符串，\n"
        "relation 只能是 prerequisite 前置依赖 / parallel 并列 / extension 拓展延伸 之一）。\n\n"
        "分析规则：\n"
        "1) 如果理解 B 必须先掌握 A，则 A->B 为 prerequisite。\n"
        "2) 如果 A 和 B 可以独立学习但属于同一主题，则为 parallel。\n"
        "3) 如果 B 是 A 的深层应用或拓展，则为 extension。\n"
        "4) 每对知识点最多一条边，优先标 prerequisite。\n\n"
        f"知识点列表：{kp_list}\n"
        f"掌握情况：{mastery_json}"
    )


# ── Intervention Sandbox ─────────────────────────────────────────────────


def intervention_sandbox_prompt(
    class_wrong_clusters: list[dict[str, Any]],
    student_count: int,
) -> str:
    clusters_json = json.dumps(class_wrong_clusters[:10], ensure_ascii=False)
    return (
        "你是一位资深教研组长，请基于班级错题聚类数据，生成 2-3 个可执行的干预策略。\n"
        "仅输出 JSON 数组，每个元素必须包含以下字段：\n"
        "strategy_name（策略名称，10字以内），\n"
        "description（具体操作描述，50字以内），\n"
        "target_knowledge_points（涉及的知识点数组），\n"
        "estimated_minutes（预估所需课堂分钟数，整数），\n"
        f"estimated_fix_rate（预估修复率百分比，0-100整数，基于{student_count}名学生的),\n"
        "target_student_count（建议覆盖的学生数，整数），\n"
        "priority（优先级：HIGH/MEDIUM/LOW）。\n\n"
        "策略设计原则：\n"
        "1) 策略之间应有差异化（如：集中复讲 vs 分层个性化练习 vs 概念类比重讲）。\n"
        "2) 优先处理错误人数最多、错误次数最集中的知识点。\n"
        "3) 时间估计要合理，单次不超过20分钟。\n\n"
        f"班级错题聚类数据：{clusters_json}\n"
        f"班级总人数：{student_count}"
    )

