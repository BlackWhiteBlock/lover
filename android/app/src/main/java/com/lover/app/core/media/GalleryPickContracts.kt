package com.lover.app.core.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia

// 优先打开系统/厂商相册，避免华为/鸿蒙上 Photo Picker 退化成「文件」选择器。
// 1) 指定常见相册包名 2) ACTION_PICK + MediaStore 图片库（勿再 setType）
// 3) ACTION_GET_CONTENT 仅限图片 4) 最后才回退 Photo Picker
private val GALLERY_PACKAGES = listOf(
    "com.huawei.photos",
    "com.huawei.gallery",
    "com.hihonor.photos",
    "com.android.gallery3d",
    "com.google.android.apps.photos",
    "com.miui.gallery",
    "com.sec.android.gallery3d",
    "com.coloros.gallery3d",
    "com.oppo.gallery3d",
    "com.oneplus.gallery",
    "com.vivo.gallery",
)

private fun Intent.isResolvable(context: Context): Boolean {
    val pm = context.packageManager
    val flags = if (Build.VERSION.SDK_INT >= 24) {
        PackageManager.MATCH_DEFAULT_ONLY
    } else {
        0
    }
    return resolveActivity(pm) != null ||
        pm.queryIntentActivities(this, flags).isNotEmpty()
}

private fun pickImagesIntent(packageName: String? = null): Intent =
    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
        // 仅用 data URI 限定图片库；再 setType 会在部分华为机型落到文件 UI
        if (packageName != null) setPackage(packageName)
    }

private fun getContentImagesIntent(packageName: String? = null): Intent =
    Intent(Intent.ACTION_GET_CONTENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "image/*"
        if (packageName != null) setPackage(packageName)
    }

private fun resolveGalleryImageIntent(context: Context): Intent {
    for (pkg in GALLERY_PACKAGES) {
        val targeted = pickImagesIntent(pkg)
        if (targeted.isResolvable(context)) return targeted
    }
    val pick = pickImagesIntent()
    if (pick.isResolvable(context)) return pick

    for (pkg in GALLERY_PACKAGES) {
        val targeted = getContentImagesIntent(pkg)
        if (targeted.isResolvable(context)) return targeted
    }
    val getContent = getContentImagesIntent()
    if (getContent.isResolvable(context)) return getContent

    return PickVisualMedia().createIntent(
        context,
        PickVisualMediaRequest(PickVisualMedia.ImageOnly),
    )
}

private fun resolveGalleryImageOrVideoIntent(context: Context): Intent {
    for (pkg in GALLERY_PACKAGES) {
        val targeted = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            setPackage(pkg)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        }
        if (targeted.isResolvable(context)) return targeted
    }

    // 无可靠相册时：先试 GET_CONTENT，再 Photo Picker
    val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
    }
    if (getContent.isResolvable(context)) return getContent

    return PickVisualMedia().createIntent(
        context,
        PickVisualMediaRequest(PickVisualMedia.ImageAndVideo),
    )
}

class PickGalleryImage : ActivityResultContract<Unit, Uri?>() {
    private val fallback = PickVisualMedia()

    override fun createIntent(context: Context, input: Unit): Intent =
        resolveGalleryImageIntent(context)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.data ?: fallback.parseResult(resultCode, intent)
    }
}

class PickGalleryImageOrVideo : ActivityResultContract<Unit, Uri?>() {
    private val fallback = PickVisualMedia()

    override fun createIntent(context: Context, input: Unit): Intent =
        resolveGalleryImageOrVideoIntent(context)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.data ?: fallback.parseResult(resultCode, intent)
    }
}

class PickGalleryImageAndVideoMultiple(
    private val maxItems: Int = 20,
) : ActivityResultContract<Unit, List<Uri>>() {
    private val fallback = PickMultipleVisualMedia(maxItems)

    override fun createIntent(context: Context, input: Unit): Intent {
        for (pkg in GALLERY_PACKAGES) {
            val targeted = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                setPackage(pkg)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, maxItems > 1)
            }
            if (targeted.isResolvable(context)) return targeted
        }

        val pick = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, maxItems > 1)
        }
        if (pick.isResolvable(context)) return pick

        val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, maxItems > 1)
        }
        if (getContent.isResolvable(context)) return getContent

        return fallback.createIntent(
            context,
            PickVisualMediaRequest(PickVisualMedia.ImageAndVideo),
        )
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
