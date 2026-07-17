-- Personal space + Lover (shared) space model, phone bind requests, content ownership.

alter table couple_spaces
  add column if not exists kind varchar(16) not null default 'personal'
    check (kind in ('personal', 'lover'));

alter table users
  add column if not exists gender varchar(20)
    check (gender is null or gender in ('male', 'female', 'unspecified')),
  add column if not exists birthday date,
  add column if not exists profile_completed boolean not null default false,
  add column if not exists personal_space_id uuid references couple_spaces(id);

-- Allow membership in personal + lover space simultaneously.
drop index if exists couple_members_one_active_space_idx;

create table if not exists couple_links (
  id uuid primary key default gen_random_uuid(),
  user_a_id uuid not null references users(id) on delete restrict,
  user_b_id uuid not null references users(id) on delete restrict,
  lover_space_id uuid not null references couple_spaces(id) on delete restrict,
  together_date date,
  status varchar(16) not null default 'active' check (status in ('active', 'ended')),
  created_at timestamptz not null default now(),
  ended_at timestamptz,
  check (user_a_id <> user_b_id)
);

create unique index if not exists couple_links_lover_space_idx
  on couple_links(lover_space_id);

create unique index if not exists couple_links_active_user_a_idx
  on couple_links(user_a_id) where status = 'active';

create unique index if not exists couple_links_active_user_b_idx
  on couple_links(user_b_id) where status = 'active';

create table if not exists couple_bind_requests (
  id uuid primary key default gen_random_uuid(),
  requester_id uuid not null references users(id) on delete cascade,
  target_user_id uuid not null references users(id) on delete cascade,
  status varchar(16) not null default 'pending'
    check (status in ('pending', 'accepted', 'rejected', 'cancelled', 'expired')),
  expires_at timestamptz not null default (now() + interval '7 days'),
  created_at timestamptz not null default now(),
  resolved_at timestamptz,
  check (requester_id <> target_user_id)
);

create index if not exists couple_bind_requests_target_pending_idx
  on couple_bind_requests(target_user_id, status)
  where status = 'pending';

create unique index if not exists couple_bind_requests_one_pending_from_requester_idx
  on couple_bind_requests(requester_id)
  where status = 'pending';

alter table media_items
  add column if not exists ownership varchar(16) not null default 'personal'
    check (ownership in ('personal', 'couple')),
  add column if not exists couple_link_id uuid references couple_links(id) on delete set null;

alter table anniversaries
  add column if not exists ownership varchar(16) not null default 'personal'
    check (ownership in ('personal', 'couple')),
  add column if not exists couple_link_id uuid references couple_links(id) on delete set null;

alter table letters
  add column if not exists ownership varchar(16) not null default 'personal'
    check (ownership in ('personal', 'couple')),
  add column if not exists couple_link_id uuid references couple_links(id) on delete set null,
  add column if not exists unlock_on_partner_bind boolean not null default false;

-- Capsule may omit unlock_at when unlock_on_partner_bind is true.
-- Constraint update lives in 008_letters_unlock_on_bind_check.sql.

-- Backfill existing single-space users as profile-complete personal spaces.
update users u
set personal_space_id = m.space_id,
    profile_completed = true,
    updated_at = now()
from couple_members m
where m.user_id = u.id
  and m.status = 'active'
  and u.personal_space_id is null;

update couple_spaces cs
set kind = 'personal'
where kind = 'personal'
  and exists (
    select 1 from couple_members m
    where m.space_id = cs.id and m.status = 'active'
    group by m.space_id having count(*) = 1
  );
