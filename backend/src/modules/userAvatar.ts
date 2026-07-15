import jwt from 'jsonwebtoken';
import type { AppContext } from '../types.js';
import { createStorageProvider, type DownloadVariant } from '../storage/provider.js';
import { assertObjectKeyBelongsToSpace } from '../storage/object-key.js';

type AvatarAssetRow = {
  id: string;
  object_key: string;
  mime_type: string;
  provider: 'local' | 'qiniu';
  bucket: string | null;
  space_id: string;
};

/** Build a short-lived download URL for a user's avatar asset. */
export async function signAvatarUrl(
  context: AppContext,
  avatarAssetId: string | null | undefined,
  viewerUserId: string,
  fallbackUrl: string | null | undefined = null,
): Promise<string | null> {
  if (!avatarAssetId) return fallbackUrl ?? null;
  const asset = await context.db.query<AvatarAssetRow>(
    `select id, object_key, mime_type, provider, bucket, space_id
     from media_assets where id = $1 and status = 'ready'`,
    [avatarAssetId],
  );
  const row = asset.rows[0];
  if (!row) return fallbackUrl ?? null;
  assertObjectKeyBelongsToSpace(row.object_key, row.space_id);

  const provider = createStorageProvider(context.config);
  if (row.provider !== provider.name || row.bucket !== provider.bucket) {
    return fallbackUrl ?? null;
  }
  if (provider.name === 'qiniu') {
    const signed = await provider.signDownload(row.object_key, row.mime_type, 'original' as DownloadVariant);
    return signed.url;
  }
  const token = jwt.sign(
    {
      type: 'download',
      assetId: row.id,
      objectKey: row.object_key,
      spaceId: row.space_id,
      sub: viewerUserId,
      avatar: true,
    },
    context.config.storage.signingSecret,
    { expiresIn: context.config.storage.tokenTtlSeconds },
  );
  return `${context.config.publicBaseUrl}/private-media/${row.id}?token=${encodeURIComponent(token)}`;
}

export async function presentUserAvatar(
  context: AppContext,
  user: { avatarUrl?: string | null; avatarAssetId?: string | null },
  viewerUserId: string,
): Promise<string | null> {
  return signAvatarUrl(context, user.avatarAssetId, viewerUserId, user.avatarUrl);
}
