-- Bind invites expire in 24 hours; a user may only receive one pending invite at a time.

alter table couple_bind_requests
  alter column expires_at set default (now() + interval '24 hours');

-- Expire already-stale pending rows so the unique index below can apply cleanly.
update couple_bind_requests
set status = 'expired', resolved_at = coalesce(resolved_at, now())
where status = 'pending' and expires_at <= now();

create unique index if not exists couple_bind_requests_one_pending_to_target_idx
  on couple_bind_requests(target_user_id)
  where status = 'pending';
