Write-Host "[1/4] 启动基础依赖..."
docker compose up -d

Write-Host "[2/4] 启动 AI 服务..."
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot/../apps/ai-service'; pip install -r requirements.txt; python -m uvicorn main:app --host 127.0.0.1 --port 8000"

Write-Host "[3/4] 启动 API 服务..."
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot/../apps/api'; mvn spring-boot:run"

Write-Host "[4/4] 启动 Web 服务..."
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot/../apps/web'; npm install; npm run dev"

Write-Host "所有服务已启动。"
