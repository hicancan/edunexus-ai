with latest_question_suggestions as (
  select id
  from (
    select
      id,
      row_number() over (
        partition by teacher_id, student_id, question_id
        order by updated_at desc, created_at desc, id desc
      ) as row_num
    from teacher_suggestions
    where question_id is not null
  ) ranked
  where row_num = 1
)
delete from teacher_suggestions
where question_id is not null
  and id not in (select id from latest_question_suggestions);

with latest_knowledge_suggestions as (
  select id
  from (
    select
      id,
      row_number() over (
        partition by teacher_id, student_id, knowledge_point
        order by updated_at desc, created_at desc, id desc
      ) as row_num
    from teacher_suggestions
    where question_id is null
      and knowledge_point is not null
      and btrim(knowledge_point) <> ''
  ) ranked
  where row_num = 1
)
delete from teacher_suggestions
where question_id is null
  and knowledge_point is not null
  and btrim(knowledge_point) <> ''
  and id not in (select id from latest_knowledge_suggestions);

create unique index if not exists uk_teacher_suggestions_question
  on teacher_suggestions(teacher_id, student_id, question_id)
  where question_id is not null;

create unique index if not exists uk_teacher_suggestions_knowledge
  on teacher_suggestions(teacher_id, student_id, knowledge_point)
  where question_id is null
    and knowledge_point is not null
    and btrim(knowledge_point) <> '';
