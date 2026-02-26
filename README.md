# EduNexus AI

> AI-native education platform for students, teachers, and administrators.  
> 面向学生、教师、管理员的一体化 AI 教育平台。

![Vue 3](https://img.shields.io/badge/Web-Vue%203-42b883)
![Spring Boot](https://img.shields.io/badge/API-Spring%20Boot%203-6db33f)
![FastAPI](https://img.shields.io/badge/AI-FastAPI-009688)
![PostgreSQL](https://img.shields.io/badge/DB-PostgreSQL-336791)
![Qdrant](https://img.shields.io/badge/Vector-Qdrant-e13f6f)
![TypeScript](https://img.shields.io/badge/Frontend-TypeScript-3178c6)

## 1) What is EduNexus AI? | 项目简介

**EN**
- Built from 12 SSOT documents (`doc/00` ~ `doc/11`) to ensure product, API, data, auth, and acceptance consistency.
- Provides a full workflow loop: learning -> analysis -> recommendation -> governance.
- Includes role-based workspaces for Student / Teacher / Admin.

**中文**
- 基于 12 份 SSOT 文档（`doc/00` ~ `doc/11`）落地，确保产品、接口、数据、鉴权与验收一致。
- 覆盖学习、分析、建议、治理的完整闭环。
- 提供学生端、教师端、管理端分角色工作空间。

## 2) Core Capabilities | 核心能力

| Role | EN | 中文 |
|---|---|---|
| Student | Chat RAG, exercises, wrong-book, AI-generated questions, profile | 智能问答、练习做题、错题本、AI 出题、个人信息 |
| Teacher | Knowledge-base ingestion, AI lesson plans, analytics, suggestions | 知识库上传与检索、AI 教案、学情分析、教师建议 |
| Admin | User/resource governance, audit logs, dashboard | 用户与资源治理、审计日志、指标看板 |

## 3) Architecture | 架构概览

```text
apps/web (Vue3 + Vite + TS)
      |
      v
apps/api (Spring Boot 3, JWT, RBAC, Flyway)
      |
      +--> apps/ai-service (FastAPI, model routing, RAG)
      |
      +--> PostgreSQL / Redis / Qdrant / MinIO (docker compose)
```

## 4) Tech Stack | 技术栈

- **Web**: Vue 3, Vite, Pinia, Vue Router, Axios, TypeScript
- **API**: Spring Boot 3, Java 21, Spring Security, Flyway, PostgreSQL
- **AI Service**: FastAPI, Qdrant, httpx, numpy
- **Infra**: Docker Compose (PostgreSQL, Redis, Qdrant, MinIO)

## 5) Quick Start | 快速启动

### 5.1 Prerequisites | 环境要求

- Node.js 18+
- Java 21+
- Maven 3.9+
- Python 3.10+
- Docker / Docker Compose

### 5.2 Configure `.env` | 配置环境变量

**macOS/Linux**
```bash
cp .env.example .env
```

**Windows PowerShell**
```powershell
Copy-Item .env.example .env
```

### 5.3 Start the stack | 启动服务

**Option A: One-command (Windows)**
```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-dev.ps1
```

**Option B: Manual (all platforms)**

1) Infrastructure
```bash
docker compose up -d
```

2) AI service
```bash
cd apps/ai-service
pip install -r requirements.txt
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

3) API service
```bash
cd apps/api
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.address=0.0.0.0 --server.port=8080"
```

4) Web service
```bash
cd apps/web
npm install
npm run dev -- --host 0.0.0.0 --port 5173
```

### 5.4 Access URLs | 访问地址

- Web: `http://127.0.0.1:5173`
- API: `http://127.0.0.1:8080`
- AI Service: `http://127.0.0.1:8000/docs`
- MinIO Console: `http://127.0.0.1:9001`

## 6) AI Routing Strategy | AI 路由策略

**Recommended**: `LLM_PROVIDER=auto`

- **Simple queries** -> local `ollama`
- **Complex queries** -> cloud `deepseek`
- **Fallback** -> automatic provider downgrade if primary fails

Example:
```env
LLM_PROVIDER=auto
OLLAMA_BASE_URL=http://127.0.0.1:11434
OLLAMA_MODEL=deepseek-r1:8b
DEEPSEEK_API_KEY=<your_deepseek_key>
DEEPSEEK_MODEL=deepseek-chat
DEEPSEEK_COMPLEX_MODEL=deepseek-reasoner
```

More details: `doc/08-AI与RAG策略.md`, `doc/09-配置与环境变量规范.md`

## 7) Demo Accounts | 默认账号

- Admin: `admin / 12345678`
- Teacher: `teacher01 / 12345678`
- Student: `student01 / 12345678`

## 8) Project Structure | 目录结构

```text
apps/
  api/          # Spring Boot backend
  ai-service/   # FastAPI AI and RAG service
  web/          # Vue 3 frontend
doc/
  00-11*        # SSOT / PRD / API contract / acceptance docs
scripts/
  run-dev.ps1
  run-dev.sh
```

## 9) Quality Checks | 质量检查

```bash
# API
cd apps/api && mvn test

# Web
cd apps/web && npm run build && npm run typecheck

# AI service syntax
cd apps/ai-service && python -m py_compile main.py
```

## 10) API Contract & Docs | 接口与文档

- OpenAPI contract: `doc/06-API契约-openapi.yaml`
- Acceptance script: `doc/11-验收清单与演示脚本.md`
- Auth & permission: `doc/07-鉴权与权限模型.md`
- Data model & migration: `doc/05-数据模型与迁移规范.md`

## 11) Roadmap | 路线图

**EN**
- Add full automated E2E test suite and CI quality gates
- Add richer analytics and class-level dashboards
- Add production deployment templates and SLO monitoring

**中文**
- 补齐端到端自动化测试与 CI 质量门禁
- 增强班级维度分析与可视化看板
- 增加生产部署模板与 SLO 监控

## 12) Security Notes | 安全说明

- Do not commit `.env` or any API keys.
- Rotate leaked keys immediately.
- Replace all demo credentials for production.
- Use strong `JWT_SECRET` in non-local environments.

## 13) Contributing | 贡献指南

Issues and PRs are welcome.  
欢迎提交 Issue 与 PR，一起改进项目。

---

If this project helps your work, please consider giving it a star.  
如果这个项目对你有帮助，欢迎点个 Star 支持一下。
