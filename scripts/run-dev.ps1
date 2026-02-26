if (-not (Get-Command conda -ErrorAction SilentlyContinue)) {
  throw "conda not found. Please install Miniforge/Conda and create env 'edunexus-ai'."
}

$HostBind = if ($env:APP_HOST) { $env:APP_HOST } else { "0.0.0.0" }
$ApiPort = if ($env:APP_PORT) { $env:APP_PORT } else { "8080" }
$AiPort = if ($env:AI_SERVICE_PORT) { $env:AI_SERVICE_PORT } else { "8000" }
$WebPort = if ($env:WEB_PORT) { $env:WEB_PORT } else { "5173" }

function Get-PortOwnerText([int]$Port) {
  $listen = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
  if (-not $listen) {
    return $null
  }
  $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$($listen.OwningProcess)" -ErrorAction SilentlyContinue
  if ($proc) {
    return "$($proc.Name) pid=$($proc.ProcessId)"
  }
  return "pid=$($listen.OwningProcess)"
}

function Start-IfPortFree([string]$Name, [int]$Port, [string]$Command) {
  $owner = Get-PortOwnerText $Port
  if ($owner) {
    Write-Warning "$Name skipped: port $Port already in use by $owner"
    return
  }
  Start-Process powershell -ArgumentList "-NoExit", "-Command", $Command
}

Write-Host "[1/4] Start infrastructure..."
docker compose up -d

Write-Host "[2/4] Start AI service..."
Start-IfPortFree "AI service" ([int]$AiPort) "Set-Location '$PSScriptRoot/../apps/ai-service'; if (-not (Test-Path '.venv/pyvenv.cfg')) { if (Test-Path '.venv') { Remove-Item '.venv' -Recurse -Force }; conda run --no-capture-output -n edunexus-ai uv venv --python 3.12 .venv }; $env:UV_LINK_MODE='copy'; conda run --no-capture-output -n edunexus-ai uv sync --project .; conda run --no-capture-output -n edunexus-ai uv run --project . uvicorn ai_service.app:app --host $HostBind --port $AiPort"

Write-Host "[3/4] Start API service..."
Start-IfPortFree "API service" ([int]$ApiPort) "Set-Location '$PSScriptRoot/../apps/api'; mvn spring-boot:run '-Dspring-boot.run.arguments=--server.address=$HostBind --server.port=$ApiPort'"

Write-Host "[4/4] Start web service..."
Start-IfPortFree "Web service" ([int]$WebPort) "Set-Location '$PSScriptRoot/../apps/web'; npm install; npm run dev -- --host $HostBind --port $WebPort"

Write-Host "All services are starting in separate terminals."
