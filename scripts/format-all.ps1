$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot

function Invoke-Maven {
  param(
    [Parameter(Mandatory = $true)]
    [string[]]$Arguments
  )

  $mvn = Get-Command mvn -ErrorAction SilentlyContinue
  if ($mvn) {
    & $mvn.Source @Arguments
    return
  }

  $mavenHome = Join-Path $env:USERPROFILE ".version-fox\sdks\maven"
  $classworlds = Join-Path $mavenHome "boot\plexus-classworlds-2.9.0.jar"
  $libGlob = Join-Path $mavenHome "lib\*"

  if (!(Test-Path $classworlds)) {
    throw "Maven executable not found in PATH, and version-fox Maven runtime is incomplete."
  }

  $classpath = "$classworlds;$libGlob"
  & java "-Dmaven.multiModuleProjectDirectory=$PWD" -cp $classpath org.apache.maven.cli.MavenCli @Arguments
}

Write-Host "Formatting web..."
Push-Location (Join-Path $repoRoot "apps/web")
try {
  npm run lint:fix
  npm run format
}
finally {
  Pop-Location
}

Write-Host "Formatting api..."
Push-Location (Join-Path $repoRoot "apps/api")
try {
  Invoke-Maven -Arguments @("spotless:apply")
}
finally {
  Pop-Location
}

Write-Host "Formatting ai-service..."
Push-Location $repoRoot
try {
  uv run --project apps/ai-service --group dev ruff check --fix apps/ai-service
  uv run --project apps/ai-service --group dev ruff format apps/ai-service
}
finally {
  Pop-Location
}
