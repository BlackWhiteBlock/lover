/**
 * Read taken/created date from a local media file (sandbox path).
 * Mirrors Android MediaTakenDateReader — returns YYYY-MM-DD or null.
 */
import { image } from '@kit.ImageKit'
import { media } from '@kit.MediaKit'
import { fileIo as fs } from '@kit.CoreFileKit'
import { DateUtils } from '../util/DateUtils'

export class MediaTakenDateReader {
  /**
   * Best-effort: EXIF for images, AV metadata for videos.
   * Result is capped at today (same as Android).
   */
  static async readIsoDate(path: string, isVideo: boolean = false): Promise<string | null> {
    if (!path || path.length === 0) {
      return null
    }
    let taken: string | null = null
    if (isVideo) {
      taken = await MediaTakenDateReader.readFromVideo(path)
    } else {
      taken = await MediaTakenDateReader.readFromImageExif(path)
      if (!taken) {
        // Some "images" may still carry video-like metadata; ignore
        taken = await MediaTakenDateReader.readFromVideo(path)
      }
    }
    if (!taken) {
      return null
    }
    const today = DateUtils.formatDate(new Date())
    return taken > today ? today : taken
  }

  private static async readFromImageExif(path: string): Promise<string | null> {
    let file: fs.File | null = null
    let source: image.ImageSource | null = null
    try {
      file = fs.openSync(path, fs.OpenMode.READ_ONLY)
      source = image.createImageSource(file.fd)
      const keys: string[] = [
        image.PropertyKey.DATE_TIME_ORIGINAL,
        image.PropertyKey.DATE_TIME,
        image.PropertyKey.DATE_TIME_DIGITIZED,
      ]
      for (let i = 0; i < keys.length; i++) {
        try {
          const raw = await source.getImageProperty(keys[i])
          const iso = MediaTakenDateReader.parseExifDateTime(raw)
          if (iso) {
            return iso
          }
        } catch (_e) {
          // try next key
        }
      }
      return null
    } catch (_e) {
      return null
    } finally {
      if (source !== null) {
        try {
          source.release()
        } catch (_e) {
          // ignore
        }
      }
      if (file !== null) {
        try {
          fs.closeSync(file)
        } catch (_e) {
          // ignore
        }
      }
    }
  }

  private static async readFromVideo(path: string): Promise<string | null> {
    let file: fs.File | null = null
    let extractor: media.AVMetadataExtractor | null = null
    try {
      file = fs.openSync(path, fs.OpenMode.READ_ONLY)
      extractor = await media.createAVMetadataExtractor()
      extractor.fdSrc = { fd: file.fd }
      const meta = await extractor.fetchMetadata()
      // Common fields across API versions
      const candidates: string[] = []
      const rec = meta as Record<string, string>
      const dateVal = rec['date'] ?? rec['DATE'] ?? ''
      const creation = rec['creationTime'] ?? rec['CREATION_TIME'] ?? ''
      const locationDate = rec['locationDate'] ?? ''
      if (typeof dateVal === 'string' && dateVal.length > 0) {
        candidates.push(dateVal)
      }
      if (typeof creation === 'string' && creation.length > 0) {
        candidates.push(creation)
      }
      if (typeof locationDate === 'string' && locationDate.length > 0) {
        candidates.push(locationDate)
      }
      for (let i = 0; i < candidates.length; i++) {
        const iso = MediaTakenDateReader.parseVideoMetadataDate(candidates[i])
        if (iso) {
          return iso
        }
      }
      return null
    } catch (_e) {
      return null
    } finally {
      if (extractor !== null) {
        try {
          await extractor.release()
        } catch (_e) {
          // ignore
        }
      }
      if (file !== null) {
        try {
          fs.closeSync(file)
        } catch (_e) {
          // ignore
        }
      }
    }
  }

  /** EXIF: "yyyy:MM:dd HH:mm:ss" */
  private static parseExifDateTime(raw: string): string | null {
    if (!raw || raw.length < 10) {
      return null
    }
    const head = raw.trim().substring(0, 19)
    // yyyy:MM:dd HH:mm:ss
    if (head.length >= 10 && head.charAt(4) === ':' && head.charAt(7) === ':') {
      const y = head.substring(0, 4)
      const m = head.substring(5, 7)
      const d = head.substring(8, 10)
      return MediaTakenDateReader.ymd(y, m, d)
    }
    // already ISO-ish
    if (head.charAt(4) === '-' && head.charAt(7) === '-') {
      return MediaTakenDateReader.ymd(
        head.substring(0, 4),
        head.substring(5, 7),
        head.substring(8, 10),
      )
    }
    return null
  }

  /**
   * Video metadata: ISO-8601, or yyyyMMdd'T'HHmmss(.SSS)(Z)
   * Skip Apple epoch placeholders starting with 1904.
   */
  private static parseVideoMetadataDate(raw: string): string | null {
    const trimmed = raw.trim()
    if (trimmed.length === 0 || trimmed.startsWith('1904')) {
      return null
    }
    // 2024-07-18T12:00:00.000Z / 2024-07-18
    if (trimmed.length >= 10 && trimmed.charAt(4) === '-' && trimmed.charAt(7) === '-') {
      return MediaTakenDateReader.ymd(
        trimmed.substring(0, 4),
        trimmed.substring(5, 7),
        trimmed.substring(8, 10),
      )
    }
    // 20240718T120000.000Z or 20240718T120000
    const compact = trimmed.replace(/[^0-9T]/g, '')
    if (compact.length >= 8 && compact.indexOf('T') === 8) {
      const y = compact.substring(0, 4)
      const m = compact.substring(4, 6)
      const d = compact.substring(6, 8)
      return MediaTakenDateReader.ymd(y, m, d)
    }
    if (/^\d{8}$/.test(compact.substring(0, 8))) {
      const y = compact.substring(0, 4)
      const m = compact.substring(4, 6)
      const d = compact.substring(6, 8)
      return MediaTakenDateReader.ymd(y, m, d)
    }
    return null
  }

  private static ymd(y: string, m: string, d: string): string | null {
    const yi = Number(y)
    const mi = Number(m)
    const di = Number(d)
    if (!Number.isFinite(yi) || !Number.isFinite(mi) || !Number.isFinite(di)) {
      return null
    }
    if (yi < 1970 || yi > 2100 || mi < 1 || mi > 12 || di < 1 || di > 31) {
      return null
    }
    return `${y}-${m.padStart(2, '0')}-${d.padStart(2, '0')}`
  }
}
