# EduNexus AI AI 与 RAG 策略

## 1. 文档目标
定义 AI 能力的工程化落地方式，确保聊天、错题解析、AI 出题、教案生成四条链路可稳定复现。

## 2. 设计原则

1. **可溯源**：AI 回答必须尽量带来源引用。
2. **少幻觉**：无检索命中时明确“不确定”，禁止编造。
3. **可切换**：模型供应商可通过 `.env` 切换（Gemini/OpenAI/DeepSeek/Ollama）。
4. **可降级**：云服务失败时有本地兜底路径。

## 3. 文档摄取流水线（教师知识库）

## 3.1 流程

1. 教师上传文件（PDF/Docx）到 MinIO。
2. 创建 `documents` 记录，状态 `UPLOADING`。
3. 解析文本并结构清洗，状态 `PARSING`。
4. 切分 chunk 并向量化，状态 `EMBEDDING`。
5. 写入 Qdrant 集合 `knowledge_chunks`，状态 `READY`。
6. 失败时状态 `FAILED` 并记录 `error_message`。

## 3.2 切分策略

- 推荐算法：Recursive + 语义边界切分。
- chunk 大小：`500~900` tokens。
- chunk overlap：`80~120` tokens。
- 每个 chunk 必带 payload：`document_id`、`teacher_id`、`filename`、`chunk_index`、`content`、`content_hash`。

## 3.3 向量模型

- 本地推荐（Ollama）：`qwen3-embedding:0.6b`（MTEB 多语言排行榜 #1 系列，639MB）
- 云端备选：OpenAI text-embedding-3-small / Gemini embedding
- 输出维度：1024（默认）
- 距离函数：Cosine Similarity

## 4. 检索与生成策略（RAG）

## 4.1 检索流程

1. 用户问题规范化（去噪、术语标准化）。
2. 生成 query embedding。
3. 先做 payload 过滤（必须带 `teacher_id` 或 `class_id`）。
4. Top-K 召回（默认 K=5）。
5. 可选重排（rerank）后取前 3 条入 prompt。

## 4.2 生成流程

1. 构造系统提示词（角色 + 规则 + 输出格式）。
2. 注入检索上下文（含来源元数据）。
3. 调用 LLM。
4. 输出后处理（格式校验、引用对齐、敏感过滤）。
5. 返回响应并落库（`chat_messages.citations`）。

## 5. 场景化 Prompt 模板

## 5.1 学生聊天（R-CHAT-08）

核心约束：

1. 只能依据 `<Context>` 回答。
2. 未命中必须明确说明“课堂资料不足”。
3. 回答必须带来源文件名引用。
4. 输出支持 Markdown，公式用 LaTeX。

## 5.2 错题 AI 解析（R-EX-09）

输入：题目、标准答案、学生答案、知识点、教师建议。

输出结构：

1. 鼓励语
2. 概念解释
3. 分步推导
4. 错因定位
5. 下一步练习建议

## 5.3 AI 个性化出题（R-AIQ-01~08）

输入：`count`、`subject`、`difficulty`、`concept_tags`、学生错题画像、教师建议。

输出必须严格 JSON（禁止代码块包裹）：

```json
[
  {
    "content": "题干",
    "options": {"A": "", "B": "", "C": "", "D": ""},
    "correct_answer": "A",
    "explanation": "分步解析",
    "knowledge_points": ["牛顿第二定律"]
  }
]
```

## 5.4 教案生成（R-TCH-05）

输出结构固定：

1. 教学目标
2. 重难点
3. 教学流程（含时间分配）
4. 作业与评估

## 6. 模型路由策略

## 6.1 Provider 级路由

- `LLM_PROVIDER=auto`：自动路由（见 6.2 场景级路由）。
- `LLM_PROVIDER=ollama`：本地模型。
- `LLM_PROVIDER=gemini|openai|deepseek`：云端模型。

## 6.2 场景级路由（4 层模型矩阵）

模型配置的完整环境变量见 `09-配置与环境变量规范.md`。

| 场景 | Ollama 模型 | 环境变量 | 理由 |
|---|---|---|---|
| **嵌入** | `qwen3-embedding:0.6b` | `OLLAMA_EMBED_MODEL` | MTEB 多语言 #1 系列，639MB 轻量 |
| **快速问答** | `qwen3:4b` | `OLLAMA_MODEL` | 低延迟，简单场景 |
| **RAG 主力** | `qwen3:8b` | `OLLAMA_RAG_MODEL` | 中文最强 8B，速度优于 deepseek-r1 |
| **深度推理** | `deepseek-r1:8b` | `OLLAMA_COMPLEX_MODEL` | CoT 推理，出题/教案 |

场景分派规则：
1. 文档嵌入、查询嵌入 → `OLLAMA_EMBED_MODEL`
2. 聊天 RAG 问答 → `OLLAMA_RAG_MODEL`
3. 错题解析、AI 出题、教案生成 → `OLLAMA_COMPLEX_MODEL`
4. 简单问答/分类路由 → `OLLAMA_MODEL`

## 6.3 降级策略

1. `auto` 模式主路径：按场景分派到对应 Ollama 模型。
2. 主 provider 超时/限流/鉴权失败：按可用性回退到其他 provider（优先 ollama）。
3. 备用 provider 仍失败：返回“服务繁忙”并保留重试按钮。

## 7. 安全与防护

1. Prompt 注入防护：用户输入做模板转义和关键词过滤。
2. 输出合规检查：禁止泄露系统提示词和敏感配置。
3. 引用一致性检查：回答中引用必须对应真实 chunk。
4. 隔离检查：检索必须有 teacher/class 过滤条件。

## 8. 参数基线（MVP）

| 场景 | temperature | max_tokens | top_p |
|---|---:|---:|---:|
| 聊天 RAG | 0.2 | 1200 | 0.9 |
| 错题解析 | 0.3 | 1400 | 0.95 |
| AI 出题 | 0.7 | 1800 | 0.95 |
| 教案生成 | 0.7 | 2200 | 0.95 |

## 9. 质量指标（验收阈值）

1. 聊天回答引用率 >= 85%。
2. AI 出题 JSON 格式合法率 >= 95%。
3. 文档删除后向量残留率 = 0（按 document_id 检查）。
4. 空命中场景禁止幻觉回答（必须触发不确定提示）。

## 10. 可观测要求

每次 AI 调用至少记录：

- provider/model
- latency
- prompt token / completion token
- 是否命中知识库
- chunk IDs（或引用元数据）
- 请求 traceId

---
文档状态：`v1.1.0`（2026-02-26 嵌入模型更新、模型矩阵对齐 doc 09）
