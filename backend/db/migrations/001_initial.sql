create extension if not exists pgcrypto;

create table users (
  id uuid primary key default gen_random_uuid(),
  phone varchar(20) not null unique,
  nickname varchar(30) not null,
  avatar_url text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table sms_codes (
  id uuid primary key default gen_random_uuid(),
  phone varchar(20) not null,
  code_hash text not null,
  expires_at timestamptz not null,
  consumed_at timestamptz,
  created_at timestamptz not null default now()
);
create index sms_codes_phone_created_idx on sms_codes(phone, created_at desc);

create table auth_sessions (
  id uuid primary key,
  user_id uuid not null references users(id) on delete cascade,
  refresh_token_hash char(64) not null,
  expires_at timestamptz not null,
  revoked_at timestamptz,
  last_used_at timestamptz,
  created_at timestamptz not null default now()
);
create index auth_sessions_user_idx on auth_sessions(user_id);

create table couple_spaces (
  id uuid primary key default gen_random_uuid(),
  name varchar(40) not null default '我们的小宇宙',
  together_date date,
  status varchar(16) not null default 'active' check (status in ('active', 'dissolved')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  dissolved_at timestamptz
);

create table couple_members (
  space_id uuid not null references couple_spaces(id) on delete restrict,
  user_id uuid not null references users(id) on delete restrict,
  status varchar(16) not null default 'active' check (status in ('active', 'inactive')),
  joined_at timestamptz not null default now(),
  left_at timestamptz,
  primary key (space_id, user_id)
);
create unique index couple_members_one_active_space_idx
  on couple_members(user_id) where status = 'active';
create index couple_members_space_idx on couple_members(space_id) where status = 'active';

create function enforce_two_active_couple_members() returns trigger language plpgsql as $$
declare active_count integer;
begin
  if new.status <> 'active' then return new; end if;
  perform 1 from couple_spaces where id = new.space_id for update;
  if tg_op = 'UPDATE' then
    select count(*) into active_count from couple_members
      where space_id = new.space_id and status = 'active' and user_id <> old.user_id;
  else
    select count(*) into active_count from couple_members
      where space_id = new.space_id and status = 'active';
  end if;
  if active_count >= 2 then
    raise exception 'couple space cannot have more than two active members'
      using errcode = '23514';
  end if;
  return new;
end $$;
create trigger couple_members_max_two
  before insert or update of status on couple_members
  for each row execute function enforce_two_active_couple_members();

create table couple_invites (
  id uuid primary key default gen_random_uuid(),
  space_id uuid not null references couple_spaces(id) on delete cascade,
  inviter_id uuid not null references users(id) on delete cascade,
  accepted_by uuid references users(id) on delete set null,
  code_hash char(64) not null unique,
  status varchar(16) not null default 'pending'
    check (status in ('pending', 'accepted', 'cancelled', 'expired')),
  expires_at timestamptz not null,
  created_at timestamptz not null default now()
);
create index couple_invites_space_idx on couple_invites(space_id, status);

create table media_assets (
  id uuid primary key,
  space_id uuid not null references couple_spaces(id) on delete restrict,
  owner_id uuid not null references users(id) on delete restrict,
  provider varchar(16) not null check (provider in ('local', 'qiniu')),
  bucket varchar(128),
  object_key text not null unique check (object_key !~ '(^/|(^|/)\.\.?(/|$)|\\)'),
  mime_type varchar(100) not null,
  expected_size bigint not null check (expected_size > 0),
  size_bytes bigint,
  object_hash text,
  status varchar(16) not null default 'pending' check (status in ('pending', 'ready', 'deleted')),
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  constraint media_assets_qiniu_bucket_check
    check ((provider = 'qiniu' and bucket is not null and bucket <> '') or provider = 'local')
);
create index media_assets_space_idx on media_assets(space_id, status);

create table media_items (
  id uuid primary key default gen_random_uuid(),
  space_id uuid not null references couple_spaces(id) on delete restrict,
  uploader_id uuid not null references users(id) on delete restrict,
  type varchar(16) not null check (type in ('image', 'video')),
  asset_id uuid not null references media_assets(id) on delete restrict,
  thumbnail_asset_id uuid references media_assets(id) on delete restrict,
  caption varchar(200) not null default '',
  media_date date not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (type = 'image' or thumbnail_asset_id is not null)
);
create index media_items_space_created_idx on media_items(space_id, created_at desc);

create table anniversaries (
  id uuid primary key default gen_random_uuid(),
  space_id uuid not null references couple_spaces(id) on delete restrict,
  created_by uuid not null references users(id) on delete restrict,
  title varchar(30) not null,
  date date not null,
  type varchar(16) not null check (type in ('yearly', 'milestone')),
  cover_asset_id uuid references media_assets(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index anniversaries_space_idx on anniversaries(space_id, date);

create table letters (
  id uuid primary key default gen_random_uuid(),
  space_id uuid not null references couple_spaces(id) on delete restrict,
  sender_id uuid not null references users(id) on delete restrict,
  title varchar(80) not null,
  content text not null check (char_length(content) between 1 and 20000),
  type varchar(16) not null check (type in ('instant', 'capsule')),
  unlock_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check ((type = 'instant' and unlock_at is null) or (type = 'capsule' and unlock_at is not null))
);
create index letters_space_created_idx on letters(space_id, created_at desc);

create table unbinding_requests (
  id uuid primary key default gen_random_uuid(),
  space_id uuid not null references couple_spaces(id) on delete restrict,
  requested_by uuid not null references users(id) on delete restrict,
  confirmed_by uuid references users(id) on delete restrict,
  status varchar(16) not null default 'pending'
    check (status in ('pending', 'confirmed', 'cancelled')),
  reason varchar(300),
  created_at timestamptz not null default now(),
  resolved_at timestamptz,
  check (confirmed_by is null or confirmed_by <> requested_by)
);
create unique index unbinding_one_pending_idx
  on unbinding_requests(space_id) where status = 'pending';
