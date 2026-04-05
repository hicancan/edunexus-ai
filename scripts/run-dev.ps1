$RepoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$AiAppDir = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot "apps\ai-service"))
$ApiAppDir = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot "apps\api"))
$WebAppDir = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot "apps\web"))

function Import-DotEnv([string]$Path) {
  if (-not (Test-Path $Path)) {
    return
  }

  foreach ($rawLine in Get-Content $Path) {
    $line = $rawLine.Trim()
    if (-not $line -or $line.StartsWith("#")) {
      continue
    }

    $idx = $line.IndexOf("=")
    if ($idx -lt 1) {
      continue
    }

    $key = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1)
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
      $value = $value.Substring(1, $value.Length - 2)
    }

    if (-not [Environment]::GetEnvironmentVariable($key, "Process")) {
      Set-Item -Path "env:$key" -Value $value
    }
  }
}

function Set-ProcessEnv([string]$Name, [string]$Value) {
  if (-not [string]::IsNullOrWhiteSpace($Value)) {
    Set-Item -Path "env:$Name" -Value $Value
  }
}

function Normalize-DatabaseUrl([string]$Value) {
  if ([string]::IsNullOrWhiteSpace($Value)) {
    return $null
  }

  $trimmed = $Value.Trim()
  if ($trimmed -match '^jdbc:postgresql://') {
    return $trimmed
  }

  if ($trimmed -match '^(postgres|postgresql)://') {
    try {
      $uri = [Uri]$trimmed
      if ($uri.UserInfo) {
        $parts = $uri.UserInfo.Split(":", 2)
        if ($parts.Length -ge 1 -and [string]::IsNullOrWhiteSpace($env:POSTGRES_USER)) {
          Set-ProcessEnv "POSTGRES_USER" $parts[0]
        }
        if ($parts.Length -ge 2 -and [string]::IsNullOrWhiteSpace($env:POSTGRES_PASSWORD)) {
          Set-ProcessEnv "POSTGRES_PASSWORD" $parts[1]
        }
      }
      $dbName = $uri.AbsolutePath.TrimStart("/")
      if ([string]::IsNullOrWhiteSpace($dbName)) {
        $dbName = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "edunexus" }
      }
      $port = if ($uri.Port -gt 0) { $uri.Port } else { 5432 }
      return "jdbc:postgresql://$($uri.Host):$port/$dbName"
    } catch {
      return $trimmed
    }
  }

  return $trimmed
}

function Normalize-LegacyOllamaModel([string]$Value) {
  if ([string]::IsNullOrWhiteSpace($Value)) {
    return $Value
  }

  switch ($Value.Trim().ToLowerInvariant()) {
    "qwen3:4b" { return "qwen3.5:4b" }
    "qwen3:8b" { return "qwen3.5:9b" }
    default { return $Value.Trim() }
  }
}

function Test-JavaHomeCandidate([string]$Candidate) {
  if ([string]::IsNullOrWhiteSpace($Candidate)) {
    return $false
  }

  $binJava = Join-Path $Candidate "bin\java.exe"
  $jvmCfg = Join-Path $Candidate "lib\jvm.cfg"
  return (Test-Path $binJava) -and (Test-Path $jvmCfg)
}

function Get-JavaReleaseMajor([string]$Candidate) {
  $releaseFile = Join-Path $Candidate "release"
  if (-not (Test-Path $releaseFile)) {
    return $null
  }

  $versionLine =
          Get-Content $releaseFile |
          Where-Object { $_ -like "JAVA_VERSION=*" } |
          Select-Object -First 1
  if (-not $versionLine) {
    return $null
  }

  $version = ($versionLine -replace '^JAVA_VERSION="?','' -replace '"$','')
  $majorPart = ($version -split "[._-]")[0]
  if ($majorPart -match '^\d+$') {
    return [int]$majorPart
  }
  return $null
}

