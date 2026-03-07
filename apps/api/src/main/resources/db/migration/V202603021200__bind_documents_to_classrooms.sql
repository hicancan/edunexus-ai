alter table documents
  add column if not exists classroom_id uuid references classrooms(id);

update documents
set
  status = 'FAILED',
  error_message = '文档缺少班级归属，已下线，请按班级重新上传',
  deleted_at = coalesce(deleted_at, now()),
  updated_at = now()
where classroom_id is null
  and deleted_at is null;

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'chk_documents_classroom_required'
  ) then
    alter table documents
      add constraint chk_documents_classroom_required
      check (classroom_id is not null or deleted_at is not null);
  end if;
end
$$;

create index if not exists idx_documents_teacher_class_status_created
  on documents(teacher_id, classroom_id, status, created_at desc)
  where deleted_at is null;
