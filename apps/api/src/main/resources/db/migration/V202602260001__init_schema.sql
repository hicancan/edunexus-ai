create extension if not exists pgcrypto;

create table if not exists users (
  id uuid primary key default gen_random_uuid(),
  username varchar(50) not null unique,
  password_hash varchar(255) not null,
  email varchar(100),
  phone varchar(20),
  role varchar(20) not null check (role in ('STUDENT','TEACHER','ADMIN')),
  status varchar(20) not null default 'ACTIVE' check (status in ('ACTIVE','DISABLED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table if not exists refresh_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id),
  token_hash varchar(255) not null,
  expires_at timestamptz not null,
  revoked_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists chat_sessions (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null references users(id),
  title varchar(120) not null default '新建对话',
  is_deleted boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table if not exists chat_messages (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references chat_sessions(id),
  role varchar(20) not null check (role in ('USER','ASSISTANT')),
  content text not null,
  citations jsonb,
  token_usage int not null default 0,
  created_at timestamptz not null default now()
);

create table if not exists questions (
  id uuid primary key default gen_random_uuid(),
  subject varchar(50) not null,
  question_type varchar(30) not null,
  difficulty varchar(20) not null,
  content text not null,
  options jsonb,
  correct_answer text not null,
  analysis text,
  knowledge_points jsonb,
  score int not null default 5,
  source varchar(20) not null default 'MANUAL',
  ai_session_id uuid,
  created_by uuid references users(id),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists exercise_records (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null references users(id),
  subject varchar(50),
  total_questions int not null,
  correct_count int not null,
  total_score int not null,
  time_spent int not null default 0,
  created_at timestamptz not null default now()
);

create table if not exists exercise_record_items (
  id uuid primary key default gen_random_uuid(),
  record_id uuid not null references exercise_records(id),
  question_id uuid not null references questions(id),
  user_answer text not null,
  correct_answer text not null,
  is_correct boolean not null,
  score int not null,
  analysis text,
  teacher_suggestion text,
  created_at timestamptz not null default now()
);

create table if not exists wrong_book (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null references users(id),
  question_id uuid not null references questions(id),
  wrong_count int not null default 1,
  last_wrong_time timestamptz not null default now(),
  status varchar(20) not null default 'ACTIVE' check (status in ('ACTIVE','MASTERED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(student_id, question_id)
);

create table if not exists ai_question_sessions (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null references users(id),
  subject varchar(50) not null,
  difficulty varchar(20),
  question_count int not null,
  completed boolean not null default false,
  correct_rate numeric(5,2),
  score int,
  context_snapshot jsonb,
  generated_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists ai_question_records (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references ai_question_sessions(id),
  student_id uuid not null references users(id),
  total_questions int not null,
  correct_count int not null,
  total_score int not null,
  submitted_at timestamptz not null default now()
);

create table if not exists ai_question_record_items (
  id uuid primary key default gen_random_uuid(),
  record_id uuid not null references ai_question_records(id),
  question_id uuid not null references questions(id),
  user_answer text not null,
  correct_answer text not null,
  is_correct boolean not null,
  score int not null,
  analysis text,
  created_at timestamptz not null default now()
);

create table if not exists documents (
  id uuid primary key default gen_random_uuid(),
  teacher_id uuid not null references users(id),
  filename varchar(255) not null,
  file_type varchar(100) not null,
  file_size bigint not null,
  storage_path varchar(512) not null,
  status varchar(20) not null default 'UPLOADING',
  error_message text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table if not exists lesson_plans (
  id uuid primary key default gen_random_uuid(),
  teacher_id uuid not null references users(id),
  topic varchar(255) not null,
  grade_level varchar(50) not null,
  duration_mins int not null,
  content_md text not null,
  is_shared boolean not null default false,
  share_token varchar(64) unique,
  shared_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table if not exists teacher_suggestions (
  id uuid primary key default gen_random_uuid(),
  teacher_id uuid not null references users(id),
  student_id uuid not null references users(id),
  question_id uuid references questions(id),
  knowledge_point varchar(100),
  suggestion text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists audit_logs (
  id uuid primary key default gen_random_uuid(),
  actor_id uuid references users(id),
  actor_role varchar(20) not null,
  action varchar(120) not null,
  resource_type varchar(80) not null,
  resource_id varchar(80) not null,
  detail jsonb,
  ip varchar(64),
  created_at timestamptz not null default now()
);

create index if not exists idx_chat_sessions_student_updated on chat_sessions(student_id, updated_at desc);
create index if not exists idx_chat_messages_session_created on chat_messages(session_id, created_at);
create index if not exists idx_exercise_records_student_created on exercise_records(student_id, created_at desc);
create index if not exists idx_wrong_book_student_status on wrong_book(student_id, status, last_wrong_time desc);
create index if not exists idx_ai_question_sessions_student_created on ai_question_sessions(student_id, generated_at desc);
create index if not exists idx_documents_teacher_status on documents(teacher_id, status, created_at desc);
create index if not exists idx_lesson_plans_teacher_updated on lesson_plans(teacher_id, updated_at desc);
create index if not exists idx_audit_logs_actor_created on audit_logs(actor_id, created_at desc);
