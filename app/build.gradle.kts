import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ------------------------------------------------------------------
//  vidma — single :app module with a strict package architecture:
//  ui (theme/components/navigation) · features (downloader/library/
//  browser/settings) · domain (model/usecase/repository) · data
//  (engine/repository/store) · util
// ------------------------------------------------------------------

// ------------------------------------------------------------------
//  APK SIZE STRATEGY
//
//  yt-dlp-android bundles a full python runtime (~40-50 MB) and ffmpeg
//  (~18-25 MB) per ABI. Building all 3 ABIs + a universal APK therefore
//  produced ~200 MB universal APKs (and a ~800 MB CI artifact).
//
//  Defaults target ONE apk for the ABI of real devices (arm64-v8a):
//    ./gradlew :app:assembleRelease                  → arm64-v8a only
//
//  Legacy/fat builds stay one flag away when truly needed:
//    ./gradlew :app:assembleRelease \
//      -Pvidma.abis=armeabi-v7a,arm64-v8a,x86_64 -Pvidma.universalApk=true
// ------------------------------------------------------------------
val vidmaAbis: List<String> = (project.findProperty("vidma.abis") as String?)
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.takeIf { it.isNotEmpty() }
    ?: listOf("arm64-v8a")

val vidmaUniversalApk: Boolean =
    (project.findProperty("vidma.universalApk") as String?)?.toBoolean() == true

android {
    namespace = "com.vidma.downloader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vidma.downloader"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // yt-dlp-android ships native python/ffmpeg per ABI. Only package
        // the ABI(s) we actually ship — this is the single biggest size lever.
        ndk {
            abiFilters += vidmaAbis
        }

        // App strings are English-only; drop translated strings pulled in
        // from material3 / media3 / androidx (few hundred KB of resources).
        resourceConfigurations += setOf("en")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8 full: shrinks unused code (e.g. the thousands of unused
            // extended icon classes) and unused resources.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // Per-ABI APKs keep the bundled yt-dlp runtime lean (Play-style delivery).
    // With the default single ABI this simply produces one APK and no longer
    // emits a giant all-in-one universal APK unless explicitly requested.
    splits {
        abi {
            isEnable = true
            reset()
            include(*vidmaAbis.toTypedArray())
            isUniversalApk = vidmaUniversalApk
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/*.version",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }

    // Optional local keystore for release builds (see README).
    val keystoreProps = Properties()
    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        keystoreProps.load(keystoreFile.inputStream())
    }
    if (keystoreProps.isNotEmpty()) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
        buildTypes {
            getByName("release") {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    // --- AndroidX core ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)

    // --- Compose (BOM) ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // --- Navigation ---
    implementation(libs.androidx.navigation.compose)

    // --- Playback (ExoPlayer / Media3) ---
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // --- Images ---
    implementation(libs.coil.compose)

    // --- yt-dlp engine (bundles python + yt-dlp; ffmpeg enables merge/audio) ---
    implementation(libs.youtubedl.android.library)
    implementation(libs.youtubedl.android.ffmpeg)

    // --- Async / storage / serialization ---
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // --- Tests ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
