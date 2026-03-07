$RepoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$AiAppDir = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot "apps\ai-service"))
$ApiAppDir = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot "apps\api"))
$WebAppDir = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot "apps\web"))

$HostBind = if ($env:APP_HOST) { $env:APP_HOST } else { "0.0.0.0" }
$ApiPort = if ($env:APP_PORT) { $env:APP_PORT } else { "8080" }
$AiPort = if ($env:AI_SERVICE_PORT) { $env:AI_SERVICE_PORT } else { "8000" }
$WebPort = if ($env:WEB_PORT) { $env:WEB_PORT } else { "5173" }

function Resolve-CommandPath([string[]]$Names, [string]$InstallHint, [string[]]$FallbackPaths = @()) {
  foreach ($name in $Names) {
    $command = Get-Command $name -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) {
      return $command.Source
    }
  }

  foreach ($path in $FallbackPaths) {
    if ($path -and (Test-Path $path)) {
      return (Resolve-Path $path).Path
    }
  }

  throw "$($Names[0]) not found. $InstallHint"
}

$jetBrainsMaven = Get-ChildItem -Path "C:\Program Files\JetBrains" -Directory -ErrorAction SilentlyContinue |
  Sort-Object Name -Descending |
  ForEach-Object { Join-Path $_.FullName "plugins\maven\lib\maven3\bin\mvn.cmd" } |
  Where-Object { Test-Path $_ } |
  Select-Object -First 1
$mavenHomeCmd = if ($env:MAVEN_HOME) { Join-Path $env:MAVEN_HOME "bin\mvn.cmd" } else { $null }
$m2HomeCmd = if ($env:M2_HOME) { Join-Path $env:M2_HOME "bin\mvn.cmd" } else { $null }
$versionFoxMavenCmd = Join-Path $HOME ".version-fox\sdks\maven\bin\mvn.cmd"

$UvExe = Resolve-CommandPath @("uv", "uv.exe") "Please install uv using: powershell -ExecutionPolicy ByPass -c `"irm https://astral.sh/uv/install.ps1 | iex`""
$MavenCmd = Resolve-CommandPath @("mvn.cmd", "mvn") "Please install Maven or ensure mvn.cmd is available on PATH." @(
  $mavenHomeCmd,
  $m2HomeCmd,
  $versionFoxMavenCmd,
  $jetBrainsMaven
)
$NpmCmd = Resolve-CommandPath @("npm.cmd", "npm") "Please install Node.js or ensure npm.cmd is available on PATH."

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

function Start-IfPortFree([string]$Name, [int]$Port, [string]$EncodedCommand) {
  $owner = Get-PortOwnerText $Port
  if ($owner) {
    Write-Warning "$Name skipped: port $Port already in use by $owner"
    return
  }
  Start-Process powershell -WorkingDirectory $RepoRoot -ArgumentList "-NoExit", "-EncodedCommand", $EncodedCommand
}

Write-Host "[1/4] Start infrastructure..."
docker compose up -d

Write-Host "[2/4] Start AI service (Powered by global uv)..."
$aiCmd = "Set-Location '$AiAppDir'; `$env:UV_LINK_MODE='copy'; & '$UvExe' run --python 3.12 uvicorn ai_service.app:app --host $HostBind --port $AiPort"
$aiEncoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($aiCmd))
Start-IfPortFree "AI service" ([int]$AiPort) $aiEncoded

Write-Host "[3/4] Start API service..."
$apiCmd = "Set-Location '$ApiAppDir'; & '$MavenCmd' spring-boot:run '-Dspring-boot.run.arguments=--server.address=$HostBind --server.port=$ApiPort'"
$apiEncoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($apiCmd))
Start-IfPortFree "API service" ([int]$ApiPort) $apiEncoded

Write-Host "[4/4] Start web service..."
$webCmd = "Set-Location '$WebAppDir'; & '$NpmCmd' install; & '$NpmCmd' run dev -- --host $HostBind --port $WebPort"
$webEncoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($webCmd))
Start-IfPortFree "Web service" ([int]$WebPort) $webEncoded

Write-Host "All services are starting in separate terminals."
