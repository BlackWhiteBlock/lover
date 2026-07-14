import assert from 'node:assert/strict';
import test from 'node:test';
import {
  anniversaryCountdown,
  assertSpaceAccess,
  canAcceptInvite,
  lovingDays,
} from '../src/domain.js';

test('loving days are inclusive and timezone-independent', () => {
  assert.equal(lovingDays('2024-02-28', '2024-02-28'), 1);
  assert.equal(lovingDays('2024-02-28', '2024-03-01'), 3);
  assert.equal(lovingDays('2025-01-01', '2024-12-31'), 0);
});

test('yearly countdown rolls forward and handles leap day', () => {
  assert.deepEqual(anniversaryCountdown('yearly', '2020-02-29', '2025-02-27'), {
    days: 1,
    nextDate: '2025-02-28',
    reached: false,
  });
  assert.deepEqual(anniversaryCountdown('yearly', '2020-02-29', '2025-03-01'), {
    days: 364,
    nextDate: '2026-02-28',
    reached: false,
  });
});

test('milestone countdown marks past targets reached', () => {
  assert.deepEqual(anniversaryCountdown('milestone', '2026-07-20', '2026-07-14'), {
    days: 6,
    nextDate: '2026-07-20',
    reached: false,
  });
  assert.deepEqual(anniversaryCountdown('milestone', '2026-07-01', '2026-07-14'), {
    days: 0,
    nextDate: null,
    reached: true,
  });
});

test('invite decision enforces pending, capacity, expiry, and one active space', () => {
  const base = {
    inviteStatus: 'pending',
    expiresAt: new Date('2026-07-20T00:00:00Z'),
    memberCount: 1,
    inviteeActiveSpaceId: null,
    inviteSpaceId: 'space-a',
    now: new Date('2026-07-14T00:00:00Z'),
  };
  assert.deepEqual(canAcceptInvite(base), { ok: true });
  assert.equal(canAcceptInvite({ ...base, memberCount: 2 }).ok, false);
  assert.equal(canAcceptInvite({ ...base, inviteeActiveSpaceId: 'space-a' }).ok, false);
  assert.equal(canAcceptInvite({ ...base, inviteeActiveSpaceId: 'space-b' }).ok, false);
  assert.equal(canAcceptInvite({ ...base, expiresAt: base.now }).ok, false);
});

test('space access rejects cross-space resources', () => {
  assert.doesNotThrow(() => assertSpaceAccess('space-a', 'space-a'));
  assert.throws(() => assertSpaceAccess('space-b', 'space-a'), /无权访问/);
});
