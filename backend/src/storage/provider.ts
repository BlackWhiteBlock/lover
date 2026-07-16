import fs from 'node:fs/promises';
import qiniu from 'qiniu';
import type { Config } from '../config.js';
import { assertSafeObjectKey } from './object-key.js';
import { resolveLocalObjectPath } from './local.js';

export type StorageProviderName = 'local' | 'qiniu';
export type DownloadVariant = 'original' | 'thumb';

export interface UploadGrantInput {
  assetId: string;
  objectKey: string;
  mimeType: string;
  sizeBytes: number;
  localUploadToken?: string;
}

export interface UploadGrant {
  provider: StorageProviderName;
  uploadToken: string;
  uploadUrl: string;
  objectKey: string;
  expiresIn: number;
  uploadFields?: Record<string, string>;
}

export interface StoredObjectInfo {
  sizeBytes: number;
  mimeType?: string;
  hash?: string;
}

export interface StorageProvider {
  readonly name: StorageProviderName;
  readonly bucket: string | null;
  createUploadGrant(input: UploadGrantInput): Promise<UploadGrant>;
  statObject(objectKey: string): Promise<StoredObjectInfo>;
  signDownload(objectKey: string, mimeType: string, variant: DownloadVariant): Promise<{ url: string; expiresIn: number }>;
}

export interface QiniuManager {
  stat(bucket: string, key: string): ReturnType<qiniu.rs.BucketManager['stat']>;
  privateDownloadUrl(domain: string, key: string, deadline: number): string;
}

export class LocalStorageProvider implements StorageProvider {
  readonly name = 'local' as const;
  readonly bucket = null;

  constructor(private readonly config: Config) {}

  async createUploadGrant(input: UploadGrantInput): Promise<UploadGrant> {
    if (!input.localUploadToken) throw new Error('Local upload token is required');
    return {
      provider: this.name,
      uploadToken: input.localUploadToken,
      uploadUrl: `${this.config.publicBaseUrl}/api/media-assets/local-upload`,
      objectKey: input.objectKey,
      expiresIn: this.config.storage.tokenTtlSeconds,
    };
  }

  async statObject(objectKey: string): Promise<StoredObjectInfo> {
    const stat = await fs.stat(resolveLocalObjectPath(this.config.storage.dir, objectKey));
    if (!stat.isFile()) throw new Error('Stored object is not a file');
    return { sizeBytes: stat.size };
  }

  async signDownload(): Promise<{ url: string; expiresIn: number }> {
    throw new Error('Local downloads are signed by the application route');
  }
}

export class QiniuStorageProvider implements StorageProvider {
  readonly name = 'qiniu' as const;
  readonly bucket: string;

  constructor(
    private readonly config: Config,
    private readonly managerFactory?: () => QiniuManager,
  ) {
    this.bucket = config.storage.qiniu.bucket;
  }

  private mac() {
    return new qiniu.auth.digest.Mac(
      this.config.storage.qiniu.accessKey,
      this.config.storage.qiniu.secretKey,
    );
  }

  private manager() {
    return this.managerFactory?.() ?? new qiniu.rs.BucketManager(this.mac(), new qiniu.conf.Config());
  }

  async createUploadGrant(input: UploadGrantInput): Promise<UploadGrant> {
    assertSafeObjectKey(input.objectKey);
    // MediaStore / 部分机型申报的 size、MIME 可能与实际上传略有偏差
    const slack = Math.max(256 * 1024, Math.ceil(input.sizeBytes * 0.02));
    const mimeLimit = input.mimeType.startsWith('video/')
      ? 'video/mp4;video/quicktime;video/3gpp;video/3gpp2;video/webm;video/x-matroska'
      : input.mimeType;
    const policy = new qiniu.rs.PutPolicy({
      scope: `${this.bucket}:${input.objectKey}`,
      expires: this.config.storage.tokenTtlSeconds,
      insertOnly: 1,
      forceSaveKey: true,
      saveKey: input.objectKey,
      fsizeMin: Math.max(1, input.sizeBytes - slack),
      fsizeLimit: input.sizeBytes + slack,
      mimeLimit,
      detectMime: 1,
      returnBody: JSON.stringify({
        key: '$(key)',
        hash: '$(etag)',
        size: '$(fsize)',
        mimeType: '$(mimeType)',
      }),
    });
    return {
      provider: this.name,
      uploadToken: policy.uploadToken(this.mac()),
      uploadUrl: this.config.storage.qiniu.uploadUrl,
      objectKey: input.objectKey,
      expiresIn: this.config.storage.tokenTtlSeconds,
      uploadFields: { key: input.objectKey },
    };
  }

  async statObject(objectKey: string): Promise<StoredObjectInfo> {
    assertSafeObjectKey(objectKey);
    const response = await this.manager().stat(this.bucket, objectKey);
    if (!response.ok()) throw new Error(`Qiniu stat failed: ${response.data.error ?? 'unknown error'}`);
    const data = response.data;
    if (typeof data.fsize !== 'number' || !data.mimeType) throw new Error('Qiniu stat returned incomplete metadata');
    return {
      sizeBytes: data.fsize,
      mimeType: data.mimeType,
      hash: data.hash,
    };
  }

  async signDownload(objectKey: string, mimeType: string, variant: DownloadVariant) {
    assertSafeObjectKey(objectKey);
    const processQuery = variant === 'thumb' && mimeType.startsWith('image/')
      ? this.config.storage.qiniu.imageThumbFop
      : '';
    const key = processQuery ? `${objectKey}?${processQuery}` : objectKey;
    const expiresIn = this.config.storage.qiniu.downloadExpiresSeconds;
    const deadline = Math.floor(Date.now() / 1000) + expiresIn;
    return {
      url: this.manager().privateDownloadUrl(this.config.storage.qiniu.downloadDomain, key, deadline),
      expiresIn,
    };
  }
}

export function createStorageProvider(config: Config): StorageProvider {
  return config.storage.provider === 'qiniu'
    ? new QiniuStorageProvider(config)
    : new LocalStorageProvider(config);
}
