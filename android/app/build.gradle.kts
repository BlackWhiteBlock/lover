plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

import java.io.File
import java.io.FileInputStream
import java.util.Properties

val loverApiBaseUrl = providers.gradleProperty("LOVER_API_BASE_URL")
    .orElse(providers.environmentVariable("LOVER_API_BASE_URL"))
    .orElse("http://10.0.2.2:4000/")

val loverInviteBaseUrl = providers.gradleProperty("LOVER_INVITE_BASE_URL")
    .orElse(providers.environmentVariable("LOVER_INVITE_BASE_URL"))
    .orElse(loverApiBaseUrl)

val inviteBase = loverInviteBaseUrl.get().trimEnd('/')
val inviteHttpsScheme = when {
    inviteBase.startsWith("http://") -> "http"
    else -> "https"
}
val inviteHost = inviteBase
    .removePrefix("https://")
    .removePrefix("http://")
    .substringBefore('/')
    .substringBefore(':')
    .ifBlank { "localhost" }

// Release signing: android/keystore.properties（勿提交）+ lover-release.jks
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
}

android {
    namespace = "com.lover.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lover.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.9.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "API_BASE_URL", "\"${loverApiBaseUrl.get()}\"")
        buildConfigField("String", "INVITE_BASE_URL", "\"$inviteBase/\"")
        manifestPlaceholders["inviteHost"] = inviteHost
        manifestPlaceholders["inviteHttpsScheme"] = inviteHttpsScheme
    }

    signingConfigs {
        create("release") {
            val storePath = keystoreProperties.getProperty("storeFile")
            if (!storePath.isNullOrBlank()) {
                storeFile = rootProject.file(storePath)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            isMinifyEnabled = false
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions.jvmTarget = "21"
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

// Release APK：额外产出 lover_install_release_V{versionName}.apk
// - Gradle：android/app/build/outputs/apk/release/
// - Android Studio「生成已签名 APK」：常再拷到 android/app/release/
fun Project.publishNamedReleaseApk() {
    val version = android.defaultConfig.versionName.orEmpty().ifBlank { "0" }
    val targetName = "lover_install_release_V$version.apk"
    val gradleOut = layout.buildDirectory.dir("outputs/apk/release").get().asFile
    val studioOut = layout.projectDirectory.dir("release").asFile
    val source = sequenceOf(gradleOut, studioOut)
        .filter { it.isDirectory }
        .flatMap { dir -> dir.listFiles()?.asSequence().orEmpty() }
        .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) && it.name != targetName }
        .firstOrNull()
        ?: return
    listOf(gradleOut, studioOut).forEach { dir ->
        dir.mkdirs()
        val target = File(dir, targetName)
        source.copyTo(target, overwrite = true)
        logger.lifecycle("Release APK → ${target.absolutePath}")
    }
}

afterEvaluate {
    tasks.matching {
        val n = it.name
        n == "assembleRelease" ||
            n == "packageRelease" ||
            (n.startsWith("package") && n.contains("Release", ignoreCase = true))
    }.configureEach {
        doLast { publishNamedReleaseApk() }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.03.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.datastore:datastore-preferences:1.1.4")

    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-compiler:2.56.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
    implementation("sh.calvin.reorderable:reorderable:2.5.1")

    // 阿里云号码认证：将控制台下载的 3 个 aar 放入 app/libs（见 libs/README.md）
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    implementation("androidx.appcompat:appcompat:1.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}
