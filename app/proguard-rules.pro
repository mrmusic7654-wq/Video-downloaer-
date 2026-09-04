# vidma release rules
# R8/minification is disabled for now, but keep a few safe defaults:

# yt-dlp-android (jackson mapper reads Kotlin val fields reflectively)
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.youtubedl_common.** { *; }
-keepattributes *Annotation*, Signature
-dontwarn org.slf4j.**

# Kotlinx serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
