param(
  [switch]$KeepInfra,
  [switch]$DownInfra,
  [switch]$ForcePortKill
)

$ApiPort = if ($env:APP_PORT) { [int]$env:APP_PORT } else { 8080 }
$AiPort = if ($env:AI_SERVICE_PORT) { [int]$env:AI_SERVICE_PORT } else { 8000 }
$WebPort = if ($env:WEB_PORT) { [int]$env:WEB_PORT } else { 5173 }

$ProjectRoot = (Resolve-Path "$PSScriptRoot/..").Path

function Get-ListenerInfo([int]$Port) {
  $listen = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
  if (-not $listen) {
    return $null
  }
  $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$($listen.OwningProcess)" -ErrorAction SilentlyContinue
  if (-not $proc) {
    return [PSCustomObject]@{
      Port = $Port
      ProcessId = $listen.OwningProcess
      Name = "unknown"
      CommandLine = ""
    }
  }
  return [PSCustomObject]@{
    Port = $Port
    ProcessId = $proc.ProcessId
    Name = $proc.Name
    CommandLine = $(if ($null -eq $proc.CommandLine) { "" } else { [string]$proc.CommandLine })
  }
}

function Stop-ByPort(
  [string]$ServiceName,
  [int]$Port,
  [string[]]$ExpectedMarkers
) {
  $info = Get-ListenerInfo $Port
  if (-not $info) {
    Write-Host "${ServiceName}: port ${Port} is already free"
    return
  }

  $command = $info.CommandLine.ToLowerInvariant()
  $matched = $false
  foreach ($marker in $ExpectedMarkers) {
    if ($command.Contains($marker.ToLowerInvariant())) {
      $matched = $true
      break
    }
  }

  if (-not $matched -and -not $ForcePortKill) {
    Write-Warning "${ServiceName}: port ${Port} is occupied by non-project process ($($info.Name) pid=$($info.ProcessId)); skipped"
    return
  }

  try {
    Stop-Process -Id $info.ProcessId -Force -ErrorAction Stop
    Write-Host "${ServiceName}: stopped $($info.Name) pid=$($info.ProcessId) on port ${Port}"
  } catch {
    Write-Warning "${ServiceName}: failed to stop pid=$($info.ProcessId) on port ${Port}, error=$($_.Exception.Message)"
  }
}

Write-Host "[1/4] Stop AI service..."
Stop-ByPort "AI service" $AiPort @("ai_service.app:app", "uvicorn.exe")

Write-Host "[2/4] Stop API service..."
Stop-ByPort "API service" $ApiPort @("com.edunexus.api.apiapplication", "spring-boot:run")

Write-Host "[3/4] Stop web service..."
Stop-ByPort "Web service" $WebPort @("vite", "apps\\web")

Write-Host "[4/4] Stop infrastructure..."
if ($KeepInfra) {
  Write-Host "Infrastructure kept running (KeepInfra=true)"
} else {
  Push-Location $ProjectRoot
  try {
    if ($DownInfra) {
      docker compose down
    } else {
      docker compose stop
    }
  } finally {
    Pop-Location
  }
}

Write-Host "Stop script completed."