function Resolve-JavaHome() {
  $candidates = [System.Collections.Generic.List[string]]::new()
  if ($env:JAVA_HOME) {
    [void]$candidates.Add($env:JAVA_HOME)
  }

  $javaCommands = @(Get-Command java.exe -All -ErrorAction SilentlyContinue)
  foreach ($command in $javaCommands) {
    $candidate = Split-Path (Split-Path $command.Source -Parent) -Parent
    if ($candidate) {
      [void]$candidates.Add($candidate)
    }
  }

  foreach ($entry in ($env:Path -split ";")) {
    if ([string]::IsNullOrWhiteSpace($entry)) {
      continue
    }
    $javaExe = Join-Path $entry "java.exe"
    if (Test-Path $javaExe) {
      $candidate = Split-Path $entry -Parent
      if ($candidate) {
        [void]$candidates.Add($candidate)
      }
    }
  }

  $jetBrainsHomes = Get-ChildItem "C:\Program Files\JetBrains" -Directory -ErrorAction SilentlyContinue |
          Sort-Object Name -Descending |
          ForEach-Object { Join-Path $_.FullName "jbr" }
  foreach ($candidate in $jetBrainsHomes) {
    if ($candidate) {
      [void]$candidates.Add($candidate)
    }
  }

  $userJdks = Join-Path $HOME ".jdks"
  if (Test-Path $userJdks) {
    foreach ($candidate in (Get-ChildItem $userJdks -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)) {
      [void]$candidates.Add($candidate)
    }
  }

  $uniqueCandidates = $candidates | Select-Object -Unique
  foreach ($candidate in $uniqueCandidates) {
    $major = Get-JavaReleaseMajor $candidate
    if ((Test-JavaHomeCandidate $candidate) -and $major -and $major -ge 21) {
      return $candidate
    }
  }

  foreach ($candidate in $uniqueCandidates) {
    if (Test-JavaHomeCandidate $candidate) {
      return $candidate
    }
  }
  return $null
}

function Should-UseComposeValue([string]$CurrentValue, [string[]]$DefaultPrefixes) {
  if ([string]::IsNullOrWhiteSpace($CurrentValue)) {
    return $true
  }

  $lowered = $CurrentValue.Trim().ToLowerInvariant()
  foreach ($prefix in $DefaultPrefixes) {
    if ($lowered.StartsWith($prefix.ToLowerInvariant())) {
      return $true
    }
  }
  return $false
}

function Get-ComposeHostPort([string]$Service, [int]$ContainerPort) {
  Push-Location $RepoRoot
  try {
    $raw = docker compose port $Service $ContainerPort 2>$null | Select-Object -First 1
    if (-not $raw) {
      return $null
    }
    if ($raw -match ":(\d+)$") {
      return [int]$Matches[1]
    }
    throw "Failed to parse mapped port for ${Service}:${ContainerPort} from '${raw}'"
  } finally {
    Pop-Location
  }
}

Import-DotEnv (Join-Path $RepoRoot ".env")

$normalizedDatabaseUrl = Normalize-DatabaseUrl $env:DATABASE_URL
if ($normalizedDatabaseUrl) {
  Set-ProcessEnv "DATABASE_URL" $normalizedDatabaseUrl
}
foreach ($modelVar in @(
    "OLLAMA_MODEL",
    "OLLAMA_RAG_MODEL",
    "OLLAMA_STRUCTURED_MODEL",
    "OLLAMA_LESSON_PLAN_MODEL"
  )) {
  $normalizedModel = Normalize-LegacyOllamaModel ([Environment]::GetEnvironmentVariable($modelVar, "Process"))
  if ($normalizedModel) {
    Set-ProcessEnv $modelVar $normalizedModel
  }
}

$resolvedJavaHome = Resolve-JavaHome
if ($resolvedJavaHome) {
  Set-ProcessEnv "JAVA_HOME" $resolvedJavaHome
  if (-not (($env:Path -split ";") | Where-Object { $_ -eq (Join-Path $resolvedJavaHome "bin") })) {
    $env:Path = "$(Join-Path $resolvedJavaHome "bin");$env:Path"
  }
}

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

