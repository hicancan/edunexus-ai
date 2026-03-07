-- Deprecate the legacy student_teacher_relations table.
-- Data was already migrated into classrooms + teacher_student_bindings by V202602270100.
-- This migration renames the table to _deprecated_student_teacher_relations so it is
-- clearly marked as dead weight and excluded from application queries.
-- A future migration will DROP it once we have confirmed no edge cases remain.

do $$
begin
  if exists (
    select 1
    from information_schema.tables
    where table_schema = 'public' and table_name = 'student_teacher_relations'
  ) then
    alter table student_teacher_relations
      rename to _deprecated_student_teacher_relations;

    comment on table _deprecated_student_teacher_relations is
      'DEPRECATED — migrated to teacher_student_bindings + classrooms in V202602270100. '
      'Do NOT query this table from application code. Pending removal.';
  end if;
end
$$;
