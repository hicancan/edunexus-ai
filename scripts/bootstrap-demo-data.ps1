param(
  [string]$ApiBaseUrl = "http://127.0.0.1:8080",
  [string]$PostgresContainer = "edunexus-postgres"
)

$ErrorActionPreference = "Stop"

function Assert-True([bool]$Condition, [string]$Message) {
  if (-not $Condition) {
    throw $Message
  }
}

function Login([string]$Username, [string]$Password) {
  $body = @{ username = $Username; password = $Password } | ConvertTo-Json
  $response = Invoke-RestMethod `
    -Uri "$ApiBaseUrl/api/v1/auth/login" `
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
  [int]$TimeoutSec = 90,
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
    Uri        = "$ApiBaseUrl$Path"
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

function Invoke-PsqlQuery([string]$Sql) {
  $wrapped = "psql -U postgres -d edunexus -At -F '|' -c ""$($Sql.Replace('"', '\"'))"""
  $output = & docker exec $PostgresContainer sh -lc $wrapped
  return ($output | Out-String).Trim()
}

function Invoke-PsqlScript([string]$Sql) {
  $tmp = New-TemporaryFile
  try {
    Set-Content -LiteralPath $tmp.FullName -Value $Sql -Encoding UTF8
    Get-Content -LiteralPath $tmp.FullName | & docker exec -i $PostgresContainer psql -U postgres -d edunexus -v ON_ERROR_STOP=1 -f -
  } finally {
    Remove-Item -LiteralPath $tmp.FullName -Force -ErrorAction SilentlyContinue
  }
}

function Get-UserIdByUsername([string]$Username) {
  $escaped = $Username.Replace("'", "''")
  $result = Invoke-PsqlQuery "select id::text from users where username='${escaped}' limit 1;"
  return $result
}

function Get-CorrectAnswerMap([string[]]$QuestionIds) {
  $map = @{}
  if (-not $QuestionIds -or $QuestionIds.Count -eq 0) {
    return $map
  }

  $quotedIds = ($QuestionIds | ForEach-Object { "'$($_.Replace("'", "''"))'" }) -join ","
  $rows = Invoke-PsqlQuery "select id::text, correct_answer from questions where id in ($quotedIds);"
  if (-not $rows) {
    return $map
  }

  foreach ($line in ($rows -split "`r?`n")) {
    if (-not $line) {
      continue
    }
    $parts = $line -split "\|", 2
    if ($parts.Length -eq 2) {
      $map[$parts[0]] = $parts[1]
    }
  }
  return $map
}

function Ensure-User(
  [string]$AdminToken,
  [string]$Username,
  [string]$Role,
  [string]$Email
) {
  $users = Invoke-ApiJson -Method "Get" -Path "/api/v1/admin/users?role=$Role&page=1&size=100" -Token $AdminToken
  $existing = $users.data.content | Where-Object { $_.username -eq $Username } | Select-Object -First 1
  if ($existing) {
    return $existing
  }

  return (Invoke-ApiJson -Method "Post" -Path "/api/v1/admin/users" -Token $AdminToken -Body @{
      username = $Username
      password = "12345678"
      role     = $Role
      email    = $Email
    }).data
}

function Ensure-ClassroomBindings() {
  $sql = @'
insert into classrooms(id, name, teacher_id, status, created_at, updated_at)
select gen_random_uuid(), '高一2班', t.id, 'ACTIVE', now(), now()
from users t
where t.username = 'teacher01'
  and not exists (
    select 1 from classrooms c where c.teacher_id = t.id and c.name = '高一2班'
  );

with teacher as (
  select id as teacher_id from users where username = 'teacher01'
),
target_students as (
  select username, id as student_id,
         case
           when username in ('student01', 'student02', 'student03') then '高一1班'
           else '高一2班'
         end as classroom_name
  from users
  where username in ('student01', 'student02', 'student03', 'student04', 'student05')
),
target_classrooms as (
  select c.id as classroom_id, c.name, c.teacher_id
  from classrooms c
  join teacher t on t.teacher_id = c.teacher_id
  where c.name in ('高一1班', '高一2班')
)
update teacher_student_bindings b
set status = 'REVOKED',
    revoked_at = now(),
    updated_at = now()
from teacher t
join target_students s on true
where b.teacher_id = t.teacher_id
  and b.student_id = s.student_id
  and b.status = 'ACTIVE';

with teacher as (
  select id as teacher_id from users where username = 'teacher01'
),
target_students as (
  select username, id as student_id,
         case
           when username in ('student01', 'student02', 'student03') then '高一1班'
           else '高一2班'
         end as classroom_name
  from users
  where username in ('student01', 'student02', 'student03', 'student04', 'student05')
),
target_classrooms as (
  select c.id as classroom_id, c.name, c.teacher_id
  from classrooms c
  join teacher t on t.teacher_id = c.teacher_id
  where c.name in ('高一1班', '高一2班')
)
insert into teacher_student_bindings(
  id,
  teacher_id,
  student_id,
  classroom_id,
  status,
  effective_from,
  created_by,
  created_at,
  updated_at
)
select
  gen_random_uuid(),
  tc.teacher_id,
  s.student_id,
  tc.classroom_id,
  'ACTIVE',
  now(),
  tc.teacher_id,
  now(),
  now()
from target_students s
join target_classrooms tc on tc.name = s.classroom_name
where not exists (
  select 1
  from teacher_student_bindings b
  where b.teacher_id = tc.teacher_id
    and b.student_id = s.student_id
    and b.classroom_id = tc.classroom_id
    and b.status = 'ACTIVE'
    and (b.revoked_at is null or b.revoked_at > now())
);
'@
  Invoke-PsqlScript $sql
}

function New-ExerciseAnswers($Questions, [int]$CorrectCount) {
  $questionIds = @($Questions | ForEach-Object { $_.id })
  $correctAnswerMap = Get-CorrectAnswerMap $questionIds
  $answers = @()
  for ($index = 0; $index -lt $Questions.Count; $index++) {
    $question = $Questions[$index]
    $questionId = [string]$question.id
    $correctAnswer = [string]($correctAnswerMap[$questionId] ?? "__invalid__")
    $userAnswer = if ($index -lt $CorrectCount) { $correctAnswer } else { "__invalid__" }
    $answers += @{
      questionId = $questionId
      userAnswer = $userAnswer
    }
  }
  return $answers
}

function Submit-ExerciseScenario(
  [string]$StudentToken,
  [string]$Subject,
  [string]$Difficulty,
  [int]$QuestionCount,
  [int]$CorrectCount,
  [int]$TimeSpent
) {
  $questionsResponse = Invoke-ApiJson -Method "Get" -Path "/api/v1/student/exercise/questions?subject=$([uri]::EscapeDataString($Subject))&difficulty=$Difficulty&page=1&size=20" -Token $StudentToken
  $questions = @($questionsResponse.data.content | Select-Object -First $QuestionCount)
  Assert-True -Condition ($questions.Count -ge $QuestionCount) -Message "exercise question pool insufficient for $Subject/$Difficulty"
  $answers = New-ExerciseAnswers $questions $CorrectCount
  return Invoke-ApiJson -Method "Post" -Path "/api/v1/student/exercise/submit" -Token $StudentToken -Body @{
    answers   = $answers
    timeSpent = $TimeSpent
  }
}

function Submit-AiQuestionScenario(
  [string]$StudentToken,
  [string]$Subject,
  [string]$Difficulty,
  [string[]]$ConceptTags,
  [int]$QuestionCount,
  [int]$CorrectCount
) {
  $session = Invoke-ApiJson -Method "Post" -Path "/api/v1/student/ai-questions/generate" -Token $StudentToken -TimeoutSec 180 -Headers @{
    "Idempotency-Key" = "bootstrap-aiq-$([guid]::NewGuid())"
  } -Body @{
    count       = $QuestionCount
    subject     = $Subject
    difficulty  = $Difficulty
    conceptTags = $ConceptTags
  }

  $questions = @($session.data.questions)
  Assert-True -Condition ($questions.Count -eq $QuestionCount) -Message "ai question generation returned unexpected count"
  $answers = New-ExerciseAnswers $questions $CorrectCount
  $submit = Invoke-ApiJson -Method "Post" -Path "/api/v1/student/ai-questions/submit" -Token $StudentToken -TimeoutSec 120 -Headers @{
    "Idempotency-Key" = "bootstrap-aiq-submit-$([guid]::NewGuid())"
  } -Body @{
    sessionId = $session.data.sessionId
    answers   = $answers
  }

  return @{
    session = $session.data
    submit  = $submit.data
  }
}

function Seed-StudentScenario(
  [string]$Username,
  [hashtable]$Scenario
) {
  Write-Host "[bootstrap] seed student $Username"
  $studentToken = Login $Username "12345678"

  $chatSession = Invoke-ApiJson -Method "Post" -Path "/api/v1/student/chat/session" -Token $studentToken
  foreach ($message in $Scenario.chatMessages) {
    $null = Invoke-ApiJson -Method "Post" -Path "/api/v1/student/chat/session/$($chatSession.data.id)/message" -Token $studentToken -TimeoutSec 90 -Body @{
      message = $message
    }
  }

  $exercise = Submit-ExerciseScenario `
    -StudentToken $studentToken `
    -Subject $Scenario.exercise.subject `
    -Difficulty $Scenario.exercise.difficulty `
    -QuestionCount $Scenario.exercise.questionCount `
    -CorrectCount $Scenario.exercise.correctCount `
    -TimeSpent $Scenario.exercise.timeSpent

  $aiQuestion = Submit-AiQuestionScenario `
    -StudentToken $studentToken `
    -Subject $Scenario.aiQuestion.subject `
    -Difficulty $Scenario.aiQuestion.difficulty `
    -ConceptTags $Scenario.aiQuestion.conceptTags `
    -QuestionCount $Scenario.aiQuestion.questionCount `
    -CorrectCount $Scenario.aiQuestion.correctCount

  $profile = Invoke-ApiJson -Method "Get" -Path "/api/v1/student/profile/analytics" -Token $studentToken

  return @{
    username          = $Username
    chatSessionId     = $chatSession.data.id
    exerciseRecordId  = $exercise.data.recordId
    aiSessionId       = $aiQuestion.session.sessionId
    aiRecordId        = $aiQuestion.submit.recordId
    wrongBookCount    = $profile.data.wrongBookCount
    supportStage      = $profile.data.supportStage.label
    interactionProfile = $profile.data.interactionProfile
  }
}

$dockerContainer = (& docker ps --filter "name=$PostgresContainer" --format "{{.Names}}" | Select-Object -First 1)
Assert-True -Condition ([string]::Equals($dockerContainer, $PostgresContainer, [System.StringComparison]::OrdinalIgnoreCase)) -Message "postgres container '$PostgresContainer' is not running"

$adminToken = Login "admin" "12345678"
$teacherToken = Login "teacher01" "12345678"

$demoUsers = @(
  @{ username = "student02"; email = "student02@edunexus.local" },
  @{ username = "student03"; email = "student03@edunexus.local" },
  @{ username = "student04"; email = "student04@edunexus.local" },
  @{ username = "student05"; email = "student05@edunexus.local" }
)

foreach ($user in $demoUsers) {
  $null = Ensure-User -AdminToken $adminToken -Username $user.username -Role "STUDENT" -Email $user.email
}

Ensure-ClassroomBindings

$scenarios = @{
  student02 = @{
    chatMessages = @(
      "如果质量变大，加速度不变，合力会怎么变化？"
    )
    exercise = @{
      subject = "物理"
      difficulty = "MEDIUM"
      questionCount = 1
      correctCount = 0
      timeSpent = 18
    }
    aiQuestion = @{
      subject = "物理"
      difficulty = "MEDIUM"
      conceptTags = @("牛顿第二定律")
      questionCount = 2
      correctCount = 1
    }
  }
  student03 = @{
    chatMessages = @(
      "我能算出结果，但总担心单位写错。"
    )
    exercise = @{
      subject = "物理"
      difficulty = "MEDIUM"
      questionCount = 1
      correctCount = 1
      timeSpent = 12
    }
    aiQuestion = @{
      subject = "物理"
      difficulty = "MEDIUM"
      conceptTags = @("牛顿第二定律")
      questionCount = 2
      correctCount = 2
    }
  }
  student04 = @{
    chatMessages = @(
      "函数 f(x)=x^2+2x+1 为什么可以看成完全平方？"
    )
    exercise = @{
      subject = "数学"
      difficulty = "EASY"
      questionCount = 1
      correctCount = 1
      timeSpent = 10
    }
    aiQuestion = @{
      subject = "数学"
      difficulty = "EASY"
      conceptTags = @("函数求值")
      questionCount = 2
      correctCount = 1
    }
  }
  student05 = @{
    chatMessages = @(
      "函数求值和代数化简有什么关系？",
      "如果先展开再代入，会不会更稳？"
    )
    exercise = @{
      subject = "数学"
      difficulty = "EASY"
      questionCount = 1
      correctCount = 0
      timeSpent = 20
    }
    aiQuestion = @{
      subject = "数学"
      difficulty = "EASY"
      conceptTags = @("函数求值")
      questionCount = 2
      correctCount = 1
    }
  }
}

$studentSummaries = @()
foreach ($username in $scenarios.Keys) {
  $studentSummaries += Seed-StudentScenario -Username $username -Scenario $scenarios[$username]
}

Write-Host "[bootstrap] refresh teacher analytics and attribution"
$students = Invoke-ApiJson -Method "Get" -Path "/api/v1/teacher/students" -Token $teacherToken
foreach ($student in $students.data) {
  $null = Invoke-ApiJson -Method "Get" -Path "/api/v1/teacher/students/$($student.id)/analytics" -Token $teacherToken
  $null = Invoke-ApiJson -Method "Get" -Path "/api/v1/teacher/students/$($student.id)/attribution" -Token $teacherToken
}

$recommendations = Invoke-ApiJson -Method "Get" -Path "/api/v1/teacher/interventions/recommendations" -Token $teacherToken -TimeoutSec 120
$topRecommendations = @($recommendations.data | Select-Object -First 2)
foreach ($item in $topRecommendations) {
  Write-Host "[bootstrap] dispatch suggestion for $($item.knowledgePoint)"
  $null = Invoke-ApiJson -Method "Post" -Path "/api/v1/teacher/suggestions/bulk" -Token $teacherToken -Body @{
    knowledgePoint = $item.knowledgePoint
    suggestion     = "请先复盘核心概念，再按 定义判断 -> 例题套用 -> 变式迁移 的顺序完成递进训练。"
  }
}

Write-Host "[bootstrap] load admin dashboard"
$dashboard = Invoke-ApiJson -Method "Get" -Path "/api/v1/admin/dashboard/metrics" -Token $adminToken

[pscustomobject]@{
  seededStudents          = @($studentSummaries | ForEach-Object { $_.username })
  classroomCount          = @((Invoke-ApiJson -Method "Get" -Path "/api/v1/teacher/classrooms" -Token $teacherToken).data).Count
  teacherStudentCount     = @($students.data).Count
  topRecommendationPoints = @($topRecommendations | ForEach-Object { $_.knowledgePoint })
  dashboardRiskStudents   = $dashboard.data.governanceSummary.activeRiskStudents
  dashboardDispatchCount  = $dashboard.data.governanceSummary.suggestionDispatchCount
  strategyLabel           = $($dashboard.data.strategyComparison | Where-Object { $_.dataState -eq "MEASURED" } | Select-Object -First 1 -ExpandProperty strategy)
  studentSnapshots        = $studentSummaries
} | ConvertTo-Json -Depth 6
