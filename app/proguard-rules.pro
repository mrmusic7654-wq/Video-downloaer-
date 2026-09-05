# vidma release rules (R8 full mode, minified + resources shrunk).
#
# Most libraries we ship (media3, okhttp, kotlinx-serialization, compose)
# carry their own consumer ProGuard rules, so we only keep the few things
# R8 cannot see: reflection-driven classes and metadata.

# --- yt-dlp-android -----------------------------------------------------
# youtubedl-android is reached entirely reflectively at runtime:
#   * YoutubeDL is a Kotlin object loaded via getInstance();
#   * its init() pulls the bundled yt-dlp/ Python from
#     R.raw.ytdlp (see res/raw/keep.xml) and the native libs;
#   * jackson-databind maps yt-dlp's JSON into the mapper.* POJOs.
# R8 sees none of these lookups, so the whole library (and every member of
# its data mappers) must be kept untouched, otherwise a minified release
# fails at engine init ("Engine failed") or at metadata resolve.
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.youtubedl_android.mapper.** { *; }
-keep class com.yausername.youtubedl_common.** { *; }
-keep class com.yausername.ffmpeg.** { *; }

# jackson-databind uses reflection to read the yt-dlp JSON into
# VideoInfo/VideoFormat. Keep the data binding machinery + type metadata.
-keep class com.fasterxml.jackson.databind.** { *; }
-keep class com.fasterxml.jackson.annotation.** { *; }
-dontwarn com.fasterxml.jackson.**

# youtubedl-android's init() copies the bundled yt-dlp binary and unpacks the
# Python runtime with commons-io. Those calls are made from kept library code
# but commons-io itself is only reached there, so keep it too.
-keep class org.apache.commons.io.** { *; }
-dontwarn org.apache.commons.io.**

# Keep the library's own R class so reflective resource lookups survive,
# and preserve the generic signatures / annotations jackson relies on.
-keep class com.yausername.youtubedl_android.R$* { *; }

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
