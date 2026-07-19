-- 时光未读：首访基线 + 按条已读（对方新增/修改且未浏览过则未读）
create table if not exists media_unread_baselines (
  user_id uuid primary key references users(id) on delete cascade,
  baseline_at timestamptz not null default now()
);

create table if not exists media_item_reads (
  user_id uuid not null references users(id) on delete cascade,
  media_item_id uuid not null references media_items(id) on delete cascade,
  read_at timestamptz not null default now(),
  primary key (user_id, media_item_id)
);

create index if not exists media_item_reads_media_idx
  on media_item_reads(media_item_id);
