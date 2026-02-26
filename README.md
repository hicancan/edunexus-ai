# EduNexus AI

基于 12 份 SSOT 文档落地的 MVP 项目，实现学生端、教师端、管理端完整闭环。

## 技术栈

- Web: Vue 3 + Vite + Pinia + Axios
- API: Spring Boot 3 + Java 21 + Flyway + PostgreSQL
- AI Service: FastAPI + Qdrant + Ollama/Gemini
- Infra: Docker Compose (PostgreSQL/Redis/Qdrant/MinIO)

## 目录

```text
apps/
  api/
  ai-service/
  web/
doc/
  00-11 SSOT 文档
```

## 快速启动

1. 启动基础依赖：

```bash
docker compose up -d
```

2. 启动 AI 服务：

```bash
cd apps/ai-service
pip install -r requirements.txt
python -m uvicorn main:app --host 127.0.0.1 --port 8000
```

3. 启动 API 服务：

```bash
cd apps/api
mvn spring-boot:run
```

4. 启动前端：

```bash
cd apps/web
npm install
npm run dev
```

或一键启动（Windows）：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-dev.ps1
```

## 默认账号

- 管理员：admin / 12345678
- 教师：teacher01 / 12345678
- 学生：student01 / 12345678

## 模型配置

- 本地 Ollama（默认）：`LLM_PROVIDER=ollama`
- 云端 Gemini：`LLM_PROVIDER=gemini` + `GOOGLE_API_KEY`

参考 `doc/09-配置与环境变量规范.md`。
