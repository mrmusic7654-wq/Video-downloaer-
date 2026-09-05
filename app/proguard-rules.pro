# vidma release rules (R8 full mode, minified + resources shrunk).
#
# Most libraries we ship (media3, okhttp, kotlinx-serialization, compose)
# carry their own consumer ProGuard rules, so we only keep the few things
# R8 cannot see: reflection-driven classes and metadata.

# --- yt-dlp-android -----------------------------------------------------
# jackson databind maps yt-dlp's VideoInfo/formats reflectively at runtime.
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.youtubedl_common.** { *; }
-keep class com.yausername.ffmpeg.** { *; }

# --- App models touched by kotlinx-serialization -------------------------
# The compiler plugin generates serializers ahead of time, but keep the
# serial names of our persisted records so history restore never breaks.
-keepclassmembers class com.vidma.downloader.data.model.** {
    <init>(...);
}
-keep @kotlinx.serialization.Serializable class com.vidma.downloader.** {
    *** Companion;
}
-keepclasseswithmembers class com.vidma.downloader.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- AndroidX ViewModels (startup crash fix) ------------------------------
# ViewModelProvider's default factory instantiates ViewModels REFLECTIVELY
# (constructor of () / (Application) / (SavedStateHandle) / (Application,
# SavedStateHandle)). R8 sees no direct constructor calls and strips them,
# which kills the app at first composition with InstantiationException /
# NoSuchMethodException — the "opens then force closes" bug.
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

# --- Kotlin / generic metadata -------------------------------------------
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# --- Noisy optional deps we never load -----------------------------------
-dontwarn org.slf4j.**
-dontwarn java.lang.management.ManagementFactory
-dontwarn jdk.internal.misc.Unsafe
