import { randomBytes } from 'node:crypto';

const SAFE_KEY = /^[A-Za-z0-9][A-Za-z0-9._/-]{0,511}$/;

export function assertSafeObjectKey(objectKey: string) {
  if (
    !SAFE_KEY.test(objectKey) ||
    objectKey.includes('\\') ||
    objectKey.includes('\0') ||
    objectKey.split('/').some((part) => !part || part === '.' || part === '..')
  ) throw new Error('Invalid object key');
  return objectKey;
}

export function assertObjectKeyBelongsToSpace(objectKey: string, spaceId: string) {
  assertSafeObjectKey(objectKey);
  if (!objectKey.startsWith(`couples/${spaceId}/`)) throw new Error('Object key does not belong to space');
  return objectKey;
}

export function buildObjectKey(spaceId: string, fileName: string, mimeType: string, now = new Date()) {
  if (!/^[0-9a-f-]{36}$/i.test(spaceId)) throw new Error('Invalid space id');
  const extension = fileExtension(fileName, mimeType);
  const month = now.toISOString().slice(0, 7);
  const kind = mimeType.startsWith('video/') ? 'videos' : 'images';
  return assertSafeObjectKey(
    `couples/${spaceId}/${kind}/${month}/${randomBytes(12).toString('hex')}.${extension}`,
  );
}

function fileExtension(name: string, mime: string) {
  const supplied = name.split(/[/\\]/).pop()?.split('.').pop()?.toLowerCase();
  const allowed: Record<string, string[]> = {
    'image/jpeg': ['jpg', 'jpeg'],
    'image/png': ['png'],
    'image/webp': ['webp'],
    'image/heic': ['heic'],
    'video/mp4': ['mp4', 'm4v'],
    'video/quicktime': ['mov', 'qt'],
    'video/3gpp': ['3gp', '3gpp'],
    'video/3gpp2': ['3g2', '3gpp2'],
    'video/webm': ['webm'],
    'video/x-matroska': ['mkv'],
  };
  const choices = allowed[mime];
  if (!choices) throw new Error('Unsupported MIME type');
  return supplied && choices.includes(supplied) ? supplied : choices[0]!;
}
