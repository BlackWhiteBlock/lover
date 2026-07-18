/**
 * MediaPicker — PhotoViewPicker + sandbox copy + upload-ready bytes.
 *
 * PhotoViewPicker returns temporary read-only URIs (安全访问图库).
 * They are NOT filesystem paths — must open with READ_ONLY then copy by FD.
 */
import { photoAccessHelper } from '@kit.MediaLibraryKit'
import { fileIo as fs } from '@kit.CoreFileKit'
import type { common } from '@kit.AbilityKit'
import { MediaFileInput } from '../model/Models'
import { VideoThumb } from './VideoThumb'

export interface PickedMediaFile {
  data: ArrayBuffer
  fileName: string
  mimeType: string
  isVideo: boolean
  /** Local sandbox path after copy (original media). */
  cachePath: string
  /** Image-component path (video → JPEG cover). */
  previewPath: string
  thumbData: ArrayBuffer | null
  thumbFileName: string
}

const VIDEO_MIME_PREFIX = 'video/'
const MAX_INLINE_BYTES = 80 * 1024 * 1024 // 80MB soft cap for in-memory upload path

function mimeFromExtension(ext: string): string {
  switch (ext.toLowerCase()) {
    case 'jpg': return 'image/jpeg'
    case 'jpeg': return 'image/jpeg'
    case 'png': return 'image/png'
    case 'gif': return 'image/gif'
    case 'webp': return 'image/webp'
    case 'heic': return 'image/heic'
    case 'heif': return 'image/heif'
    case 'bmp': return 'image/bmp'
    case 'mp4': return 'video/mp4'
    case 'mov': return 'video/quicktime'
    case 'avi': return 'video/x-msvideo'
    case 'mkv': return 'video/x-matroska'
    case '3gp': return 'video/3gpp'
    default: return 'application/octet-stream'
  }
}

function extractFileName(uri: string): string {
  const lastSlash = uri.lastIndexOf('/')
  if (lastSlash >= 0 && lastSlash < uri.length - 1) {
    const raw = uri.substring(lastSlash + 1)
    const q = raw.indexOf('?')
    const name = q >= 0 ? raw.substring(0, q) : raw
    // Picker URIs often end with opaque ids without extension — keep as hint only
    if (name.length > 0 && name.length < 120) {
      return decodeURIComponent(name)
    }
  }
  return `media_${Date.now()}`
}

function detectMimeType(uri: string, fileName: string): string {
  const fromName = fileName.split('.').pop() ?? ''
  if (fromName && fromName.length <= 5 && fromName.indexOf('/') < 0) {
    const mime = mimeFromExtension(fromName)
    if (mime !== 'application/octet-stream') {
      return mime
    }
  }
  const lower = uri.toLowerCase()
  if (lower.includes('video') || lower.includes('/vid')) {
    return 'video/mp4'
  }
  if (lower.includes('image') || lower.includes('/img') || lower.includes('photo')) {
    return 'image/jpeg'
  }
  const ext = uri.split('.').pop() ?? ''
  const cleanExt = ext.includes('?') ? ext.split('?')[0] : ext
  if (cleanExt && cleanExt.length <= 5) {
    return mimeFromExtension(cleanExt)
  }
  return 'image/jpeg'
}

function ensureExtension(fileName: string, mimeType: string, isVideo: boolean): string {
  if (fileName.includes('.') && fileName.lastIndexOf('.') > 0) {
    const ext = fileName.substring(fileName.lastIndexOf('.') + 1)
    if (ext.length > 0 && ext.length <= 5 && /^[a-zA-Z0-9]+$/.test(ext)) {
      return fileName
    }
  }
  if (isVideo || mimeType.startsWith(VIDEO_MIME_PREFIX)) {
    return `${fileName}.mp4`
  }
  if (mimeType === 'image/png') {
    return `${fileName}.png`
  }
  if (mimeType === 'image/webp') {
    return `${fileName}.webp`
  }
  if (mimeType === 'image/heic' || mimeType === 'image/heif') {
    return `${fileName}.heic`
  }
  return `${fileName}.jpg`
}

function ensureCacheDir(context: common.UIAbilityContext): string {
  const dir = `${context.cacheDir}/picked_media`
  try {
    fs.mkdirSync(dir, true)
  } catch (_e) {
    // already exists
  }
  return dir
}

/**
 * Copy picker URI → app sandbox.
 * Must use openSync(uri, READ_ONLY) + copyFileSync(fd, destPath).
 * String-to-string copyFileSync(uri, dest) throws "No such file or directory".
 */
function copyUriToCache(context: common.UIAbilityContext, uri: string, fileName: string): string {
  const dir = ensureCacheDir(context)
  const safeName = fileName.replace(/[^\w.\-]/g, '_')
  const dest = `${dir}/${Date.now()}_${safeName}`

  let srcFile: fs.File | null = null
  try {
    srcFile = fs.openSync(uri, fs.OpenMode.READ_ONLY)
    fs.copyFileSync(srcFile.fd, dest)
  } finally {
    if (srcFile !== null) {
      try {
        fs.closeSync(srcFile)
      } catch (_closeErr) {
        // ignore
      }
    }
  }
  return dest
}

