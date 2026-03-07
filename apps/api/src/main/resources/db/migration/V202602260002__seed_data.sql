insert into users(id, username, password_hash, role, status)
values
  ('00000000-0000-0000-0000-000000000001', 'admin', 'plain:12345678', 'ADMIN', 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000002', 'teacher01', 'plain:12345678', 'TEACHER', 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000003', 'student01', 'plain:12345678', 'STUDENT', 'ACTIVE')
on conflict (id) do nothing;

insert into questions(id,subject,question_type,difficulty,content,options,correct_answer,analysis,knowledge_points,score,source,created_by)
values
  ('10000000-0000-0000-0000-000000000001','物理','SINGLE_CHOICE','MEDIUM','质量为 10kg 的物体加速度为 2m/s^2，合力是多少？','{"A":"10N","B":"20N","C":"5N","D":"2N"}','B','根据牛顿第二定律 F=ma，F=10*2=20N。','["牛顿第二定律","力学"]',5,'MANUAL','00000000-0000-0000-0000-000000000002'),
  ('10000000-0000-0000-0000-000000000002','数学','SINGLE_CHOICE','EASY','函数 f(x)=x^2+2x+1，f(2) 等于多少？','{"A":"5","B":"7","C":"9","D":"11"}','C','代入 x=2，可得 4+4+1=9。','["函数求值","二次函数"]',5,'MANUAL','00000000-0000-0000-0000-000000000002')
on conflict (id) do nothing;
