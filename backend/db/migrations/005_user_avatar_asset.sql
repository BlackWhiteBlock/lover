-- Avatar media asset linkage for private storage + signed download URLs.

alter table users
  add column if not exists avatar_asset_id uuid references media_assets(id) on delete set null;

create index if not exists users_avatar_asset_id_idx
  on users(avatar_asset_id)
  where avatar_asset_id is not null;