function readFileData(path: string): ArrayBuffer {
  let file: fs.File | null = null
  try {
    file = fs.openSync(path, fs.OpenMode.READ_ONLY)
    const stat = fs.statSync(file.fd)
    if (stat.size > MAX_INLINE_BYTES) {
      throw new Error(`文件过大（超过 ${Math.floor(MAX_INLINE_BYTES / 1024 / 1024)}MB），请选择更小的文件`)
    }
    const buf = new ArrayBuffer(stat.size)
    fs.readSync(file.fd, buf)
    return buf
  } finally {
    if (file !== null) {
      fs.closeSync(file)
    }
  }
}

/** Yield so we are outside PhotoViewPicker's immediate return stack (system requirement). */
function deferAfterPicker(): Promise<void> {
  return new Promise<void>((resolve) => {
    setTimeout(() => {
      resolve()
    }, 0)
  })
}

export class MediaPicker {
  private context: common.UIAbilityContext

  constructor(context: common.UIAbilityContext) {
    this.context = context
  }

  async pickMedia(maxItems: number = 20, allowVideo: boolean = true): Promise<PickedMediaFile[]> {
    const picker = new photoAccessHelper.PhotoViewPicker()
    const selectOptions: photoAccessHelper.PhotoSelectOptions = new photoAccessHelper.PhotoSelectOptions()
    selectOptions.MIMEType = allowVideo
      ? photoAccessHelper.PhotoViewMIMETypes.IMAGE_VIDEO_TYPE
      : photoAccessHelper.PhotoViewMIMETypes.IMAGE_TYPE
    selectOptions.maxSelectNumber = maxItems

    const photoSelectResult = await picker.select(selectOptions)
    if (!photoSelectResult || photoSelectResult.photoUris.length === 0) {
      return []
    }

    // Persist URI list, then open AFTER picker UI has fully returned.
    const uris: string[] = photoSelectResult.photoUris.slice()
    await deferAfterPicker()

    const files: PickedMediaFile[] = []
    const errors: string[] = []

    for (let i = 0; i < uris.length; i++) {
      const uri: string = uris[i]
      try {
        let fileName = extractFileName(uri)
        const mimeType = detectMimeType(uri, fileName)
        const isVideo = mimeType.startsWith(VIDEO_MIME_PREFIX) ||
          fileName.toLowerCase().endsWith('.mp4') ||
          fileName.toLowerCase().endsWith('.mov')
        fileName = ensureExtension(fileName, mimeType, isVideo)

        const cachePath = copyUriToCache(this.context, uri, fileName)
        const data = readFileData(cachePath)
        const resolvedMime = isVideo && !mimeType.startsWith(VIDEO_MIME_PREFIX)
          ? 'video/mp4'
          : (!isVideo && mimeType === 'application/octet-stream' ? 'image/jpeg' : mimeType)

        let previewPath = cachePath
        let thumbData: ArrayBuffer | null = null
        let thumbFileName = ''
        if (isVideo) {
          const thumb = await VideoThumb.extract(this.context, cachePath, fileName)
          if (thumb !== null) {
            previewPath = thumb.cachePath
            thumbData = thumb.data
            thumbFileName = thumb.fileName
          }
        }

        files.push({
          data,
          fileName: fileName.length > 0 ? fileName : `media_${Date.now()}.jpg`,
          mimeType: resolvedMime,
          isVideo,
          cachePath,
          previewPath,
          thumbData,
          thumbFileName,
        })
      } catch (readErr) {
        const msg = readErr instanceof Error ? readErr.message : String(readErr)
        errors.push(msg)
        console.error(`Failed to read picked file: ${uri} — ${msg}`)
      }
    }

    if (files.length === 0 && errors.length > 0) {
      throw new Error(errors[0])
    }
    if (errors.length > 0 && files.length > 0) {
      console.warn(`部分媒体读取失败: ${errors.length}`)
    }
    return files
  }

  async pickImages(maxItems: number = 20): Promise<PickedMediaFile[]> {
    return this.pickMedia(maxItems, false)
  }

  static toMediaInputs(picked: PickedMediaFile[]): MediaFileInput[] {
    const result: MediaFileInput[] = []
    for (let index = 0; index < picked.length; index++) {
      const p = picked[index]
      const input = new MediaFileInput()
      input.data = p.data
      input.fileName = p.fileName
      input.mimeType = p.mimeType
      input.isVideo = p.isVideo
      input.cachePath = p.cachePath
      input.previewPath = p.previewPath.length > 0 ? p.previewPath : p.cachePath
      input.thumbData = p.thumbData
      input.thumbFileName = p.thumbFileName
      input.draftId = `${Date.now()}_${index}_${p.fileName}`
      result.push(input)
    }
    return result
  }
}
