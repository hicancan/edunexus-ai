create table if not exists classrooms (
  id uuid primary key default gen_random_uuid(),
  name varchar(120) not null,
  teacher_id uuid not null references users(id),
  status varchar(20) not null default 'ACTIVE' check (status in ('ACTIVE', 'ARCHIVED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index if not exists ux_classrooms_teacher_name
  on classrooms(teacher_id, name);

create table if not exists teacher_student_bindings (
  id uuid primary key default gen_random_uuid(),
  teacher_id uuid not null references users(id),
  student_id uuid not null references users(id),
  classroom_id uuid references classrooms(id),
  status varchar(20) not null default 'PENDING' check (status in ('PENDING', 'ACTIVE', 'REVOKED')),
  effective_from timestamptz,
  revoked_at timestamptz,
  created_by uuid references users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (teacher_id <> student_id)
);

create unique index if not exists ux_teacher_student_bindings_active
  on teacher_student_bindings(teacher_id, student_id, coalesce(classroom_id, '00000000-0000-0000-0000-000000000000'::uuid))
  where status = 'ACTIVE';

create index if not exists idx_teacher_student_bindings_teacher_student_status
  on teacher_student_bindings(teacher_id, student_id, status);

create index if not exists idx_teacher_student_bindings_student_status
  on teacher_student_bindings(student_id, status);

create table if not exists job_runs (
  id uuid primary key default gen_random_uuid(),
  job_type varchar(50) not null,
  business_id uuid,
  status varchar(20) not null check (status in ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'DEAD_LETTER')),
  attempt int not null default 1,
  payload jsonb,
  result jsonb,
  error_message text,
  started_at timestamptz,
  finished_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_job_runs_type_status_created
  on job_runs(job_type, status, created_at desc);

create table if not exists idempotency_keys (
  id uuid primary key default gen_random_uuid(),
  scope varchar(80) not null,
  idem_key varchar(128) not null,
  request_hash varchar(128) not null,
  response_snapshot jsonb,
  expires_at timestamptz not null,
  created_at timestamptz not null default now()
);

create unique index if not exists ux_idempotency_keys_scope_key
  on idempotency_keys(scope, idem_key);

do $$
begin
  if exists (
    select 1
    from information_schema.tables
    where table_schema = 'public' and table_name = 'student_teacher_relations'
  ) then
    insert into classrooms(id, name, teacher_id, status, created_at, updated_at)
    select
      gen_random_uuid(),
      coalesce(nullif(trim(class_name), ''), '未分班'),
      teacher_id,
      'ACTIVE',
      created_at,
      created_at
    from student_teacher_relations
    on conflict (teacher_id, name) do nothing;

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
      r.teacher_id,
      r.student_id,
      c.id,
      'ACTIVE',
      r.created_at,
      r.teacher_id,
      r.created_at,
      r.created_at
    from student_teacher_relations r
    left join classrooms c
      on c.teacher_id = r.teacher_id
      and c.name = coalesce(nullif(trim(r.class_name), ''), '未分班')
    where not exists (
      select 1
      from teacher_student_bindings b
      where b.teacher_id = r.teacher_id
        and b.student_id = r.student_id
        and b.status = 'ACTIVE'
    );
  end if;
end
$$;
