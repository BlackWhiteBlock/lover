import type { AppContext } from '../types.js';
import { forbidden, notFound } from '../errors.js';

export type CoupleLinkRow = {
  id: string;
  userAId: string;
  userBId: string;
  loverSpaceId: string;
  togetherDate: string | null;
  status: string;
};

/** User's personal space (required after onboarding). */
export async function personalSpaceId(context: AppContext, userId: string) {
  const result = await context.db.query<{ personal_space_id: string | null }>(
    `select personal_space_id from users where id = $1`,
    [userId],
  );
  const spaceId = result.rows[0]?.personal_space_id;
  if (!spaceId) throw forbidden('请先完成空间创建');
  return spaceId;
}

export async function getActiveCoupleLink(context: AppContext, userId: string) {
  const result = await context.db.query<CoupleLinkRow>(
    `select id, user_a_id as "userAId", user_b_id as "userBId",
            lover_space_id as "loverSpaceId", together_date as "togetherDate", status
     from couple_links
     where status = 'active' and (user_a_id = $1 or user_b_id = $1)`,
    [userId],
  );
  return result.rows[0] ?? null;
}

/** Where new content is written: lover space if bound, otherwise personal. */
export async function writeSpaceId(context: AppContext, userId: string) {
  const link = await getActiveCoupleLink(context, userId);
  if (link) return { spaceId: link.loverSpaceId, ownership: 'couple' as const, coupleLinkId: link.id };
  const spaceId = await personalSpaceId(context, userId);
  return { spaceId, ownership: 'personal' as const, coupleLinkId: null as string | null };
}

/** Spaces visible in feeds: my personal + partner personal + lover. */
export async function readableSpaceIds(context: AppContext, userId: string) {
  const personal = await personalSpaceId(context, userId);
  const link = await getActiveCoupleLink(context, userId);
  if (!link) return [personal];
  const partnerId = link.userAId === userId ? link.userBId : link.userAId;
  const partner = await context.db.query<{ personal_space_id: string | null }>(
    `select personal_space_id from users where id = $1`,
    [partnerId],
  );
  const partnerSpace = partner.rows[0]?.personal_space_id;
  return [personal, partnerSpace, link.loverSpaceId].filter(Boolean) as string[];
}

/** @deprecated Prefer writeSpaceId / personalSpaceId. Kept for gradual migration. */
export async function activeSpaceId(context: AppContext, userId: string) {
  return (await writeSpaceId(context, userId)).spaceId;
}

export async function assertReadableSpace(
  context: AppContext,
  userId: string,
  spaceId: string,
) {
  const ids = await readableSpaceIds(context, userId);
  if (!ids.includes(spaceId)) throw notFound('空间不存在或无权访问');
}

export function partnerIdFromLink(link: CoupleLinkRow, userId: string) {
  return link.userAId === userId ? link.userBId : link.userAId;
}