$PwshCmd = Resolve-CommandPath @("pwsh.exe", "pwsh", "powershell.exe", "powershell") "Please install PowerShell." @(
  (Join-Path $PSHOME "pwsh.exe"),
  (Join-Path $PSHOME "powershell.exe")
)

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

function Escape-SingleQuoted([string]$Value) {
  return ($Value -replace "'", "''")
}

function Build-EnvPrelude([string[]]$Names) {
  $parts = @()
  foreach ($name in $Names) {
    $value = [Environment]::GetEnvironmentVariable($name, "Process")
    if ([string]::IsNullOrWhiteSpace($value)) {
      continue
    }
    $parts += "`$env:${name}='$(Escape-SingleQuoted $value)'"
  }
  return ($parts -join "; ")
}

function Start-IfPortFree([string]$Name, [int]$Port, [string]$EncodedCommand) {
  $owner = Get-PortOwnerText $Port
  if ($owner) {
    Write-Warning "$Name skipped: port $Port already in use by $owner"
    return
  }
  Start-Process $PwshCmd -WorkingDirectory $RepoRoot -ArgumentList "-NoLogo", "-NoExit", "-EncodedCommand", $EncodedCommand
}

Write-Host "[1/4] Start infrastructure..."
Push-Location $RepoRoot
try {
  docker compose up -d
} finally {
  Pop-Location
}

$postgresPort = Get-ComposeHostPort "postgres" 5432
if ($postgresPort -and (Should-UseComposeValue $env:DATABASE_URL @(
      "jdbc:postgresql://127.0.0.1:5432/",
      "jdbc:postgresql://localhost:5432/",
      "postgresql://127.0.0.1:5432/",
      "postgresql://localhost:5432/"))) {
  $dbName = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "edunexus" }
  Set-ProcessEnv "POSTGRES_HOST" "127.0.0.1"
  Set-ProcessEnv "POSTGRES_PORT" "$postgresPort"
  Set-ProcessEnv "DATABASE_URL" "jdbc:postgresql://127.0.0.1:$postgresPort/$dbName"
}

$redisPort = Get-ComposeHostPort "redis" 6379
if ($redisPort -and (Should-UseComposeValue $env:REDIS_URL @(
      "redis://127.0.0.1:6379/",
      "redis://localhost:6379/"))) {
  Set-ProcessEnv "REDIS_HOST" "127.0.0.1"
  Set-ProcessEnv "REDIS_PORT" "$redisPort"
  Set-ProcessEnv "REDIS_URL" "redis://127.0.0.1:$redisPort/0"
}

$qdrantHttpPort = Get-ComposeHostPort "qdrant" 6333
if ($qdrantHttpPort -and (Should-UseComposeValue $env:QDRANT_URL @(
      "http://127.0.0.1:6333",
      "http://localhost:6333"))) {
  Set-ProcessEnv "QDRANT_HOST" "127.0.0.1"
  Set-ProcessEnv "QDRANT_PORT" "$qdrantHttpPort"
  Set-ProcessEnv "QDRANT_URL" "http://127.0.0.1:$qdrantHttpPort"
}

$minioPort = Get-ComposeHostPort "minio" 9000
if ($minioPort -and (Should-UseComposeValue $env:S3_ENDPOINT @(
      "http://127.0.0.1:9000",
      "http://localhost:9000"))) {
  Set-ProcessEnv "S3_ENDPOINT" "http://127.0.0.1:$minioPort"
}

