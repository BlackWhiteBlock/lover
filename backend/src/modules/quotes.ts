import type { FastifyInstance } from 'fastify';
import type { AppContext, AuthHandler } from '../types.js';
import { formatCalendarDate } from '../domain.js';
import { getActiveCoupleLink } from './spaces.js';

export type QuoteAudience = 'couple' | 'solo';

export type DailyQuote = {
  text: string;
  author: string | null;
  date: string;
};

type QuoteRow = {
  body: string;
  author: string | null;
};

/** Stable day ordinal from calendar date YYYY-MM-DD (UTC). */
export function calendarDayOrdinal(date: string): number {
  const [y, m, d] = date.split('-').map(Number) as [number, number, number];
  return Math.floor(Date.UTC(y, m - 1, d) / 86_400_000);
}

export function pickQuoteForDay(quotes: QuoteRow[], date: string): QuoteRow | null {
  if (!quotes.length) return null;
  const index = calendarDayOrdinal(date) % quotes.length;
  return quotes[index] ?? null;
}

const FALLBACK_BY_AUDIENCE: Record<QuoteAudience, string> = {
  couple: '平凡的日子，因为有你，每一天都值得被记住。',
  solo: '把今天过成日记里值得留下的一页。',
};

export async function loadTodayQuote(
  context: AppContext,
  today = formatCalendarDate(new Date()),
  audience: QuoteAudience = 'couple',
): Promise<DailyQuote> {
  const result = await context.db.query<QuoteRow>(
    `select body, author
     from daily_quotes
     where active = true
       and audience in ($1, 'both')
     order by sort_order asc, id asc`,
    [audience],
  );
  const picked = pickQuoteForDay(result.rows, today);
  if (!picked) {
    return {
      text: FALLBACK_BY_AUDIENCE[audience],
      author: null,
      date: today,
    };
  }
  return {
    text: picked.body,
    author: picked.author,
    date: today,
  };
}

export function registerQuotes(app: FastifyInstance, context: AppContext, auth: AuthHandler) {
  app.get('/api/daily-quote', { preHandler: auth }, async (request) => {
    const link = await getActiveCoupleLink(context, request.user.id);
    const audience: QuoteAudience = link ? 'couple' : 'solo';
    return loadTodayQuote(context, formatCalendarDate(new Date()), audience);
  });
}
