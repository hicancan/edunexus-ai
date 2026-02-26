$ErrorActionPreference = "Stop"

$apiBase = $env:API_BASE
if (-not $apiBase) { $apiBase = "http://127.0.0.1:8080" }
$aiBase = $env:AI_BASE
if (-not $aiBase) { $aiBase = "http://127.0.0.1:8000" }

Write-Host "[1/7] API health"
Invoke-RestMethod "$apiBase/actuator/health" | Out-Null

Write-Host "[2/7] AI health"
Invoke-RestMethod "$aiBase/health" | Out-Null

Write-Host "[3/7] Student login"
$studentLogin = Invoke-RestMethod "$apiBase/api/v1/auth/login" -Method Post -ContentType "application/json" -Body '{"username":"student01","password":"12345678"}'
$studentToken = $studentLogin.data.accessToken

Write-Host "[4/7] Student fetch sessions"
Invoke-RestMethod "$apiBase/api/v1/student/chat/sessions?page=1&size=5" -Headers @{ Authorization = "Bearer $studentToken" } | Out-Null

Write-Host "[5/7] Teacher login"
$teacherLogin = Invoke-RestMethod "$apiBase/api/v1/auth/login" -Method Post -ContentType "application/json" -Body '{"username":"teacher01","password":"12345678"}'
$teacherToken = $teacherLogin.data.accessToken

Write-Host "[6/7] Teacher analytics"
Invoke-RestMethod "$apiBase/api/v1/teacher/students/00000000-0000-0000-0000-000000000003/analytics" -Headers @{ Authorization = "Bearer $teacherToken" } | Out-Null

Write-Host "[7/7] AI internal auth guard"
try {
  Invoke-WebRequest "$aiBase/internal/v1/ping" -UseBasicParsing | Out-Null
  $code = 200
} catch {
  if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
    $code = [int]$_.Exception.Response.StatusCode
  } else {
    throw
  }
}
if ($code -ne 401) {
  throw "Expected 401 for missing X-Service-Token, got $code"
}

Write-Host "Acceptance smoke passed"
