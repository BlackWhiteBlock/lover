-- Partner activity inbox (pull-based; no push required).
create table if not exists partner_activity_events (
  id uuid primary key default gen_random_uuid(),
  couple_link_id uuid not null references couple_links(id) on delete cascade,
  actor_id uuid not null references users(id) on delete cascade,
  recipient_id uuid not null references users(id) on delete cascade,
  type varchar(32) not null
    check (type in (
      'letter_instant',
      'letter_capsule',
      'media_created',
      'anniversary_created'
    )),
  entity_type varchar(16) not null
    check (entity_type in ('letter', 'media', 'anniversary')),
  entity_id uuid not null,
  title varchar(160) not null,
  payload jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  read_at timestamptz,
  check (actor_id <> recipient_id)
);

create index if not exists partner_activity_recipient_created_idx
  on partner_activity_events(recipient_id, created_at desc);

create index if not exists partner_activity_recipient_unread_idx
  on partner_activity_events(recipient_id)
  where read_at is null;

create unique index if not exists partner_activity_dedupe_idx
  on partner_activity_events(recipient_id, type, entity_id);
