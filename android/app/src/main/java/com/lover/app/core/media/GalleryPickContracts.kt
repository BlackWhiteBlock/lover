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

/**
 * 相册选取（含华为 / 鸿蒙适配）。
 *
 * 鸿蒙上常见坑：
 * - ACTION_PICK + Images URI 再 setType("*/*") / EXTRA_MIME_TYPES → 「暂无可用打开方式」
 * - 强行 setPackage 到不支持该 Intent 的相册包 → 同样无打开方式
 * - ACTION_GET_CONTENT + CATEGORY_OPENABLE → 退化成「文件」选择器
 *
 * 策略：图片优先干净的 ACTION_PICK；图文混选优先系统 Photo Picker，再降级。
 */
private val GALLERY_PACKAGES = listOf(
    "com.huawei.photos",
    "com.huawei.hmos.photos",
    "com.huawei.gallery",
    "com.ohos.photos",
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

private fun isHuaweiOrHarmony(): Boolean {
    val brand = Build.BRAND.orEmpty()
    val manufacturer = Build.MANUFACTURER.orEmpty()
    val product = Build.PRODUCT.orEmpty()
    if (listOf(brand, manufacturer, product).any {
            it.contains("huawei", ignoreCase = true) ||
                it.contains("honor", ignoreCase = true) ||
                it.contains("harmony", ignoreCase = true)
        }
    ) {
        return true
    }
    val props = listOf(
        "ro.build.version.emui",
        "ro.build.version.harmony",
        "hw_sc.build.platform.version",
        "ro.huawei.build.version.security_patch",
    )
    return props.any { key ->
        runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java, String::class.java)
            val value = get.invoke(null, key, "") as? String
            !value.isNullOrBlank()
        }.getOrDefault(false)
    }
}

private fun Intent.isResolvable(context: Context): Boolean {
    val pm = context.packageManager
    val flags = PackageManager.MATCH_DEFAULT_ONLY
    return try {
        resolveActivity(pm) != null || pm.queryIntentActivities(this, flags).isNotEmpty()
    } catch (_: Throwable) {
        false
    }
}

/** 仅图片：干净 ACTION_PICK，不要再 setType / EXTRA_MIME_TYPES。 */
private fun pickImagesOnlyIntent(packageName: String? = null): Intent =
    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
        if (packageName != null) setPackage(packageName)
    }

private fun wrapChooser(intent: Intent, title: String = "选择照片"): Intent =
    Intent.createChooser(intent, title)

private fun resolveGalleryImageIntent(context: Context): Intent {
    // 1) 系统默认图片库 PICK（鸿蒙/华为上最稳，避免文件管理器）
    val systemPick = pickImagesOnlyIntent()
    if (systemPick.isResolvable(context)) {
        return wrapChooser(systemPick)
    }

    // 2) 指定厂商相册包（仍保持干净 Images URI，不改 type）
    for (pkg in GALLERY_PACKAGES) {
        val targeted = pickImagesOnlyIntent(pkg)
        if (targeted.isResolvable(context)) return targeted
    }

    // 3) Photo Picker（比 GET_CONTENT 更接近相册；鸿蒙 6 通常可用）
    if (PickVisualMedia.isPhotoPickerAvailable(context)) {
        return PickVisualMedia().createIntent(
            context,
            PickVisualMediaRequest(PickVisualMedia.ImageOnly),
        )
    }

    // 4) 最后 GET_CONTENT（可能像文件选择，但总比打不开好）
    val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
        type = "image/*"
        addCategory(Intent.CATEGORY_OPENABLE)
    }
    if (getContent.isResolvable(context)) return wrapChooser(getContent, "选择图片")

    return PickVisualMedia().createIntent(
        context,
        PickVisualMediaRequest(PickVisualMedia.ImageOnly),
    )
}

/**
 * 图片或视频。
 * 鸿蒙对「Images URI + */* + MIME_TYPES」支持很差，优先系统 Photo Picker。
 */
private fun resolveGalleryImageOrVideoIntent(context: Context): Intent {
    val huaweiLike = isHuaweiOrHarmony()

    if (huaweiLike || PickVisualMedia.isPhotoPickerAvailable(context)) {
        if (PickVisualMedia.isPhotoPickerAvailable(context)) {
            return PickVisualMedia().createIntent(
                context,
                PickVisualMediaRequest(PickVisualMedia.ImageAndVideo),
            )
        }
    }

    // 非鸿蒙：可尝试 Files 库 PICK
    val filesPick = Intent(
        Intent.ACTION_PICK,
        MediaStore.Files.getContentUri("external"),
    ).apply {
        type = "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
    }
    if (!huaweiLike && filesPick.isResolvable(context)) {
        return wrapChooser(filesPick, "选择照片或视频")
    }

    // 干净图片 PICK（至少能选图；视频可走下面的 GET_CONTENT / Picker）
    val imagePick = pickImagesOnlyIntent()
    if (imagePick.isResolvable(context)) {
        // 若仅能选图，仍给用户可用路径；混选失败时比「无打开方式」好
        if (!huaweiLike) return wrapChooser(imagePick, "选择照片或视频")
    }

    val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
        type = "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        addCategory(Intent.CATEGORY_OPENABLE)
    }
    if (getContent.isResolvable(context)) {
        return wrapChooser(getContent, "选择照片或视频")
    }

    return PickVisualMedia().createIntent(
        context,
        PickVisualMediaRequest(PickVisualMedia.ImageAndVideo),
    )
}

private fun resolveGalleryImageAndVideoMultipleIntent(context: Context, maxItems: Int): Intent {
    val fallback = PickMultipleVisualMedia(maxItems)
    val request = PickVisualMediaRequest(PickVisualMedia.ImageAndVideo)

    // 鸿蒙 / 有系统 Photo Picker：多选混媒体最稳
    if (isHuaweiOrHarmony() || PickVisualMedia.isPhotoPickerAvailable(context)) {
        if (PickVisualMedia.isPhotoPickerAvailable(context)) {
            return fallback.createIntent(context, request)
        }
    }

    val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
        type = "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, maxItems > 1)
        addCategory(Intent.CATEGORY_OPENABLE)
    }
    if (getContent.isResolvable(context)) {
        return wrapChooser(getContent, "选择照片或视频")
    }

    // 单图 PICK + 允许多选（部分机型会忽略多选，但仍可打开相册）
    val imagePick = pickImagesOnlyIntent().apply {
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, maxItems > 1)
    }
    if (imagePick.isResolvable(context)) {
        return wrapChooser(imagePick, "选择照片或视频")
    }

    return fallback.createIntent(context, request)
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

    override fun createIntent(context: Context, input: Unit): Intent =
        resolveGalleryImageAndVideoMultipleIntent(context, maxItems)

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
