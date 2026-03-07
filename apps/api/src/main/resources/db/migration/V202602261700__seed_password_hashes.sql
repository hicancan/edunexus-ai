update users
set password_hash = '$2a$10$Cbqibi97IAsmqado2bp2SO2OsosVIcytz2/4ITYl9K3TTsi7A/A8O',
    updated_at = now()
where username = 'admin' and password_hash like 'plain:%';

update users
set password_hash = '$2a$10$GJFUKOXMTDhh5Wsr5A.rWuUbdBjDQCtUybZlS7ZV/Y23BBABuixuO',
    updated_at = now()
where username = 'teacher01' and password_hash like 'plain:%';

update users
set password_hash = '$2a$10$ziMc2swU7zPo4rGkeDeSWubI5CMERgZQHnRWRfTL/yra1cfOV579O',
    updated_at = now()
where username = 'student01' and password_hash like 'plain:%';
