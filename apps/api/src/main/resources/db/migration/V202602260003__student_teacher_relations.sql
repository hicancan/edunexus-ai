create table if not exists student_teacher_relations (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null references users(id),
  teacher_id uuid not null references users(id),
  class_name varchar(80),
  created_at timestamptz not null default now(),
  unique(student_id, teacher_id)
);

insert into student_teacher_relations(id, student_id, teacher_id, class_name)
values ('20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002', '高一1班')
on conflict do nothing;
