import assert from 'node:assert/strict';
import test from 'node:test';
import { calendarDayOrdinal, pickQuoteForDay } from '../src/modules/quotes.js';

test('calendar day ordinal is stable for a given date', () => {
  assert.equal(calendarDayOrdinal('2026-07-17'), calendarDayOrdinal('2026-07-17'));
  assert.equal(calendarDayOrdinal('2026-07-18') - calendarDayOrdinal('2026-07-17'), 1);
});

test('pickQuoteForDay rotates by calendar day and wraps', () => {
  const quotes = [
    { body: 'a', author: null },
    { body: 'b', author: 'x' },
    { body: 'c', author: null },
  ];
  const first = pickQuoteForDay(quotes, '2026-01-01');
  const same = pickQuoteForDay(quotes, '2026-01-01');
  const next = pickQuoteForDay(quotes, '2026-01-02');
  assert.deepEqual(first, same);
  assert.ok(first);
  assert.ok(next);
  assert.notEqual(first!.body, next!.body);
  assert.equal(pickQuoteForDay([], '2026-01-01'), null);
});
