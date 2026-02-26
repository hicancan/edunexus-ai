# AGENTS.md

## Purpose
- Provide practical commands and coding conventions for agentic coding agents.
- Scope: entire repository (`apps/web`, `apps/api`, `apps/ai-service`, scripts, and docs).
- Prefer small, reversible edits that follow existing local patterns.

## Rule Sources Checked
- Cursor rules checked: `.cursor/rules/` and `.cursorrules` -> not found.
- Copilot rules checked: `.github/copilot-instructions.md` -> not found.
- Primary project rules come from `README.md`, `doc/10-*.md`, and `doc/09-*.md`.

## Repository Map
- `apps/web`: Vue 3 + Vite + Pinia frontend.
- `apps/api`: Spring Boot 3 + Java 21 backend.
- `apps/ai-service`: FastAPI AI service.
- `scripts/run-dev.sh` and `scripts/run-dev.ps1`: start all services.
- `docker-compose.yml`: local infra (Postgres, Redis, Qdrant, MinIO).

## Build, Lint, and Test Commands

### Root / Infra
- Start infra only: `docker compose up -d`
- Stop infra: `docker compose down`
- Start full stack on Linux/macOS: `bash scripts/run-dev.sh`
- Start full stack on Windows: `powershell -ExecutionPolicy Bypass -File scripts/run-dev.ps1`

### Frontend (`apps/web`)
- Install dependencies: `npm install`
- Run dev server: `npm run dev`
- Build production bundle: `npm run build`
- Preview built bundle: `npm run preview`
- Lint status: no lint script configured in `apps/web/package.json`
- Test status: no test framework configured yet

### Backend API (`apps/api`)
- Run app: `mvn spring-boot:run`
- Compile: `mvn compile`
- Run tests: `mvn test`
- Package artifact: `mvn package`
- Lint status: no Checkstyle/Spotless/PMD plugin configured
- Baseline quality gates: `mvn test` and `mvn package`

### AI Service (`apps/ai-service`)
- Install dependencies: `pip install -r requirements.txt`
- Run service: `python -m uvicorn main:app --host 127.0.0.1 --port 8000`
- Test status: no Python tests currently present
- Lint status: no ruff/flake8/black config currently present

## Single-Test Quick Reference
- Maven single test class: `mvn -Dtest=AuthControllerTest test`
- Maven single test method: `mvn -Dtest=AuthControllerTest#loginSuccess test`
- Maven selected test set: `mvn -Dtest=AuthControllerTest,StudentControllerTest test`
- Pytest single file (if pytest is added): `pytest tests/test_chat.py`
- Pytest single test (if pytest is added): `pytest tests/test_chat.py::test_chat_happy_path`
- Vitest single file (if Vitest is added): `npm run test -- src/pages/LoginPage.spec.js`
- Vitest by test name (if Vitest is added): `npm run test -- -t "renders login form"`

## Agent Workflow Tips
- Read nearby code before editing; this repo has app-specific conventions.
- Do not assume lint/test scripts exist; check app manifests first.
- If a required check is missing, run the closest available build/test command.
- Keep changes scoped to the requested task; avoid opportunistic refactors.

## Code Style Guidelines

### Cross-Cutting Rules
- Keep API behavior aligned with the OpenAPI contract in `doc/06-*.yaml`.
- Keep auth and permission logic aligned with `doc/07-*.md`.
- Keep data model and migrations aligned with `doc/05-*.md`.
- Never hardcode secrets; use environment variables.
- Do not commit `.env`, tokens, API keys, or passwords.
- Preserve response envelope shape: `code`, `message`, `data`, `traceId`, `timestamp`.

### Imports and Dependencies
- Add dependencies only when required by the task.
- Remove unused imports in touched files.
- Java: no wildcard imports; one import per line.
- JS/Python: order imports as standard library, third-party, local modules.

### Formatting and Structure
- Follow existing style in each file you touch.
- JS/Vue style here: 2-space indent, semicolons, double quotes.
- Java style here: 4-space indent, braces on same line, concise methods.
- Python style here: PEP8-like formatting, 4-space indent, explicit type hints when practical.
- Prefer small helpers over deep nesting.

### Naming Conventions
- Java classes: PascalCase.
- Java methods/variables: camelCase.
- Vue page/component filenames: PascalCase (for example `StudentPage.vue`).
- JS variables/functions: camelCase.
- Python functions/variables: snake_case; classes: PascalCase.
- SQL tables/columns/migrations: snake_case.
- API routes: lowercase, namespaced under `/api/v1/...`.

### Types and Data Contracts
- Java request/response payloads commonly use `record` DTOs.
- Use Bean Validation on Java request fields (`@NotBlank`, `@Pattern`, etc.) where needed.
- Keep JSON key names stable (`sessionId`, `createdAt`, etc.).
- FastAPI request bodies should use Pydantic models.
- Do not change payload shapes without updating all consumers.

### Error Handling
- Java input and missing-resource errors should use `IllegalArgumentException`.
- Java auth/permission failures should use `SecurityException`.
- Let `GlobalExceptionHandler` produce normalized error responses.
- Python should use `HTTPException` for client-facing failures.
- Catch broad exceptions only for intentional fallback behavior.
- Frontend should display actionable error states for failed API calls.

### Backend Layering and Transaction Rules
- Controllers handle HTTP mapping, auth checks, and response wrapping.
- Data access should go through `DbService` with parameterized SQL.
- Multi-write business flows should use `@Transactional`.
- Enforce role and ownership checks before reading/writing protected data.
- Preserve request tracing behavior (`TraceFilter`, `X-Request-Id`).

### Frontend Rules
- Use Composition API with `<script setup>`.
- Keep API access centralized in `src/services/api.js`.
- Keep auth state in Pinia (`src/stores/auth.js`).
- Keep role/login guarding in router guards.
- Include loading/empty/error states for async pages.

### AI Service Rules
- Keep model provider routing environment-driven (`LLM_PROVIDER`, provider vars).
- Do not embed provider secrets in code.
- Keep prompt construction explicit and maintainable.
- Keep parsing and fallback logic defensive and deterministic.
- Preserve teacher/student scope filtering in retrieval logic.

### SQL and Flyway Rules
- All schema changes must be new files in `apps/api/src/main/resources/db/migration`.
- Migration naming format: `V<timestamp>__<description>.sql`.
- Prefer additive migrations; avoid rewriting historical migrations.
- Use UUIDs and existing naming patterns consistently.

## Testing and Verification Expectations
- Backend changes: run `mvn test` (or targeted `-Dtest=...`) in `apps/api`.
- Frontend changes: run `npm run build` in `apps/web`.
- AI service changes: run uvicorn startup command to catch runtime/import errors.
- If no automated test exists for changed behavior, add one when feasible or document manual verification.

## Safety Rules
- Do not edit generated/ignored directories: `apps/web/node_modules`, `apps/web/dist`, `apps/api/target`.
- Do not expose secrets in logs, responses, tests, or fixtures.
- Keep CORS/auth/trace behavior stable unless the task explicitly changes security.
- Avoid unrelated cleanup/refactor in the same task.

## Commit Guidance
- Preferred commit format: `feat|fix|refactor|docs(scope): message`.
- Keep each commit focused on one intent.

## Pre-Completion Checklist
- Commands used are valid for the touched app.
- API responses still use the standard envelope.
- Role and ownership checks still hold for protected endpoints.
- No secrets were introduced into tracked files.
- Relevant build/test command was run, or missing automation was clearly noted.
