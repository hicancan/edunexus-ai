from ai_service.prompts import (
    extract_lesson_plan_step_durations,
    has_valid_plan_structure,
    lesson_plan_repair_prompt,
    sanitize_lesson_plan_markdown,
)

VALID_PLAN = """
## 1. 教学目标
掌握牛顿第二定律。

## 2. 重难点
重点是 F=ma。

## 3. 教学流程（含时间分配）
### 步骤一：导入（10 分钟）
- 用生活案例引入。

### 步骤二：概念讲解（15 分钟）
- 讲解合力、质量、加速度。

### 步骤三：例题训练（10 分钟）
- 完成典型例题。

### 步骤四：总结反馈（10 分钟）
- 回顾重点并布置任务。

## 4. 作业与评估
完成练习并提交。
""".strip()


def test_sanitize_lesson_plan_markdown_repairs_step_heading_typo() -> None:
    raw = "### 步, 三：课堂练习与互动（20 分钟）"
    assert sanitize_lesson_plan_markdown(raw) == "### 步骤三：课堂练习与互动（20 分钟）"


def test_extract_lesson_plan_step_durations_reads_all_step_minutes() -> None:
    assert extract_lesson_plan_step_durations(VALID_PLAN) == [10, 15, 10, 10]


def test_has_valid_plan_structure_requires_matching_duration_budget() -> None:
    assert has_valid_plan_structure(VALID_PLAN, 45) is True
    assert has_valid_plan_structure(VALID_PLAN, 60) is False


def test_lesson_plan_repair_prompt_includes_duration_contract() -> None:
    prompt = lesson_plan_repair_prompt("原始内容", 45)
    assert "45 分钟" in prompt
    assert "步骤一：标题（X 分钟）" in prompt
