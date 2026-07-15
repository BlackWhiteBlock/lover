-- One media_item (时光) may contain multiple ordered assets.

create table media_item_assets (
  id uuid primary key default gen_random_uuid(),
  media_item_id uuid not null references media_items(id) on delete cascade,
  sort_order int not null check (sort_order >= 0),
  type varchar(16) not null check (type in ('image', 'video')),
  asset_id uuid not null references media_assets(id) on delete restrict,
  thumbnail_asset_id uuid references media_assets(id) on delete restrict,
  created_at timestamptz not null default now(),
  check (type = 'image' or thumbnail_asset_id is not null),
  unique (media_item_id, sort_order)
);

create index media_item_assets_item_idx on media_item_assets(media_item_id, sort_order);

insert into media_item_assets (media_item_id, sort_order, type, asset_id, thumbnail_asset_id)
select id, 0, type, asset_id, thumbnail_asset_id
from media_items;

alter table media_items drop constraint if exists media_items_check;
alter table media_items drop column type;
alter table media_items drop column asset_id;
alter table media_items drop column thumbnail_asset_id;
