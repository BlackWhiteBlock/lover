import type { FastifyInstance } from 'fastify';
import type { AppContext, AuthHandler } from '../types.js';
import { formatCalendarDate } from '../domain.js';

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

export async function loadTodayQuote(context: AppContext, today = formatCalendarDate(new Date())): Promise<DailyQuote> {
  const result = await context.db.query<QuoteRow>(
    `select body, author
     from daily_quotes
     where active = true
     order by sort_order asc, id asc`,
  );
  const picked = pickQuoteForDay(result.rows, today);
  if (!picked) {
    return {
      text: '平凡的日子，因为有你，每一天都值得被记住。',
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
  app.get('/api/daily-quote', { preHandler: auth }, async () => loadTodayQuote(context));
}
