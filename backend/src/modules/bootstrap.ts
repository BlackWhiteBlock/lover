import type { FastifyInstance } from 'fastify';
import type { AppContext, AuthHandler } from '../types.js';
import { formatCalendarDate, lovingDays } from '../domain.js';
import { activeSpaceId } from './couples.js';

export function registerBootstrap(app: FastifyInstance, context: AppContext, auth: AuthHandler) {
  app.get('/api/bootstrap', { preHandler: auth }, async (request) => {
    const spaceId = await activeSpaceId(context, request.user.id);
    const [space, members, media] = await Promise.all([
      context.db.query<{ id: string; name: string; togetherDate: string | null; createdAt: Date }>(
        `select id, name, together_date as "togetherDate", created_at as "createdAt"
         from couple_spaces where id = $1 and status = 'active'`,
        [spaceId],
      ),
      context.db.query(
        `select u.id, u.nickname, u.avatar_url as "avatarUrl"
         from couple_members m join users u on u.id = m.user_id
         where m.space_id = $1 and m.status = 'active' order by m.joined_at`,
        [spaceId],
      ),
      context.db.query(
        `select id, type, asset_id as "assetId", thumbnail_asset_id as "thumbnailAssetId",
                caption, media_date as "mediaDate", created_at as "createdAt"
         from media_items where space_id = $1 order by created_at desc limit 6`,
        [spaceId],
      ),
    ]);
    const row = space.rows[0]!;
    const today = formatCalendarDate(new Date());
    return {
      space: { ...row, members: members.rows },
      lovingJourney: {
        togetherDate: row.togetherDate,
        days: row.togetherDate ? lovingDays(row.togetherDate, today) : null,
        needsTogetherDate: !row.togetherDate,
      },
      recentMedia: media.rows,
    };
  });
}
