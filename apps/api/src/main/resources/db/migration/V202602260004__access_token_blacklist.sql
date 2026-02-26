create table if not exists access_token_blacklist (
  id uuid primary key default gen_random_uuid(),
  jti varchar(64) not null unique,
  user_id uuid not null references users(id),
  expires_at timestamptz not null,
  revoked_at timestamptz not null default now()
);

create index if not exists idx_access_token_blacklist_expires_at
  on access_token_blacklist(expires_at);
