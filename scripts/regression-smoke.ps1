$ErrorActionPreference = "Stop"

function Assert-True([bool]$Condition, [string]$Message) {
  if (-not $Condition) {
    throw $Message
  }
}

function Login([string]$Username, [string]$Password) {
  $body = @{ username = $Username; password = $Password } | ConvertTo-Json
  $response = Invoke-RestMethod `
    -Uri "http://127.0.0.1:8080/api/v1/auth/login" `
    -Method Post `
    -ContentType "application/json" `
    -Body $body `
    -TimeoutSec 30
  return $response.data.accessToken
}

function Invoke-ApiJson(
  [string]$Method,
  [string]$Path,
  [string]$Token = "",
  $Body = $null,
  [int]$TimeoutSec = 60,
  [hashtable]$Headers = @{}
) {
  $requestHeaders = @{}
  if ($Token) {
    $requestHeaders["Authorization"] = "Bearer $Token"
  }
  foreach ($key in $Headers.Keys) {
    $requestHeaders[$key] = $Headers[$key]
  }

  $params = @{
    Uri        = "http://127.0.0.1:8080$Path"
    Method     = $Method
    Headers    = $requestHeaders
    TimeoutSec = $TimeoutSec
  }
  if ($null -ne $Body) {
    $params["ContentType"] = "application/json"
    $params["Body"] = $Body | ConvertTo-Json -Depth 20
  }
  return Invoke-RestMethod @params
}

$studentToken = Login "student01" "12345678"
$teacherToken = Login "teacher01" "12345678"
$adminToken = Login "admin" "12345678"

$pseudoJsonPattern = "\{'text'"

$chatSession = Invoke-ApiJson -Method "Post" -Path "/api/v1/student/chat/session" -Token $studentToken
$sessionId = $chatSession.data.id
Assert-True -Condition ($sessionId -is [string] -and $sessionId.Length -gt 0) -Message "chat session create failed"
$chatReply = Invoke-ApiJson -Method "Post" -Path "/api/v1/student/chat/session/$sessionId/message" -Token $studentToken -TimeoutSec 90 -Body @{
  message = "请解释牛顿第二定律"
}
Assert-True -Condition ($chatReply.data.assistantMessage.id.Length -gt 0) -Message "chat assistant message missing id"
Assert-True -Condition ($chatReply.data.assistantMessage.content.Length -gt 0) -Message "chat assistant message missing content"
$null = Invoke-ApiJson -Method "Delete" -Path "/api/v1/student/chat/session/$sessionId" -Token $studentToken

$exerciseQuestions = Invoke-ApiJson -Method "Get" -Path "/api/v1/student/exercise/questions?subject=%E7%89%A9%E7%90%86&difficulty=MEDIUM&page=1&size=10" -Token $studentToken
Assert-True -Condition ($exerciseQuestions.data.content.Count -ge 1) -Message "exercise questions empty"
$exerciseJson = $exerciseQuestions.data.content | ConvertTo-Json -Depth 10
Assert-True -Condition (-not ($exerciseJson -match "correctAnswer")) -Message "exercise question payload leaked correctAnswer"
Assert-True -Condition (-not ($exerciseJson -match $pseudoJsonPattern)) -Message "exercise question payload still contains pseudo-json option text"
$firstExerciseQuestion = $exerciseQuestions.data.content[0]
$exerciseSubmit = Invoke-ApiJson -Method "Post" -Path "/api/v1/student/exercise/submit" -Token $studentToken -Body @{
  answers = @(@{
      questionId = $firstExerciseQuestion.id
      userAnswer = "__invalid__"
    })
  timeSpent = 15
}
$exerciseRecordId = $exerciseSubmit.data.recordId
Assert-True -Condition ($exerciseSubmit.data.items.Count -eq 1) -Message "exercise submit items mismatch"
Assert-True -Condition (-not [bool]$exerciseSubmit.data.items[0].isCorrect) -Message "exercise invalid answer unexpectedly marked correct"
$wrongQuestions = Invoke-ApiJson -Method "Get" -Path "/api/v1/student/exercise/wrong-questions?status=ACTIVE" -Token $studentToken
Assert-True -Condition ($wrongQuestions.data.content.Count -ge 1) -Message "wrong-book entry not created"
$wrongQuestionId = $wrongQuestions.data.content[0].questionId
$exerciseAnalysis = Invoke-ApiJson -Method "Get" -Path "/api/v1/student/exercise/$exerciseRecordId/analysis" -Token $studentToken
Assert-True -Condition ($exerciseAnalysis.data.items.Count -ge 1) -Message "exercise analysis empty"
$null = Invoke-ApiJson -Method "Delete" -Path "/api/v1/student/exercise/wrong-questions/$wrongQuestionId" -Token $studentToken
$exerciseRecords = Invoke-ApiJson -Method "Get" -Path "/api/v1/student/exercise/records?page=1&size=20" -Token $studentToken
Assert-True -Condition ($exerciseRecords.data.totalElements -ge 1) -Message "exercise records empty"

$aiGenerate = Invoke-ApiJson -Method "Post" -Path "/api/v1/student/ai-questions/generate" -Token $studentToken -TimeoutSec 180 -Headers @{
  "Idempotency-Key" = "api-regression-generate-$([guid]::NewGuid())"
} -Body @{
  count       = 2
  subject     = "物理"
  difficulty  = "MEDIUM"
  conceptTags = @("牛顿第二定律")
}
Assert-True -Condition ($aiGenerate.data.questions.Count -eq 2) -Message "ai generate returned unexpected question count"
$aiJson = $aiGenerate.data.questions | ConvertTo-Json -Depth 10
Assert-True -Condition (-not ($aiJson -match "correctAnswer")) -Message "ai question payload leaked correctAnswer"
Assert-True -Condition (-not ($aiJson -match $pseudoJsonPattern)) -Message "ai question payload still contains pseudo-json option text"
$aiAnswers = @()
foreach ($question in $aiGenerate.data.questions) {
  if ($question.questionType -eq "SHORT_ANSWER") {
    $userAnswer = "待补充"
  } else {
    $optionKeys = @($question.options.PSObject.Properties.Name)
    if ($optionKeys.Count -gt 0) {
      $userAnswer = $optionKeys[0]
    } else {
      $userAnswer = "__invalid__"
    }
  }
  $aiAnswers += @{
    questionId = $question.id
    userAnswer = $userAnswer
  }
}
$aiSubmit = Invoke-ApiJson -Method "Post" -Path "/api/v1/student/ai-questions/submit" -Token $studentToken -TimeoutSec 120 -Headers @{
  "Idempotency-Key" = "api-regression-submit-$([guid]::NewGuid())"
} -Body @{
  sessionId = $aiGenerate.data.sessionId
  answers   = $aiAnswers
}
$aiRecordId = $aiSubmit.data.recordId
$aiAnalysis = Invoke-ApiJson -Method "Get" -Path "/api/v1/student/ai-questions/$aiRecordId/analysis" -Token $studentToken
Assert-True -Condition ($aiAnalysis.data.items.Count -ge 1) -Message "ai analysis empty"
$weakPoints = Invoke-ApiJson -Method "Get" -Path "/api/v1/student/profile/weak-points" -Token $studentToken
Assert-True -Condition ($null -ne $weakPoints.data) -Message "weak points missing"

$classrooms = Invoke-ApiJson -Method "Get" -Path "/api/v1/teacher/classrooms" -Token $teacherToken
$students = Invoke-ApiJson -Method "Get" -Path "/api/v1/teacher/students" -Token $teacherToken
Assert-True -Condition ($classrooms.data.Count -ge 1) -Message "teacher classrooms empty"
Assert-True -Condition ($students.data.Count -ge 1) -Message "teacher students empty"
$classId = $classrooms.data[0].id
$studentId = $students.data[0].id

$tempFile = Join-Path $env:TEMP "edunexus-regression-$([guid]::NewGuid()).md"
Set-Content -Path $tempFile -Value "# regression upload" -Encoding UTF8
try {
  $uploadResponse = Invoke-RestMethod `
    -Uri "http://127.0.0.1:8080/api/v1/teacher/knowledge/documents" `
    -Method Post `
    -Headers @{
      Authorization   = "Bearer $teacherToken"
      "Idempotency-Key" = "upload-$([guid]::NewGuid())"
    } `
    -Form @{
      classId = $classId
      file    = Get-Item $tempFile
    } `
    -TimeoutSec 180
} finally {
  Remove-Item $tempFile -Force -ErrorAction SilentlyContinue
}
Assert-True -Condition ($null -ne $uploadResponse.data.id) -Message "knowledge upload failed"
$documentId = $uploadResponse.data.id
$documents = Invoke-ApiJson -Method "Get" -Path "/api/v1/teacher/knowledge/documents" -Token $teacherToken
Assert-True -Condition (($documents.data | Where-Object { $_.id -eq $documentId }).Count -ge 1) -Message "uploaded document missing from list"
$null = Invoke-ApiJson -Method "Delete" -Path "/api/v1/teacher/knowledge/documents/$documentId" -Token $teacherToken

