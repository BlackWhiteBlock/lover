-- Per-user couple card cover (合照). Independent per account — not shared.

alter table users
  add column if not exists couple_cover_asset_id uuid references media_assets(id) on delete set null;

create index if not exists users_couple_cover_asset_id_idx
  on users(couple_cover_asset_id)
  where couple_cover_asset_id is not null;
