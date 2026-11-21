<div align="center">

# SpotUI Android — v2.5.0 (`202611220`)

### Expressive Jetpack Compose Audio Workstation for Android 8 – 17 & One UI 9

[![GitHub Stars](https://img.shields.io/github/stars/lfisher447-afk/SpotUi-Offical?style=for-the-badge&color=F5B841&logo=github)](https://github.com/lfisher447-afk/SpotUi-Offical/stargazers)
[![GitHub Forks](https://img.shields.io/github/forks/lfisher447-afk/SpotUi-Offical?style=for-the-badge&color=5C8DF6&logo=github)](https://github.com/lfisher447-afk/SpotUi-Offical/network/members)
[![Latest Release](https://img.shields.io/github/v/release/lfisher447-afk/SpotUi-Offical?style=for-the-badge&color=1ED760&label=Release)](https://github.com/lfisher447-afk/SpotUi-Offical/releases/latest)
[![Total Downloads](https://img.shields.io/github/downloads/lfisher447-afk/SpotUi-Offical/total?style=for-the-badge&color=E87A24)](https://github.com/lfisher447-afk/SpotUi-Offical/releases)
[![Android SDK](https://img.shields.io/badge/Android-API%2026%E2%80%9336%20%7C%20Android%2017%20Ready-1ED760?style=for-the-badge&logo=android&logoColor=white)](https://github.com/lfisher447-afk/SpotUi-Offical)
[![Media3](https://img.shields.io/badge/Media3-1.11.0-3B82F6?style=for-the-badge)](https://github.com/lfisher447-afk/SpotUi-Offical)
[![CI Workflow](https://img.shields.io/github/actions/workflow/status/lfisher447-afk/SpotUi-Offical/release-apk.yml?style=for-the-badge&label=CI%20Build)](https://github.com/lfisher447-afk/SpotUi-Offical/actions)

> **Enjoying SpotUI? Give the repository a [Star on GitHub](https://github.com/lfisher447-afk/SpotUi-Offical) — it directly boosts visibility, helps other Android audiophiles discover the project, and fuels faster releases!**

**SpotUI (`com.music.spotui`) is a pure Kotlin & Jetpack Compose Android music workstation powered by Media3 `1.11.0`, an in-process 16/24/32-bit/Float Studio DSP & Bluetooth-safe 3D Binaural Spatializer, independent `0–100%` In-App Volume control, multi-source lossless & synchronized lyrics cascading across 10 integrated `modules/app/libs` submodules, native YouTube Music library sync, `Music/spotui/` export, multi-MB/s NovaAc v8 AEAD archives, and deep Bluetooth/component diagnostics.**

[What's New in v2.5.0](#-whats-new-in-v250-202611220) · [All 10 Library Modules](#-modulesapplibs--10-integrated-submodules) · [Architecture](#-project-architecture--module-map) · [Build & Release](#-build--release-workflows) · [Version History](#-version-history-v151--v250)

</div>

---

## Package Identity & Build Baseline

| Item | Current Value |
| :--- | :--- |
| **Application ID** | `com.music.spotui` |
| **Version Name** | `2.5.0` |
| **Build Version Code** | `202611220` |
| **SDK Range** | `minSdk 26` (Android 8.0) · `compileSdk / targetSdk 36` · Tested on Android 17 / One UI 9 |
| **Toolchain** | Java 21 · Kotlin `2.2.0` · AGP `8.13.2` · KSP `2.2.0-2.0.2` · Hilt `2.57.1` |
| **Media Engine** | AndroidX Media3 `1.11.0` (`ExoPlayer`, `MediaLibraryService`, `OkHttpDataSource`, `WorkManager`) |
| **Submodule Suite** | All **10** `modules/app/libs` Gradle & CI modules active & configurable in Advanced Settings |
| **Archive Engine** | **NovaAc v8** Multi-MB/s Single-Pass Streaming (`Fast`: 4 MiB I/O · 2 MiB AEAD · Zero-Deflate) |
| **Diagnostics** | `DevConsoleManager` Component/Button Fault Ledger · Bluetooth Codec/Conflict Inspector · 20-Point Health Check |
| **Repository** | [lfisher447-afk/SpotUi-Offical](https://github.com/lfisher447-afk/SpotUi-Offical) |
| **License & Trademarks** | See `LICENSE`. Spotify is a trademark of Spotify AB; developers are responsible for respecting provider terms and local law. |

---

## What's New in v2.5.0 (`202611220`)

### 1. New Expressive SongPlay UI (`v2.5`) + Legacy UI Toggle
- **Glassmorphic & Adaptive Player (`NewPlayerLayout`)**: Layered album-art ambient halo, pill source badge, live stream telemetry & `85% · 3D` audio quick dock pill, direct 1-tap **Add to Playlist** and **Love/Like** actions, spring-animated play button, and unified output/share/queue dock.
- **Hot-Swappable Legacy UI Option**: Switch seamlessly anytime under **Settings -> Advanced Settings -> SongPlay UI, Live Clock & Tactile Sounds** between **New Expressive UI (v2.5)** and **Legacy Classic UI** without restarting playback.

### 2. Tactile UI Sound Engine (`UiSoundEngine`)
- **Zero-Asset Procedural PCM Chimes**: Synthesizes low-latency PCM audio feedback on `AudioAttributes.USAGE_ASSISTANCE_SONIFICATION` without interrupting active music.
- **Configurable Everywhere**: Independent toggles for **Tab/Navigation chimes**, **Toggle/Switch tones** (ascending ON / descending OFF), **Slider scrub micro-ticks**, adjustable UI volume (`0–100%`), and 3 sound profiles (**Crisp Studio**, **Warm Acoustic**, **Minimal Soft**).

### 3. First-Login & Post-Update Interactive Patch Notes
- Automatically welcomes users after login or upgrading to `v2.5.0` with a structured **Patch Notes & Changelog Dialog** detailing **"Issues Found & Fixed"** (`"Fixed an issue where [cause] was causing [symptom]"`) and **"Improvements & How They Work"**, plus manual access anytime from Settings.

### 4. Auto-Location Live Home Clock Card (`LiveLocationClockCard`)
- Displays live ticking hours/minutes/seconds (12h/24h locale-aware), formatted calendar date, and auto-detected region & timezone offset (`ZonedDateTime.now()`) right at the top of both classic and SumUp Home feeds—zero location permissions required.

### 5. All 10 `modules/app/libs` Submodules Active & Configurable
- Every Gradle submodule under `modules/app/libs` is compiled, wired through `ModuleLibsBridge`, and individually toggleable & tunable inside **Settings -> Advanced Settings -> Library Submodules**.

### 6. NovaAc Fast Mode Multi-MB/s Overhaul
- **Eliminated 556 KB/s Bottleneck**: Rewrote `MrbeanNovaAcController.Profile.FAST` and `NovaAcExportManager` to stream with **4 MiB I/O buffers**, **2 MiB AEAD frames** (down from 64 KiB cipher roundtrips), **`Deflater.NO_COMPRESSION` (`zeroDeflateAudio = true`)** for already-compressed audio (`FLAC`/`M4A`/`OPUS`/`MP3`), and single-pass inline SHA-256 + `OP_AUDIO_TRAILER`.

### 7. Search Screen "Add to Playlist" Fix
- Fixed Search quick-add so tapping **Add to Playlist** directly on any search result immediately opens `SavedInSheet` and persists into matching local playlists—even for tracks lacking a `spotifyTrackId`—with full `48dp` targets, `PlaylistAddCheck` status highlighting, and Toast confirmations.

### 8. 1000x Logger, Component Fault Detector & Bluetooth Audio Inspector
- **`BT & Faults` Tab in Developer Console**: Inspects connected Bluetooth A2DP/BLE/SCO devices (codec ID, sample rates, channel masks, spatial-offload conflict detection, SCO mic contention warnings), captures interactive component/button faults (`logComponentFault`), and expands the active **`SystemHealthChecker`** to **20 live automated checks**.

### 9. Release Asset Verification & Compliance
- `UpdateChecker` now retrieves and surfaces exact release APK filenames, MB sizes, and `.sha256` digests (with 1-tap clipboard copy) alongside direct browser release handoff (`Intent.ACTION_VIEW`), and unit tests are updated cleanly to `v2.4.0` / `v2.5.0` (`@Config(sdk = [35])`).

---

## `modules/app/libs` — 10 Integrated Submodules

| Module | Gradle Path | Role in SpotUI `v2.5.0` | Configurable In Advanced Settings |
| :--- | :--- | :--- | :--- |
| **Spotify** | `:spotify` (`modules/app/libs/spotify`) | GraphQL home/search/playlists, Canvas videos & SpotiFlac lossless mirrors | Enable toggle + Lossless Provider priority |
| **InnerTube** | `:innertube` (`modules/app/libs/innertube`) | YouTube & YT Music stream resolution, PoToken, DoH & native library sync | Enable toggle + DoH / Proxy / PoToken controls |
| **LRCLIB** | `:lrclib` (`modules/app/libs/lrclib`) | Keyless millisecond-synced LRC database with duration scoring | Enable toggle + waterfall priority `#1–#5` |
| **BetterLyrics** | `:betterlyrics` (`modules/app/libs/betterlyrics`) | TTML XML parser & word-by-word synced karaoke timing | Enable toggle + waterfall priority `#1–#5` |
| **SimpMusic** | `:simpmusic` (`modules/app/libs/simpmusic`) | YouTube Video-ID aligned community synced lyrics API | Enable toggle + waterfall priority `#1–#5` |
| **Paxsenix** | `:paxsenix` (`modules/app/libs/paxsenix`) | Multi-source lyrics relay (Apple Music, Musixmatch, NetEase, Spotify) | Enable toggle + Multi-endpoint vs AMP-only + priority |
| **KuGou** | `:kugou` (`modules/app/libs/kugou`) | High-coverage CJK & international LRC lyrics provider | Enable toggle + Traditional Chinese toggle + priority |
| **Last.fm** | `:lastfm` (`modules/app/libs/lastfm`) | Live `updateNowPlaying`, auto-scrobbling & track love sync | Enable toggle, Mobile session sign-in, Scrobble threshold slider |
| **ShazamKit** | `:shazamkit` (`modules/app/libs/shazamkit`) | Acoustic signature recognition queue & cache management | Enable toggle, Queue telemetry, Clear Cache action |
| **Fastlane** | `modules/app/libs/fastlane` | Automated Play Store release notes, metadata & track channel config | Production / Beta / Nightly track selector |

---

## Project Architecture & Module Map

```text
SpotUi-Offical/
├── app/                              # Main Android app (:app)
│   ├── src/main/java/com/music/spotui/
│   │   ├── audio/                    # AudioEffectController, SpatialWideningAudioProcessor, SpotuiDspAudioProcessor,
│   │   │                             # CrossfadeFilterAudioProcessor (16/24/32-bit/Float PCM), UiSoundEngine
│   │   ├── data/
│   │   │   ├── api/                  # Api, ModuleLibsBridge (all 10 modules), LyricsApi, SpotifySession, SpotifySync
│   │   │   ├── export/               # NovaAcExportManager (single-pass multi-MB/s streaming AEAD)
│   │   │   ├── preferences/          # SettingsPref, ModuleAndExperiencePref, DownloadPref (Music/spotui), OfflineCollectionsPref
│   │   │   ├── update/               # UpdateChecker (release asset + SHA-256 digest inspection)
│   │   │   └── youtube/              # YouTubeMusicSync (Native Playlists / Liked Music / Albums + OAuth PKCE)
│   │   ├── di/                       # Hilt DI modules & SongPlayer / SpotifyWebPlayer orchestration
│   │   ├── engine/                   # PlaybackController, MediaNotificationAdapter (Exit Mini-Notif)
│   │   ├── ui/
│   │   │   ├── components/           # PatchNotesDialog, LiveLocationClockCard, SavedInSheet, ThanksgivingCountdownBanner
│   │   │   ├── navigation/           # AdaptiveNavigation (BottomBar / Rail / TopTabs / Sidebar), Routes, MyNavHost
│   │   │   ├── screens/              # Home, Search, Library, Downloads, Player (+ NewPlayerLayout), Settings, DevConsole
│   │   │   └── theme/                # Material 3 expressive Color, Typography (system SansSerif), ThemePreferences
│   │   └── util/                     # DevConsoleManager, BluetoothAudioInspector, SystemHealthChecker (20 checks), BackupHelper
│   └── src/test/                     # Multi-module JVM & Robolectric test suite (executed cleanly in GitHub Actions)
├── modules/app/libs/                 # All 10 submodules (spotify, innertube, lrclib, betterlyrics, kugou,
│                                     # simpmusic, paxsenix, lastfm, shazamkit, fastlane)
├── org/                              # Eclipse MrBean network & multi-MB/s MrbeanNovaAcController
├── tools/                            # build-release-apk.sh & archive utilities
└── .github/workflows/                # release-apk.yml (CI unit tests + Release APK + SHA-256 + GitHub Release)
```

---

## Build & Release Workflows

### Option A — Automated GitHub Actions (`.github/workflows/release-apk.yml`)
1. Push a tag matching `v*` (e.g., `git tag v2.5.0 && git push origin v2.5.0`) or manually trigger **Actions -> Build & Publish Release APK -> Run workflow**.
2. CI automatically runs module unit tests (`:spotify:test`, `:innertube:testDebugUnitTest`, `:app:testDebugUnitTest`), builds `:app:assembleRelease`, verifies APK signatures via `apksigner`, generates a `.sha256` digest, and publishes **`SpotUI-v2.5.0-release.apk`** + **`.sha256`** to **GitHub Releases**.

### Option B — Local Release Script (`tools/build-release-apk.sh`)
Requires **JDK 21** and **Android SDK Platform 36**:

```bash
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-21
bash tools/build-release-apk.sh
```

Outputs `dist/SpotUI-v2.5.0-release.apk` and `dist/SpotUI-v2.5.0-release.apk.sha256`.

> **Signing Notice:** The checked-in `sharedDebug` signing config enables seamless local sideload upgrades across test builds. Always swap in a private release keystore before public store distribution, and never commit account cookies, ARLs, tokens, or private keys.

---

## Permissions Overview

SpotUI adheres strictly to least-privilege and Google Play Creator Program compliant Android permissions:
- **Network & Playback**: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK`, `POST_NOTIFICATIONS`.
- **Audio & Bluetooth**: `MODIFY_AUDIO_SETTINGS`, `BLUETOOTH` (`maxSdkVersion 30`), `BLUETOOTH_CONNECT`, `VIBRATE`.
- **Media & Export**: `READ_MEDIA_AUDIO` (API 33+) / `READ_EXTERNAL_STORAGE` (`maxSdkVersion 32`) for local audio playback, `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion 28`) for pre-Scoped-Storage `Music/spotui` export (API 29+ uses zero-permission `MediaStore`).

---

## Version History (v1.5.1 — v2.5.0)

| Version | Code | Summary |
| :--- | :--- | :--- |
| **v2.5.0** | `202611220` | **Expressive Suite, Multi-MB/s NovaAc & 10-Module Integration**: Added `NewPlayerLayout` (`v2.5`) + Legacy UI toggle in Advanced Settings; added procedural PCM Tactile UI Sound Engine (tab/toggle/slider chimes); added first-login & post-update Patch Notes dialog; added auto-location live Home Clock card; wired & exposed all 10 `modules/app/libs` submodules (`spotify`, `innertube`, `lrclib`, `betterlyrics`, `kugou`, `simpmusic`, `paxsenix`, `lastfm`, `shazamkit`, `fastlane`) in Advanced Settings; upgraded DevConsole with component fault detection, 20-point Health check & Bluetooth codec/conflict diagnostics; fixed Search screen direct Add-to-Playlist; rewrote NovaAc Fast Mode to 4 MiB buffers, 2 MiB AEAD frames & zero-deflate multi-MB/s speed; added SHA-256 release asset verification metadata. |
| **v2.4.0** | `202611210` | **Pro Audio & Native Sync Release**: Fixed offline downloaded track playback (`SimpleCache` bypass, container sniff & repair, Play All / Shuffle / Verify & Repair); added independent `0–100%` In-App Volume slider (non-OS); rebuilt 3D Spatial Soundstage + 7-Band EQ for 16/24/32-bit/Float PCM + mid-track offload->PCM switching; added broadcast-only Exit Play/Pause Mini-Notification that never opens the app; upgraded native YouTube Music sync for all playlists, Liked Music & saved albums directly into Library/Album screens. |
| **v2.3.0** | `202611180` | **Thanksgiving Harvest Update**: Immediate foreground service promotion (zero `ForegroundServiceDidNotStartInTimeException`), Bluetooth-safe in-process 3D Spatial + HRTF + 7-band EQ routing, fixed `Music/spotui` MediaStore export & auto-export, live Thanksgiving Countdown Banner, custom adaptive/monochrome Thanksgiving icons. |
| **v2.2.0** | `202610108` | **Playback Resurrection & 18-Point Health Scanner**: Fixed Samsung One UI Now Bar desync, rebuilt 7-band Biquad DSP + 3D Binaural Spatializer, Lossless Provider Priority with Solo Primary, Deezer ARL Blowfish decryption, Search filters, A-B Loop, Sleep Timer, Quick Queue, and 18-point active Health Scanner. |
| **v2.1.7** | `202610107` | **Thanksgiving Harvest Gold & Auth Hardening**: Harvest Gold preset, `PlaybackAuthState` & `AuthSessionStore`, DoH/proxy/PoToken controls, YouTube channel verification, Speed/Pitch/Skip Silence tuning, manual update checker, `CrashReportPrompt`, fixed launch NPE in `PlaybackService.updateCurrentMetadata()`. |
| **v2.1.6** | `202610106` | Dual-mode Chrome-UA Spotify WebView login, 1-tap `sp_dc` cookie login, external browser handoff, guest mode, WebView crash/SSL recovery, dynamic download container extensions. |
| **v2.1.5** | `202610105` | NovaAc v8 Header-Bound AEAD encryption, `MrbeanNovaAcController` v8, LRCLIB millisecond lyric sync, M3 corner scaling. |
| **v2.1.0** | `202610100` | NovaAc v6 64 KiB framed AEAD engine eliminating cipher heap spikes, MrBean transport telemetry. |
| **v2.0.0** | `202609200` | Upgraded Media3 to `1.11.0`, unified MrBean Media3 transport, Google OAuth YouTube playlist sync, Theme Studio (JSON & artwork palettes), NovaAc v5 container integrity. |
| **v1.9.9** | — | Sequential playlist downloads, fresh resolver fallback, 5-band EQ & software spatializer, YouTube Music companion, collapsible library groups. |
| **v1.5.1–1.9.5** | — | Timed LRC parser, local library continuity, collapsible settings, persistent `AppDiagnostics`, Android Auto, full-audio NovaAc & Keystore metadata. |

---

<div align="center">

## Support SpotUI — Star History

[![Star History Chart](https://api.star-history.com/svg?repos=lfisher447-afk/SpotUi-Offical&type=Date)](https://star-history.com/#lfisher447-afk/SpotUi-Offical&Date)

**If SpotUI helped your Android listening setup, please click the Star button at the top right of [lfisher447-afk/SpotUi-Offical](https://github.com/lfisher447-afk/SpotUi-Offical)!**

</div>
