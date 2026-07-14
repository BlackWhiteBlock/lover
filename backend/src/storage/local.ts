import path from 'node:path';
import { assertSafeObjectKey } from './object-key.js';

export function resolveLocalObjectPath(root: string, objectKey: string) {
  assertSafeObjectKey(objectKey);
  const absoluteRoot = path.resolve(root);
  const target = path.resolve(absoluteRoot, ...objectKey.split('/'));
  if (!target.startsWith(`${absoluteRoot}${path.sep}`)) throw new Error('Invalid object key');
  return target;
}
