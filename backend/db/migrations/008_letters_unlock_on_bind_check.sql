-- Capsule with unlock_on_partner_bind=true may omit unlock_at.
-- The original letters_check required unlock_at for every capsule and was never updated in 004.

alter table letters drop constraint if exists letters_check;

alter table letters
  add constraint letters_unlock_check check (
    (
      type = 'instant'
      and unlock_at is null
      and unlock_on_partner_bind = false
    )
    or (
      type = 'capsule'
      and (
        (unlock_on_partner_bind = true and unlock_at is null)
        or (unlock_on_partner_bind = false and unlock_at is not null)
      )
    )
  );