Set-ProcessEnv "APP_PORT" "$ApiPort"
if (-not $env:APP_RUNTIME_STRATEGY) {
  Set-ProcessEnv "APP_RUNTIME_STRATEGY" "云边端协同"
}
Set-ProcessEnv "AI_SERVICE_PORT" "$AiPort"
Set-ProcessEnv "WEB_PORT" "$WebPort"
Set-ProcessEnv "API_BASE_URL" "http://127.0.0.1:$ApiPort"
if (-not $env:VITE_API_BASE_URL) {
  Set-ProcessEnv "VITE_API_BASE_URL" "http://127.0.0.1:$ApiPort/api/v1"
}
Set-ProcessEnv "AI_SERVICE_GRPC_HOST" "127.0.0.1"
Set-ProcessEnv "AI_SERVICE_GRPC_PORT" "50051"
if (-not $env:APP_GRPC_SERVER_PORT) {
  Set-ProcessEnv "APP_GRPC_SERVER_PORT" "9090"
}
Set-ProcessEnv "JAVA_GRPC_URL" "127.0.0.1:$($env:APP_GRPC_SERVER_PORT)"

$sharedEnvPrelude = Build-EnvPrelude @(
  "APP_HOST",
  "APP_PORT",
  "APP_RUNTIME_STRATEGY",
  "AI_SERVICE_PORT",
  "WEB_PORT",
  "API_BASE_URL",
  "VITE_API_BASE_URL",
  "AI_SERVICE_TOKEN",
  "AI_SERVICE_GRPC_HOST",
  "AI_SERVICE_GRPC_PORT",
  "APP_GRPC_SERVER_PORT",
  "JAVA_GRPC_URL",
  "AI_QUESTION_TIMEOUT_SECONDS",
  "LESSON_PLAN_TIMEOUT_SECONDS",
  "KB_INGEST_TIMEOUT_SECONDS",
  "KB_DELETE_TIMEOUT_SECONDS",
  "DATABASE_URL",
  "POSTGRES_HOST",
  "POSTGRES_PORT",
  "POSTGRES_DB",
  "POSTGRES_USER",
  "POSTGRES_PASSWORD",
  "REDIS_HOST",
  "REDIS_PORT",
  "REDIS_URL",
  "QDRANT_HOST",
  "QDRANT_PORT",
  "QDRANT_URL",
  "QDRANT_API_KEY",
  "S3_ENDPOINT",
  "S3_REGION",
  "S3_ACCESS_KEY",
  "S3_SECRET_KEY",
  "S3_BUCKET",
  "S3_FORCE_PATH_STYLE",
  "OLLAMA_BASE_URL",
  "OLLAMA_EMBED_MODEL",
  "OLLAMA_MODEL",
  "OLLAMA_RAG_MODEL",
  "OLLAMA_STRUCTURED_MODEL",
  "OLLAMA_LESSON_PLAN_MODEL",
  "OLLAMA_COMPLEX_MODEL",
  "JAVA_HOME",
  "Path"
)

Write-Host "[2/4] Start AI service (Powered by global uv)..."
$aiCmd = "$sharedEnvPrelude; Set-Location '$AiAppDir'; `$env:UV_LINK_MODE='copy'; & '$UvExe' run --python 3.12 uvicorn ai_service.app:app --host $HostBind --port $AiPort"
$aiEncoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($aiCmd))
Start-IfPortFree "AI service" ([int]$AiPort) $aiEncoded

Write-Host "[3/4] Start API service..."
$apiCmd = "$sharedEnvPrelude; Set-Location '$ApiAppDir'; & '$MavenCmd' spring-boot:run '-Dspring-boot.run.arguments=--server.address=$HostBind --server.port=$ApiPort'"
$apiEncoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($apiCmd))
Start-IfPortFree "API service" ([int]$ApiPort) $apiEncoded

Write-Host "[4/4] Start web service..."
$webCmd = "$sharedEnvPrelude; Set-Location '$WebAppDir'; & '$NpmCmd' install; & '$NpmCmd' run dev -- --host $HostBind --port $WebPort"
$webEncoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($webCmd))
Start-IfPortFree "Web service" ([int]$WebPort) $webEncoded

Write-Host "All services are starting in separate terminals."
