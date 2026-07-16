package com.lover.app.core.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia

/**
 * 优先走系统相册（ACTION_PICK + MediaStore），避免部分华为/鸿蒙机型上
 * Photo Picker 退化成「文件」选择器。无相册 Activity 时再回退 Photo Picker。
 */
class PickGalleryImage : ActivityResultContract<Unit, Uri?>() {
    private val fallback = PickVisualMedia()

    override fun createIntent(context: Context, input: Unit): Intent {
        val gallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
        return if (gallery.resolveActivity(context.packageManager) != null) {
            gallery
        } else {
            fallback.createIntent(
                context,
                PickVisualMediaRequest(PickVisualMedia.ImageOnly),
            )
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.data ?: fallback.parseResult(resultCode, intent)
    }
}

/**
 * 单选图片或视频，优先相册。
 */
class PickGalleryImageOrVideo : ActivityResultContract<Unit, Uri?>() {
    private val fallback = PickVisualMedia()

    override fun createIntent(context: Context, input: Unit): Intent {
        val gallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        }
        return if (gallery.resolveActivity(context.packageManager) != null) {
            gallery
        } else {
            fallback.createIntent(
                context,
                PickVisualMediaRequest(PickVisualMedia.ImageAndVideo),
            )
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.data ?: fallback.parseResult(resultCode, intent)
    }
}

/**
 * 多选图片/视频，优先相册（EXTRA_ALLOW_MULTIPLE）。
 */
class PickGalleryImageAndVideoMultiple(
    private val maxItems: Int = 9,
) : ActivityResultContract<Unit, List<Uri>>() {
    private val fallback = PickMultipleVisualMedia(maxItems)

    override fun createIntent(context: Context, input: Unit): Intent {
        val gallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, maxItems > 1)
        }
        return if (gallery.resolveActivity(context.packageManager) != null) {
            gallery
        } else {
            fallback.createIntent(
                context,
                PickVisualMediaRequest(PickVisualMedia.ImageAndVideo),
            )
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
        val fromClip = buildList {
            val clip = intent.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i)?.uri?.let(::add)
                }
            } else {
                intent.data?.let(::add)
            }
        }.distinct().take(maxItems)
        if (fromClip.isNotEmpty()) return fromClip
        return fallback.parseResult(resultCode, intent)
    }
}
