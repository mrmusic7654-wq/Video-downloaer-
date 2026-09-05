# vidma ✦ fluid video downloader

A premium, dark-UI **YouTube/1000+ site video & audio downloader** for Android,
built 100% with **Kotlin + Jetpack Compose**. vidma downloads with the real
[yt-dlp](https://github.com/yt-dlp/yt-dlp) engine (bundled inside the APK),
plays media back with ExoPlayer (Media3), and browses the web in a built-in
native browser — all under one lucid, animated "aurora" glass design.

<p>
  <img alt="Downloader tab" src="https://img.shields.io/badge/vidma-1.0.0-7C5CFF" />
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-24-2FD8FF" />
  <img alt="compileSdk" src="https://img.shields.io/badge/compileSdk-35-FF5CB8" />
  <img alt="engine" src="https://img.shields.io/badge/engine-yt--dlp-3DF2A5" />
</p>

---

## ✨ What you get

| | |
|---|---|
| 🏠 **Download tab** | Paste any link (or share it into vidma from any app) → vidma extracts the media with yt-dlp and shows a format studio: **Video/Audio**, quality presets up to 8K, container (MP4/MKV/WebM), source audio formats in the lean build and MP3/M4A/Opus/FLAC/WAV conversion in full FFmpeg builds. |
| 📚 **Library tab** | Searchable grid of everything you saved, with thumbnails, file sizes and dates. Tap to **play in a glass ExoPlayer sheet**, share, open with another app, or delete. |
| 🌐 **Browser tab** | A Chromium-powered Android system WebView with back/forward/refresh, progress bar, Google search fallback and a glowing **"download this page"** button. Long-press and drag the button to place it anywhere; its position is remembered. |
| ⚙️ Settings | 4 switchable **accent themes** that re-skin the whole app & backdrop live, storage policy (public Downloads vs private shelf), engine status, library hygiene. |
| 🎨 Design | Fully procedural **lucid fluid backdrop** — breathing aurora orbs, starfield twinkle, light ribbons, vignette — drawn in Compose at 60fps (no bitmap). Glass cards, gradient CTAs with shimmer, glow progress rings, haptic feedback, custom Sora/Inter typography. |
| 🧠 Engine | Real **yt-dlp + Python** with a lean single-file mode, concurrent downloads (2 slots, queue with cancel/retry), live %/ETA/console line, `.part` resume, thumbnail auto-save and an OkHttp fallback for direct CDN media URLs. Optional FFmpeg builds add merging/audio conversion. |
| 🔒 Storage | Scoped-storage safe on every Android version: files land in `Download/Vidma` via **MediaStore** (no all-files permission), with an in-app private shelf option. |

## 📱 Screens

1. **Home / Capture** — one glass URL field with **Paste**, Resolve → format studio → big gradient **Download** button; the Chromium browser feeds the same queue.
2. **Downloads** — a dedicated live progress centre with aggregate progress, active/queued/finished counters, ETA, retry and cancel actions.
3. **Library** — search, video/audio filters, 2-column grid, in-app player sheet.
4. **Browser** — Chromium-powered Android system WebView + instant download action that can be long-press dragged.
Plus a floating Settings screen and an always-visible **active download tray** above the dock.

## 🏗 Architecture

Single `:app` module with strict multi-layer packages — plain MVVM, manual DI
(`AppContainer` in `VidmaApp`), no annotation processors:

```
app/src/main/java/com/vidma/downloader
├── VidmaApp.kt              App + manual DI container (prefs, storage, coordinator, repo)
├── MainActivity.kt          Edge-to-edge host, share/VIEW intent handling
├── ui
│   ├── theme                Accent presets, palette tokens, Sora/Inter typography, shapes
│   ├── components
│   │   ├── background       LucidBackdrop — animated procedural fluid layer
│   │   ├── core             GlassCard, VidmaButton/Glass/Icon, fields, chips, progress, bits
│   │   └── media            MediaArt, TaskRow, PlayerSheet (Media3)
│   └── navigation           VidmaDock (bottom glass dock), MainScaffold, toast, tab routing
├── features                 Screen features (each with its ViewModel)
│   ├── downloader           Home: URL resolver + format studio + queue
│   ├── downloads             Live progress centre, ETA, retry/cancel actions
│   ├── library              Grid, filters, playback & item actions
│   ├── browser              Chromium WebView VM + chrome + download-this-page
│   └── settings             Accent, storage policy, engine, about
├── domain                   Pure Kotlin
│   ├── model                MediaKind/DownloadState/Task/Item/MediaSummary, format rules
│   └── repository           DownloadRepository interface
├── data
│   ├── engine               YtDlpEngine + OkHttp direct fallback + DownloadCoordinator
│   ├── storage              MediaStorage (staging → MediaStore publishing, covers)
│   ├── store                VidmaPrefs (DataStore: settings + JSON library history)
│   ├── model                Persisted HistoryRecord (@Serializable)
│   └── repository           DownloadRepositoryImpl
└── util                     Formatters, URL helpers
```

**Data flow** — UI → ViewModel (StateFlow) → `DownloadRepository` (domain interface)
→ coordinator/engine (IO coroutines, semaphore 2) → yt-dlp process with progress
callback → staged file → publish via MediaStore → library history in DataStore.

## 🧰 Stack (all pinned in `gradle/libs.versions.toml`)

| Piece | Choice |
|---|---|
| Language / UI | Kotlin 2.1.21 · Jetpack Compose BOM 2025.01.01 · Material3 |
| Engine | `io.github.junkfood02.youtubedl-android:library` **0.18.1** (yt-dlp + Python) · optional `:ffmpeg` via `-Pvidma.withFfmpeg=true` · OkHttp 4.12 direct-media fallback |
| Playback | Media3 / ExoPlayer 1.4.1 |
| Images | Coil 2.7.0 |
| Nav / lifecycle | Navigation-Compose 2.8.5 · lifecycle 2.8.7 |
| Network | OkHttp 4.12 for the direct-media fallback and resilient HTTP streaming |
| Persistence | DataStore + kotlinx.serialization (no Room needed) |
| Build | AGP 8.7.3 · Gradle 8.10.2 · JDK 17 · release builds are minified (R8) with shrunk resources, compressed native libs and single-ABI by default (arm64-v8a) |

## 📦 App size — why the old build was ~800 MB

The engine is the weight: `youtubedl-android` embeds a Python runtime, and
shipping several CPU ABIs or FFmpeg more than once makes the artifact grow
quickly. The previous build packaged 3 ABIs into universal APKs. The optimized
build now does the following:

1. **arm64-v8a only by default** — the native runtime is shipped once instead
   of being triplicated. Other ABIs remain an explicit opt-in.
2. **No FFmpeg in the default APK** — single-file muxed formats and source
   audio work without a second native runtime. FFmpeg is detected at runtime
   when a full build includes it.
3. **Compressed native libraries**, R8 minification, resource shrinking,
   English-only resources and the small Material icon core instead of the
   extended icon bundle.
4. **Android's system Chromium WebView** is used; no browser binary is copied
   into the APK.

This is the practical route toward a **20–30 MB download target** while
retaining an offline yt-dlp resolver. Exact size depends on the published
runtime and signing/build tools, so the CI size report remains authoritative.
A full FFmpeg build is intentionally larger but enables stream merging and
MP3/M4A/etc. conversion.

```bash
# Default lean release (arm64-v8a, direct formats):
./gradlew :app:assembleRelease

# Full feature release with stream merging/audio conversion:
./gradlew :app:assembleRelease -Pvidma.withFfmpeg=true

# Opt-in legacy/fat build when truly needed:
./gradlew :app:assembleRelease \
  -Pvidma.abis=armeabi-v7a,arm64-v8a,x86_64 -Pvidma.universalApk=true
```

## 🚀 Build & run

```bash
# Android Studio (Ladybug+): open the repo, sync, Run ▶
# or from the CLI:
./gradlew :app:assembleDebug          # debug APK (arm64-v8a; better on real devices)
./gradlew :app:assembleRelease        # minified release APK in app/build/outputs/apk

# Developing on an x86_64 emulator? Add your ABI:
./gradlew :app:assembleDebug -Pvidma.abis=x86_64
```

> **First run:** vidma warms the bundled yt-dlp/Python runtime once in the
> Application startup coroutine. The Downloader can accept a paste while that
> work is in progress; the loading card reports the state instead of requiring
> a second tap. Full FFmpeg builds warm their optional converter at the same
> time.
> **Release signing:** optional — create `keystore.properties`
> (`storeFile/storePassword/keyAlias/keyPassword`) in the project root; the
> build picks it up automatically.

Requirements: JDK 17, Android SDK 35. `local.properties` with `sdk.dir` is
auto-generated by Android Studio.

## 🤖 CI — automatic APK builds (GitHub Actions)

`.github/workflows/build-apk.yml` compiles the app on every push/PR:

1. **wrapper-validation** — verifies the checked-in Gradle wrapper jar.
2. **build** — JDK 17 + Android SDK (licenses auto-accepted) → generates a
   throwaway signing keystore → builds **arm64-v8a debug + release APKs** →
   prints a size report → uploads them as the **`vidma-apks`** artifact
   (lean arm64 APKs, with no optional FFmpeg payload).

Triggering the workflow manually offers an *"all ABIs + universal"* option if
a fat build is ever needed for legacy devices.

```bash
# Local equivalent of what CI runs
./gradlew :app:assembleDebug :app:assembleRelease -Pvidma.abis=arm64-v8a
```

## 🧭 Notes & roadmap

- Downloads run in the app process (2 concurrent, queue with cancel/retry,
  `.part` resume) and are visible in the dedicated Downloads progress centre.
  A **foreground-service downloader** is the natural next step for
  background-forever behaviour.
- Site coverage = yt-dlp coverage (YouTube, TikTok, Instagram, Vimeo, Twitter/X,
  Facebook, Twitch, SoundCloud, Bandcamp, 1000+ more). DRM-protected streams
  cannot be downloaded by any tool, including vidma.
- **Only download content you own or have permission to keep.** vidma is an
  unofficial client; the yt-dlp engine, Sora & Inter fonts are used under their
  respective open licenses (see `app/src/main/assets/licenses`).

## 📁 Repository layout

```
├── app/src/main/java/...         all Kotlin sources (architecture above)
├── app/src/main/res/font         Sora + Inter static weights (OFL)
├── gradle/libs.versions.toml     single source of dependency truth
├── gradle/wrapper                Gradle 8.10.2 wrapper
└── app/build.gradle.kts          build + signing configuration
```
