import assert from 'node:assert/strict';
import test from 'node:test';
import { resolveLocalObjectPath } from '../src/storage/local.js';
import { serializeLetter } from '../src/modules/letters.js';

test('local storage path remains below its configured root', () => {
  assert.equal(
    resolveLocalObjectPath('/srv/lover', 'couples/abc/2026-07/file.jpg'),
    '/srv/lover/couples/abc/2026-07/file.jpg',
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
  const locked = serializeLetter(letter, new Date('2029-12-31T23:59:59Z'));
  assert.equal(locked.isUnlocked, false);
  assert.equal('content' in locked, false);
  assert.equal('summary' in locked, false);

  const unlocked = serializeLetter(letter, new Date('2030-01-01T00:00:00Z'));
  assert.equal(unlocked.isUnlocked, true);
  assert.equal(unlocked.content, letter.content);
});
