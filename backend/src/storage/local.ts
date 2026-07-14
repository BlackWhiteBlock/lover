import path from 'node:path';

export function resolveLocalObjectPath(root: string, objectKey: string) {
  if (
    !objectKey ||
    objectKey.includes('\0') ||
    objectKey.includes('\\') ||
    path.posix.isAbsolute(objectKey) ||
    objectKey.split('/').some((part) => part === '..' || part === '.' || part === '')
  ) throw new Error('Invalid object key');
  const absoluteRoot = path.resolve(root);
  const target = path.resolve(absoluteRoot, ...objectKey.split('/'));
  if (!target.startsWith(`${absoluteRoot}${path.sep}`)) throw new Error('Invalid object key');
  return target;
}
