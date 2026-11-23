<div align="center">

# SpotUI Android — v2.5.2 (`202611232`)

### Expressive Jetpack Compose Audio Workstation for Android 8 – 17 & Samsung One UI 9.0

[![GitHub Stars](https://img.shields.io/github/stars/lfisher447-afk/SpotUi-Offical?style=for-the-badge&color=F5B841&logo=github)](https://github.com/lfisher447-afk/SpotUi-Offical/stargazers)
[![GitHub Forks](https://img.shields.io/github/forks/lfisher447-afk/SpotUi-Offical?style=for-the-badge&color=5C8DF6&logo=github)](https://github.com/lfisher447-afk/SpotUi-Offical/network/members)
[![Latest Release](https://img.shields.io/github/v/release/lfisher447-afk/SpotUi-Offical?style=for-the-badge&color=1ED760&label=Release)](https://github.com/lfisher447-afk/SpotUi-Offical/releases/latest)
[![Total Downloads](https://img.shields.io/github/downloads/lfisher447-afk/SpotUi-Offical/total?style=for-the-badge&color=E87A24)](https://github.com/lfisher447-afk/SpotUi-Offical/releases)
[![Android SDK](https://img.shields.io/badge/Android-API%2026%E2%80%9336%20%7C%20Android%2017%20Ready-1ED760?style=for-the-badge&logo=android&logoColor=white)](https://github.com/lfisher447-afk/SpotUi-Offical)
[![Media3](https://img.shields.io/badge/Media3-1.11.0-3B82F6?style=for-the-badge)](https://github.com/lfisher447-afk/SpotUi-Offical)
[![CI Workflow](https://img.shields.io/github/actions/workflow/status/lfisher447-afk/SpotUi-Offical/release-apk.yml?style=for-the-badge&label=CI%20Build)](https://github.com/lfisher447-afk/SpotUi-Offical/actions)

> **Enjoying SpotUI? Give the repository a [Star on GitHub](https://github.com/lfisher447-afk/SpotUi-Offical) — it directly boosts visibility, helps other Android audiophiles discover the project, and fuels faster releases!**

**SpotUI (`com.music.spotui`) is an all-in-one Kotlin & Jetpack Compose Android audio player powered by Media3 `1.11.0`, the Eclipse MrBean HTTP/2 multi-range download engine (`1–10` concurrent songs), a next-generation 3D Binaural Soundstage & 8D Orbit DSP, a native `android.content.pm.PackageInstaller` in-app auto-updater, a Samsung One UI 9.0 Accessibility floating mini-player for full-screen games, 10 configurable `modules/app/libs` submodules, multi-MB/s NovaAc v8 AEAD archives, and deep Bluetooth/component diagnostics.**

[User Guide & Highlights](#-user-guide--key-features) · [What's New in v2.5.2](#-whats-new-in-v252-202611232) · [Complete Library & SDK Inventory](#-complete-library--sdk-inventory) · [App Modules & Submodules](#-modulesapplibs--10-integrated-submodules) · [Architecture](#-project-architecture--module-map) · [Build & CI](#-build--release-workflows) · [Permissions](#-permissions-overview) · [Version History](#-version-history)

</div>

---

## Package Identity & Build Baseline

| Item | Current Value |
| :--- | :--- |
| **Application ID** | `com.music.spotui` (`com.music.spotui.debug` for debug builds) |
| **Version Name** | `2.5.2` |
| **Build Version Code** | `202611232` |
| **SDK Range** | `minSdk 26` (Android 8.0) · `compileSdk / targetSdk 36` · Tested & hardened for **Android 17 (API 37) / One UI 9.0** |
| **Toolchain** | Java `21` · Kotlin `2.2.0` · AGP `8.13.2` · KSP `2.2.0-2.0.2` · Hilt `2.57.1` · Compose BOM `2024.12.01` |
| **Media Engine** | AndroidX Media3 `1.11.0` (`ExoPlayer`, `MediaLibraryService`, `DASH`, `OkHttpDataSource`, `WorkManager`) |
| **Network Engine** | **Eclipse MrBean (`org.eclipse.Mrbean.*`)** HTTP/2 multiplexing + `206` FileChannel multi-range slices (`1–10` concurrent tracks) |
| **Audio DSP Engine** | **3D Spatial Soundstage v2** (Cascaded Schroeder All-Pass + Woodworth HRTF + 8D Binaural Orbit + 7-Band EQ + Soft Limiter) |
| **Auto-Updater** | MrBean Multi-Range APK Transfer + SHA-256 Verification + Native **Android `PackageInstaller` Session API** |
| **Submodule Suite** | All **10** `modules/app/libs` submodules integrated & hot-configurable in **Advanced Settings** |
| **Archive Engine** | **NovaAc v8** Multi-MB/s Single-Pass Streaming (`Fast`: 4 MiB I/O · 2 MiB AEAD · Zero-Deflate) |
| **Diagnostics** | `DevConsoleManager` Live Telemetry · Component/Button Fault Ledger · Bluetooth Codec/Conflict Inspector · 20-Point Health Check |
| **Repository** | [lfisher447-afk/SpotUi-Offical](https://github.com/lfisher447-afk/SpotUi-Offical) |
| **License & Trademarks** | See `LICENSE`. Spotify is a trademark of Spotify AB; users and developers are responsible for respecting provider terms and local law. |

---

## User Guide & Key Features

### Getting Signed In (Spotify, YouTube Music, Deezer & Guest Mode)
1. **Spotify Account Login**:
   - **Android Chrome WebView Login**: Strips `; wv` to present an authentic mobile Chrome Chromium User-Agent and handles OAuth popups plus 1-tap Email Code flows cleanly without external cookie extensions.
   - **HTTP 402 / 400 / 403 Auto-Recover**: Automatically clears/cycles offending cookies, switches UA/login entry hints, caches TOTP Nuance secrets with raw Gist fallback, and bridges verified `#session` / `sp_dc` + `sp_key` credentials directly into `SpotifyAuth`.
   - **Direct `sp_dc` Cookie Paste & Guest Mode**: Paste raw `sp_dc` strings or Cookie-Editor JSON exports directly, hand off to an external browser, or browse anonymously in **Guest Mode**.
2. **YouTube & YouTube Music Library Sync** (`YouTubeMusicSync`):
   - **Zero-Config Signed-In Session Sync**: Sign in once via the built-in `YouTubeMusicWebActivity` to sync playlists, **Liked Music (`LM`)**, and saved albums directly into your Library & Album tabs.
   - **Public / Unlisted Playlist URL Import**: Paste any YouTube or YouTube Music playlist link or ID without Google Cloud OAuth setup.
   - **Official Google OAuth PKCE Sync**: Supports both Android Custom Scheme (`com.music.spotui:/oauth2redirect`) and Loopback HTTP (`http://127.0.0.1:8765/oauth2redirect`).
3. **Lossless Mirrors & Custom Network Routing**:
   - Configure **Lossless Provider Priority** & **Solo Primary Mode** (`SpotiFlac` with 6x parallel DASH-FLAC segment fetching, Deezer ARL Blowfish decryption, custom mirror ping, DoH presets, rotating proxies, and PoToken toggles).

### Playing & Controlling Music
- **Expressive SongPlay UI (`v2.5`) & Legacy UI Toggle**: Switch anytime under **Settings -> Advanced Settings -> SongPlay UI, Live Clock & Tactile Sounds** between the ambient-art **New Expressive UI (`NewPlayerLayout`)** (with live stream/BT badge, `0–100%` In-App Volume & `3D` quick dock, 1-tap Add to Playlist / Love) and **Legacy Classic UI** without interrupting playback.
- **Independent In-App Volume (`0–100%`)**: Adjusts internal ExoPlayer PCM gain smoothly without modifying Android system OS media volume.
- **Home Screen Widgets & Background Mini-Notification**: Add the `2x2` Compact or `4x2` Expanded Glance widgets (`SpotuiCompactWidgetReceiver` / `SpotuiExpandedWidgetReceiver`), connect via **Android Auto**, or enable the broadcast-only Exit Play/Pause Mini-Notification (`ExitPlaybackControlReceiver`) that controls playback without foregrounding the app.
- **Samsung One UI 9.0 / Android 17 Gaming Floating Mini-Player**:
  - Uses a lightweight `MediaAccessibilityService` (`TYPE_ACCESSIBILITY_OVERLAY`) to pop open Play/Pause, Next/Previous, and In-App Volume controls right on top of full-screen landscape games (like *Call of Duty: Mobile*) **without** requiring `SYSTEM_ALERT_WINDOW`.
  - Features a **5-second auto-dismiss timer** (resets on any tap, drag, or volume scrub so you never accidentally tap into the game), separate persisted **Portrait** and **Landscape** drag positions, and direct `MediaController` commands for Android 17 Background Audio Hardening compatibility.
  - **One UI 9.0 Setup**: Unlock **Allow restricted settings** in **Android Settings -> Apps -> SpotUI -> ⋮**, enable **SpotUI Accessibility Mini-Player** under **Accessibility -> Installed apps**, and set **Accessibility button -> Location** to **Floating** (or disable **Block navigation buttons** in One UI Game Booster).

### Downloading & Managing Offline Libraries
- **Turbo Multi-Track Downloads (`1–10` Songs Simultaneously)**: Adjust concurrency (`1` to `10` active downloads), per-track HTTP `206` parallel range workers (`1–6`), and `64–512 KB` I/O buffer size live in **Downloads** or **Settings -> Downloads & MrBean Network**.
- **Public Music Export (`Music/spotui/`)**: Automatically or manually exports downloaded tracks (FLAC, M4A, Opus, WebM, MP3) via zero-permission `MediaStore` into `Music/spotui/` with **Verify & Repair** container sniffing and local offline playback.
- **NovaAc v8 Encrypted Archives**: Export playlists or liked songs to tamper-proof Header-Bound AEAD `.novaac` archives with Fast Mode single-pass 4 MiB streaming (`Deflater.NO_COMPRESSION` for compressed audio).

### In-App Automatic Updates
- Go to **Settings -> Updates -> Check for Updates** (or wait for the startup **UpdatePrompt**).
- Toggle **Auto-download updates in-app** to stream the GitHub Release APK directly inside SpotUI via **MrBean HTTP/2 multi-range slices**, inspect live MB/s + ETA + SHA-256 verification, keep a backup copy in `Downloads/SpotUI/`, and install in-place via Android's native **`PackageInstaller` Session API** (with a one-time *Allow from this source* grant and explicit **System Fallback** option if restricted by an OEM).

---

## What's New in v2.5.2 (`202611232`)

1. **Eclipse MrBean Turbo Network Engine (`1–10` Concurrent Song Downloads)**:
   - Shared HTTP/2 warm `ConnectionPool` (`24` sockets / `5m` keep-alive) and raised `Dispatcher` (`maxRequests = 96`, `maxRequestsPerHost = 24`) with a **5-minute TTL DNS cache**.
   - Adaptive parallel HTTP `206` byte-range slicing (`1–6` concurrent workers writing atomically via `FileChannel`), async artwork + lyrics prefetch, live `throughputBytesPerSec` MB/s telemetry, hot-scalable **1–10 concurrent downloads**, and **6x parallel DASH-FLAC segment fetching** in SpotiFlac.
   - Unified HTTP/2 & Ktor connection pooling across every module and utility in the app (`SpotifyAuth`, `SpotifyCanvas`, `InnerTubeHttp`, `UpdateChecker`, `ArtworkHelper`, `DeepLinkHandler`, `LyricsApi`, `AudioFeaturesClient`, `YouTubeMusicSync`, `BetterLyrics`, `PaxsenixLyrics`, `LastFM`, `LrcLib`, `SimpMusicLyrics`, `KuGou`, and `Shazam`).
2. **Next-Gen 3D Spatial Soundstage v2 & 8D Binaural Orbit (`SpatialWideningAudioProcessor`)**:
   - **Sub-120 Hz Mono-Bass Anchor** + vocal center integrity + real-time stereo **Correlation Guard**.
   - **3-Stage Cascaded Schroeder All-Pass Decorrelator Tank** with coprime prime delay lines per channel (flat magnitude response — zero metallic comb-filtering).
   - **Woodworth Spherical-Head HRTF + Outer-Ear Pinna Filter** (fractional-sample ITD, biquad ILD head-shadow, `~3.4 kHz` concha peak, `~7.8 kHz` elevation notch).
   - **8D Binaural Orbit LFO (`0.10–1.50 Hz`)** for smooth 360° constant-power azimuth rotation and Android 13–17 `AudioManager.spatializer` telemetry.
3. **Native Android `PackageInstaller` Session API Auto-Updater**:
   - Upgraded `UpdateDownloader` + new `UpdateInstallReceiver` to stream verified release APKs straight into `PackageManager.packageInstaller` sessions (`SessionParams.MODE_FULL_INSTALL`, API 31/33/34 flags, live `SessionCallback` percentages, `STATUS_PENDING_USER_ACTION` handling, and FileProvider fallback).
4. **One UI 9.0 / Android 17 Accessibility Floating Mini-Player Hardening**:
   - `isAccessibilityTool="true"`, `canRetrieveWindowContent="false"`, `MediaController`-first transport actions, 5-second inactivity auto-dismiss, and per-orientation saved `(x, y)` coordinates.

---

## Complete Library & SDK Inventory

All versions below are defined directly in `gradle/libs.versions.toml` and `app/build.gradle.kts`:

### Core Language, Build & Concurrency
| Library / Tool | Version | Purpose |
| :--- | :--- | :--- |
| **Kotlin (`android`, `jvm`, `compose`, `serialization`)** | `2.2.0` | Primary language, Compose compiler plugin, `@Serializable` navigation & API models |
| **Android Gradle Plugin (AGP)** | `8.13.2` | Multi-module Android build toolchain (`compileSdk 36`, `targetSdk 36`, `minSdk 26`) |
| **KSP (`com.google.devtools.ksp`)** | `2.2.0-2.0.2` | Annotation processing for Hilt & Room |
| **Kotlinx Coroutines (`android`, `guava`, `play-services`)** | `1.11.0` | Structured async concurrency, Media3 `ListenableFuture` bridging & Task interop |
| **Kotlinx Serialization JSON** | `1.7.3` | Zero-reflection JSON parsing for Spotify, InnerTube, Lyrics & navigation routes |
| **Core Library Desugaring (`desugar_jdk_libs_nio`)** | `2.1.5` | Java `java.time` & NIO APIs backported cleanly to Android 8.0+ (API 26+) |
| **Concurrent Futures KTX (`androidx.concurrent`)** | `1.2.0` | Clean suspend-bridging for callback & executor-based Android APIs |

### UI, Design, Widgets & WebView
| Library / Artifact | Version | Purpose |
| :--- | :--- | :--- |
| **Jetpack Compose BOM (`androidx.compose:compose-bom`)** | `2024.12.01` | Material Design 3 (`material3`), UI (`ui`, `graphics`, `tooling-preview`), Runtime LiveData, & Material Icons Extended |
| **AndroidX Core KTX (`androidx.core:core-ktx`)** | `1.15.0` | WindowInsets, `ContextCompat`, `IntentCompat`, FileProvider & MediaStore helpers |
| **AndroidX Lifecycle (`lifecycle-runtime-ktx`)** | `2.8.7` | Lifecycle-aware `StateFlow` collection (`collectAsStateWithLifecycle`) |
| **AndroidX Activity Compose (`activity-compose`)** | `1.9.3` | `enableEdgeToEdge()`, `BackHandler`, & `rememberLauncherForActivityResult` |
| **Navigation Compose (`navigation-compose`)** | `2.8.5` | Adaptive single-activity navigation across Home, Search, Library, Downloads, Player, Settings & DevConsole |
| **AndroidX WebKit (`androidx.webkit:webkit`)** | `1.13.0` | Safe WebView auth flows for Spotify & YouTube Music with error/cookie diagnostics |
| **Jetpack Glance (`glance-appwidget`, `glance-material3`)** | `1.1.1` | Material 3 `2x2` Compact and `4x2` Expanded Home Screen music widgets |
| **Glide (`com.github.bumptech.glide:glide` & `:compose`)** | `4.16.0` / `1.0.0-beta01` | High-speed artwork loading, caching, and zoomable release markdown images |
| **AndroidX Core SplashScreen** | `1.0.1` | Seamless cold-start splash transition |
| **AndroidX Palette KTX** | `1.0.0` | Dynamic color extraction from album art into Theme Studio & Player halos |

### Dependency Injection, Persistence & Background Scheduling
| Library / Artifact | Version | Purpose |
| :--- | :--- | :--- |
| **Dagger Hilt (`hilt-android`, `hilt-android-compiler`)** | `2.57.1` | Application & ViewModel dependency injection |
| **Hilt Navigation & Work (`hilt-navigation-compose`, `hilt-work`)** | `1.2.0` | Navigation-scoped ViewModels & custom `HiltWorkerFactory` initialization |
| **AndroidX Room (`room-runtime`, `room-ktx`, `room-compiler`)** | `2.7.1` | Offline tracks, playlists, listening history, cached metadata & search history SQLite database |
| **AndroidX WorkManager (`work-runtime-ktx`)** | `2.11.2` | Scheduled periodic YouTube Music library sync & resilient background workers |

### Media3 Audio & Playback Stack
| Library / Artifact | Version | Purpose |
| :--- | :--- | :--- |
| **Media3 ExoPlayer (`media3-exoplayer`)** | `1.11.0` | Core audio playback pipeline hosting SpotUI's custom `AudioProcessor` DSP chain |
| **Media3 DASH (`media3-exoplayer-dash`)** | `1.11.0` | Adaptive DASH audio streaming & FLAC segment handling |
| **Media3 Session (`media3-session`)** | `1.11.0` | `MediaLibraryService`, Lockscreen / Samsung Now Bar media session & Android Auto browse tree |
| **Media3 UI (`media3-ui`)** | `1.11.0` | `PlayerView` surface for looping Spotify Canvas videos |
| **Media3 OkHttp DataSource (`media3-datasource-okhttp`)** | `1.11.0` | Pooled HTTP/2 ExoPlayer transport paired with `MrbeanMediaDataSource` |
| **Media3 WorkManager (`media3-exoplayer-workmanager`)** | `1.11.0` | Background Media3 download/prefetch integration |

### Networking, Extraction, Compression & Logging
| Library / Artifact | Version | Purpose |
| :--- | :--- | :--- |
| **OkHttp (`okhttp` & `okhttp-dnsoverhttps`)** | `5.0.0-alpha.12` | Pooled HTTP/2 connections, multi-range APK/track downloads & DNS-over-HTTPS (DoH) |
| **Ktor Client (`core`, `cio`, `okhttp`, `content-negotiation`, `encoding`, `serialization-json`)** | `3.5.1` | Async multi-platform HTTP clients for InnerTube, Spotify Canvas, LRCLIB, SimpMusic, KuGou & ShazamKit |
| **NewPipeExtractor (`TeamNewPipe/NewPipeExtractor`)** | `v0.26.3` | Signature deobfuscation & fallback YouTube stream extraction |
| **Brotli (`org.brotli:dec`)** | `0.1.2` | Brotli response decompression in network modules |
| **Timber (`com.jakewharton.timber:timber`)** | `5.0.1` | Structured logging integrated into `DevConsoleManager` |
| **Eclipse MrBean (`org.eclipse.Mrbean.*`)** | Embedded | High-throughput HTTP/2 `ConnectionPool`, TTL DNS cache, `FileChannel` range slicer & NovaAc v8 stream controller |

### On-Device Machine Learning & Testing Stack
| Library / Artifact | Version | Purpose |
| :--- | :--- | :--- |
| **Google ML Kit Translate (`com.google.mlkit:translate`)** | `17.0.3` | Offline, API-key-free on-device lyric translation after language pack download |
| **Google ML Kit Language ID (`com.google.mlkit:language-id`)** | `17.0.6` | Automatic lyric language detection for smart translation prompts |
| **JUnit / Robolectric / AndroidX Test / Espresso** | `4.13.2` / `4.14.1` / `1.6.1` / `3.6.1` | Local JVM unit, Robolectric & Compose UI verification suite |

---

## `modules/app/libs` — 10 Integrated Submodules

| Module | Gradle Path | Role in SpotUI | Configurable In Advanced Settings |
| :--- | :--- | :--- | :--- |
| **Spotify** | `:spotify` (`/spotify` & `modules/app/libs/spotify`) | GraphQL home/search/playlists, Canvas MP4 videos, OAuth/WebView token exchange & SpotiFlac 6x DASH-FLAC lossless mirrors (HTTP/2 OkHttp pool) | Enable toggle + Lossless Provider priority |
| **InnerTube** | `:innertube` (`/innertube` & `modules/app/libs/innertube`) | YouTube & YT Music stream resolution, PoToken, DoH, rotating proxies & native library sync (HTTP/2 Ktor OkHttp pool) | Enable toggle + DoH / Proxy / PoToken controls |
| **LRCLIB** | `:lrclib` (`modules/app/libs/lrclib`) | Keyless millisecond-synced LRC database with relaxed duration matching (Ktor CIO `maxConnectionsCount = 64`) | Enable toggle + waterfall priority `#1–#5` |
| **BetterLyrics** | `:betterlyrics` (`modules/app/libs/betterlyrics`) | TTML XML parser & word-by-word synced karaoke timing (OkHttp HTTP/2 pool) | Enable toggle + waterfall priority `#1–#5` |
| **SimpMusic** | `:simpmusic` (`modules/app/libs/simpmusic`) | YouTube Video-ID aligned community synced lyrics API (Ktor CIO `maxConnectionsCount = 48`) | Enable toggle + waterfall priority `#1–#5` |
| **Paxsenix** | `:paxsenix` (`modules/app/libs/paxsenix`) | Multi-source lyrics relay (Apple Music, Musixmatch, NetEase, Spotify) (OkHttp HTTP/2 pool) | Enable toggle + Multi-endpoint vs AMP-only + priority |
| **KuGou** | `:kugou` (`modules/app/libs/kugou`) | High-coverage CJK & international LRC lyrics provider (Ktor `HttpTimeout` + pooled HTTP) | Enable toggle + Traditional Chinese toggle + priority |
| **Last.fm** | `:lastfm` (`modules/app/libs/lastfm`) | Live `updateNowPlaying`, auto-scrobbling & track love sync (OkHttp HTTP/2 pool) | Enable toggle, Mobile session sign-in, Scrobble threshold slider |
| **ShazamKit** | `:shazamkit` (`modules/app/libs/shazamkit`) | Acoustic signature recognition queue, backoff & rate-limited cache (Ktor CIO `maxConnectionsCount = 32`) | Enable toggle, Queue telemetry, Clear Cache action |
| **Fastlane** | `modules/app/libs/fastlane` | Release notes, changelog metadata & release track channel definitions | Production / Beta / Nightly track selector |

---

## Project Architecture & Module Map

```text
SpotUi-Offical/
├── app/                                      # Main Android application module (:app)
│   ├── src/main/java/com/music/spotui/
│   │   ├── accessibility/                    # MediaAccessibilityService (One UI 9 TYPE_ACCESSIBILITY_OVERLAY pill),
│   │   │                                     # AccessibilityHelper (Android 17 & Game Booster setup guide)
│   │   ├── audio/                            # SpatialWideningAudioProcessor (v2 HRTF/Schroeder/8D),
│   │   │                                     # SpotuiDspAudioProcessor, CrossfadeFilterAudioProcessor,
│   │   │                                     # BiquadFilter, AudioEffectController, BluetoothAudioInspector, UiSoundEngine
│   │   ├── data/
│   │   │   ├── api/                          # Api, ModuleLibsBridge (all 10 modules), LyricsApi (HTTP/2 pool),
│   │   │   │                                 # SpotifySession, SpotifySync, DeepLinkHandler (HTTP/2 pool)
│   │   │   ├── export/                       # NovaAcExportManager (single-pass 4 MiB / 2 MiB AEAD streaming)
│   │   │   ├── preferences/                  # SettingsPref, ModuleAndExperiencePref, DownloadPref (Music/spotui),
│   │   │   │                                 # OfflineCollectionsPref
│   │   │   ├── update/                       # UpdateChecker (HTTP/2 GitHub release + .sha256 metadata),
│   │   │   │                                 # UpdateDownloader (MrBean multi-range + PackageInstaller Session API),
│   │   │   │                                 # UpdateInstallReceiver (BroadcastReceiver for session commit callbacks)
│   │   │   └── youtube/                      # YouTubeMusicSync (Playlists / Liked Music / Albums + OAuth PKCE via HTTP/2)
│   │   ├── deezer/                           # Direct Deezer ARL Blowfish lossless resolver & HTTP/2 client
│   │   ├── di/                               # Hilt DI modules & SongPlayer / SpotifyWebPlayer orchestration
│   │   ├── engine/                           # PlaybackController, MediaNotificationAdapter, AudioFeaturesClient (HTTP/2)
│   │   ├── ui/
│   │   │   ├── components/                   # PatchNotesDialog, UpdatePrompt, LiveLocationClockCard, SavedInSheet
│   │   │   ├── navigation/                   # AdaptiveNavigation (BottomBar / Rail / TopTabs / Sidebar), Routes, MyNavHost
│   │   │   ├── notification/                 # PlaybackService (MediaLibraryService), ExitPlaybackControlReceiver
│   │   │   ├── screens/                      # Home, Search, Library, Downloads, Player (+ NewPlayerLayout),
│   │   │   │                                 # Settings, UpdateRoadmapScreen, DevConsole
│   │   │   └── theme/                        # Material 3 expressive Color, Typography (system SansSerif), ThemePreferences
│   │   ├── util/                             # DevConsoleManager, SystemHealthChecker (20 checks), ArtworkHelper, BackupHelper
│   │   └── widget/                           # Glance 2x2 & 4x2 Home Screen Widgets + WidgetKeyRelay
│   └── src/main/java/org/eclipse/Mrbean/     # Embedded Eclipse MrBean HTTP/2, WebSocket, Multi-Range Download & NovaAc Engine
│       └── client/                           # MrbeanDownloadClient (206 slicer), MrbeanMediaDataSource,
│                                             # MrbeanNetworkSettings (1–10 concurrency & 64–512 KB buffer), MrbeanNovaAcController
├── spotify/ & innertube/                     # Core root Gradle modules (:spotify, :innertube)
├── modules/app/libs/                         # Extended submodule suite (betterlyrics, kugou, lastfm, lrclib,
│                                             # paxsenix, shazamkit, simpmusic, fastlane, spotify, innertube)
├── tools/                                    # build-release-apk.sh & archive verification utilities
└── .github/workflows/                        # release-apk.yml (CI APK build + apksigner + SHA-256 + GitHub Release)
```

---

## Build & Release Workflows

### Option A — Automated GitHub Actions (`.github/workflows/release-apk.yml`)
1. Push a commit to `main`/`master`, push a tag matching `v*` (e.g., `git tag v2.5.2 && git push origin v2.5.2`), or manually trigger **Actions -> Build & Publish Release APK -> Run workflow**.
2. **Default Fast Builds (`skip_tests: true` by default)**: Tag/branch builds and default manual dispatches skip unit tests so release APKs build quickly and reliably. To run `:spotify:test`, `:innertube:testDebugUnitTest`, and `:app:testDebugUnitTest`, trigger **Run workflow** manually and **uncheck** `Skip unit tests`.
3. CI builds `:app:assembleRelease`, verifies signatures with `apksigner`, generates a `.sha256` checksum, uploads build artifacts, and (on `v*` tags or `workflow_dispatch`) publishes **`SpotUI-v2.5.2-release.apk`** + **`.sha256`** to **GitHub Releases**.

### Option B — Local Release Script (`tools/build-release-apk.sh`) or Gradle
Requires **JDK 21** and **Android SDK Platform 36**:

```bash
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-21
bash tools/build-release-apk.sh
```

Or compile directly with Gradle:

```bash
gradle :app:assembleRelease
```

Outputs `dist/SpotUI-v2.5.2-release.apk` and `dist/SpotUI-v2.5.2-release.apk.sha256`.

> **Signing Notice:** The checked-in `sharedDebug` signing config enables seamless in-place sideload & `PackageInstaller` upgrades across builds (`app/build.gradle.kts`). Always substitute a private release keystore before public store distribution, and never commit account cookies, ARLs, OAuth secrets, or private keys.

---

## Permissions Overview

SpotUI adheres to least-privilege and Google Play Creator Program compliant Android permission practices:
- **Network & Background Playback**: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- **Audio, Bluetooth & Tactile Feedback**: `MODIFY_AUDIO_SETTINGS`, `BLUETOOTH` (`maxSdkVersion 30`), `BLUETOOTH_CONNECT`, `VIBRATE`.
- **Local Audio & Public Downloads**: `READ_MEDIA_AUDIO` (API 33+) / `READ_EXTERNAL_STORAGE` (`maxSdkVersion 32`) for scanning local audio playback; `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion 28`) for pre-Scoped-Storage `Music/spotui/` export (Android 10+ uses zero-permission `MediaStore`).
- **In-App PackageInstaller Updates**: `REQUEST_INSTALL_PACKAGES` (used exclusively when the user confirms downloading & installing an updated GitHub Release APK in-app).
- **Optional Floating Mini-Player Service**: `BIND_ACCESSIBILITY_SERVICE` on `MediaAccessibilityService` with `canRetrieveWindowContent="false"` and `isAccessibilityTool="true"` — reads **zero** screen content and renders only the user-toggled `TYPE_ACCESSIBILITY_OVERLAY` playback pill.

---

## Version History

| Version | Code | Highlights |
| :--- | :--- | :--- |
| **v2.5.2** | `202611232` | **MrBean Turbo 1–10 Multi-Range Downloader, 3D Spatial Soundstage v2 & Native PackageInstaller Updater**: Rebuilt Eclipse MrBean networking with HTTP/2 multiplexing (`ConnectionPool` 24 / `Dispatcher` 96), 5-min TTL DNS cache, `1–6` parallel HTTP `206` `FileChannel` byte-range slices, `64–512 KB` buffer selection, async cover/lyrics prefetch, live MB/s telemetry, hot-scalable `1–10` concurrent song downloads, and 6x parallel SpotiFlac DASH-FLAC segments; unified HTTP/2 & CIO endpoint pooling across all submodules and app HTTP helpers; upgraded the in-app auto-updater to MrBean multi-range transfers + Android's native `PackageInstaller` Session API (`UpdateInstallReceiver` + progress/status callbacks); rebuilt 3D Spatial Audio with cascaded Schroeder all-pass decorrelation, sub-120 Hz mono-bass anchor, Woodworth spherical-head HRTF + Pinna elevation filter, 8D Binaural Orbit LFO, Correlation Guard & Android 13–17 Spatializer telemetry; hardened `MediaAccessibilityService` for Android 17 & One UI 9.0. |
| **v2.5.1** | `202611221` | **In-App Auto-Update Downloader, Spotify HTTP 402/400 Hardening & BT SCO/A2DP Fix**: Initial direct in-app release downloader + SHA-256 verification, dual-profile Bluetooth A2DP vs HFP/SCO fix, clean Spotify WebView login URL & HTTP 402/400 Auto-Recover, TOTP caching + raw Gist mirror fallback, and `#session` token seeding. |
| **v2.5.0** | `202611220` | **Expressive Suite, Multi-MB/s NovaAc & 10-Module Integration**: Added `NewPlayerLayout` (`v2.5`) + Legacy UI toggle; procedural PCM Tactile UI Sound Engine; first-login & post-update Patch Notes modal; auto-location Live Home Clock card; all 10 `modules/app/libs` submodules wired in Advanced Settings; DevConsole `BT & Faults` + 20-point Health check; Search screen quick Add-to-Playlist fix; NovaAc Fast Mode 4 MiB / 2 MiB AEAD zero-deflate multi-MB/s overhaul. |
| **v2.4.0** | `202611210` | **Pro Audio DSP, Non-OS In-App Volume & Native YouTube Sync**: Fixed offline downloaded playback (`SimpleCache` bypass + CBR seek + Verify & Repair), independent `0–100%` In-App Volume slider, 16/24/32-bit/Float PCM Spatial + EQ processors, broadcast-only Exit Mini-Notification, and paginated native YouTube Music playlists/Liked Music/albums sync. |
| **v2.3.0** | `202611180` | **Thanksgiving Harvest Update**: Synchronous foreground service promotion, Bluetooth-safe in-process 3D Spatial/EQ routing, fixed `Music/spotui` MediaStore export & auto-export, Thanksgiving Countdown Banner & adaptive/monochrome icons. |
| **v2.2.0** | `202610108` | **Playback Resurrection & 18-Point Health Scanner**: Fixed Samsung One UI Now Bar desync, rebuilt 7-band Biquad DSP, Lossless Provider Priority + Solo Primary, Deezer ARL Blowfish decryption, Search filters, A-B Loop, Sleep Timer & 18-point Health Scanner. |
| **v2.1.7** | `202610107` | **Auth & Network Controls**: Harvest Gold preset, `PlaybackAuthState` & `AuthSessionStore`, DoH/proxy/PoToken controls, Speed/Pitch/Skip Silence tuning, manual update checker & `CrashReportPrompt`. |
| **v2.1.6** | `202610106` | Dual-mode Chrome-UA Spotify WebView login, 1-tap `sp_dc` cookie login, external browser handoff, guest mode & WebView recovery. |
| **v2.1.5** | `202610105` | NovaAc v8 Header-Bound AEAD encryption, `MrbeanNovaAcController` v8, LRCLIB millisecond lyric sync & M3 corner scaling. |
| **v2.1.0** | `202610100` | NovaAc v6 64 KiB framed AEAD engine & MrBean transport telemetry. |
| **v2.0.0** | `202609200` | Upgraded Media3 to `1.11.0`, unified MrBean Media3 transport, Google OAuth YouTube playlist sync, Theme Studio & NovaAc v5 container integrity. |
| **v1.5.1–1.9.9** | — | Timed LRC parser, sequential playlist downloads, 5-band EQ, software spatializer, collapsible settings, persistent `AppDiagnostics`, Android Auto & full-audio NovaAc. |

---

<div align="center">

## Support SpotUI — Star History

[![Star History Chart](https://api.star-history.com/svg?repos=lfisher447-afk/SpotUi-Offical&type=Date)](https://star-history.com/#lfisher447-afk/SpotUi-Offical&Date)

**If SpotUI helped your Android listening setup, please click the Star button at the top right of [lfisher447-afk/SpotUi-Offical](https://github.com/lfisher447-afk/SpotUi-Offical)!**

</div>
