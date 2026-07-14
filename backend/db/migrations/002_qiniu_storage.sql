alter table media_assets
  add column if not exists bucket varchar(128),
  add column if not exists object_hash text;

alter table media_assets drop constraint if exists media_assets_provider_check;
alter table media_assets
  add constraint media_assets_provider_check check (provider in ('local', 'qiniu'));

alter table media_assets drop constraint if exists media_assets_qiniu_bucket_check;
alter table media_assets
  add constraint media_assets_qiniu_bucket_check
  check ((provider = 'qiniu' and bucket is not null and bucket <> '') or provider = 'local');
