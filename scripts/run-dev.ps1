if (-not (Get-Command conda -ErrorAction SilentlyContinue)) {
  throw "conda not found. Please install Miniforge/Conda and create env 'edunexus-ai'."
}

Write-Host "[1/4] Start infrastructure..."
docker compose up -d

Write-Host "[2/4] Start AI service..."
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$PSScriptRoot/../apps/ai-service'; conda run -n edunexus-ai uv sync --project . --python 3.12; conda run -n edunexus-ai uv run --project . --python 3.12 uvicorn ai_service.app:app --host 0.0.0.0 --port 8000"

Write-Host "[3/4] Start API service..."
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$PSScriptRoot/../apps/api'; mvn spring-boot:run '-Dspring-boot.run.arguments=--server.address=0.0.0.0 --server.port=8080'"

Write-Host "[4/4] Start web service..."
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$PSScriptRoot/../apps/web'; npm install; npm run dev -- --host 0.0.0.0 --port 5173"

Write-Host "All services are starting in separate terminals."