$plan = Invoke-ApiJson -Method "Post" -Path "/api/v1/teacher/plans/generate" -Token $teacherToken -TimeoutSec 180 -Headers @{
  "Idempotency-Key" = "plan-$([guid]::NewGuid())"
} -Body @{
  topic        = "牛顿第二定律"
  gradeLevel   = "高一"
  durationMins = 45
}
$planId = $plan.data.id
Assert-True -Condition ($plan.data.contentMd.Length -gt 0) -Message "plan content empty"
$plans = Invoke-ApiJson -Method "Get" -Path "/api/v1/teacher/plans?page=1&size=20" -Token $teacherToken
Assert-True -Condition ($plans.data.totalElements -ge 1) -Message "teacher plans empty"
$updatedPlan = Invoke-ApiJson -Method "Put" -Path "/api/v1/teacher/plans/$planId" -Token $teacherToken -Body @{
  contentMd = "# 教学目标`n- 回归验证版本"
}
Assert-True -Condition ($updatedPlan.data.id -eq $planId) -Message "plan update failed"
$share = Invoke-ApiJson -Method "Post" -Path "/api/v1/teacher/plans/$planId/share" -Token $teacherToken
Assert-True -Condition ($share.data.shareToken.Length -gt 0) -Message "plan share token missing"
$sharedPlan = Invoke-ApiJson -Method "Get" -Path "/api/v1/teacher/plans/shared/$($share.data.shareToken)"
Assert-True -Condition ($sharedPlan.data.id -eq $planId) -Message "shared plan lookup failed"
$exportResponse = Invoke-WebRequest `
  -Uri "http://127.0.0.1:8080/api/v1/teacher/plans/$planId/export?format=md" `
  -Headers @{ Authorization = "Bearer $teacherToken" } `
  -TimeoutSec 60
Assert-True -Condition ($exportResponse.StatusCode -eq 200) -Message "plan export failed"
$recommendations = Invoke-ApiJson -Method "Get" -Path "/api/v1/teacher/interventions/recommendations" -Token $teacherToken
Assert-True -Condition ($recommendations.data.Count -ge 1) -Message "teacher recommendations empty"
$bulkKnowledgePoint = $recommendations.data[0].knowledgePoint
$bulkSuggestion = Invoke-ApiJson -Method "Post" -Path "/api/v1/teacher/suggestions/bulk" -Token $teacherToken -Body @{
  knowledgePoint = $bulkKnowledgePoint
  suggestion     = "建议先复盘概念定义，再完成三组递进训练"
}
Assert-True -Condition ($bulkSuggestion.data.createdCount -ge 1) -Message "bulk suggestion did not create any items"
$analytics = Invoke-ApiJson -Method "Get" -Path "/api/v1/teacher/students/$studentId/analytics" -Token $teacherToken
$attribution = Invoke-ApiJson -Method "Get" -Path "/api/v1/teacher/students/$studentId/attribution" -Token $teacherToken
Assert-True -Condition ($analytics.data.studentId.Length -gt 0) -Message "student analytics missing studentId"
Assert-True -Condition ($null -ne $attribution.data.impactCount) -Message "student attribution missing impactCount"
$teacherSuggestion = Invoke-ApiJson -Method "Post" -Path "/api/v1/teacher/suggestions" -Token $teacherToken -Body @{
  studentId      = $studentId
  knowledgePoint = "牛顿第二定律"
  suggestion     = "建议复习受力分析"
}
Assert-True -Condition ($teacherSuggestion.data.id.Length -gt 0) -Message "teacher suggestion create failed"
$null = Invoke-ApiJson -Method "Delete" -Path "/api/v1/teacher/plans/$planId" -Token $teacherToken

$users = Invoke-ApiJson -Method "Get" -Path "/api/v1/admin/users?page=1&size=20" -Token $adminToken
Assert-True -Condition ($users.data.totalElements -ge 1) -Message "admin users empty"
$newUsername = "regression_$(Get-Date -Format 'MMddHHmmss')"
$newUser = Invoke-ApiJson -Method "Post" -Path "/api/v1/admin/users" -Token $adminToken -Body @{
  username = $newUsername
  password = "12345678"
  role     = "TEACHER"
  email    = "$newUsername@example.com"
}
$userId = $newUser.data.id
$patchedUser = Invoke-ApiJson -Method "Patch" -Path "/api/v1/admin/users/$userId" -Token $adminToken -Body @{
  status = "DISABLED"
}
Assert-True -Condition ($patchedUser.data.status -eq "DISABLED") -Message "admin patch user failed"
$resources = Invoke-ApiJson -Method "Get" -Path "/api/v1/admin/resources?page=1&size=20" -Token $adminToken
Assert-True -Condition ($resources.data.totalElements -ge 1) -Message "admin resources empty"
$resourceId = $resources.data.content[0].resourceId
$resourceDownload = Invoke-WebRequest `
  -Uri "http://127.0.0.1:8080/api/v1/admin/resources/$resourceId/download" `
  -Headers @{ Authorization = "Bearer $adminToken" } `
  -TimeoutSec 60
Assert-True -Condition ($resourceDownload.StatusCode -eq 200 -and $resourceDownload.Content.Length -gt 0) -Message "admin resource download failed"
$dashboard = Invoke-ApiJson -Method "Get" -Path "/api/v1/admin/dashboard/metrics" -Token $adminToken
$audits = Invoke-ApiJson -Method "Get" -Path "/api/v1/admin/audits?page=1&size=20" -Token $adminToken
Assert-True -Condition ($dashboard.data.totalUsers -ge 1) -Message "admin dashboard metrics empty"
Assert-True -Condition ($audits.data.content.Count -ge 1) -Message "admin audits empty"

[pscustomobject]@{
  chat                            = "ok"
  exercise_no_answer_leak         = "ok"
  exercise_wrong_book             = "ok"
  ai_question_no_answer_leak      = "ok"
  teacher_knowledge_upload        = "ok"
  teacher_plan_generate_export    = "ok"
  teacher_recommendations         = "ok"
  admin_users_resources_dashboard = "ok"
} | ConvertTo-Json -Depth 5
