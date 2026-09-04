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
| 🏠 **Download tab** | Paste any link (or share it into vidma from any app) → vidma extracts the media with yt-dlp and shows a format studio: **Video/Audio**, quality presets up to 8K, container (MP4/MKV/WebM), audio formats (MP3/M4A/Opus/FLAC/WAV), sizes & duration. |
| 📚 **Library tab** | Searchable grid of everything you saved, with thumbnails, file sizes and dates. Tap to **play in a glass ExoPlayer sheet**, share, open with another app, or delete. |
| 🌐 **Browser tab** | A native WebView browser with back/forward/refresh, progress bar and a glowing **"download this page"** button that hands the current URL straight to yt-dlp. |
| ⚙️ Settings | 4 switchable **accent themes** that re-skin the whole app & backdrop live, storage policy (public Downloads vs private shelf), engine status, library hygiene. |
| 🎨 Design | Fully procedural **lucid fluid backdrop** — breathing aurora orbs, starfield twinkle, light ribbons, vignette — drawn in Compose at 60fps (no bitmap). Glass cards, gradient CTAs with shimmer, glow progress rings, haptic feedback, custom Sora/Inter typography. |
| 🧠 Engine | Real **yt-dlp + Python + FFmpeg** bundled per-ABI; concurrent downloads (2 slots, queue), live %/ETA/console line, cancel & retry, `.part` resume, thumbnail auto-save, cover art caching. |
| 🔒 Storage | Scoped-storage safe on every Android version: files land in `Download/Vidma` via **MediaStore** (no all-files permission), with an in-app private shelf option. |

## 📱 Screens

1. **Home / Downloader** — hero title, glass URL field with **Paste**, Resolve → format studio → big gradient **Download** button, live queue with progress rings.
2. **Library** — search, video/audio filters, 2-column grid, in-app player sheet.
3. **Browser** — native in-app browser + instant download action.
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
│   │   └── media            MediaArt, TaskRow, TaskQueueSheet, PlayerSheet (Media3)
│   └── navigation           VidmaDock (bottom glass dock), MainScaffold, toast, tab routing
├── features                 Screen features (each with its ViewModel)
│   ├── downloader           Home: URL resolver + format studio + queue
│   ├── library              Grid, filters, playback & item actions
│   ├── browser              WebView VM + chrome + download-this-page
│   └── settings             Accent, storage policy, engine, about
├── domain                   Pure Kotlin
│   ├── model                MediaKind/DownloadState/Task/Item/MediaSummary, format rules
│   └── repository           DownloadRepository interface
├── data
│   ├── engine               YtDlpEngine (library wrapper) + DownloadCoordinator (queue)
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
| Engine | `io.github.junkfood02.youtubedl-android:library` + `:ffmpeg` **0.18.1** (bundles yt-dlp, python & ffmpeg — maintained fork on Maven Central) |
| Playback | Media3 / ExoPlayer 1.4.1 |
| Images | Coil 2.7.0 |
| Nav / lifecycle | Navigation-Compose 2.8.5 · lifecycle 2.8.7 |
| Persistence | DataStore + kotlinx.serialization (no Room needed) |
| Build | AGP 8.7.3 · Gradle 8.10.2 · JDK 17 · per-ABI splits (arm64-v8a, armeabi-v7a, x86_64) |

## 🚀 Build & run

```bash
# Android Studio (Ladybug+): open the repo, sync, Run ▶
# or from the CLI:
./gradlew :app:assembleDebug        # debug APK (all ABIs)
./gradlew :app:assembleRelease      # plus per-ABI APKs in app/build/outputs/apk
```

> **First run:** vidma unpacks the bundled yt-dlp/Python/FFmpeg runtime once —
> the Downloader shows *"Preparing the download engine"* for a few seconds.
> **Release signing:** optional — create `keystore.properties`
> (`storeFile/storePassword/keyAlias/keyPassword`) in the project root; the
> build picks it up automatically.

Requirements: JDK 17, Android SDK 35. `local.properties` with `sdk.dir` is
auto-generated by Android Studio.

## 🤖 CI — automatic APK builds (GitHub Actions)

`.github/workflows/build-apk.yml` compiles the app on every push/PR:

1. **wrapper-validation** — verifies the checked-in Gradle wrapper jar.
2. **build** — JDK 17 + Android SDK (licenses auto-accepted) → generates a
   throwaway signing keystore → `./gradlew :app:assembleDebug :app:assembleRelease`
   → uploads every produced APK (debug + release per-ABI splits + universal)
   as the **`vidma-apks`** artifact.

```bash
# Local equivalent of what CI runs
./gradlew :app:assembleDebug :app:assembleRelease
```

## 🧭 Notes & roadmap

- Downloads run while vidma is open/foregrounded (2 concurrent, queue with
  cancel/retry, `.part` resume). A **foreground-service downloader** is the
  natural next step for background-forever behaviour.
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
