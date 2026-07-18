/**
 * Extract a JPEG cover frame from a local video (sandbox path).
 */
import { media } from '@kit.MediaKit'
import { image } from '@kit.ImageKit'
import { fileIo as fs } from '@kit.CoreFileKit'
import type { common } from '@kit.AbilityKit'

export class VideoThumbResult {
  data: ArrayBuffer = new ArrayBuffer(0)
  fileName: string = ''
  cachePath: string = ''
}

export class VideoThumb {
  /**
   * Best-effort: returns null if frame extraction fails on device.
   */
  static async extract(
    context: common.UIAbilityContext,
    videoPath: string,
    sourceName: string,
  ): Promise<VideoThumbResult | null> {
    let srcFile: fs.File | null = null
    let generator: media.AVImageGenerator | null = null
    let pixelMap: image.PixelMap | null = null
    try {
      srcFile = fs.openSync(videoPath, fs.OpenMode.READ_ONLY)
      generator = await media.createAVImageGenerator()
      generator.fdSrc = { fd: srcFile.fd }

      const param: media.PixelMapParams = {
        width: 720,
        height: 720,
      }
      pixelMap = await generator.fetchFrameByTime(
        0,
        media.AVImageQueryOptions.AV_IMAGE_QUERY_NEXT_SYNC,
        param,
      )
      if (!pixelMap) {
        return null
      }

      const packer = image.createImagePacker()
      const packOpts: image.PackingOption = { format: 'image/jpeg', quality: 82 }
      const jpegData: ArrayBuffer = await packer.packing(pixelMap, packOpts)
      packer.release()

      const base = sourceName.includes('.')
        ? sourceName.substring(0, sourceName.lastIndexOf('.'))
        : sourceName
      const fileName = `${base}-thumbnail.jpg`
      const dir = `${context.cacheDir}/picked_media`
      try {
        fs.mkdirSync(dir, true)
      } catch (_e) {
        // exists
      }
      const cachePath = `${dir}/${Date.now()}_${fileName.replace(/[^\w.\-]/g, '_')}`
      let out: fs.File | null = null
      try {
        out = fs.openSync(cachePath, fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE)
        fs.writeSync(out.fd, jpegData)
      } finally {
        if (out !== null) {
          fs.closeSync(out)
        }
      }

      const result = new VideoThumbResult()
      result.data = jpegData
      result.fileName = fileName
      result.cachePath = cachePath
      return result
    } catch (e) {
      console.error(`VideoThumb.extract failed: ${e}`)
      return null
    } finally {
      if (pixelMap !== null) {
        try {
          await pixelMap.release()
        } catch (_e) {
          // ignore
        }
      }
      if (generator !== null) {
        try {
          await generator.release()
        } catch (_e) {
          // ignore
        }
      }
      if (srcFile !== null) {
        try {
          fs.closeSync(srcFile)
        } catch (_e) {
          // ignore
        }
      }
    }
  }
}
