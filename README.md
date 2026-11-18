<div align="center">

# SpotUI Android — v2.3.0 (Thanksgiving Harvest Edition)

[![Current Release](https://img.shields.io/badge/Version-v2.3.0%20(202611180)-E87A24?style=for-the-badge)](https://github.com/lfisher447-afk/SpotUi-Offical)
[![Android SDK](https://img.shields.io/badge/Android-API%2026%E2%80%9336%20%7C%20Android%2017%20Ready-1ED760?style=for-the-badge)](https://github.com/lfisher447-afk/SpotUi-Offical)
[![Media3](https://img.shields.io/badge/Media3-1.11.0-blue?style=for-the-badge)](https://github.com/lfisher447-afk/SpotUi-Offical)
[![CI Workflow](https://img.shields.io/badge/CI-GitHub%20Actions%20APK-purple?style=for-the-badge)](https://github.com/lfisher447-afk/SpotUi-Offical/actions)

**SpotUI (`com.music.spotui`) is a Kotlin & Jetpack Compose Android music client powered by Media3 1.11.0, Material 3 expressive theming, an in-process 32-bit float Studio DSP & Bluetooth-safe 3D Spatializer, lossless multi-source resolution, `Music/spotui` MediaStore export, and framed NovaAc v8 AEAD archives.**

</div>

---

## Package Identity & Build Baseline

| Item | Current Value |
| :--- | :--- |
| **Application ID** | `com.music.spotui` |
| **Version Name** | `2.3.0` |
| **Build Version Code** | `202611180` |
| **SDK Range** | `minSdk 26` (Android 8.0) · `compileSdk / targetSdk 36` · Tested on Android 17 / One UI 9 |
| **Toolchain** | Java 21 · Kotlin `2.2.0` · AGP `8.13.2` · KSP `2.2.0-2.0.2` · Hilt `2.57.1` |
| **Media Engine** | AndroidX Media3 `1.11.0` (ExoPlayer, MediaLibraryService, OkHttp & WorkManager) |
| **Networking** | OkHttp `5.0.0-alpha.12` (+ DoH), Ktor `3.5.1`, Brotli `0.1.2`, `kotlinx.serialization` `1.7.3` |
| **On-Device ML / UI** | Jetpack Glance `1.1.1`, ML Kit Language ID `17.0.6` & Translate `17.0.3`, Coil / Glide |
| **Archive Engine** | **NovaAc v8** 64 KiB Header-Bound AEAD (AES-GCM) + Android Keystore Metadata |
| **Repository** | [lfisher447-afk/SpotUi-Offical](https://github.com/lfisher447-afk/SpotUi-Offical) |
| **License & Trademarks** | See `LICENSE`. Spotify is a trademark of Spotify AB; developers are responsible for respecting provider terms and local law. |

---

## What's New in v2.3.0 (`202611180`)

### 1. Android 17 / One UI 9 Foreground Playback & Crash Elimination
- **Zero `ForegroundServiceDidNotStartInTimeException`**: `PlaybackService.onStartCommand()` now promotes itself synchronously to foreground using an immediate lightweight placeholder notification whenever invoked via `ContextCompat.startForegroundService(...)`, before Media3 or coroutine debouncers execute.
- **Debounced MediaSession Lifecycle**: Hardened `MediaCommandDispatcher`, `PlaybackSessionBus`, and `SongPlayer` transitions so rapid play/pause/skip taps on One UI 9 Now Bar, lock screen, Bluetooth AVRCP, or Glance widgets never race or drop `startForeground()`.
- **Non-Destructive Metadata Refresh & Buffering Sync**: Preserves `MediaItem.localConfiguration` across metadata updates, forwards `STATE_BUFFERING` immediately, holds `WakeLock`/`WifiLock` across screen-off track transitions, and supports seamless autoplay radio continuation.
- **Automatic Startup Crash Inspection (`CrashReportPrompt`)**: Captures uncaught exceptions with device, heap, thread, and stack diagnostics in `CrashHandler` and surfaces a 1-tap **Copy Log / Dismiss** prompt at next launch.

### 2. Bluetooth-Safe 3D Spatial Soundstage & Studio 7-Band DSP
- **Zero Bluetooth A2DP / BLE Audio Dropouts**: Resolved an Android/One UI audio HAL conflict where hardware `Virtualizer` and `PresetReverb` stalled or muted audio when active on Bluetooth headsets. Whenever a Bluetooth sink or software 3D DSP is active (`AudioEffectController` + `AudioDeviceCallback`), SpotUI cleanly bypasses conflicting hardware auxiliary effects and routes all spatialization through the in-process PCM-16 / PCM-Float ExoPlayer pipeline.
- **Studio `SpatialWideningAudioProcessor`**:
  - NaN / Infinity sample guard + click-free exponential gain smoothing across live toggle and slider adjustments.
  - Sub-115 Hz mono-bass anchoring to preserve kick punch while widening mids and highs.
  - 4-tap prime-spaced acoustic early reflections with high-frequency air-absorption damping.
  - True Interaural Time Difference (~0.28 ms ITD) + ~750 Hz head-shadow ILD HRTF crossfeed tailored for Bluetooth headphones and wired IEMs.
  - Soft-knee peak limiter keeping AAC, SBC, and LDAC Bluetooth encoders distortion-free.
- **7-Band Biquad Studio Equalizer (`SpotuiDspAudioProcessor`)**:
  - 7 precision parametric bands (`32 Hz`, `64 Hz`, `250 Hz`, `1 kHz`, `4 kHz`, `10 kHz`, `16 kHz`), Preamp (`-12 dB` to `+6 dB`), dedicated Sub-Bass Shelf Boost (`0–9 dB`), RMS Loudness Normalizer, and Soft-Knee Peak Limiter.
  - `onReset()` preserves user EQ/Spatial curves across track changes, seeks, and background transitions.
  - Real-time active audio output telemetry in **Audio & DSP Settings** (Phone Speaker, Bluetooth Headset, USB Audio/DAC, Wired Headphones, Hearing Aid).

### 3. Reliable `Music/spotui` Download Export & Storage Hub
- **Fixed Public `Music/spotui/` Export (`DownloadPref`)**: Resolved missing folder/file issues on Android 10+ (API 29–36)Scoped Storage and legacy devices (API 26–28) via proper `MediaStore.Audio.Media` inserts with `RELATIVE_PATH = Environment.DIRECTORY_MUSIC + "/spotui"`, `IS_PENDING` atomic commits, MIME-type detection (`.flac`, `.m4a`, `.opus`, `.webm`, `.mp3`), and `MediaScannerConnection` indexing.
- **Auto-Export on Download Completion**: Configurable toggle in Settings & Downloads to automatically mirror newly finished offline tracks straight to `Music/spotui/`.
- **Single-Track & Bulk Export + Storage Telemetry**: Export individual tracks from `SongOptionsSheet`, batch-export the offline library from `DownloadsScreen`, view exported vs. local byte breakdowns, and inspect detailed `ExportReport` results.

### 4. Thanksgiving Harvest Experience & Custom Adaptive Branding
- **Live Thanksgiving Countdown Banner (`ThanksgivingCountdownBanner`)**: Real-time collapsible Home card computing the 4th Thursday of November (`ThanksgivingClockCalculator`) with live days/hours/minutes/seconds ticker, November harvest progress bar, automatic year rollover, and celebration state on Thanksgiving Day.
- **Custom Thanksgiving Launcher Icons**: Updated adaptive launcher (`ic_launcher`), round launcher (`ic_launcher_round`), and Android 13+ monochrome themed icon (`ic_launcher_monochrome`) built directly from the supplied Thanksgiving Harvest & Pilgrim-Hat SpotUI composite assets.

### 5. 18-Point Active Health Scanner & Lossless Controller
- **`SystemHealthChecker`**: 18 live diagnostic checks spanning playback service state, audio DSP & Bluetooth routing, Spotify/YouTube/Deezer auth sessions, DoH/proxy/custom-mirror ping probes, offline/MediaStore storage health, battery optimization, and runtime permissions—complete with 1-tap remediation actions.
- **Lossless Priority & Transport**: Configurable provider priority ordering with **Solo Primary Mode**, direct Deezer ARL Blowfish stream decryption, YouTube Music account/channel verification, live quality switch resuming at the exact playback timestamp, A-B Loop, Sleep Timer, Playback Speed (`0.50x–2.00x`), Pitch (`0.75x–1.25x`), and Skip Silence.

---

## Project Architecture & Module Map

```text
SpotUi-Offical/
├── app/                          # Main Android app (:app)
│   ├── src/main/java/com/music/spotui/
│   │   ├── audio/                # AudioEffectController, SpotuiDspAudioProcessor, SpatialWideningAudioProcessor
│   │   ├── data/                 # Room/SharedPreferences stores, API clients, BatteryOptimizationHelper, caches
│   │   ├── di/                   # Hilt DI modules & core SongPlayer / SpotifyWebPlayer orchestration
│   │   ├── engine/               # PlaybackController, QueueManager, StreamResolver, FallbackResolver,
│   │   │                         # LrcParser, LyricsSynchronizer, DownloadCoordinator, NovaAc v8 archive engine
│   │   ├── playback/             # MediaCommandDispatcher, PlaybackSessionBus, QueueWindow, ResumePlanBuilder
│   │   ├── ui/
│   │   │   ├── components/       # ThanksgivingCountdownBanner, SongOptionsSheet, CrashReportPrompt, LyricsSyncOverlay
│   │   │   ├── navigation/       # AdaptiveNavigation (BottomBar / Rail / TopTabs / Sidebar), Routes, MyNavHost
│   │   │   ├── notification/     # PlaybackService (MediaLibraryService), SpotuiMediaNotificationProvider
│   │   │   ├── screens/          # Home, Search, Library, Player, Queue, Lyrics, Downloads, Settings, Roadmap, Auth
│   │   │   ├── theme/            # Material 3 expressive Color, Typography (system SansSerif), ThemePreferences
│   │   │   └── viewmodel/        # StateFlow ViewModels across all screens
│   │   ├── util/                 # SystemHealthChecker (18 checks), AppDiagnostics, CrashHandler, BackupHelper
│   │   └── widget/               # Jetpack Glance home-screen Now Playing widgets & debounced sinks
│   └── src/test/                 # Multi-module JVM & Robolectric test suite (run in CI / local workflow)
├── spotify/                      # :spotify — Spotify GraphQL & Web metadata client module
├── innertube/                    # :innertube — YouTube / InnerTube resolver, AuthSessionStore & stream client
├── org/                          # Project-local Eclipse MrBean transport & NovaAc controller hierarchy
├── androidx/                     # Project-local Android compatibility helpers
├── tools/                        # build-release-apk.sh, NovaAc/project documentation utilities
└── .github/workflows/            # release-apk.yml (CI unit tests + Release APK + SHA-256 + GitHub Release)
```

---

## Complete Feature Reference

### Playback, Queue & Lyrics
- **Media3 `1.11.0` + Android Auto**: Full `MediaLibraryService` browse tree (Liked Songs, Downloads, Playlists, Albums), lock-screen controls, Samsung One UI Now Bar synchronization, and widget transport relay.
- **Gapless & Crossfade Engine**: Configurable crossfade duration, remembered playback position, true shuffle (`TrueShuffleEngine`), A-B loop repeat, sleep timer, and smart headphone unplug handling (`SmartUnplugHandler`).
- **Time-Synced Lyrics**: Millisecond-accurate LRC parser (`LrcParser` / `LRCLIB`) with manual lyric offset calibration, tap-to-seek lines, and on-device **ML Kit** language identification + offline translation caching.

### Streaming, Quality & Authentication
- **Dual-Mode Spotify Login**: Desktop-UA Chrome WebView flow (`Chrome/131.0.0.0`), 1-tap `sp_dc` cookie paste & auto-sync, external browser handoff, or instant Guest / Local mode.
- **YouTube Music & Deezer Integrations**: In-app YouTube login & channel verification, official Google OAuth playlist sync, Deezer ARL Blowfish lossless decryption, configurable PoToken & SponsorBlock/ad-banner stripping.
- **Network & Privacy Transport**: Built-in DoH (DNS-over-HTTPS) presets, rotating HTTP/SOCKS proxy configuration, MrBean socket connection pooling, and expired-stream auto re-resolution.

### Downloads, Offline & NovaAc v8 Archives
- **Offline Manager**: Background `WorkManager` downloads with dynamic format detection (`FLAC`, `M4A`, `OPUS`, `WEBM`, `MP3`), embedded album art, sort filters (Added / Title / Artist), and non-destructive transient cache cleanup.
- **Public `Music/spotui/` Export**: Safe MediaStore export (API 29+) and `Environment.DIRECTORY_MUSIC/spotui` fallback (API 26–28) with per-song, batch, and auto-export workflows.
- **NovaAc v8 Encrypted Archives**: Playlist & Liked Songs archive exporter using 64 KiB Header-Bound AEAD (AES-GCM) framed streaming with SHA-256 frame integrity and Android Keystore metadata protection. *(Designed strictly for user-controlled metadata & app-cached archive portability—not DRM circumvention.)*

### UI, Personalization & Diagnostics
- **Material 3 Theme Studio**: Dynamic color extraction, expressive presets (including **Harvest Gold**), JSON/image-derived palettes, adjustable corner radius (`0–48 dp`), type scale, contrast boost, reduced motion, and 48 dp accessibility touch targets.
- **Developer Console & SAF Backup**: Bounded `AppDiagnostics` log stream, crash report dialog, 18-point active `SystemHealthChecker`, and Storage Access Framework (SAF) user-selected backup/restore.

---

## Build & Release Workflows

### Option A — Automated GitHub Actions (`.github/workflows/release-apk.yml`)
1. Push a tag matching `v*` (e.g., `git tag v2.3.0 && git push origin v2.3.0`) or manually trigger **Actions -> Build & Publish Release APK -> Run workflow**.
2. CI automatically runs `:spotify:test`, `:innertube:testDebugUnitTest`, and `:app:testDebugUnitTest`, builds `:app:assembleRelease`, verifies APK signatures via `apksigner`, generates a SHA-256 digest, and publishes `SpotUI-v2.3.0-release.apk` + `.sha256` to **GitHub Releases**.

### Option B — Local Release Script (`tools/build-release-apk.sh`)
Requires **JDK 21** and **Android SDK Platform 36**:

```bash
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-21
bash tools/build-release-apk.sh
```

Outputs `dist/SpotUI-v2.3.0-release.apk` and `dist/SpotUI-v2.3.0-release.apk.sha256`.

> **Signing Notice:** The checked-in `sharedDebug` signing config enables seamless local sideload upgrades across test builds. Always swap in a private release keystore before public store distribution, and never commit account cookies, ARLs, tokens, or private keys.

---

## Permissions Overview

SpotUI adheres to least-privilege Android permissions:
- **Network & Playback**: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK`, `POST_NOTIFICATIONS`.
- **Audio & Bluetooth**: `MODIFY_AUDIO_SETTINGS`, `BLUETOOTH` (`maxSdkVersion 30`), `BLUETOOTH_CONNECT`, `VIBRATE`.
- **Media & Export**: `READ_MEDIA_AUDIO` (API 33+) / `READ_EXTERNAL_STORAGE` (`maxSdkVersion 32`) for local audio playback, `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion 28`) for pre-Scoped-Storage `Music/spotui` export (API 29+ uses zero-permission `MediaStore`).

---

## Version History (v1.5.1 — v2.3.0)

| Version | Code | Summary |
| :--- | :--- | :--- |
| **v2.3.0** | `202611180` | **Thanksgiving Harvest Update**: Immediate foreground service promotion (zero `ForegroundServiceDidNotStartInTimeException`), Bluetooth-safe in-process 3D Spatial + HRTF + 7-band EQ routing, fixed `Music/spotui` MediaStore export & auto-export, live Thanksgiving Countdown Banner, custom adaptive/monochrome Thanksgiving icons. |
| **v2.2.0** | `202610108` | **Playback Resurrection & 18-Point Health Scanner**: Fixed Samsung One UI Now Bar desync, rebuilt 7-band Biquad DSP + 3D Binaural Spatializer, Lossless Provider Priority with Solo Primary, Deezer ARL Blowfish decryption, Search filters, A-B Loop, Sleep Timer, Quick Queue, and 18-point active Health Scanner. |
| **v2.1.7** | `202610107` | **Thanksgiving Harvest Gold & Auth Hardening**: Harvest Gold preset, `PlaybackAuthState` & `AuthSessionStore`, DoH/proxy/PoToken controls, YouTube channel verification, Speed/Pitch/Skip Silence tuning, manual update checker, `CrashReportPrompt`, fixed launch NPE in `PlaybackService.updateCurrentMetadata()`. |
| **v2.1.6** | `202610106` | Dual-mode Chrome-UA Spotify WebView login, 1-tap `sp_dc` cookie login, external browser handoff, guest mode, WebView crash/SSL recovery, dynamic download container extensions. |
| **v2.1.5** | `202610105` | NovaAc v8 Header-Bound AEAD encryption, `MrbeanNovaAcController` v8, LRCLIB millisecond lyric sync, M3 corner scaling. |
| **v2.1.0** | `202610100` | NovaAc v6 64 KiB framed AEAD engine eliminating cipher heap spikes, MrBean transport telemetry. |
| **v2.0.0** | `202609200` | Upgraded Media3 to `1.11.0`, unified MrBean Media3 transport, Google OAuth YouTube playlist sync, Theme Studio (JSON & artwork palettes), NovaAc v5 container integrity. |
| **v1.9.9** | — | Sequential playlist downloads, fresh resolver fallback, 5-band EQ & software spatializer, YouTube Music companion, collapsible library groups. |
| **v1.9.5** | `202609050` | NovaAc full-audio archive exporter, passphrase portability, adaptive navigation shell. |
| **v1.8.0–1.8.9** | — | API 36 target integration, Android Keystore AES-GCM NovaAc metadata, monotonic version code, startup class repair. |
| **v1.5.1–1.7.9** | — | Timed LRC parser foundation, local library continuity, collapsible settings, persistent `AppDiagnostics`, Android Auto & notification integration. |
