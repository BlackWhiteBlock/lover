-- Solo-mode (unlinked) daily quotes: waiting & self-growth themes.

alter table daily_quotes
  add column if not exists audience varchar(16) not null default 'couple'
  check (audience in ('couple', 'solo', 'both'));

create index if not exists daily_quotes_audience_active_sort_idx
  on daily_quotes (audience, active, sort_order, id);

-- Existing rows stay couple-facing (column default already 'couple').

insert into daily_quotes (body, author, sort_order, audience) values
  ('等待不是空白，而是把心慢慢养得更柔软。', null, 1, 'solo'),
  ('You are enough, even on the quietest days.', null, 2, 'solo'),
  ('每一个独自走过的清晨，都在为某次相遇铺路。', null, 3, 'solo'),
  ('Be patient with yourself. Growth is a slow bloom.', null, 4, 'solo'),
  ('把今天过成日记里值得留下的一页。', null, 5, 'solo'),
  ('The right person will feel like coming home — until then, make a home in yourself.', null, 6, 'solo'),
  ('不必急着遇见谁，先好好成为自己。', null, 7, 'solo'),
  ('One heart. One day. One gentle step.', null, 8, 'solo'),
  ('孤独有时是礼物：它让你听清自己的声音。', null, 9, 'solo'),
  ('In the meantime, collect small joys like pressed flowers.', null, 10, 'solo'),
  ('等风来的日子，也可以先把自己过成风。', null, 11, 'solo'),
  ('You are already becoming someone worth waiting for.', null, 12, 'solo');
