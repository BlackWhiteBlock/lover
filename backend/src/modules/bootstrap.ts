import type { FastifyInstance } from 'fastify';
import type { AppContext, AuthHandler } from '../types.js';
import { formatCalendarDate, lovingDays } from '../domain.js';
import { getActiveCoupleLink, personalSpaceId, readableSpaceIds } from './spaces.js';
import { listRecentMedia } from './media.js';

export function registerBootstrap(app: FastifyInstance, context: AppContext, auth: AuthHandler) {
  app.get('/api/bootstrap', { preHandler: auth }, async (request) => {
    const personalId = await personalSpaceId(context, request.user.id);
    const link = await getActiveCoupleLink(context, request.user.id);
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const displaySpaceId = link?.loverSpaceId ?? personalId;

    const [space, members, recentMedia] = await Promise.all([
      context.db.query<{ id: string; name: string; createdAt: Date; kind: string }>(
        `select id, name, created_at as "createdAt", kind
         from couple_spaces where id = $1 and status = 'active'`,
        [displaySpaceId],
      ),
      link
        ? context.db.query(
          `select u.id, u.nickname, u.avatar_url as "avatarUrl"
           from users u where u.id = any($1::uuid[])`,
          [[link.userAId, link.userBId]],
        )
        : context.db.query(
          `select u.id, u.nickname, u.avatar_url as "avatarUrl"
           from users u where u.id = $1`,
          [request.user.id],
        ),
      listRecentMedia(context, spaceIds, 6),
    ]);
    const row = space.rows[0]!;
    const togetherDate = link?.togetherDate ?? null;
    const today = formatCalendarDate(new Date());
    return {
      space: { ...row, togetherDate, members: members.rows },
      lovingJourney: {
        togetherDate,
        days: togetherDate ? lovingDays(togetherDate, today) : null,
        needsTogetherDate: Boolean(link) && !togetherDate,
      },
      recentMedia,
      linked: Boolean(link),
      coupleLinkId: link?.id ?? null,
    };
  });
}
