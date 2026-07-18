/**
 * Asset uploader — multipart to Qiniu or local storage.
 * Field layout mirrors Android AssetUploader.kt.
 */
import http from '@ohos.net.http'
import util from '@ohos.util'
import { BackendException } from './NetworkErrors'
import type { TokenAssetResponse } from '../model/Models'

/** A single field in a multipart form. */
export class MultipartField {
  name: string = ''
  value: string | ArrayBuffer = ''
  fileName: string | null = null
  contentType: string | null = null
}

/** Result of building a multipart body. */
export class MultipartResult {
  body: ArrayBuffer = new ArrayBuffer(0)
  boundary: string = ''
}

function encodeUtf8(text: string): Uint8Array {
  // Use classic encode() — encodeInto(string) can return non-Uint8Array on some SDKs
  // and corrupt the multipart body (→ storage 400).
  return new util.TextEncoder().encode(text)
}

function safeFileName(name: string): string {
  const cleaned = name.replace(/[\r\n\"]/g, '_').trim()
  return cleaned.length > 0 ? cleaned : `upload_${Date.now()}`
}

/** Build a multipart/form-data body as ArrayBuffer. */
function buildMultipartBody(parts: Array<MultipartField>): MultipartResult {
  const boundary = '----LoverFormBoundary' + Date.now().toString(36)
  const chunks: Uint8Array[] = []

  for (let i = 0; i < parts.length; i++) {
    const part = parts[i]
    chunks.push(encodeUtf8(`--${boundary}\r\n`))
    if (part.fileName) {
      chunks.push(encodeUtf8(
        `Content-Disposition: form-data; name="${part.name}"; filename="${safeFileName(part.fileName)}"\r\n`))
      if (part.contentType && part.contentType.length > 0) {
        chunks.push(encodeUtf8(`Content-Type: ${part.contentType}\r\n`))
      }
    } else {
      chunks.push(encodeUtf8(`Content-Disposition: form-data; name="${part.name}"\r\n`))
    }
    chunks.push(encodeUtf8('\r\n'))

    if (typeof part.value === 'string') {
      chunks.push(encodeUtf8(part.value))
    } else {
      chunks.push(new Uint8Array(part.value))
    }
    chunks.push(encodeUtf8('\r\n'))
  }

  chunks.push(encodeUtf8(`--${boundary}--\r\n`))

  let totalLen = 0
  for (let i = 0; i < chunks.length; i++) {
    totalLen += chunks[i].byteLength
  }
  const result = new MultipartResult()
  result.body = new ArrayBuffer(totalLen)
  result.boundary = boundary
  const resultView = new Uint8Array(result.body)
  let offset = 0
  for (let i = 0; i < chunks.length; i++) {
    resultView.set(chunks[i], offset)
    offset += chunks[i].byteLength
  }
  return result
}

function textField(name: string, value: string): MultipartField {
  const f = new MultipartField()
  f.name = name
  f.value = value
  return f
}

function fileField(name: string, fileName: string, data: ArrayBuffer, mimeType: string): MultipartField {
  const f = new MultipartField()
  f.name = name
  f.value = data
  f.fileName = fileName
  f.contentType = mimeType
  return f
}

/**
 * Read uploadFields as string map.
 * Backend sends `{ key: objectKey }`; never an array of {key,value}.
 */
function fieldsMap(grant: TokenAssetResponse): Record<string, string> {
  const out: Record<string, string> = {}
  const raw = grant.uploadFields as Object
  if (!raw) {
    return out
  }
  const keys = Object.keys(raw)
  for (let i = 0; i < keys.length; i++) {
    const k = keys[i]
    const v = (raw as Record<string, string>)[k]
    if (typeof v === 'string') {
      out[k] = v
    }
  }
  return out
}

function uploadErrorMessage(code: number, body: string): string {
  if (code === 413) {
    return '上传被拒绝（413）：文件过大'
  }
  try {
    const parsed = JSON.parse(body) as Record<string, Object>
    const errObj = parsed['error']
    let msg = ''
    if (typeof errObj === 'string') {
      msg = errObj
    } else if (errObj && typeof errObj === 'object') {
      const m = (errObj as Record<string, string>)['message']
      if (typeof m === 'string') {
        msg = m
      }
    }
    if (msg.length === 0 && typeof parsed['message'] === 'string') {
      msg = parsed['message'] as string
    }
    if (msg.length > 0) {
      const lower = msg.toLowerCase()
      if (lower.includes('size') || lower.includes('fsize')) {
        return '视频大小校验失败，请重试或换一段较短视频'
      }
      if (lower.includes('mime')) {
        return '不支持该视频格式，请转换为常见 MP4 后再试'
      }
      return msg
    }
  } catch (_e) {
    // ignore parse errors
  }
  return `媒体上传失败（${code}）`
}

export class AssetUploader {
  /**
   * Upload a file to the storage backend.
   * Qiniu form order matches Android: token, key, extra fields, then file.
   */
  async upload(
    grant: TokenAssetResponse,
    fileName: string,
    fileData: ArrayBuffer,
    mimeType: string,
    onProgress?: (fraction: number) => void,
  ): Promise<void> {
    const parts: Array<MultipartField> = []
    let bearerToken = ''
    let hasBearer = false

    if (grant.provider === 'local') {
      hasBearer = true
      bearerToken = grant.uploadToken
      parts.push(fileField('file', fileName, fileData, mimeType))
    } else if (grant.provider === 'qiniu') {
      // Android: token + key (+ other fields) then file
      parts.push(textField('token', grant.uploadToken))
      const map = fieldsMap(grant)
      const keyValue = (map['key'] && map['key'].length > 0) ? map['key'] : grant.objectKey
      parts.push(textField('key', keyValue))
      const names = Object.keys(map).sort()
      for (let i = 0; i < names.length; i++) {
        const name = names[i]
        if (name === 'token' || name === 'key' || name === 'file') {
          continue
        }
        parts.push(textField(name, map[name]))
      }
      parts.push(fileField('file', fileName, fileData, mimeType))
    } else {
      throw new BackendException('UNSUPPORTED_STORAGE_PROVIDER', `不支持的存储服务：${grant.provider}`)
    }

    const mpResult = buildMultipartBody(parts)
    await this.doUpload(
      grant.uploadUrl,
      mpResult.body,
      mpResult.boundary,
      bearerToken,
      hasBearer,
      onProgress,
    )
  }

  private async doUpload(
    uploadUrl: string,
    body: ArrayBuffer,
    boundary: string,
    bearerToken: string,
    hasBearer: boolean,
    onProgress?: (fraction: number) => void,
  ): Promise<void> {
    try {
      const httpRequest = http.createHttp()
      const headerRecord: Record<string, string> = {
        'Content-Type': `multipart/form-data; boundary=${boundary}`,
      }
      if (hasBearer && bearerToken.length > 0) {
        headerRecord['Authorization'] = `Bearer ${bearerToken}`
      }

      const response = await httpRequest.request(uploadUrl, {
        method: http.RequestMethod.POST,
        header: headerRecord,
        extraData: body,
        expectDataType: http.HttpDataType.STRING,
        connectTimeout: 900000,
        readTimeout: 900000,
      })

      httpRequest.destroy()
      if (onProgress !== undefined) {
        onProgress(1)
      }

      if (response.responseCode < 200 || response.responseCode >= 300) {
        const bodyText = typeof response.result === 'string' ? response.result : String(response.result ?? '')
        throw new BackendException('UPLOAD_FAILED', uploadErrorMessage(response.responseCode, bodyText))
      }
    } catch (e) {
      if (e instanceof BackendException) {
        throw e
      }
      const msg = e instanceof Error ? e.message : String(e)
      throw new BackendException('UPLOAD_NETWORK_ERROR', `无法连接媒体存储服务（${msg}）`)
    }
  }
}
