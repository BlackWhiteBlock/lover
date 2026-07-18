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

// Gallery pick (Android APK, including Huawei / HarmonyOS compatibility layer).
//
// IMPORTANT: ohos PhotoViewPicker is HarmonyOS-native only and cannot be called
// from this Android module. On Harmony we must stay on Android intents.
//
// Do NOT use Intent.createChooser / GET_CONTENT / PickVisualMedia-when-unavailable
// on Huawei/Harmony: those paths open the file manager.

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
    val markers = listOf(brand, manufacturer, product)
    if (markers.any { name ->
            name.contains("huawei", ignoreCase = true) ||
                name.contains("honor", ignoreCase = true) ||
                name.contains("harmony", ignoreCase = true)
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
    return props.any { key -> systemPropertyNonBlank(key) }
}

private fun systemPropertyNonBlank(key: String): Boolean {
    return try {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java, String::class.java)
        val value = get.invoke(null, key, "") as? String
        value != null && value.isNotBlank()
    } catch (error: Throwable) {
        false
    }
}

private fun Intent.isResolvable(context: Context): Boolean {
    val pm = context.packageManager
    return try {
        resolveActivity(pm) != null ||
            pm.queryIntentActivities(this, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
    } catch (error: Throwable) {
        false
    }
}

/** Clean image pick. Never setType / EXTRA_MIME_TYPES on Images URI. */
private fun pickImagesOnlyIntent(packageName: String? = null): Intent {
    return Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
        if (packageName != null) setPackage(packageName)
    }
}

private fun pickImagesWithTypeIntent(packageName: String? = null): Intent {
    return Intent(Intent.ACTION_PICK).apply {
        setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
        if (packageName != null) setPackage(packageName)
    }
}

/** Direct gallery intents for Huawei/Harmony — no chooser, no file manager. */
private fun resolveHarmonyImageIntent(context: Context): Intent {
    // Real system photo picker only (when AndroidX reports available).
    if (PickVisualMedia.isPhotoPickerAvailable(context)) {
        return PickVisualMedia().createIntent(
            context,
            PickVisualMediaRequest(PickVisualMedia.ImageOnly),
        )
    }

    for (pkg in GALLERY_PACKAGES) {
        val withType = pickImagesWithTypeIntent(pkg)
        if (withType.isResolvable(context)) return withType
        val clean = pickImagesOnlyIntent(pkg)
        if (clean.isResolvable(context)) return clean
    }

    val systemTyped = pickImagesWithTypeIntent()
    if (systemTyped.isResolvable(context)) return systemTyped

    val systemClean = pickImagesOnlyIntent()
    if (systemClean.isResolvable(context)) return systemClean

    // Last resort still avoid GET_CONTENT: launch Photo Picker contract intent
    // only if somehow resolvable; otherwise clean PICK.
    return systemClean
}

private fun resolveHarmonyImageAndVideoIntent(context: Context, maxItems: Int): Intent {
    if (PickVisualMedia.isPhotoPickerAvailable(context)) {
        return if (maxItems > 1) {
            PickMultipleVisualMedia(maxItems).createIntent(
                context,
                PickVisualMediaRequest(PickVisualMedia.ImageAndVideo),
            )
        } else {
            PickVisualMedia().createIntent(
                context,
                PickVisualMediaRequest(PickVisualMedia.ImageAndVideo),
            )
        }
    }

    // No system photo picker: open gallery for images (still album UI).
    // Mixing video via GET_CONTENT would open file manager on Harmony — avoid it.
    for (pkg in GALLERY_PACKAGES) {
        val imagePick = pickImagesWithTypeIntent(pkg).apply {
            if (maxItems > 1) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        if (imagePick.isResolvable(context)) return imagePick
        val clean = pickImagesOnlyIntent(pkg).apply {
            if (maxItems > 1) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        if (clean.isResolvable(context)) return clean
    }

    return pickImagesWithTypeIntent().apply {
        if (maxItems > 1) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    }
}

private fun resolveStockImageIntent(context: Context): Intent {
    if (PickVisualMedia.isPhotoPickerAvailable(context)) {
        return PickVisualMedia().createIntent(
            context,
            PickVisualMediaRequest(PickVisualMedia.ImageOnly),
        )
    }

    val systemPick = pickImagesOnlyIntent()
    if (systemPick.isResolvable(context)) return systemPick

    for (pkg in GALLERY_PACKAGES) {
        val targeted = pickImagesOnlyIntent(pkg)
        if (targeted.isResolvable(context)) return targeted
    }

    val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
        type = "image/*"
        addCategory(Intent.CATEGORY_OPENABLE)
    }
    if (getContent.isResolvable(context)) {
        return Intent.createChooser(getContent, "选择图片")
    }

    return PickVisualMedia().createIntent(
        context,
        PickVisualMediaRequest(PickVisualMedia.ImageOnly),
    )
}

private fun resolveStockImageOrVideoIntent(context: Context): Intent {
    if (PickVisualMedia.isPhotoPickerAvailable(context)) {
        return PickVisualMedia().createIntent(
            context,
            PickVisualMediaRequest(PickVisualMedia.ImageAndVideo),
        )
    }

    val filesPick = Intent(
        Intent.ACTION_PICK,
        MediaStore.Files.getContentUri("external"),
    ).apply {
        type = "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
    }
    if (filesPick.isResolvable(context)) {
        return Intent.createChooser(filesPick, "选择照片或视频")
    }

    val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
        type = "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        addCategory(Intent.CATEGORY_OPENABLE)
    }
    if (getContent.isResolvable(context)) {
        return Intent.createChooser(getContent, "选择照片或视频")
    }

    return PickVisualMedia().createIntent(
        context,
        PickVisualMediaRequest(PickVisualMedia.ImageAndVideo),
    )
}

private fun resolveStockImageAndVideoMultipleIntent(context: Context, maxItems: Int): Intent {
    val fallback = PickMultipleVisualMedia(maxItems)
    val request = PickVisualMediaRequest(PickVisualMedia.ImageAndVideo)

    if (PickVisualMedia.isPhotoPickerAvailable(context)) {
        return fallback.createIntent(context, request)
    }

    val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
        type = "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, maxItems > 1)
        addCategory(Intent.CATEGORY_OPENABLE)
    }
    if (getContent.isResolvable(context)) {
        return Intent.createChooser(getContent, "选择照片或视频")
    }

    return fallback.createIntent(context, request)
}

private fun resolveGalleryImageIntent(context: Context): Intent {
    return if (isHuaweiOrHarmony()) {
        resolveHarmonyImageIntent(context)
    } else {
        resolveStockImageIntent(context)
    }
}

private fun resolveGalleryImageOrVideoIntent(context: Context): Intent {
    return if (isHuaweiOrHarmony()) {
        resolveHarmonyImageAndVideoIntent(context, maxItems = 1)
    } else {
        resolveStockImageOrVideoIntent(context)
    }
}

private fun resolveGalleryImageAndVideoMultipleIntent(context: Context, maxItems: Int): Intent {
    return if (isHuaweiOrHarmony()) {
        resolveHarmonyImageAndVideoIntent(context, maxItems = maxItems)
    } else {
        resolveStockImageAndVideoMultipleIntent(context, maxItems)
    }
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
