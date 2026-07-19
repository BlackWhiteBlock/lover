# Retrofit interfaces and Kotlin serialization are retained by generated serializers.
-keepattributes Signature, InnerClasses, EnclosingMethod

# 阿里云号码认证 SDK（授权页依赖 AppCompat）
-keep class androidx.appcompat.app.AppCompatActivity
-keep class androidx.core.content.ContextCompat { *; }
-keep class com.mobile.auth.** { *; }
-dontwarn com.mobile.auth.**
