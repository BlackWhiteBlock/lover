import { forbidden } from './errors.js';

const DAY_MS = 86_400_000;
const datePattern = /^\d{4}-\d{2}-\d{2}$/;

export function parseCalendarDate(value: string): Date {
  if (!datePattern.test(value)) throw new Error('Invalid calendar date');
  const [year, month, day] = value.split('-').map(Number) as [number, number, number];
  const date = new Date(Date.UTC(year, month - 1, day));
  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day
  ) throw new Error('Invalid calendar date');
  return date;
}

export function formatCalendarDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

export function lovingDays(togetherDate: string, today: string): number {
  const days = Math.floor((parseCalendarDate(today).getTime() - parseCalendarDate(togetherDate).getTime()) / DAY_MS);
  return Math.max(0, days + 1);
}

export function anniversaryCountdown(
  type: 'yearly' | 'milestone',
  date: string,
  today: string,
): { days: number; nextDate: string | null; reached: boolean } {
  const target = parseCalendarDate(date);
  const now = parseCalendarDate(today);
  if (type === 'milestone') {
    const days = Math.ceil((target.getTime() - now.getTime()) / DAY_MS);
    return { days: Math.max(0, days), nextDate: days >= 0 ? date : null, reached: days < 0 };
  }
  let year = now.getUTCFullYear();
  const month = target.getUTCMonth();
  const day = target.getUTCDate();
  let next = new Date(Date.UTC(year, month, day));
  // Feb 29 anniversaries use Feb 28 in non-leap years.
  if (next.getUTCMonth() !== month) next = new Date(Date.UTC(year, 1, 28));
  if (next < now) {
    year += 1;
    next = new Date(Date.UTC(year, month, day));
    if (next.getUTCMonth() !== month) next = new Date(Date.UTC(year, 1, 28));
  }
  return {
    days: Math.round((next.getTime() - now.getTime()) / DAY_MS),
    nextDate: formatCalendarDate(next),
    reached: false,
  };
}

export function assertSpaceAccess(resourceSpaceId: string, activeSpaceId: string) {
  if (resourceSpaceId !== activeSpaceId) throw forbidden();
}

export function canAcceptInvite(input: {
  inviteStatus: string;
  expiresAt: Date;
  memberCount: number;
  inviteeActiveSpaceId: string | null;
  inviteSpaceId: string;
  now?: Date;
}) {
  if (input.inviteStatus !== 'pending') return { ok: false, reason: '邀请已失效' };
  if (input.expiresAt <= (input.now ?? new Date())) return { ok: false, reason: '邀请已过期' };
  if (input.memberCount >= 2) return { ok: false, reason: '情侣空间已有两名成员' };
  if (input.inviteeActiveSpaceId) {
    return {
      ok: false,
      reason: input.inviteeActiveSpaceId === input.inviteSpaceId
        ? '用户已是该情侣空间成员'
        : '用户已加入其他情侣空间',
    };
  }
  return { ok: true } as const;
}

export function isLetterUnlocked(type: 'instant' | 'capsule', unlockAt: Date | null, now = new Date()) {
  return type === 'instant' || (unlockAt !== null && unlockAt <= now);
}
