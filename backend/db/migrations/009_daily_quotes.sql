-- Daily love quotes shown on the home page (rotated by calendar day).

create table if not exists daily_quotes (
  id bigserial primary key,
  body text not null check (char_length(body) between 1 and 500),
  author varchar(80),
  sort_order int not null default 0,
  active boolean not null default true,
  created_at timestamptz not null default now()
);

create index if not exists daily_quotes_active_sort_idx
  on daily_quotes (active, sort_order, id);

insert into daily_quotes (body, author, sort_order) values
  ('爱不是寻找一个完美的人，而是学会用完美的眼光，欣赏那个不完美的人。', 'Sam Keen', 1),
  ('你是我见过最好的事，也是我最不想失去的事。', null, 2),
  ('在你身边，我才明白什么叫做刚刚好。', null, 3),
  ('最美好的事情，是找到一个人，他让你微笑，也让你变成更好的自己。', null, 4),
  ('The best thing to hold onto in life is each other.', 'Audrey Hepburn', 5),
  ('有些人一旦遇见，便一眼万年。', null, 6),
  ('爱情不需要轰轰烈烈，只需要长长久久。', null, 7),
  ('You are my today and all of my tomorrows.', 'Leo Christopher', 8),
  ('平凡的日子，因为有你，每一天都值得被记住。', null, 9),
  ('我想和你虚度时光，比如看看落日，比如散散步。', '李宗盛', 10),
  ('Whatever our souls are made of, his and mine are the same.', 'Emily Brontë', 11),
  ('世界上最遥远的距离，是我站在你面前，你却不知道我爱你。', '泰戈尔', 12),
  ('爱一个人，就是在他疲惫的时候给他力量，在他失落的时候给他温暖。', null, 13),
  ('I would rather spend one lifetime with you, than face all the ages of this world alone.', 'Tolkien', 14);
