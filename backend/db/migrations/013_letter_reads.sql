-- 信件已拆：接收方打开后记已读；发送方可见对方是否已阅
create table if not exists letter_unread_baselines (
  user_id uuid primary key references users(id) on delete cascade,
  baseline_at timestamptz not null default now()
);

create table if not exists letter_item_reads (
  user_id uuid not null references users(id) on delete cascade,
  letter_id uuid not null references letters(id) on delete cascade,
  opened_at timestamptz not null default now(),
  primary key (user_id, letter_id)
);

create index if not exists letter_item_reads_letter_idx
  on letter_item_reads(letter_id);
