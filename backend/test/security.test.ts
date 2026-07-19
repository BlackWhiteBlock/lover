import assert from 'node:assert/strict';
import path from 'node:path';
import test from 'node:test';
import { resolveLocalObjectPath } from '../src/storage/local.js';
import { serializeLetter } from '../src/modules/letters.js';

test('local storage path remains below its configured root', () => {
  assert.equal(
    resolveLocalObjectPath('/srv/lover', 'couples/abc/2026-07/file.jpg'),
    path.resolve('/srv/lover', 'couples', 'abc', '2026-07', 'file.jpg'),
  );
  for (const key of ['../secret', 'couples/../../secret', '/etc/passwd', 'a\\..\\secret', 'a//b', './a']) {
    assert.throws(() => resolveLocalObjectPath('/srv/lover', key), /Invalid object key/);
  }
});

test('locked capsule serialization never returns content or summary', () => {
  const letter = {
    id: 'letter',
    senderId: 'sender',
    senderNickname: 'A',
    title: '未来',
    content: 'server secret body',
    type: 'capsule' as const,
    unlockAt: new Date('2030-01-01T00:00:00Z'),
    createdAt: new Date('2026-01-01T00:00:00Z'),
  };
  const locked = serializeLetter(letter, 'viewer', new Date('2029-12-31T23:59:59Z'));
  assert.equal(locked.isUnlocked, false);
  assert.equal('content' in locked, false);
  assert.equal('summary' in locked, false);

  const unlocked = serializeLetter(letter, 'viewer', new Date('2030-01-01T00:00:00Z'));
  assert.equal(unlocked.isUnlocked, true);
  // 接收方未拆开时仍不返回正文
  assert.equal('content' in unlocked, false);

  const openedByRecipient = serializeLetter(
    { ...letter, myOpenedAt: new Date('2030-01-01T00:00:01Z') },
    'viewer',
    new Date('2030-01-01T00:00:00Z'),
  );
  assert.equal(openedByRecipient.isUnlocked, true);
  assert.equal(openedByRecipient.content, letter.content);

  const asSender = serializeLetter(letter, 'sender', new Date('2030-01-01T00:00:00Z'));
  assert.equal(asSender.content, letter.content);
  assert.equal(asSender.deliveryStatus, 'sent');
});
