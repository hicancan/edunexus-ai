do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'chk_questions_question_type'
  ) then
    alter table questions
      add constraint chk_questions_question_type
      check (question_type in ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE', 'SHORT_ANSWER'));
  end if;

  if not exists (
    select 1 from pg_constraint where conname = 'chk_questions_difficulty'
  ) then
    alter table questions
      add constraint chk_questions_difficulty
      check (difficulty in ('EASY', 'MEDIUM', 'HARD'));
  end if;

  if not exists (
    select 1 from pg_constraint where conname = 'chk_questions_source'
  ) then
    alter table questions
      add constraint chk_questions_source
      check (source in ('MANUAL', 'AI_GENERATED'));
  end if;

  if not exists (
    select 1 from pg_constraint where conname = 'chk_documents_status'
  ) then
    alter table documents
      add constraint chk_documents_status
      check (status in ('UPLOADING', 'PARSING', 'EMBEDDING', 'READY', 'FAILED'));
  end if;

  if not exists (
    select 1 from pg_constraint where conname = 'chk_ai_question_sessions_difficulty'
  ) then
    alter table ai_question_sessions
      add constraint chk_ai_question_sessions_difficulty
      check (difficulty is null or difficulty in ('EASY', 'MEDIUM', 'HARD'));
  end if;
end
$$;
