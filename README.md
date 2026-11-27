<div align="center">

# spotUI Android — v2.5.7 (`202611270`)

### Expressive Jetpack Compose Spotify, YouTube Music & Lossless Audio Player — Android 8–17 & One UI 9.0 Ready

[![GitHub Stars](https://img.shields.io/github/stars/lfisher447-afk/SpotUi-Offical?style=for-the-badge&color=F5B841&logo=github)](https://github.com/lfisher447-afk/SpotUi-Offical/stargazers)
[![GitHub Forks](https://img.shields.io/github/forks/lfisher447-afk/SpotUi-Offical?style=for-the-badge&color=5C8DF6&logo=github)](https://github.com/lfisher447-afk/SpotUi-Offical/network/members)
[![Latest Release](https://img.shields.io/github/v/release/lfisher447-afk/SpotUi-Offical?style=for-the-badge&color=1ED760&label=Release)](https://github.com/lfisher447-afk/SpotUi-Offical/releases/latest)
[![Total Downloads](https://img.shields.io/github/downloads/lfisher447-afk/SpotUi-Offical/total?style=for-the-badge&color=E87A24)](https://github.com/lfisher447-afk/SpotUi-Offical/releases)
[![Android SDK](https://img.shields.io/badge/Android-API%2026%E2%80%9336%20%7C%20Android%2017%20Ready-1ED760?style=for-the-badge&logo=android&logoColor=white)](https://github.com/lfisher447-afk/SpotUi-Offical)
[![Media3](https://img.shields.io/badge/Media3-1.11.0-3B82F6?style=for-the-badge)](https://github.com/lfisher447-afk/SpotUi-Offical)
[![CI Workflow](https://img.shields.io/github/actions/workflow/status/lfisher447-afk/SpotUi-Offical/release-apk.yml?style=for-the-badge&label=CI%20Build)](https://github.com/lfisher447-afk/SpotUi-Offical/actions)

> **Enjoying SpotUI? Give the repository a [Star on GitHub](https://github.com/lfisher447-afk/SpotUi-Offical) — it directly boosts visibility, helps other Android audiophiles discover the project, and fuels faster releases!**

**SpotUI (`com.music.spotui`) is a full-stack Kotlin & Jetpack Compose Android audio platform powered by Media3 `1.11.0`, a Multi-Tab Auth Browser Engine with persistent Bookmarks + Local URL Login Portal, Eclipse MrBean Turbo 8x HTTP/2 multi-range downloads (`1–10` concurrent tracks), embedded APK-native `yt-dlp` (`0.18.1`) + FFmpeg extraction, Stationary Advanced 8D Binaural Audio with `AudioOffloadMonitor` safety guards, Harmonic & Dynamics processing, a Live Internal Game Audio Voice Translator (Vosk `0.3.75` offline ASR + ML Kit + 7 configurable engines) inside a click-through Accessibility Floater Studio, NovaAc v8 AEAD archives, and 17 modular audio/lyrics/recognition libraries.**

[User Guide & Highlights](#user-guide--key-features) · [What's New in v2.5.7](#whats-new-in-v257-202611270) · [Complete Library & SDK Inventory](#complete-library--sdk-inventory) · [17 Integrated Modules](#17-integrated-modules--submodules) · [Architecture](#project-architecture--module-map) · [Build & CI](#build--release-workflows) · [Permissions](#permissions-overview) · [Version History](#version-history)

</div>

---

## Package Identity & Build Baseline

| Item | Current Value |
| :--- | :--- |
| **Application ID** | `com.music.spotui` (`com.music.spotui.debug` for debug builds) |
| **Version Name** | `2.5.7` |
| **Build Version Code** | `202611270` |
| **SDK Range** | `minSdk 26` (Android 8.0) · `compileSdk / targetSdk 36` (`buildToolsVersion 36.0.0`) · Hardened for **Android 17 (API 37) / One UI 9.0** |
| **Toolchain** | Java `21` · Kotlin `2.2.0` · AGP `8.13.2` · KSP `2.2.0-2.0.2` · Hilt `2.57.1` · Compose BOM `2025.05.01` · Material 3 `1.4.0` |
| **Media Engine** | AndroidX Media3 `1.11.0` (`ExoPlayer`, `MediaLibraryService`, `DASH`, `OkHttpDataSource`, `WorkManager`) |
| **Stream Extraction** | Dual-path **InnerTube** (`MWEB`/`ANDROID_MUSIC`/90s circuit breaker + `NewPipeExtractor v0.26.3`) + **Embedded `youtubedl-android 0.18.1` + FFmpeg** (`YtDlpEngine`) + **SpotiFlac** magic-byte verification |
| **Live Game Translator** | **Zero-Mic-Lock `AudioPlaybackCaptureConfiguration`** (`MediaProjection`) + **Vosk `0.3.75`** offline ASR + **ML Kit Language ID `17.0.6`** + **7 Translation Engines** (`ML Kit`, `Google GTX`, `Lingva`, `MyMemory`, `LibreTranslate`, `Hybrid Auto`, `Lexicon`) |
| **Network Engine** | **Eclipse MrBean Turbo 8x (`org.eclipse.Mrbean.*`)** (`32`-socket HTTP/2 `ConnectionPool` / `128` dispatcher, up to `8` `206` `FileChannel` slices, `64 KiB – 1 MiB` I/O buffers, `1–10` concurrent tracks) |
| **Audio DSP Engine** | **Stationary Advanced 8D Soundstage** (zero orbit, Woodworth HRTF + Pinna filter + Schroeder Tank, `SpatialSoundstagePresets`) + **Harmonic & Dynamics Processor** + **7-Band EQ & Custom JSON Slots** + **`AudioOffloadMonitor`** |
| **Auto-Updater** | MrBean Multi-Range APK Transfer + SHA-256 Verification + Native **Android `PackageInstaller` Session API** (`UpdateInstallReceiver`) |
| **Module Suite** | **17** integrated SpotUI modules (**10** `modules/app/libs` submodules + **7** core engine/UI modules) hot-configurable in **Settings -> Modules** |
| **Archive Engine** | **NovaAc v8** Multi-MB/s Single-Pass Streaming (`Fast`: 4 MiB I/O · 2 MiB AEAD · Zero-Deflate) |
| **Diagnostics** | `DevConsoleManager` 8-Channel Telemetry (`2,000`-entry ring buffer) · `PlaybackDiagnosticsEngine` · Bluetooth Codec/Conflict Inspector · **21-Point** Health Scanner |
| **Repository** | [lfisher447-afk/SpotUi-Offical](https://github.com/lfisher447-afk/SpotUi-Offical) |
| **License & Trademarks** | See `LICENSE`. Spotify is a trademark of Spotify AB; users and developers are responsible for respecting provider terms and local law. |

---

## User Guide & Key Features

### Getting Signed In (Auth Browser Engine, Local URL Portal, OAuth PKCE & Multi-Platform Sync)
1. **5-Method SpotUI Auth Studio (`ui/screens/SpotifyLogin/{page,module,helper}/` + `:spotify` `Spotify.kt`)**:
   - **Full Multi-Tab Auth Browser Engine (`AuthBrowserEnginePanel` + `LoginBrowserBookmarkStore`)**: Built-in Chromium browser engine inside SpotUI featuring multi-tab browsing, SSO popup tab spawning (`onCreateWindow`), editable **Omnibox URL / Search Bar**, Back / Forward / Reload / Home navigation, **Find-in-Page**, Zoom (`85% / 100% / 115%`), **User-Agent switcher** (`Desktop Chrome`, `Mobile Chrome`, `System`), **Incognito Mode**, browsing history, live **Cookie & Session Inspector** (`sp_dc`, `sp_key`, YTM `SAPISID`, Deezer `arl`), and persistent **Bookmarks** (1-tap star, pinned quick-bar, folder filters, custom CRUD, JSON import/export).
   - **Local URL Portal (`LocalLoginWebServer`)**: Embedded HTTP/1.1 `ServerSocket` portal (`http://127.0.0.1:8765/`) with optional Wi-Fi LAN mode for desktop-assisted sign-in across Spotify, YouTube Music, and Deezer ARL.
   - **Chrome Custom Tabs, Official OAuth PKCE & Direct Token Studio**: One-tap Chrome Tab sign-in with smart clipboard detection, loopback OAuth 2.0 PKCE (`http://127.0.0.1/callback`), and direct `sp_dc` / Cookie-Editor JSON / YTM / ARL import.
2. **YouTube Music Library Sync, Exact URL Playback & Dual-Path Stream Extraction** (`YouTubeMusicSync` + `YtDlpEngine`):
   - **Zero-Config Signed-In Session Sync**: Sign in via Local URL Portal, Auth Browser Engine, or `YouTubeMusicWebActivity` to sync playlists, **Liked Music (`LM`)**, and saved albums directly into your Library & Album tabs.
   - **Resilient InnerTube + Embedded APK-Native `yt-dlp`**: Combines InnerTube (`MWEB`, `ANDROID_MUSIC`, `2026` presets, 90s per-client circuit breakers, HTTP 429/5xx backoff, HTML `VISITOR_DATA` fallback) and `NewPipeExtractor` with an embedded on-device **`youtubedl-android 0.18.1` + FFmpeg** runtime (`YtDlpEngine`), plus exact 11-character YouTube/Shorts/Music URL alternative-stream routing.
3. **Lossless Mirrors & SpotFlac Verification (`SpotiFlac.verifyLosslessPipeline()`)**:
   - Configure **Lossless Provider Priority**, **Solo Primary Mode**, SpotFlac magic-signature verification (`fLaC` / `ftyp` / `<MPD>`), Deezer ARL Blowfish decryption, custom mirror ping, DoH presets, rotating proxies, and PoToken toggles.

### Playing, Pro Audio DSP & Live Game Translation
- **Material 3 Expressive Overhaul & Side-Tab Settings**: Luminous Material 3 Expressive surfaces, searchable `NavigationRail` side-tab switcher in **Settings** (`Style`, `Audio`, `Floater`, `Translate`, `Modules`, `Turbo`, `Network`, `System`, `Logs`, `All`), Winter Noel Christmas countdown, custom adaptive launcher artwork, tactile `SoundPool` UI chimes, `EqualizerProfileStore` custom JSON slots, and **Expressive SongPlay UI** / **Legacy Classic UI** switching.
- **Stationary Advanced 8D, Harmonic/Dynamics & `AudioOffloadMonitor`**: Zero-orbit stationary 8D holographic externalization (`SpatialSoundstagePresets`), soft-knee compressor & warm saturation (`HarmonicAndDynamicsProcessor` with live peak/RMS/gain-reduction telemetry), automatic offload bypass guards during capture/VoIP/DSP/speed changes, DJ Crossfade curves, Finish-Track Sleep timer, Speed/Pitch presets, and per-track Timestamp Bookmarks (`PlaybackAutomationManager`).
- **Independent In-App Volume (`0–100%`)**: Adjusts internal ExoPlayer PCM gain smoothly without modifying Android system OS media volume.
- **Home Screen Widgets & Background Mini-Notification**: Add the `2x2` Compact or `4x2` Expanded Glance widgets (`SpotuiCompactWidgetReceiver` / `SpotuiExpandedWidgetReceiver`), connect via **Android Auto**, or enable the broadcast-only Exit Play/Pause Mini-Notification (`ExitPlaybackControlReceiver`).
- **Live Internal Game Audio Voice Translator & Accessibility Floater Studio (`MediaAccessibilityService` + `GameVoiceTranslatorEngine`)**:
  - Uses Jetpack Compose Material 3 Expressive (`TYPE_ACCESSIBILITY_OVERLAY`) + `layout_mini_player.xml` with pinned quick-tabs (`Player`, `Queue`, `EQ`, `3D`, `Tools`, `Translate`), collapsible sidebar rail (`MEDIA`, `SOUND`, `PRO`, `GAME`), corner-drag resizing, edge-docking, and `0–100%` In-App Volume.
  - **Zero-Mic-Lock Internal Game Capture (`AudioPlaybackCaptureConfiguration`)**: Captures `USAGE_GAME` / `USAGE_MEDIA` internal audio via `MediaProjection` inside `PlaybackService` (excluding SpotUI UID) so team voice mics never lock, transcribes offline via **Vosk (`0.3.75`)** (`TranslatorModelManager`), auto-detects teammate language with **ML Kit Language ID (`17.0.6`)**, routes across **7 configurable translation engines** (`Settings -> Translate`), and displays click-through captions (`FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`) with 4-second silence auto-hide.

### Downloading & Managing Offline Libraries
- **Turbo 8x Multi-Track Downloads (`1–10` Songs Simultaneously, `5` Default)**: Adjust active concurrency (`1–10` tracks), parallel HTTP `206` range workers (`1–8` slices kicking in for files >= 512 KiB), and `64 KiB – 1 MiB` (`512 KiB` default) I/O buffers live in **Downloads** or **Settings -> Downloads & MrBean Network**, backed by a `32`-socket keep-alive pool (`128` max requests) and live current/peak MB/s tracking.
- **Public Music Export (`Music/spotui/`)**: Automatically or manually exports downloaded tracks (FLAC, M4A, Opus, WebM, MP3) via zero-permission `MediaStore` into `Music/spotui/` with **Verify & Repair** container sniffing and local offline playback.
- **NovaAc v8 Encrypted Archives**: Export playlists or liked songs to tamper-proof Header-Bound AEAD `.novaac` archives with Fast Mode single-pass 4 MiB streaming (`Deflater.NO_COMPRESSION` for compressed audio).

### In-App Automatic Updates
- Go to **Settings -> Updates -> Check for Updates** (or wait for the startup **UpdatePrompt**).
- Toggle **Auto-download updates in-app** to stream the GitHub Release APK directly inside SpotUI via **MrBean HTTP/2 multi-range slices**, inspect live MB/s + ETA + SHA-256 verification, keep a backup copy in `Downloads/SpotUI/`, and install in-place via Android's native **`PackageInstaller` Session API** (with a one-time *Allow from this source* grant and explicit **System Fallback** option if restricted by an OEM).

---

## What's New in v2.5.7 (`202611270`)

1. **Full SpotUI Auth Browser Engine with Persistent Bookmarks & History (`AuthBrowserEnginePanel` + `LoginBrowserBookmarkStore`)**:
   - Transformed the embedded Spotify login WebView into a full multi-tab Chromium browser engine with Back/Forward/Reload/Home navigation, editable Omnibox URL & search bar, Find-in-Page, User-Agent profiles (`Desktop`/`Mobile`/`System`), multi-window SSO popup tabs, Incognito mode, Zoom (`85–115%`), browsing history, live Cookie/Session Inspector (`sp_dc`, `sp_key`, YTM `SAPISID`, Deezer `arl`), and persistent Bookmarks (1-tap star, pinned quick-bar, folders, custom CRUD, JSON import/export).
2. **GitHub Actions CI Release Build Fix (`.gitignore` `models/` Anchoring)**:
   - Anchored `/models/` and `vosk-model*/` in `.gitignore` so Kotlin `src/**/models/` packages across `:simpmusic`, `:shazamkit`, `:spotify`, `:innertube`, `:kugou`, `:lrclib`, and `:lastfm` are always committed and compiled cleanly in GitHub Actions (`:app:assembleRelease`).
3. **Live Internal Game Audio Voice Translator & Multi-Engine Suite (`com.music.spotui.translator.*`)**:
   - Implemented `MediaProjection` + `AudioPlaybackCaptureConfiguration` internal game audio capture (excluding SpotUI UID) with offline **Vosk (`0.3.75`)** speech recognition, **ML Kit Language ID (`17.0.6`)**, `GamingCalloutLexicon`, 7 configurable translation engines in **Settings -> Translate**, and a click-through `TYPE_ACCESSIBILITY_OVERLAY` caption pill with 4-second silence auto-hide and pinned Floater tabs (`layout_mini_player.xml`).
4. **Stationary Advanced 8D Audio, Audio Offload Guard & Pro Audio Expansion (`v2.5.6`–`v2.5.7`)**:
   - Reworked 8D audio into non-orbiting **Stationary Advanced 8D** presets (`SpatialSoundstagePresets`), added `AudioOffloadMonitor`, `HarmonicAndDynamicsProcessor`, `EqualizerProfileStore` (custom slots + JSON), `PlaybackDiagnosticsEngine`, DJ Crossfade curves, Finish-Track Sleep Timer, Track Bookmarks, `SpotiFlac` magic-signature verification, and Winter Noel branding.

### Earlier v2.5.5 Highlights (`202611245`)

1. **Spotify WebView Anti-Bot Shield, Client Hints Alignment & Auto-Capture (`SpotifyLogin/{page,module,helper}/`)**:
   - Eliminated `"Oops! Something went wrong"` anti-bot blocks by matching default `SYSTEM_DEFAULT` User-Agent with `Sec-CH-UA` Client Hints, stripping `X-Requested-With` package identification via `WebSettingsCompat`, removing native JS prototype overrides that tripped reCAPTCHA Enterprise, wiping stale cookies/`WebStorage` prior to load, and handling in-app SSO popups cleanly into `Spotify.kt` (`SpotifyAuth`) + `SpotifyLoginPipeline` + YouTube-style **Sync All**.
2. **Complete Auto-Updater Pre-Flight Signing Check & Persistent CI Keystore (`tools/setup-keystore.sh`)**:
   - Fixed `UpdateChecker.extractSigningMetadata()` regex so GitHub Release markdown (`**Signing Key ID:** \`...\``) parses accurately before downloading, preventing unexpected `Signing key mismatch` blocks.
   - Added shared `tools/setup-keystore.sh` and `tools/verify-keystore.sh`, whitelisted `!debug.keystore.base64` in `.gitignore`, pinned `buildToolsVersion = "36.0.0"` across all Android modules, and synchronized `release-apk.yml` + `tools/build-release-apk.sh`.
3. **Material 3 Expressive Floater Studio & Game-Only Live Translation Bubble (`MediaAccessibilityService`)**:
   - Rebuilt the Accessibility Floater cleanly in Jetpack Compose with Material 3 Expressive transport controls, collapsible navigation rail (`MEDIA`, `SOUND`, `PRO`, plus conditional `GAME`), live `Player`/`Queue`/`EQ`/`3D`/`Tools`/`Translate` tabs, `0–100%` In-App Volume slider, bottom-left & bottom-right corner-drag resizing, and tactile vibration.
   - Added foreground game detection (`GameTranslatorEngine`) unlocking `Translate` **only inside games** and projecting a Top-Left auto-detect-to-English live teammate translation bubble with adjustable width (`160–420 dp`), text size (`11–24 sp`), opacity (`55–98%`), drag positioning, and corner resizing.
4. **Embedded APK-Native `yt-dlp` (`0.18.1`) + FFmpeg, InnerTube Hardening, Turbo 8x MrBean & 8-Channel DevConsole (`v2.5.3`–`v2.5.5`)**:
   - Bundled on-device `youtubedl-android 0.18.1` + FFmpeg (`YtDlpEngine`) alongside InnerTube (`MWEB`/`ANDROID_MUSIC`/`2026` presets, 90s per-client circuit breakers, HTTP 429/5xx exponential backoff, HTML `VISITOR_DATA` fallback, `NewPipeExtractor`).
   - Expanded `DevConsoleManager` (`8` channels, `2,000`-entry ring buffer), **21-point** `SystemHealthChecker`, **17 integrated modules**, `EqPresetCatalog`, Turbo 8x MrBean (`32` sockets, `8` `206` slices, `5` default concurrent downloads up to `10`), and enhanced `SpatialWideningAudioProcessor`.

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
| **Kotlinx Serialization JSON** | `1.8.1` | Zero-reflection JSON parsing for Spotify, InnerTube, Lyrics & navigation routes |
| **Core Library Desugaring (`desugar_jdk_libs_nio`)** | `2.1.5` | Java `java.time` & NIO APIs backported cleanly to Android 8.0+ (API 26+) |
| **Concurrent Futures KTX (`androidx.concurrent`)** | `1.2.0` | Clean suspend-bridging for callback & executor-based Android APIs |

### UI, Design, Widgets & WebView
| Library / Artifact | Version | Purpose |
| :--- | :--- | :--- |
| **Jetpack Compose BOM (`androidx.compose:compose-bom`)** | `2025.05.01` | Material Design 3 (`material3 1.4.0`), UI (`ui`, `graphics`, `tooling-preview`), Runtime LiveData, & Material Icons Extended |
| **AndroidX Core KTX (`androidx.core:core-ktx`)** | `1.16.0` | WindowInsets, `ContextCompat`, `IntentCompat`, FileProvider & MediaStore helpers |
| **AndroidX Lifecycle (`lifecycle-runtime-ktx` & `-compose`)** | `2.9.1` | Lifecycle-aware `StateFlow` collection (`collectAsStateWithLifecycle`) |
| **AndroidX Activity Compose (`activity-compose`)** | `1.10.1` | `enableEdgeToEdge()`, `BackHandler`, & `rememberLauncherForActivityResult` |
| **Navigation Compose (`navigation-compose`)** | `2.9.0` | Adaptive single-activity navigation across Home, Search, Library, Downloads, Player, Settings & DevConsole |
| **AndroidX WebKit & Browser (`webkit` / `browser`)** | `1.14.0` / `1.8.0` | Hardened WebView auth flows for Spotify & YouTube Music with Client Hints alignment & Custom Tabs handoff |
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
| **Embedded yt-dlp + FFmpeg (`io.github.junkfood02.youtubedl-android`)** | `0.18.1` | On-device APK-native `library` + `ffmpeg` runtime for primary/fallback stream resolution & live extractor updates |
| **Brotli (`org.brotli:dec`)** | `0.1.2` | Brotli response decompression in network modules |
| **Timber (`com.jakewharton.timber:timber`)** | `5.0.1` | Structured logging integrated into `DevConsoleManager` |
| **Eclipse MrBean (`org.eclipse.Mrbean.*`)** | Embedded | `32`-socket HTTP/2 `ConnectionPool` (`128` dispatcher), TTL DNS cache, up to `8` `FileChannel` `206` slices, `1 MiB` buffers & NovaAc v8 controller |

### On-Device Speech, Machine Learning & Testing Stack
| Library / Artifact | Version | Purpose |
| :--- | :--- | :--- |
| **Vosk Android (`com.alphacephei:vosk-android`)** | `0.3.75` | Pure-Gradle offline streaming ASR for internal game voice capture (`filesDir/vosk-model`) |
| **JNA (`net.java.dev.jna:jna`)** | `5.14.0` | Native ABI bridge required by Vosk Android offline recognition |
| **Google ML Kit Translate (`com.google.mlkit:translate`)** | `17.0.3` | Offline, API-key-free on-device lyric & game caption translation |
| **Google ML Kit Language ID (`com.google.mlkit:language-id`)** | `17.0.6` | Automatic spoken & lyric language detection |
| **JUnit / Robolectric / AndroidX Test / Espresso** | `4.13.2` / `4.14.1` / `1.6.1` / `3.6.1` | Multi-module JVM & Robolectric SDK 35 automated test suite |

---

## 17 Integrated Modules & Submodules

### `modules/app/libs` — 10 Submodules

| Module | Gradle Path | Role in SpotUI | Configurable In Advanced Settings |
| :--- | :--- | :--- | :--- |
| **Spotify** | `:spotify` (`/spotify` & `modules/app/libs/spotify`) | GraphQL home/search/playlists, Canvas MP4 videos, OAuth/WebView token exchange & SpotiFlac 6x DASH-FLAC lossless mirrors (HTTP/2 OkHttp pool) | Enable toggle + Lossless Provider priority |
| **InnerTube** | `:innertube` (`/innertube` & `modules/app/libs/innertube`) | YouTube & YT Music stream resolution, 90s circuit breaker, `MWEB`/`ANDROID_MUSIC` presets, PoToken, DoH, rotating proxies & library sync | Enable toggle + DoH / Proxy / PoToken controls |
| **LRCLIB** | `:lrclib` (`modules/app/libs/lrclib`) | Keyless millisecond-synced LRC database with relaxed duration matching (Ktor CIO `maxConnectionsCount = 64`) | Enable toggle + waterfall priority `#1–#5` |
| **BetterLyrics** | `:betterlyrics` (`modules/app/libs/betterlyrics`) | TTML XML parser & word-by-word synced karaoke timing (OkHttp HTTP/2 pool) | Enable toggle + waterfall priority `#1–#5` |
| **SimpMusic** | `:simpmusic` (`modules/app/libs/simpmusic`) | YouTube Video-ID aligned community synced lyrics API (Ktor CIO `maxConnectionsCount = 48`) | Enable toggle + waterfall priority `#1–#5` |
| **Paxsenix** | `:paxsenix` (`modules/app/libs/paxsenix`) | Multi-source lyrics relay (Apple Music, Musixmatch, NetEase, Spotify) (OkHttp HTTP/2 pool) | Enable toggle + Multi-endpoint vs AMP-only + priority |
| **KuGou** | `:kugou` (`modules/app/libs/kugou`) | High-coverage CJK & international LRC lyrics provider (Ktor `HttpTimeout` + pooled HTTP) | Enable toggle + Traditional Chinese toggle + priority |
| **Last.fm** | `:lastfm` (`modules/app/libs/lastfm`) | Live `updateNowPlaying`, auto-scrobbling & track love sync (OkHttp HTTP/2 pool) | Enable toggle, Mobile session sign-in, Scrobble threshold slider |
| **ShazamKit** | `:shazamkit` (`modules/app/libs/shazamkit`) | Acoustic signature recognition queue, backoff & rate-limited cache (Ktor CIO `maxConnectionsCount = 32`) | Enable toggle, Queue telemetry, Clear Cache action |
| **Fastlane** | `modules/app/libs/fastlane` | Release notes, changelog metadata & release track channel definitions | Production / Beta / Nightly track selector |

### Core Engine & Studio Modules (`SpotuiModuleId`)

| Module ID | Package / Artifact | Role in SpotUI |
| :--- | :--- | :--- |
| **`YTDLP`** | `io.github.junkfood02.youtubedl-android` (`YtDlpEngine`) | Embedded APK-native `yt-dlp 0.18.1` + FFmpeg stream extractor with primary toggle & live binary updates |
| **`MRBEAN_ENGINE`** | `org.eclipse.Mrbean.client` | `32`-socket HTTP/2 `ConnectionPool`, TTL DNS cache & Turbo 8x multi-range downloader (`1–10` concurrent tracks) |
| **`MEDIA3_DSP`** | `com.music.spotui.audio` | 64-bit Biquad 7-Band EQ (`EqPresetCatalog`), Haas 3D Spatializer, Pinna HRTF, 8D Orbit & Soft Limiter |
| **`FLOATER_A11Y`** | `com.music.spotui.accessibility` | Material 3 Expressive Floater Studio with collapsible sidebar, 6 tabs (conditional Game `Translate`), Top-Left translation bubble, In-App Volume & corner resize |
| **`NOVAA_ARCHIVE`** | `com.music.spotui.data.export` | Single-pass AEAD encrypted library & lossless track backup vault (`NovaAc v8`) |
| **`GLANCE_WIDGETS`** | `com.music.spotui.widget` | Jetpack Glance `2x2` & `4x2` home screen player widgets synced via `PlaybackService` |
| **`ANDROID_AUTO`** | `com.music.spotui.ui.notification` | Browsable Android Auto catalog, One UI Now Bar & Media3 `MediaLibraryService` session |

---

## Project Architecture & Module Map

```text
SpotUi-Offical/
├── app/                                      # Main Android application module (:app)
│   ├── src/main/java/com/music/spotui/
│   │   ├── accessibility/                    # MediaAccessibilityService (Compose M3 Expressive Floater Studio, corner resize & haptics),
│   │   │                                     # GameTranslatorEngine (Game detection + Auto-Detect→English Top-Left Bubble),
│   │   │                                     # FloaterComposeContent, FloaterPanels, FloaterUiState, OverlayLifecycleOwner
│   │   ├── audio/                            # SpatialWideningAudioProcessor (v2 Haas/Early Reflections/HRTF/Schroeder/8D),
│   │   │                                     # SpotuiDspAudioProcessor, CrossfadeFilterAudioProcessor, EqPresetCatalog,
│   │   │                                     # BiquadFilter, AudioEffectController, BluetoothAudioInspector, UiSoundEngine
│   │   ├── data/
│   │   │   ├── api/                          # Api, ModuleLibsBridge (all 17 modules), LyricsApi (HTTP/2 pool),
│   │   │   │                                 # SpotifySession, SpotifySync, TranslationApi (ML Kit), DeepLinkHandler
│   │   │   ├── export/                       # NovaAcExportManager (single-pass 4 MiB / 2 MiB AEAD streaming)
│   │   │   ├── preferences/                  # SettingsPref, ModuleAndExperiencePref (SpotuiModuleId), DownloadPref,
│   │   │   │                                 # OfflineCollectionsPref
│   │   │   ├── update/                       # UpdateChecker (HTTP/2 GitHub release + .sha256 metadata),
│   │   │   │                                 # UpdateDownloader (MrBean multi-range + PackageInstaller Session API),
│   │   │   │                                 # UpdateInstallReceiver (BroadcastReceiver for session commit callbacks)
│   │   │   └── youtube/                      # YouTubeMusicSync (Playlists / Liked Music / Albums + OAuth PKCE via HTTP/2)
│   │   ├── deezer/                           # Direct Deezer ARL Blowfish lossless resolver & HTTP/2 client
│   │   ├── di/                               # Hilt DI modules & SongPlayer / SpotifyWebPlayer orchestration
│   │   ├── engine/                           # YtDlpEngine (youtubedl-android 0.18.1 + FFmpeg), PlaybackController,
│   │   │                                     # MediaNotificationAdapter, AudioFeaturesClient, AdBlockManager, TrueShuffleEngine
│   │   ├── ui/
│   │   │   ├── components/                   # PatchNotesDialog, UpdatePrompt, LiveLocationClockCard, SavedInSheet
│   │   │   ├── navigation/                   # AdaptiveNavigation (BottomBar / Rail / TopTabs / Sidebar), Routes, MyNavHost
│   │   │   ├── notification/                 # PlaybackService (MediaLibraryService), ExitPlaybackControlReceiver
│   │   │   ├── screens/
│   │   │   │   ├── SpotifyLogin/page/        # SpotifyLoginScreen (Sync All), YouTubeMusicWebActivity, DeezerLoginActivity
│   │   │   │   ├── SpotifyLogin/module/      # SpotifyWebViewAdapter (shield/engine), SpotifyLoginPipeline, SpotifyHashRecovery
│   │   │   │   ├── SpotifyLogin/helper/      # SpotifyLoginHelpers (credential extractors & session helpers)
│   │   │   │   └── ...                       # Home, Search, Library, Downloads, Player (+ NewPlayerLayout), Settings, UpdateRoadmap, DevConsole
│   │   │   └── theme/                        # Material 3 expressive Color, Typography (system SansSerif), ThemePreferences
│   │   ├── util/                             # DevConsoleManager (8 channels / 2,000 entries), SystemHealthChecker (21 checks),
│   │   │                                     # ArtworkHelper, BackupHelper
│   │   └── widget/                           # Glance 2x2 & 4x2 Home Screen Widgets + WidgetKeyRelay
│   ├── src/main/java/org/eclipse/Mrbean/     # Embedded Eclipse MrBean HTTP/2, WebSocket, Multi-Range Download & NovaAc Engine
│   │   └── client/                           # MrbeanDownloadClient (32-pool / 128-dispatcher / 8x 206 slicer / peak MB/s),
│   │                                         # MrbeanMediaDataSource, MrbeanNetworkSettings (1–10 concurrency & 64–1024 KB buffer),
│   │                                         # MrbeanNovaAcController
│   └── src/main/res/xml/                     # accessibility_service_config.xml, adaptive icon & widget configs
├── spotify/ & innertube/                     # Core root Gradle modules (:spotify, :innertube)
├── modules/app/libs/                         # Extended submodule suite (betterlyrics, kugou, lastfm, lrclib,
│                                             # paxsenix, shazamkit, simpmusic, fastlane, spotify, innertube)
├── tools/                                    # setup-keystore.sh, verify-keystore.sh, build-release-apk.sh & archive verification utilities
└── .github/workflows/                        # release-apk.yml (CI APK build + apksigner + SHA-256 + GitHub Release)
```

---

## Build & Release Workflows

### Option A — Automated GitHub Actions (`.github/workflows/release-apk.yml`)
1. Push a commit to `main`/`master`, push a tag matching `v*` (e.g., `git tag v2.5.7 && git push origin v2.5.7`), or manually trigger **Actions -> Build & Publish Release APK -> Run workflow**.
2. **Default Fast Builds (`skip_tests: true` by default)**: Tag/branch builds and default manual dispatches skip unit tests so release APKs build quickly and reliably. To run `:spotify:test`, `:innertube:testDebugUnitTest`, and `:app:testDebugUnitTest`, trigger **Run workflow** manually and **uncheck** `Skip unit tests`.
3. **Persistent Signing Key Resolution (Prevents `Signing key mismatch`)**: Android requires every in-place update APK to use the exact same signing certificate as the installed build (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Both CI and local builds call `tools/setup-keystore.sh`, resolving `debug.keystore` in strict priority order:
   1. **GitHub Actions Secret (`SIGNING_KEYSTORE_BASE64` or `DEBUG_KEYSTORE_BASE64`)** — Recommended so all CI builds share one consistent key even across branches/forks (`cat debug.keystore.base64` or `base64 -w0 debug.keystore`).
   2. **Committed `debug.keystore.base64`** in the repository root (whitelisted in `.gitignore`; signing certificate short ID: `3e079b97f0…`). Ensure `debug.keystore.base64` is committed & pushed to GitHub!
   3. **Ephemeral `keytool` fallback** (only if both above are missing — emits a CI warning and prints a notice in the release notes because a one-off key cannot update over previous releases in-place).
4. CI builds `:app:assembleRelease`, verifies signatures with `apksigner` and `tools/verify-keystore.sh`, prints the **Signing Key ID** and **APK SHA-256** in the build summary & release notes, uploads build artifacts, and publishes **`SpotUI-v2.5.7-release.apk`** + **`.sha256`** to **GitHub Releases**.

> **Fixing `Signing key mismatch` from an older build:** If your currently installed APK was signed with an earlier ephemeral key (different SHA-256 prefix than the new release), back up your library/settings in **Settings -> System -> Backup**, tap **Uninstall Old App** (or uninstall SpotUI once), and install the newly downloaded APK from `Downloads/SpotUI/`. Once installed with a persistent `SIGNING_KEYSTORE_BASE64` secret or `debug.keystore.base64`, all subsequent releases update cleanly in-place via Android `PackageInstaller`.

### Option B — Local Release Script (`tools/build-release-apk.sh`) or Gradle
Requires **JDK 21** and **Android SDK Platform 36 (`build-tools 36.0.0`)**:

```bash
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-21
bash tools/verify-keystore.sh
bash tools/build-release-apk.sh
```

Or compile modules, run all unit/Robolectric tests, and build the signed release APK directly:

```bash
./gradlew :simpmusic:compileKotlin :shazamkit:compileKotlin :spotify:compileKotlin --continue
./gradlew :spotify:test :innertube:testDebugUnitTest :app:testDebugUnitTest --console=plain
./gradlew :app:assembleRelease --console=plain
```

Outputs `dist/SpotUI-v2.5.7-release.apk` and `dist/SpotUI-v2.5.7-release.apk.sha256`.

> **Signing Notice:** The checked-in `sharedDebug` signing config enables seamless in-place sideload & `PackageInstaller` upgrades across builds (`app/build.gradle.kts`). Always substitute a private release keystore before public store distribution, and never commit account cookies, ARLs, OAuth secrets, or private keys.

---

## Permissions Overview

SpotUI adheres to least-privilege and Google Play Creator Program compliant Android permission practices:
- **Network & Background Playback**: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- **Audio, Bluetooth, Speech & Tactile Feedback**: `MODIFY_AUDIO_SETTINGS`, `RECORD_AUDIO` (requested cleanly at runtime only when enabling Game-Only Live Teammate Translation inside the Floater), `BLUETOOTH` (`maxSdkVersion 30`), `BLUETOOTH_CONNECT`, `VIBRATE`.
- **Local Audio & Public Downloads**: `READ_MEDIA_AUDIO` (API 33+) / `READ_EXTERNAL_STORAGE` (`maxSdkVersion 32`) for scanning local audio playback; `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion 28`) for pre-Scoped-Storage `Music/spotui/` export (Android 10+ uses zero-permission `MediaStore`).
- **In-App PackageInstaller Updates**: `REQUEST_INSTALL_PACKAGES` (used exclusively when the user confirms downloading & installing an updated GitHub Release APK in-app).
- **Optional Floating Mini-Player Service**: `BIND_ACCESSIBILITY_SERVICE` on `MediaAccessibilityService` with `canRetrieveWindowContent="false"` and `isAccessibilityTool="true"` — reads **zero** screen content, detects foreground game packages only to reveal the Game Translate tab, and renders Jetpack Compose M3 Expressive overlays.

---

## Version History

| Version | Code | Highlights |
| :--- | :--- | :--- |
| **v2.5.7** | `202611270` | **Full Auth Browser Engine + Bookmarks, Live Internal Game Audio Translator, Pro Audio DSP & CI `models/` Fix**: Rebuilt Spotify Login WebView into a multi-tab Auth Browser Engine with omnibox URL/search bar, Back/Forward/Reload/Home, Find-in-Page, User-Agent profiles, Incognito mode, browsing history, live Cookie/Session Inspector, and persistent Bookmarks (1-tap star, pinned quick-bar, folders, custom CRUD, JSON import/export); anchored `/models/` & `vosk-model*/` in `.gitignore` so `:simpmusic`, `:shazamkit`, and `:spotify` models packages always track in git and compile cleanly in GitHub Actions; integrated `MediaProjection` `AudioPlaybackCapture` live game voice translation (Vosk + ML Kit + 7 engines), click-through Floater caption pill with 4s silence auto-hide, pinned Floater tabs (`layout_mini_player.xml`), `AudioOffloadMonitor`, `HarmonicAndDynamicsProcessor`, Custom EQ slots, Playback Diagnostics, and full README overhaul. |
| **v2.5.6** | `202611260` | **Winter Noel Edition — Stationary Advanced 8D Audio, SpotFlac Verification, In-Game Translator & Adaptive Settings**: Replaced orbiting 8D panning with stationary Advanced 8D Holographic Binaural externalization; added `SpotiFlac` magic-signature verification lab, searchable adaptive rail Settings, Winter Noel Christmas countdown, Cyber Prism launcher icon, tactile `SoundPool` chimes, and exact YouTube URL routing. |
| **v2.5.5** | `202611245` | **Spotify WebView Anti-Bot Shield, Persistent CI Keystore Auto-Updater, M3 Expressive Floater & Game-Only Translator**: Fixed Spotify WebView `Sec-CH-UA` Client Hints mismatches & prototype tampering triggers, stripped `X-Requested-With` package leaks, wiped stale cookies/`WebStorage` before login, and unified SSO popups into `Spotify.kt` + Library Sync All; fixed `UpdateChecker.extractSigningMetadata` markdown parsing for pre-flight signing compatibility checks; added `tools/setup-keystore.sh` + `tools/verify-keystore.sh`, whitelisted `!debug.keystore.base64` in `.gitignore`, pinned `buildToolsVersion = "36.0.0"`, updated Fastlane metadata, and aligned 17-module / 21-point telemetry. |
| **v2.5.4** | `202611234` | **Unified `SpotifyLogin/{page,module,helper}/`, Material 3 Expressive Floater Studio, Game-Only Live Translation Bubble, Embedded `yt-dlp` + FFmpeg & InnerTube Hardening**: Consolidated all Spotify, YouTube Music Web, and Deezer login screens/helpers/modules under `ui/screens/SpotifyLogin/{page,module,helper}/` with `Spotify.kt` (`SpotifyAuth`) + hardened `SpotifyWebViewAdapter` and YouTube-style **Sync All**; rebuilt `MediaAccessibilityService` in Jetpack Compose with Material 3 Expressive transport controls, collapsible rail (`MEDIA`, `SOUND`, `PRO`, conditional `GAME`), 6 live tabs (`Player`, `Queue`, `EQ`, `3D`, `Tools`, game-only `Translate`), `0–100%` In-App Volume slider, corner-drag resizing, tactile vibration, and a Top-Left adjustable auto-detect-to-English live teammate translation bubble; bundled on-device `youtubedl-android 0.18.1` + FFmpeg (`YtDlpEngine`) with primary extraction toggle & live binary updater; added XOR TOTP v61 secret extraction + Auto-Recover for HTTP 400/402/403, 90s per-client InnerTube circuit breakers, HTTP 429/5xx backoff, `MWEB`/`ANDROID_MUSIC`/`2026` presets, HTML `VISITOR_DATA` fallback, `NewPipeExtractor` activation, `EqPresetCatalog`, 17-module telemetry & 21-point `SystemHealthChecker`. |
| **v2.5.3** | — | **Material 3 Expressive Overhaul, Side-Tab Settings, Turbo 8x MrBean & 2,000-Entry Logger**: Introduced Material 3 Expressive surfaces, animated `NavigationRail` side-tab Settings, upgraded `Home`/`Downloads`/Mini-Player cards, Turbo 8x MrBean (`32` keep-alive sockets, `128` dispatcher, `5` default concurrent downloads up to `10`, up to `8` parallel HTTP `206` slices >= 512 KiB, `512 KiB`–`1 MiB` buffers, peak MB/s meter), haptic Floater corner-drag resizing, Haas/Early Reflections/Pinna/8D Spatializer audibility overhaul, and an 8-channel `2,000`-entry `DevConsoleManager`. |
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
