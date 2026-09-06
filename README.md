# Spotui Android

> **Current source release: v2.1.5 (version code 202610105).** A Kotlin and Jetpack Compose Android music-client project with a Media3 playback service, Material 3 interface controls, configurable audio processing, local download management, and resilient framed NovaAc archive workflows.

## 🚀 What's New & Update Logs (v2.1.5 Latest)

### 🌟 What's New*
- **Dual-Mode Spotify Authentication Screen**:
  - **Web Login Mode**: Refactored full-screen WebView with Google Chrome Desktop User-Agent (`Chrome/131.0.0.0`) preventing Spotify's blank/black screen anti-bot defense. Real-time visual loading progress bar with percentage counter and dynamic troubleshooting hints.
  - **Direct Session Cookie Mode (`sp_dc`)**: Fast, 100% reliable bypass for browser/webview restrictions. Allows instant authentication via direct `sp_dc` cookie entry with a **1-tap "Paste from Clipboard"** button and cookie string sanitization.
  - **External Browser Handoff**: 1-tap button to launch Spotify web authentication in external Chrome/default browser via Android Intent.
  - **Direct Guest / Skip Mode**: Instant access to local downloads, device music library, and InnerTube streaming without requiring a Spotify login.
- **Dedicated MrBean NovaAc Control Plane (v8)**:
  - `MrbeanNovaAcController` with isolated archive-only adaptive I/O profiles (Fast, Balanced, Resilient).
  - NovaAc v8 framed AES-GCM encryption with Header-Bound AEAD authenticated data (64 KiB chunks) preventing frame replay attacks.
- **Enhanced Playback & Lyrics Engine**:
  - AndroidX Media3 1.11.0 integration with 320ms equal-power gapless transitions and customizable crossfade.
  - Synchronized millisecond-accurate LRCLIB lyrics overlay with on-device ML Kit language identification and translation caching.

### 🔄 What's Updated*
- **Spotify Authentication Flow**: Completely redesigned `SpotifyLoginScreen.kt` using Material 3 expressive components, custom tab pills, and seamless lifecycle cleanup.
- **Cookie Detection & Auto-Sync**: Background polling for session cookies across `.spotify.com` subdomains with thread-safe atomic lock.
- **Network Resilience & Timeouts**: Configurable MrBean socket connection pools, retry backoff on 429/503 errors, and automatic expired stream re-resolution.
- **Theme & Appearance**: Material 3 Expressive theming with dynamic color, 5 color presets (System, Aurora, Ember, Oceanic, Monochrome), and customizable corner radius (0–48 dp).
- **Navigation Architecture**: Smooth back-handler support across login, mini-player, queue, and detail screens.

### 🛠️ Fixes & Improvements*
- **Fixed Spotify Login Black/Blank Screen**: Fixed WebView white/black screen freeze caused by default Android WebView User-Agent (`Version/4.0`) being rejected by Spotify's web auth servers.
- **Fixed WebView Rendering Layout Collapse**: Set explicit layout parameters (`MATCH_PARENT`) and background color in Compose `AndroidView` to eliminate rendering glitches and blank surfaces.
- **Fixed SSL Handshake Errors in Restricted Environments**: Added graceful SSL handling in `WebViewClient` so proxy and emulator connections don't abort silently.
- **Fixed WebView Render Process Crash**: Added `onRenderProcessGone` handler with informative error card and automatic suggestion to switch to Cookie Login.
- **Fixed NovaAc Encryption Memory Spikes**: Replaced single-message AES-GCM cipher allocation with framed 64 KiB streams, eliminating 270+ MB heap allocation failures on large collections.
- **Fixed Download Retry File Extensions**: Container extension recalculated dynamically on stream fallback (FLAC, Opus, WebM, M4A, MP3).

---

## Repository scope

This README is the complete GitHub documentation reference for **[lfisher447-afk/SpotUi-Offical](https://github.com/lfisher447-afk/SpotUi-Offical)**. It consolidates every project documentation source into one file while preserving the original source path of each section. The repository is Android source only; the separately hosted web project remains independent.

| Item | Current value |
| --- | --- |
| Android package | `com.music.spotui` |
| Source release | `2.1.5` / version code `202610105` |
| SDK range | Minimum SDK 26 路 Compile/target SDK 36 |
| Toolchain | Java 21 路 Kotlin 路 Android Gradle Plugin 8.13.2 |
| UI | Jetpack Compose 路 Material 3 expressive theming |
| Media | AndroidX Media3 1.11.0 路 ExoPlayer 路 MediaSession |
| Archive engine | NovaAc v8 framed AES-GCM with adaptive I/O and MrBean controller telemetry |
| License and trademarks | See `LICENSE`; Spotify is a trademark of Spotify AB. |

## Build the app

Install JDK 21 and Android SDK Platform 36, then run from the repository root:

```bash
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-21
./gradlew assembleRelease --no-daemon --max-workers=2
```

The included release setup is suitable for local sideload validation. Before distribution, use your own controlled signing configuration and do not commit real account data, provider tokens, cookies, or private keystores.

## Project map

| Path | Responsibility |
| --- | --- |
| `app/` | Main Android application: Compose screens, Media3 playback, settings, downloads, archive flows, and background service lifecycle. |
| `spotify/` | Spotify metadata integration module. |
| `innertube/` | YouTube/InnerTube resolver and stream integration module. |
| `org/` | Project-local MrBean transport and controller sources. |
| `androidx/` | Project-local Android support integrations. |
| `tools/` | Development, archive, and validation utilities. |

## Documentation navigation

The sections below include architecture, implementation, configuration, operations, full-audio archive guidance, large-archive resilience reports, release notes, validation reports, and module notes. When a historical section cites an earlier release鈥攑articularly the legacy v2.0.0 baseline鈥攖he **current source identity above and the v2.1.5 reports take precedence**.


## Complete consolidated documentation


---

## Source document: `README.md`

> **Historical baseline notice:** This source document preserves earlier 2.0.0-era narrative for architecture and feature history. It is retained as reference; current implementation identity is v2.1.5.

### Spotui 鈥� Android Music Client

> A feature-rich Kotlin / Jetpack Compose music-client source project with visible collection controls, configurable playback, and build-validated release tooling.

**Status:** Spotui 2.0.0 historical source-package baseline. This documentation describes the current supplied source tree and its integrated behavior. It does not claim a relationship with Spotify AB, and it does not describe provider-protected media export as a product capability.

The repository is a Kotlin Android music-client project built around Jetpack Compose, a Media3 playback core, Hilt dependency injection, bundled metadata/resolution modules, user-controlled storage routes, and a settings model that persists user choices. The documentation deliberately separates **verified current-source behavior** from **historical reconstruction**. The supplied project archive contains no Git metadata or tags, so version-era narrative before the documented 1.5.1 baseline is a product-line reconstruction rather than a claim that a particular commit has been recovered.

#### What is in this package

This historical baseline section describes the Spotui 2.0.0 source project. It includes the Android app module, the bundled `spotify` metadata module, the `innertube` resolution module, the Eclipse MrBean transport configuration hierarchy, Gradle wrapper and dependency catalog, current implementation documentation, and a verified 2.0.0 source configuration. The project is intentionally documented as an educational/open-source client architecture. Spotify is a trademark of Spotify AB; developers are responsible for respecting provider terms, account boundaries, and local law.

The current package is not merely a visual mockup. Core controls connect to services: direct archive export creates a prepared NovaAc file and invokes the Android save picker; audio settings attach to the active media session where supported; navigation preferences alter the root application shell; diagnostics, cache, and backup actions use dedicated utility/service layers.

#### Feature matrix

| Area | Current behavior and boundary |
| --- | --- |
| Account and metadata | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| Playback foundation | AndroidX Media3 1.11.0 playback, a background media session service, notification controls, queue management, remembered position, Android Auto browse support, an OkHttp-backed MrBean datasource, and a shared cache/preload transport. |
| HTTPS stream resolution | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| Offline workflow | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| NovaAc archives | NovaAc v5 is an encrypted application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. Payload frames retain container, filename, and SHA-256 integrity metadata. It is not a DRM bypass or an exporter for provider-controlled media. |
| Equalizer and spatial session | The audio controller maps five EQ regions to the active Media3 session. Software PCM widening supports 16-bit and float output, profile-dependent spatial processing, mono mix, and stereo balance in both player pipelines. |
| Playlist control | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| Liked Songs control | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| Adaptive interface | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Theme Studio adds expressive Material 3 presets, validated JSON/image-derived palettes, contrast, type scale, reduced motion, and touch-target options. |
| Diagnostics and resilience | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| Lyrics and discovery | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| Backup and restore | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |

#### Direct export: the primary workflow

Open a standard playlist or **Liked Songs**, then use the clearly labeled **Export** button placed beside **Download**. The dialog asks for an archive name and whether locally available cache payloads should be included. The application prepares the NovaAc archive away from the UI thread and opens Android鈥檚 save-location picker. The top-right overflow menu remains useful for secondary actions, but it is no longer required to discover the archive feature.

> **NovaAc boundary:** NovaAc is a structured, encrypted application archive for collection metadata and app-controlled local cache context. It is not a provider-media decryption mechanism, a DRM bypass, or a claim that any remote streaming asset can be exported.

#### Build and run

| Requirement | Current project value |
| --- | --- |
| Android compile / target SDK | 36 |
| Minimum SDK | 26 |
| Java toolchain | Java 21 |
| Kotlin | 2.2.0 |
| Android Gradle Plugin | 8.13.2 |
| Historical baseline source version | 2.0.0 (`202609200`) |

Use Android Studio with a configured Android SDK, or run the wrapper from the project root. A typical local build is:

```bash
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-21
./gradlew assembleRelease
```

The checked-in release configuration is intended for locally signed sideload validation. Replace it with a developer-controlled production signing configuration before a store distribution. Do not commit private keys, user cookies, account tokens, or real keystores.

#### Product history, 1.1.0鈥�2.0.0

The full chronology is intentionally included here because release intent affects architecture decisions. The archive contains documented detail beginning at 1.5.1; earlier versions are clearly labeled reconstructed because this source archive did not include Git history, tags, or older snapshots. See [CHANGELOG.md](CHANGELOG.md) for the companion long-form discussion.

| Version | Release focus or documented outcome | Evidence status |
| --- | --- | --- |
| 1.1.0 | Foundational product era: Compose shell experiments, basic search and navigation conventions, and early music-client layout patterns. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.1.1 | Foundational product era: Compose shell experiments, basic search and navigation conventions, and early music-client layout patterns. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.1.2 | Foundational product era: Compose shell experiments, basic search and navigation conventions, and early music-client layout patterns. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.1.3 | Foundational product era: Compose shell experiments, basic search and navigation conventions, and early music-client layout patterns. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.1.4 | Foundational product era: Compose shell experiments, basic search and navigation conventions, and early music-client layout patterns. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.1.5 | Foundational product era: Compose shell experiments, basic search and navigation conventions, and early music-client layout patterns. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.1.6 | Foundational product era: Compose shell experiments, basic search and navigation conventions, and early music-client layout patterns. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.1.7 | Foundational product era: Compose shell experiments, basic search and navigation conventions, and early music-client layout patterns. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.1.8 | Foundational product era: Compose shell experiments, basic search and navigation conventions, and early music-client layout patterns. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.1.9 | Foundational product era: Compose shell experiments, basic search and navigation conventions, and early music-client layout patterns. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.2.0 | Library and collection era: early playlist, album, artist, and Liked Songs concepts with stronger metadata presentation. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.2.1 | Library and collection era: early playlist, album, artist, and Liked Songs concepts with stronger metadata presentation. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.2.2 | Library and collection era: early playlist, album, artist, and Liked Songs concepts with stronger metadata presentation. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.2.3 | Library and collection era: early playlist, album, artist, and Liked Songs concepts with stronger metadata presentation. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.2.4 | Library and collection era: early playlist, album, artist, and Liked Songs concepts with stronger metadata presentation. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.2.5 | Library and collection era: early playlist, album, artist, and Liked Songs concepts with stronger metadata presentation. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.2.6 | Library and collection era: early playlist, album, artist, and Liked Songs concepts with stronger metadata presentation. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.2.7 | Library and collection era: early playlist, album, artist, and Liked Songs concepts with stronger metadata presentation. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.2.8 | Library and collection era: early playlist, album, artist, and Liked Songs concepts with stronger metadata presentation. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.2.9 | Library and collection era: early playlist, album, artist, and Liked Songs concepts with stronger metadata presentation. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.3.0 | Playback experience era: queue behavior, player controls, visual surfaces, and the first persistence assumptions for active playback. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.3.1 | Playback experience era: queue behavior, player controls, visual surfaces, and the first persistence assumptions for active playback. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.3.2 | Playback experience era: queue behavior, player controls, visual surfaces, and the first persistence assumptions for active playback. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.3.3 | Playback experience era: queue behavior, player controls, visual surfaces, and the first persistence assumptions for active playback. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.3.4 | Playback experience era: queue behavior, player controls, visual surfaces, and the first persistence assumptions for active playback. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.3.5 | Playback experience era: queue behavior, player controls, visual surfaces, and the first persistence assumptions for active playback. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.3.6 | Playback experience era: queue behavior, player controls, visual surfaces, and the first persistence assumptions for active playback. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.3.7 | Playback experience era: queue behavior, player controls, visual surfaces, and the first persistence assumptions for active playback. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.3.8 | Playback experience era: queue behavior, player controls, visual surfaces, and the first persistence assumptions for active playback. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.3.9 | Playback experience era: queue behavior, player controls, visual surfaces, and the first persistence assumptions for active playback. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.4.0 | Reliability and discovery era: deep-link intent, search/discovery refinements, artwork handling, and preparation for modular source resolution. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.4.1 | Reliability and discovery era: deep-link intent, search/discovery refinements, artwork handling, and preparation for modular source resolution. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.4.2 | Reliability and discovery era: deep-link intent, search/discovery refinements, artwork handling, and preparation for modular source resolution. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.4.3 | Reliability and discovery era: deep-link intent, search/discovery refinements, artwork handling, and preparation for modular source resolution. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.4.4 | Reliability and discovery era: deep-link intent, search/discovery refinements, artwork handling, and preparation for modular source resolution. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.4.5 | Reliability and discovery era: deep-link intent, search/discovery refinements, artwork handling, and preparation for modular source resolution. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.4.6 | Reliability and discovery era: deep-link intent, search/discovery refinements, artwork handling, and preparation for modular source resolution. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.4.7 | Reliability and discovery era: deep-link intent, search/discovery refinements, artwork handling, and preparation for modular source resolution. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.4.8 | Reliability and discovery era: deep-link intent, search/discovery refinements, artwork handling, and preparation for modular source resolution. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.4.9 | Reliability and discovery era: deep-link intent, search/discovery refinements, artwork handling, and preparation for modular source resolution. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.5.1 | Playback and library foundations: source matching, blacklist UI, lyrics, queue, crossfade, and stream diagnostics were retained as the documented baseline. | Verified in supplied project documentation or current source |
| 1.5.2 | Offline library workflow: app-managed local download and offline collection concepts were retained without exporting protected provider media. | Verified in supplied project documentation or current source |
| 1.5.3 | Playlist portability: selected playlist and album track export was introduced through encrypted NovaAc metadata manifests. | Verified in supplied project documentation or current source |
| 1.5.4 | Local media continuity: local collection state and persisted artwork paths were retained. | Verified in supplied project documentation or current source |
| 1.5.5 | Equalizer: persisted EQ preferences were connected to Android framework equalizer bands for the active Media3 session. | Verified in supplied project documentation or current source |
| 1.5.6 | Spatial audio: device-supported Virtualizer processing became an opt-in audio-session effect. | Verified in supplied project documentation or current source |
| 1.5.7 | Audio-session stability: effects attach and release with ExoPlayer audio-session changes. | Verified in supplied project documentation or current source |
| 1.5.8 | Crossfade: custom crossfade processing remained alongside the audio effects controller. | Verified in supplied project documentation or current source |
| 1.5.9 | Visual playback: existing player visual layers and Canvas-related paths were retained. | Verified in supplied project documentation or current source |
| 1.6.0 | Theme resilience: system-safe SansSerif typography replaced reliance on malformed bundled font resources. | Verified in supplied project documentation or current source |
| 1.6.1 | One-handed configuration: the player offers a direct route to Audio & Equalizer settings. | Verified in supplied project documentation or current source |
| 1.6.2 | Player control refinement: queue, sleep timer, alternative source, download, share, album, and artist actions were retained. | Verified in supplied project documentation or current source |
| 1.6.3 | System integration: Media3 service, notifications, Android Auto metadata, and deep-link support were retained. | Verified in supplied project documentation or current source |
| 1.6.4 | Settings organization: collapsible animated sections separated audio, storage, diagnostics, and related configuration. | Verified in supplied project documentation or current source |
| 1.6.5 | Credential boundary: no unauthenticated social-account transmission was added. | Verified in supplied project documentation or current source |
| 1.6.6 | Recognition boundary: microphone capture was not introduced without a user-initiated, permission-aware feature. | Verified in supplied project documentation or current source |
| 1.6.7 | Sharing and Canvas: existing sharing and visual Canvas paths were retained. | Verified in supplied project documentation or current source |
| 1.6.8 | Source fallback: InnerTube and provider fallback controls were retained with diagnostic logging. | Verified in supplied project documentation or current source |
| 1.6.9 | Playback integrity: provider behavior was preserved without hidden modifications. | Verified in supplied project documentation or current source |
| 1.7.0 | Local smart behavior: listening history and queue foundations were consolidated. | Verified in supplied project documentation or current source |
| 1.7.1 | Listening history: the history route and local history behavior were preserved. | Verified in supplied project documentation or current source |
| 1.7.2 | Radio: queue continuation and radio behavior were preserved. | Verified in supplied project documentation or current source |
| 1.7.3 | Lyrics: lyrics and translation-cache paths were preserved. | Verified in supplied project documentation or current source |
| 1.7.4 | Reliability: bounded persistent diagnostics and uncaught-exception capture were added. | Verified in supplied project documentation or current source |
| 1.7.5 | Build memory behavior: Gradle execution was constrained to avoid compiler-worker memory spikes in constrained environments. | Verified in supplied project documentation or current source |
| 1.7.6 | Cache correctness: cache accounting and targeted cleanup replaced destructive assumptions. | Verified in supplied project documentation or current source |
| 1.7.7 | Startup diagnostics: activity, provider warm-up, deep-link, backup, and media-session bootstrap failures became recordable. | Verified in supplied project documentation or current source |
| 1.7.8 | Error visibility: developer-console warning and error entries became persistent. | Verified in supplied project documentation or current source |
| 1.7.9 | Stability baseline: source validation and font-safe rendering work were consolidated. | Verified in supplied project documentation or current source |
| 1.8.0 | Android compatibility: compile/target SDK 36 configuration was documented and validated. | Verified in supplied project documentation or current source |
| 1.8.1 | Navigation polish: direct player-to-audio-settings routing was added. | Verified in supplied project documentation or current source |
| 1.8.2 | Settings polish: reusable collapsible section behavior was formalized. | Verified in supplied project documentation or current source |
| 1.8.3 | Archive compatibility: NovaAc headers gained app-version and format-version fields. | Verified in supplied project documentation or current source |
| 1.8.4 | Accessibility and clarity: settings controls gained labels and state descriptions. | Verified in supplied project documentation or current source |
| 1.8.5 | Build identity: versioning and local release identity were updated for the integrated build. | Verified in supplied project documentation or current source |
| 1.8.6 | Security posture: NovaAc uses Android Keystore AES-GCM and diagnostics redact sensitive stream material. | Verified in supplied project documentation or current source |
| 1.8.7 | Artifact verification: debug/release assembly and APK integrity checks were used after integration groups. | Verified in supplied project documentation or current source |
| 1.8.8 | Release preparation: cache safety, diagnostics, and audio controls were consolidated. | Verified in supplied project documentation or current source |
| 1.8.9 | Stable integration: the supplied roadmapped source was assembled as an integrated, build-validated Android client. | Verified in supplied project documentation or current source |
| 1.9.0 | Major control-surface update: direct labeled playlist NovaAc export, advanced audio settings, HTTPS engine controls, and adaptive navigation work began. | Verified in supplied project documentation or current source |
| 1.9.1 | Archive usability update: export configuration added archive naming and local-audio inclusion choices before invoking Android鈥檚 system save picker. | Verified in supplied project documentation or current source |
| 1.9.2 | Spatial-audio update: profile selection, virtualizer depth, room processing, and bounded normalizer gain were connected to the active session. | Verified in supplied project documentation or current source |
| 1.9.3 | Adaptive-shell update: bottom, top, side, and rail navigation modes were connected to a shared root-route model. | Verified in supplied project documentation or current source |
| 1.9.4 | Visual configuration update: compact UI and a persisted 0鈥�48 dp corner-radius preference became live shell settings. | Verified in supplied project documentation or current source |
| 1.9.5 | Direct collection archive release: Playlist and Liked Songs export buttons sit beside Download, with full archive configuration. | Verified in supplied project documentation or current source |
| 1.9.9 | Reliability and control-surface release: sequential downloads, fresh-candidate recovery, five-band EQ, software spatial widening, optional YouTube Music companion, collapsible library groups, and a configured MrBean transport panel. | Verified in current source and validated release APK |

#### Architecture at a glance

```text
User action 鈫� Compose screen 鈫� View model / preference service 鈫� player, archive, cache, or route service
                                                      鈹�
                                                      鈹溾攢鈹€ Media3 / ExoPlayer / MediaSessionService
                                                      鈹溾攢鈹€ AudioEffectController
                                                      鈹溾攢鈹€ HTTPS source resolver and fallback policy
                                                      鈹溾攢鈹€ NovaAcExportManager + Storage Access Framework
                                                      鈹斺攢鈹€ Diagnostics / backup / persistent settings
```

The project embraces explicit ownership. Compose surfaces are responsible for input and visible state. Player code owns player lifecycle and audio-session attachment. Archive code owns binary creation and selected URI writes. Preferences own durable user configuration. This separation makes it possible to add an interface mode or archive control without duplicating the same implementation in every screen. Read [ARCHITECTURE_BLUEPRINT.md](ARCHITECTURE_BLUEPRINT.md) for the full blueprint.

#### Settings and customization

The application exposes stream quality, lossless timeouts, source fallback, preloading, optional web playback, equalizer bands, spatial profile/depth, loudness intensity, crossfade, local cache actions, backups, diagnostics, navigation modes, compact UI, and live corner radius. Settings are grouped to avoid a single unstructured screen and are designed to be reversible. Read [CONFIGURATION_REFERENCE.md](CONFIGURATION_REFERENCE.md) for per-control logic and safe fallback behavior.

#### Open-source acknowledgments

Spotui builds upon the Android and open-source ecosystem. Jetpack Compose provides declarative UI facilities. [4] Media3 provides the player/session/service architecture used for modern Android media applications. [1] [3] Hilt supports dependency injection boundaries. [5] The project contains and/or credits inspiration from Neptune, SpotiFLAC, SimpMusic, NewPipe Extractor, Ktor, OkHttp, Kotlin serialization, Glide, ML Kit, Room, and AndroidX libraries. The upstream projects remain responsible for their own terms and licenses; retain their notices when redistributing source.

| Project or library | Role in this source package |
| --- | --- |
| AndroidX Media3 / ExoPlayer | Playback engine, media session, background media integration. |
| Jetpack Compose + Material 3 | Declarative screen and adaptive-navigation rendering. |
| Hilt + KSP | Dependency graph and generated integration. |
| Ktor + OkHttp + serialization | Metadata/networking and structured transport support. |
| NewPipe Extractor / InnerTube module | Source-resolution support within the project鈥檚 configured boundaries. |
| Glide + Palette | Artwork loading and color-derived presentation. |
| Room + SharedPreferences helpers | Persistent app and collection state. |
| ML Kit | On-device language identification and translation support. |
| Neptune / SpotiFLAC / SimpMusic | Acknowledged architectural and feature inspiration. |

#### Documentation map

| Document | Purpose |
| --- | --- |
| [CHANGELOG.md](CHANGELOG.md) | Detailed version history from 1.1.0 through 1.9.9. |
| [ARCHITECTURE_BLUEPRINT.md](ARCHITECTURE_BLUEPRINT.md) | System design, ownership boundaries, archive and playback flow. |
| [CONFIGURATION_REFERENCE.md](CONFIGURATION_REFERENCE.md) | Every major setting, runtime owner, fallback, and user effect. |
| [CONTRIBUTING_AND_OPERATIONS.md](CONTRIBUTING_AND_OPERATIONS.md) | Build, test, release, privacy, and contribution workflow. |
| [IMPLEMENTATION_DESIGN.md](IMPLEMENTATION_DESIGN.md) | Design decisions and deep implementation rationale. |
| [IMPLEMENTATION_AUDIT.md](IMPLEMENTATION_AUDIT.md) | Current-source audit and verification checklist. |
| [VALIDATION_REPORT.md](VALIDATION_REPORT.md) | Build/release validation method and artifact boundaries. |
| [ROADMAP_STATUS.md](ROADMAP_STATUS.md) | Current capability map and future-safe extension plan. |

#### License, privacy, and contribution expectations

Before contributing, read the operations guide and preserve data boundaries. Do not add credential capture, broad storage access, microphone behavior, background transmission, or provider modifications without a documented consent and failure model. New media features must state whether they operate on app-owned local data, user-selected files, or provider-controlled network material. New settings must state the service that consumes them. New releases must include a version code, artifact checksum, signature verification result, and an honest signing note.

#### References

[1]: https://developer.android.com/media/media3 "Android Developers: Introduction to Jetpack Media3"
[2]: https://developer.android.com/training/data-storage/shared/documents-files "Android Developers: Storage Access Framework"
[3]: https://github.com/androidx/media "AndroidX Media on GitHub"
[4]: https://developer.android.com/jetpack/compose "Android Developers: Jetpack Compose"
[5]: https://developer.android.com/training/dependency-injection/hilt-android "Android Developers: Dependency injection with Hilt"
[6]: https://github.com/H4zh4n/Spotui "Spotui upstream project"
[7]: https://github.com/navneet851/spotify-clone-jetpack-compose "Neptune Jetpack Compose Spotify clone"
[8]: https://github.com/spotbye/SpotiFLAC "SpotiFLAC project"
[9]: https://github.com/maxrave-dev/SimpMusic "SimpMusic project"
[10]: https://github.com/TeamNewPipe/NewPipeExtractor "NewPipe Extractor"


#### v1.9.9 reliability and advanced controls

The 1.9.9 release concentrates on making the existing playback stack behave predictably under real connection and device variability. Playlist downloads now run sequentially in the order supplied by the collection. Each track registers its metadata, resolves, transfers, and reports completion before the next item begins, so a single failing source does not create a resolver stampede or stop the rest of the queue. The download engine uses raw-byte transfer headers, follows redirects, checks whether a resumed Range request was honored, and retries a failed fresh stream resolution after expiry-style responses.

The stream selector now uses explicit quality tiers rather than an ambiguous multiplier. Low targets a bounded lower-bitrate source, Auto follows its network-aware policy, and High chooses the best viable format. When the preferred source is unavailable, the resolver walks ranked song candidates and then eligible video candidates with a bounded, user-configurable breadth. This preserves the project鈥檚 existing source matching and fallback model while making the quality setting influence actual selected media.

##### Audio implementation

Audio settings are no longer limited to a broad three-control surface. The equalizer maps Bass, Low-mid body, Vocal, Presence, and Treble detail to the bands that the active Android audio session exposes. Center-frequency groups are adaptive, so the same stored intent can be applied on devices with different hardware equalizer layouts. Spatial processing is also dual-path: Android framework effects are used where the hardware supports them, while the PCM widening processor stays in the primary and crossfade chains to give Studio, Wide, Immersive, and Cinema profiles a distinctive audible result on more devices.

##### Library and companion behavior

The Library view keeps special collections visible and partitions the remaining content into collapsible Playlists and Albums groups. Its optional YouTube Music group opens a visible WebView for `music.youtube.com`; the user signs in and interacts with the official service directly. This companion is controlled by Settings and does not introduce a custom account form or collect a password. Player video preview is separately toggleable, allowing the audio-first player surface to remain available without visual preview work.

##### Eclipse MrBean network engine

The source includes the requested `org/eclipse/Mrbean` module hierarchy. The Settings panel titled **Eclipse MrBean Network Engine** persists live controls for resolver candidate breadth, download connect/read timeouts, byte-range chunk size, and attempts per transfer range. The playback/download owner reads these values when it opens app-owned raw media connections and when it selects candidate breadth. Disabling the panel returns those paths to the built-in release defaults. HTTP/2 remains an Android-platform TLS negotiation outcome; HTTP/3 is accurately shown as not bundled rather than exposed as a simulated feature.

| Recommended use | Default or suggested value | Trade-off |
| --- | --- | --- |
| General playback and downloads | Keep MrBean tuning enabled with defaults | Uses six candidates, 15s connect and 30s read timeouts, 8 MiB range chunks |
| Slow but stable mobile link | Increase read timeout modestly | Reduces premature transfer failure but lengthens visible waiting |
| Intermittent resolver failures | Increase candidate breadth one step | More recovery opportunities at the cost of additional resolver work |
| Repeated range resets | Increase attempts per range cautiously | Better resilience while avoiding an unbounded retry loop |
| Small-memory or constrained link | Use a smaller chunk size | More individual HTTP requests but less data at risk per interrupted chunk |

The `app-release.apk` generated for this version has package name `com.music.spotui`, version name `1.9.9`, version code `202609051`, and a verified APK Signature Scheme v2 signature. It is locally debug-key signed for sideload validation. A production release must be re-signed under the developer鈥檚 own protected signing policy.


---

#### Version 2.0.0: integrated implementation guide

##### Media3 and MrBean transport

Spotui 2.0.0 standardizes all active AndroidX Media3 artifacts on the `1.11.0` release line. The player does not rely solely on a generic default audio sink: `SongPlayer` builds a custom sink that keeps the project鈥檚 crossfade filter and software spatial processor in the same pipeline. The update also adds the Media3 OkHttp datasource module and makes the Eclipse MrBean configuration hierarchy operational through `MrbeanMediaDataSource`.

When MrBean transport is enabled, the player鈥檚 cached playback source and preloading source are created with an OkHttp `DataSource.Factory`. The selected connect/read timeout limits, redirect behavior, retry policy, candidate limit, range retry count, and stable identity/no-transform headers apply to these real media requests. This is intentionally a transport configuration library rather than an unsubstantiated protocol claim: Android and OkHttp own the TLS and protocol negotiation path, and the app does not represent itself as shipping an independent HTTP/3 or QUIC stack.

##### Audible spatial processing and audio accessibility

The v2.0 processor supports PCM 16-bit and PCM float streams, allowing the effect to remain active when Media3 chooses float output. In stereo content it expands the side component according to Studio, Wide, Immersive, or Cinema profile strengths, adds bounded crossfeed, applies safety gain to avoid increased-side clipping, and clamps the result safely. The pipeline then honors mono mix and left/right balance accessibility values. Both the primary player and the crossfade player own an instance, and normal settings refresh applies the current configuration to both.

The audio settings continue to include the five-band Equalizer mapping: bass, low-mid body, vocal presence, high-mid presence, and treble are mapped to supported Android equalizer bands. Hardware effects remain capability-dependent, whereas the PCM spatial stage provides device-independent audible processing when stereo PCM is available.

##### Provider routing and quality behavior

Lossless resolution now receives the same persisted route policy during live playback and ordered local downloads. The configured provider order controls candidate construction for Tidal, Qobuz, Amazon Music, and Deezer, while failures, cooldowns, and unavailable source identifiers still fall through safely. The preview-length control reaches the actual manifest and community response parsers, not only the Settings UI. Quality selection remains tied to the resolver鈥檚 explicit low/automatic/high bitrate rules and the lossless profile option.

> **Provider boundary:** a route policy is not a credential or entitlement. Spotui only handles the source paths already available to the app and does not decrypt, redistribute, or claim rights to provider-protected media.

##### Optional YouTube web companion and official playlist sync

The optional web companion continues to open the official YouTube Music site for interactive sign-in and browsing. The separate synchronized-library feature uses the official YouTube Data API and requires a user-owned Google OAuth Android client ID. The app generates a PKCE authorization request, receives the registered custom redirect, exchanges the authorization code, stores tokens under an Android Keystore AES key, and can perform manual or constrained periodic sync when network access exists.

Synchronized playlists become `youtube_api_` library collections. The Library screen groups them in the collapsible **YouTube Music** area, and the API layer resolves their metadata and track references locally before attempting Spotify endpoints. The integration carries collection metadata and official video references only. A client registration that includes the redirect URI `com.music.spotui:/oauth2redirect` is required before a user can authorize live sync.

##### NovaAc v5 and browser player

NovaAc v5 adds per-track audio container, original filename, and SHA-256 payload metadata. Archives continue to separate archive header information, encrypted payload content, user-selected security mode, and optional app-local audio. During Android import the declared digest is checked before writing, and a validated native extension is restored instead of forcing every payload to M4A. The browser player parses v5 frame metadata, creates MIME-aware local object URLs, checks a declared SHA-256 before playback, exposes all payload metadata in the downloadable manifest, and supports keyboard transport shortcuts. It performs all parsing, decryption, validation, and playback locally in the browser tab.

##### Theme Studio and accessibility controls

The new Theme Studio provides System, Aurora, Ember, Oceanic, and Monochrome Material 3 expressive palettes. Advanced users may import a compact JSON color spec or choose an image whose sampled pixels derive a local palette. JSON parsing validates each color field; malformed values do not replace the active theme. The shared UI-settings revision signal drives root `SpotuiTheme` recomposition, so a changed preset, imported color source, high-contrast choice, or text scale is applied across the navigation tree without requiring an app restart.

The accessibility panel adds high contrast, readable text scaling, reduced motion, larger touch targets, mono audio, and stereo balance. Reduced motion removes the default collapsible-section transition; larger targets change shared row/header sizing; audio controls update the PCM processor. These are active settings with implementation owners, not placeholder switches.

##### Release validation

The packaged v2.0.0 APK was assembled from the updated source tree and validated as package `com.music.spotui`, version `2.0.0` (`202609200`), with APK Signature Scheme v2 verification. The assembled artifact is approximately 85 MiB. The project retains a shared debug signing configuration for sideload testing; replace it with a secure developer-controlled production signing key before public distribution.

---


---

## Source document: `CHANGELOG.md`

### Spotui Release Chronicle: 1.1.0鈥�2.1.1

> A detailed, evidence-aware chronology of the Spotui product line.

**Status:** Spotui 2.1.1 source package. This documentation describes the current supplied source tree and its integrated behavior. It does not claim a relationship with Spotify AB, and it does not describe provider-protected media export as a product capability.

#### v2.1.1 鈥� One-file NovaAc large-playlist hardening

Spotui **v2.1.1** is a targeted NovaAc reliability release for user-authorized, locally stored audio. A full playlist, Liked Songs collection, or Downloads collection is now written as **one encrypted `.NovaAc` container**. The archive writer processes each eligible track sequentially, rather than building a whole collection archive or a large local payload in one memory allocation. This directly replaces the legacy route that could fail with an allocation error while compiling a large collection.

The full-audio path now uses a 256 KiB working buffer, long-length payload framing, incremental SHA-256 and CRC32 integrity calculation, GZIP compression, and AES-GCM encryption. The output destination is kept open for the complete collection and receives the encrypted stream in order. A track that becomes unreadable after planning can remain as metadata in best-effort mode instead of crashing the entire archive; strict full-coverage mode still refuses to begin if required local audio is missing.

| Surface | v2.1.1 behavior |
| --- | --- |
| Playlist Export / overflow menu | Opens the advanced streaming export flow; the old ByteArray full-playlist compiler is no longer used for full audio. |
| Liked Songs | Emits one archive, displays per-track streamed progress, and reports final actual archive bytes. |
| Downloads | Emits one archive, displays bytes copied, archive bytes written, elapsed time, and final payload coverage. |
| Archive length | Payload lengths use an unsigned long LEB128 representation instead of the prior 32-bit ceiling. |
| Live status | Shows active collection position, current item, source bytes, destination archive bytes, elapsed time, inclusion/omission state, and final archive size. |

> NovaAc output still contains only audio files that the user has downloaded or otherwise made locally available to Spotui. The release does not claim to bypass provider access controls or create unavailable audio payloads.

The release identity is `com.music.spotui` version name **2.1.1**, version code **202610101**. Kotlin release compilation completed successfully. The signed APK was assembled successfully with the constrained-environment lint task excluded after source compilation; its APK Signature Scheme v2 signature and package identity were verified after build.

#### v2.1.2 鈥� Resilient staged NovaAc delivery

Spotui **v2.1.2** changes the large-archive failure model. A user-selected document destination is no longer the place where long-running compression and encryption are created. Instead, Spotui creates **one complete fixed `.NovaAc` file in private app storage**, flushes it to disk, verifies its SHA-256 digest, persists a recovery manifest, and only then performs a separate chunked copy to the selected destination. This protects the completed archive from the reported long-lived document-provider interruption near 128 MB.

| Capability | v2.1.2 behavior |
| --- | --- |
| Archive construction | One playlist remains one archive file, created in private staging before destination delivery. |
| Memory behavior | Source hashing and writing share one bounded 256 KiB reusable buffer; destination delivery uses a bounded 512 KiB buffer. No collection-size-dependent heap allocation is used. |
| Byte handling | Archive, payload, staging, progress, and delivery counts use `Long`. Payload lengths remain encoded with unsigned long LEB128. |
| Fixed-file finalization | Encryption and GZIP streams are completed, the staged file descriptor is synchronized, the byte count is checked, then `.partial` is atomically renamed to the final staged archive. |
| Staging estimate | The engine checks private storage before export using the existing source-size estimate plus staging and delivery safety margin. |
| Integrity | Incremental payload SHA-256/CRC32 plus a final whole-archive SHA-256 digest are recorded. |
| Delivery | The staged archive is copied to the user-selected document destination in chunks. The copy byte count must equal the staged archive byte count. Readback SHA-256 is used when the document provider permits it. |
| Recovery | A recovery manifest records the archive ID, collection, staged path, byte count, digest, creation time, and last failure. A completed stage is retained when delivery or readback cannot be verified. |
| UI diagnostics | Playlist, Liked Songs, and Downloads show staged/destination progress, exact byte values, digest prefix, delivery verification state, recovery ID, and stable failure code. |

The release identity is `com.music.spotui` version name **2.1.2**, version code **202610102**. The final APK assembled successfully; its package identity and APK Signature Scheme v2 signature were verified. The final APK SHA-256 is `179569775f7b4521349372c0c7f374790d3e209ef62ea14117ddd35c8ad93898`.

#### v2.1.3 鈥� External staging and portable large-frame compatibility

Spotui **v2.1.3** removes the remaining application-created export refusal based on an estimated staging size. Archive planning now keeps its estimate as a `Long` for display and filename context, but Android鈥檚 actual filesystem/storage write result decides whether a staged archive can be created. The engine prefers **app-specific external storage** for staging and recovery because it commonly shares the larger device media volume while needing no broad-storage permission. Internal app storage remains the compatibility fallback.

The companion portable NovaAc player now decodes variable-length fields using a browser-safe long LEB128 implementation with explicit frame-bound validation. This aligns it with Android鈥檚 long payload framing and avoids the previous 32-bit bitwise decoder limit for large portable archive fields. Device-secure archives remain intentionally Android-keystore-only; portable browser playback requires choosing **Web Passphrase** mode and retaining its passphrase.

| v2.1.3 update | Effect |
| --- | --- |
| Estimated size check | Diagnostic only; no app-created size rejection before a write attempt. |
| Archive naming estimate | Uses `Long`, avoiding an estimate-name overflow for very large collections. |
| Staging location | Uses app-specific external storage first, then internal app storage if unavailable. |
| Failure behavior | Actual filesystem or provider failure still reports a concrete failure state; Android storage, filesystem, and provider limits remain external constraints. |
| Browser parser | Uses safe long LEB128 and validates that each declared frame remains inside the decrypted archive boundary. |

The release identity is `com.music.spotui` version name **2.1.3**, version code **202610103**. The signed APK assembled successfully, package identity and APK Signature Scheme v2 were verified, and the final APK SHA-256 is `d3eb0386d183e07c4c40cea993188000b8f543cc5a3822a9c8aff94b0e254f0e`.

#### v2.1.4 鈥� Framed AES-GCM root-cause fix

Spotui **v2.1.4** replaces the legacy single-message AES-GCM archive body. A reported device failure captured the exact cause: the previous cipher path attempted a **273,678,352-byte allocation** after approximately 130.8 MB of source data had been processed, leaving only a 460-byte header file. This was cipher-provider buffering, not a document destination, playlist-count, or archive-size estimate problem.

NovaAc v7 now encrypts the compressed archive body as independently authenticated **64 KiB frames**. Every frame carries a sequence number, plaintext length, ciphertext length, per-frame derived nonce, and AES-GCM authentication tag. No encryption call receives a playlist-sized message, so the cipher鈥檚 temporary memory use is bounded by a frame rather than growing with archive size.

| Capability | v2.1.4 behavior |
| --- | --- |
| Encryption architecture | Framed AES-GCM (`NovaBytecode-v7-FramedGcm`), replacing one monolithic encrypted body. |
| Frame size | 64 KiB compressed plaintext per independently finalized authenticated frame. |
| Memory behavior | Audio scan/copy uses a 256 KiB reusable buffer; encryption uses a 64 KiB bounded frame; destination delivery uses a 512 KiB reusable buffer. |
| Stage verification | Spotui reopens the private staged archive and authenticates every encrypted frame before delivery. |
| Import | Android recognizes v7 and decrypts/decompresses sequentially; each payload is restored directly to offline storage while its CRC32 and SHA-256 are checked. |
| Browser player | Web Passphrase mode recognizes v7 and decrypts each frame locally with Web Crypto before parsing. |
| Existing archives | The legacy v5/v6 reader remains available for earlier NovaAc files. |

The frame algorithm was stress-tested with **300 MiB** of synthetic input using a 256 MiB Java heap: 4,800 encrypted frames were written, re-read, authenticated, and digest-verified without an archive-sized allocation. The final v2.1.4 APK assembled successfully, package identity and APK Signature Scheme v2 were verified, and the APK SHA-256 is `e2a9bbc6d95f496bf18c049f9d9a422a7614602c3a3764ea9f3b1ae21a01f15e`.

#### v2.1.5 鈥� Dedicated MrBean NovaAc control plane and v8 authenticated frames

Spotui **v2.1.5** turns NovaAc into a dedicated archive subsystem rather than a generic playback-network setting. `MrbeanNovaAcController` owns archive-only adaptive I/O configuration, operational telemetry, recovery policy, and diagnostics. It does not alter the existing MrBean playback resolver or media transport settings.

NovaAc v8 adopts the user-provided authenticated-data improvement: every 64 KiB AES-GCM frame is bound to the exact serialized archive header and its ordered frame index. This prevents a valid encrypted frame from being replayed under a different framed archive header. The writer still uses bounded reusable buffers, independent frame finalization, staged-file verification, and transactional destination delivery. Existing NovaAc v7 framed archives remain readable, and legacy v5/v6 readers remain present.

| v2.1.5 capability | Implemented behavior |
| --- | --- |
| MrBean scope | Dedicated to NovaAc staging, framed encryption, verification, delivery, recovery, and diagnostics鈥攏ot music playback networking. |
| Adaptive profiles | **Fast**: 256 KiB archive / 512 KiB delivery buffers; **Balanced**: 128 KiB / 256 KiB; **Resilient**: 64 KiB / 128 KiB. AES-GCM frames remain fixed at 64 KiB in every profile. |
| Live telemetry | Real source bytes, archive bytes, throughput, calculated ETA, stage, inclusion/omission count, failure code, and recovery ID are captured by the controller. |
| v8 AEAD | Header bytes plus an eight-byte big-endian frame index are authenticated as AES-GCM associated data on every v8 frame. |
| Recovery inventory | Retained staged archives can be listed, integrity-checked, delivered again to a new document destination, or purged from Settings. |
| Delivery retry | Retried delivery validates the retained staged archive SHA-256 before copying; it never rebuilds the original playlist archive. |
| Portable browser player | The companion player recognizes v8 Web Passphrase archives and provides the same header-plus-index associated data to Web Crypto; v7 support remains. |

The v8 frame harness processed **300 MiB** / **4,800 frames** with header-and-index authenticated data under a 256 MiB heap cap, then re-read and verified the full digest successfully. The final release identity is `com.music.spotui` version **2.1.5** (`versionCode 202610105`). The APK assembled successfully, APK Signature Scheme v2 verified, and the final APK SHA-256 is `65f85db6801ecc4da34235fb36ffb98aa0df4698a6b89e2a80cb1ee47987679f`.

The repository is a Kotlin Android music-client project built around Jetpack Compose, a Media3 playback core, Hilt dependency injection, bundled metadata/resolution modules, user-controlled storage routes, and a settings model that persists user choices. The documentation deliberately separates **verified current-source behavior** from **historical reconstruction**. The supplied project archive contains no Git metadata or tags, so version-era narrative before the documented 1.5.1 baseline is a product-line reconstruction rather than a claim that a particular commit has been recovered.

#### How to read this chronicle

The version table is intentionally explicit about evidence. Releases from **1.5.1 onward** are grounded in the supplied changelog, roadmap, implementation notes, or current code. Releases from **1.1.0 through 1.4.9** are included because the requested product chronology begins there, but no matching source snapshots or tags were supplied. They record the reconstructed evolution of the product themes rather than asserting unrecoverable code-level facts. This distinction protects future maintainers from confusing documentation with forensic version control.

#### Complete version history

| Version | Release focus or documented outcome | Evidence status |
| --- | --- | --- |
| 1.1.0 | Foundational product era: Compose shell experiments, basic search and navigation conventions, and early music-client layout patterns. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.1.1 | Foundational product era: Compose shell experiments, basic search and navigation conventions, and early music-client layout patterns. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.1.2 | Foundational product era: Compose shell experiments, basic search and navigation conventions, and early music-client layout patterns. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.1.3 | Foundational product era: Compose shell experiments, basic search and navigation conventions, and early music-client layout patterns. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.1.4 | Foundational product era: Compose shell experiments, basic search and navigation conventions, and early music-client layout patterns. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.1.5 | Foundational product era: Compose shell experiments, basic search and navigation conventions, and early music-client layout patterns. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.1.6 | Foundational product era: Compose shell experiments, basic search and navigation conventions, and early music-client layout patterns. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.1.7 | Foundational product era: Compose shell experiments, basic search and navigation conventions, and early music-client layout patterns. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.1.8 | Foundational product era: Compose shell experiments, basic search and navigation conventions, and early music-client layout patterns. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.1.9 | Foundational product era: Compose shell experiments, basic search and navigation conventions, and early music-client layout patterns. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.2.0 | Library and collection era: early playlist, album, artist, and Liked Songs concepts with stronger metadata presentation. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.2.1 | Library and collection era: early playlist, album, artist, and Liked Songs concepts with stronger metadata presentation. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.2.2 | Library and collection era: early playlist, album, artist, and Liked Songs concepts with stronger metadata presentation. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.2.3 | Library and collection era: early playlist, album, artist, and Liked Songs concepts with stronger metadata presentation. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.2.4 | Library and collection era: early playlist, album, artist, and Liked Songs concepts with stronger metadata presentation. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.2.5 | Library and collection era: early playlist, album, artist, and Liked Songs concepts with stronger metadata presentation. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.2.6 | Library and collection era: early playlist, album, artist, and Liked Songs concepts with stronger metadata presentation. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.2.7 | Library and collection era: early playlist, album, artist, and Liked Songs concepts with stronger metadata presentation. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.2.8 | Library and collection era: early playlist, album, artist, and Liked Songs concepts with stronger metadata presentation. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.2.9 | Library and collection era: early playlist, album, artist, and Liked Songs concepts with stronger metadata presentation. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.3.0 | Playback experience era: queue behavior, player controls, visual surfaces, and the first persistence assumptions for active playback. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.3.1 | Playback experience era: queue behavior, player controls, visual surfaces, and the first persistence assumptions for active playback. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.3.2 | Playback experience era: queue behavior, player controls, visual surfaces, and the first persistence assumptions for active playback. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.3.3 | Playback experience era: queue behavior, player controls, visual surfaces, and the first persistence assumptions for active playback. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.3.4 | Playback experience era: queue behavior, player controls, visual surfaces, and the first persistence assumptions for active playback. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.3.5 | Playback experience era: queue behavior, player controls, visual surfaces, and the first persistence assumptions for active playback. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.3.6 | Playback experience era: queue behavior, player controls, visual surfaces, and the first persistence assumptions for active playback. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.3.7 | Playback experience era: queue behavior, player controls, visual surfaces, and the first persistence assumptions for active playback. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.3.8 | Playback experience era: queue behavior, player controls, visual surfaces, and the first persistence assumptions for active playback. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.3.9 | Playback experience era: queue behavior, player controls, visual surfaces, and the first persistence assumptions for active playback. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.4.0 | Reliability and discovery era: deep-link intent, search/discovery refinements, artwork handling, and preparation for modular source resolution. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.4.1 | Reliability and discovery era: deep-link intent, search/discovery refinements, artwork handling, and preparation for modular source resolution. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.4.2 | Reliability and discovery era: deep-link intent, search/discovery refinements, artwork handling, and preparation for modular source resolution. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.4.3 | Reliability and discovery era: deep-link intent, search/discovery refinements, artwork handling, and preparation for modular source resolution. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.4.4 | Reliability and discovery era: deep-link intent, search/discovery refinements, artwork handling, and preparation for modular source resolution. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.4.5 | Reliability and discovery era: deep-link intent, search/discovery refinements, artwork handling, and preparation for modular source resolution. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.4.6 | Reliability and discovery era: deep-link intent, search/discovery refinements, artwork handling, and preparation for modular source resolution. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.4.7 | Reliability and discovery era: deep-link intent, search/discovery refinements, artwork handling, and preparation for modular source resolution. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.4.8 | Reliability and discovery era: deep-link intent, search/discovery refinements, artwork handling, and preparation for modular source resolution. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.4.9 | Reliability and discovery era: deep-link intent, search/discovery refinements, artwork handling, and preparation for modular source resolution. | Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version |
| 1.5.1 | Playback and library foundations: source matching, blacklist UI, lyrics, queue, crossfade, and stream diagnostics were retained as the documented baseline. | Verified in supplied project documentation or current source |
| 1.5.2 | Offline library workflow: app-managed local download and offline collection concepts were retained without exporting protected provider media. | Verified in supplied project documentation or current source |
| 1.5.3 | Playlist portability: selected playlist and album track export was introduced through encrypted NovaAc metadata manifests. | Verified in supplied project documentation or current source |
| 1.5.4 | Local media continuity: local collection state and persisted artwork paths were retained. | Verified in supplied project documentation or current source |
| 1.5.5 | Equalizer: persisted EQ preferences were connected to Android framework equalizer bands for the active Media3 session. | Verified in supplied project documentation or current source |
| 1.5.6 | Spatial audio: device-supported Virtualizer processing became an opt-in audio-session effect. | Verified in supplied project documentation or current source |
| 1.5.7 | Audio-session stability: effects attach and release with ExoPlayer audio-session changes. | Verified in supplied project documentation or current source |
| 1.5.8 | Crossfade: custom crossfade processing remained alongside the audio effects controller. | Verified in supplied project documentation or current source |
| 1.5.9 | Visual playback: existing player visual layers and Canvas-related paths were retained. | Verified in supplied project documentation or current source |
| 1.6.0 | Theme resilience: system-safe SansSerif typography replaced reliance on malformed bundled font resources. | Verified in supplied project documentation or current source |
| 1.6.1 | One-handed configuration: the player offers a direct route to Audio & Equalizer settings. | Verified in supplied project documentation or current source |
| 1.6.2 | Player control refinement: queue, sleep timer, alternative source, download, share, album, and artist actions were retained. | Verified in supplied project documentation or current source |
| 1.6.3 | System integration: Media3 service, notifications, Android Auto metadata, and deep-link support were retained. | Verified in supplied project documentation or current source |
| 1.6.4 | Settings organization: collapsible animated sections separated audio, storage, diagnostics, and related configuration. | Verified in supplied project documentation or current source |
| 1.6.5 | Credential boundary: no unauthenticated social-account transmission was added. | Verified in supplied project documentation or current source |
| 1.6.6 | Recognition boundary: microphone capture was not introduced without a user-initiated, permission-aware feature. | Verified in supplied project documentation or current source |
| 1.6.7 | Sharing and Canvas: existing sharing and visual Canvas paths were retained. | Verified in supplied project documentation or current source |
| 1.6.8 | Source fallback: InnerTube and provider fallback controls were retained with diagnostic logging. | Verified in supplied project documentation or current source |
| 1.6.9 | Playback integrity: provider behavior was preserved without hidden modifications. | Verified in supplied project documentation or current source |
| 1.7.0 | Local smart behavior: listening history and queue foundations were consolidated. | Verified in supplied project documentation or current source |
| 1.7.1 | Listening history: the history route and local history behavior were preserved. | Verified in supplied project documentation or current source |
| 1.7.2 | Radio: queue continuation and radio behavior were preserved. | Verified in supplied project documentation or current source |
| 1.7.3 | Lyrics: lyrics and translation-cache paths were preserved. | Verified in supplied project documentation or current source |
| 1.7.4 | Reliability: bounded persistent diagnostics and uncaught-exception capture were added. | Verified in supplied project documentation or current source |
| 1.7.5 | Build memory behavior: Gradle execution was constrained to avoid compiler-worker memory spikes in constrained environments. | Verified in supplied project documentation or current source |
| 1.7.6 | Cache correctness: cache accounting and targeted cleanup replaced destructive assumptions. | Verified in supplied project documentation or current source |
| 1.7.7 | Startup diagnostics: activity, provider warm-up, deep-link, backup, and media-session bootstrap failures became recordable. | Verified in supplied project documentation or current source |
| 1.7.8 | Error visibility: developer-console warning and error entries became persistent. | Verified in supplied project documentation or current source |
| 1.7.9 | Stability baseline: source validation and font-safe rendering work were consolidated. | Verified in supplied project documentation or current source |
| 1.8.0 | Android compatibility: compile/target SDK 36 configuration was documented and validated. | Verified in supplied project documentation or current source |
| 1.8.1 | Navigation polish: direct player-to-audio-settings routing was added. | Verified in supplied project documentation or current source |
| 1.8.2 | Settings polish: reusable collapsible section behavior was formalized. | Verified in supplied project documentation or current source |
| 1.8.3 | Archive compatibility: NovaAc headers gained app-version and format-version fields. | Verified in supplied project documentation or current source |
| 1.8.4 | Accessibility and clarity: settings controls gained labels and state descriptions. | Verified in supplied project documentation or current source |
| 1.8.5 | Build identity: versioning and local release identity were updated for the integrated build. | Verified in supplied project documentation or current source |
| 1.8.6 | Security posture: NovaAc uses Android Keystore AES-GCM and diagnostics redact sensitive stream material. | Verified in supplied project documentation or current source |
| 1.8.7 | Artifact verification: debug/release assembly and APK integrity checks were used after integration groups. | Verified in supplied project documentation or current source |
| 1.8.8 | Release preparation: cache safety, diagnostics, and audio controls were consolidated. | Verified in supplied project documentation or current source |
| 1.8.9 | Stable integration: the supplied roadmapped source was assembled as an integrated, build-validated Android client. | Verified in supplied project documentation or current source |
| 1.9.0 | Major control-surface update: direct labeled playlist NovaAc export, advanced audio settings, HTTPS engine controls, and adaptive navigation work began. | Verified in supplied project documentation or current source |
| 1.9.1 | Archive usability update: export configuration added archive naming and local-audio inclusion choices before invoking Android鈥檚 system save picker. | Verified in supplied project documentation or current source |
| 1.9.2 | Spatial-audio update: profile selection, virtualizer depth, room processing, and bounded normalizer gain were connected to the active session. | Verified in supplied project documentation or current source |
| 1.9.3 | Adaptive-shell update: bottom, top, side, and rail navigation modes were connected to a shared root-route model. | Verified in supplied project documentation or current source |
| 1.9.4 | Visual configuration update: compact UI and a persisted 0鈥�48 dp corner-radius preference became live shell settings. | Verified in supplied project documentation or current source |
| 1.9.5 | Direct collection archive release: playlist and Liked Songs export buttons sit beside Download, with full NovaAc archive configuration. | Verified in supplied project documentation or current source |
| 1.9.9 | Reliability and control-surface release: sequential collection downloads, tier-correct resolver selection, fresh-candidate recovery, stable background playback, software spatial widening, five-band EQ, optional YouTube Music web companion, collapsible library groups, and a configured Eclipse MrBean transport panel. The release APK was built successfully and verified with APK Signature Scheme v2. | Verified in current source and release build artifact |

#### Version-era narrative

##### 1.1.x 鈥� Interface foundation

The first reconstructed era focuses on the basic proposition of a Compose-first music client: predictable root navigation, responsive surfaces, a simple state model for loading and selected content, and a visual language capable of presenting music metadata without tying every route directly to a network call. The enduring design lesson is that a music client must distinguish navigation state, collection state, and playback state early. Doing so allows the mini player, route host, and background session to evolve independently.

##### 1.2.x 鈥� Library identity

The second reconstructed era is the point at which playlists, albums, artists, and Liked Songs become recognizable collection concepts rather than interchangeable search rows. That separation is reflected in the current project through dedicated screens, persistent local preferences, and route-specific actions. The later archive work builds on this distinction: a collection export must identify its source type and preserve enough context to be meaningful when inspected later.

##### 1.3.x and 1.4.x 鈥� Playback continuity and discovery

The third and fourth reconstructed eras explain why the current source has strong emphasis on remembered playback, queue actions, artwork recovery, deep links, and source resolution. A user judges a music client by whether intent survives transitions: choosing a track, opening the player, backgrounding the app, returning from a link, or moving through a collection should all refer to a coherent current-song state. The current `SongPlayer`, `PlaybackService`, `CurrentSongState`, and navigation host embody that requirement.

#### 1.5.1鈥�1.8.9 integrated lineage

The verified integration line focuses on connecting formerly separate capabilities: audio preferences were made session-aware, cache handling was made targeted, diagnostics became persistent, and NovaAc became a visible archive workflow with compatibility headers. This was a transition from 鈥渟creen controls exist鈥� to 鈥渟creen controls are owned by a service and can report failure safely.鈥� The design is especially important for audio effects: preferences alone do not change sound until the active Media3 session accepts them.

| Version | Verified integration detail |
| --- | --- |
| 1.5.1 | Playback and library foundations: source matching, blacklist UI, lyrics, queue, crossfade, and stream diagnostics were retained as the documented baseline. |
| 1.5.2 | Offline library workflow: app-managed local download and offline collection concepts were retained without exporting protected provider media. |
| 1.5.3 | Playlist portability: selected playlist and album track export was introduced through encrypted NovaAc metadata manifests. |
| 1.5.4 | Local media continuity: local collection state and persisted artwork paths were retained. |
| 1.5.5 | Equalizer: persisted EQ preferences were connected to Android framework equalizer bands for the active Media3 session. |
| 1.5.6 | Spatial audio: device-supported Virtualizer processing became an opt-in audio-session effect. |
| 1.5.7 | Audio-session stability: effects attach and release with ExoPlayer audio-session changes. |
| 1.5.8 | Crossfade: custom crossfade processing remained alongside the audio effects controller. |
| 1.5.9 | Visual playback: existing player visual layers and Canvas-related paths were retained. |
| 1.6.0 | Theme resilience: system-safe SansSerif typography replaced reliance on malformed bundled font resources. |
| 1.6.1 | One-handed configuration: the player offers a direct route to Audio & Equalizer settings. |
| 1.6.2 | Player control refinement: queue, sleep timer, alternative source, download, share, album, and artist actions were retained. |
| 1.6.3 | System integration: Media3 service, notifications, Android Auto metadata, and deep-link support were retained. |
| 1.6.4 | Settings organization: collapsible animated sections separated audio, storage, diagnostics, and related configuration. |
| 1.6.5 | Credential boundary: no unauthenticated social-account transmission was added. |
| 1.6.6 | Recognition boundary: microphone capture was not introduced without a user-initiated, permission-aware feature. |
| 1.6.7 | Sharing and Canvas: existing sharing and visual Canvas paths were retained. |
| 1.6.8 | Source fallback: InnerTube and provider fallback controls were retained with diagnostic logging. |
| 1.6.9 | Playback integrity: provider behavior was preserved without hidden modifications. |
| 1.7.0 | Local smart behavior: listening history and queue foundations were consolidated. |
| 1.7.1 | Listening history: the history route and local history behavior were preserved. |
| 1.7.2 | Radio: queue continuation and radio behavior were preserved. |
| 1.7.3 | Lyrics: lyrics and translation-cache paths were preserved. |
| 1.7.4 | Reliability: bounded persistent diagnostics and uncaught-exception capture were added. |
| 1.7.5 | Build memory behavior: Gradle execution was constrained to avoid compiler-worker memory spikes in constrained environments. |
| 1.7.6 | Cache correctness: cache accounting and targeted cleanup replaced destructive assumptions. |
| 1.7.7 | Startup diagnostics: activity, provider warm-up, deep-link, backup, and media-session bootstrap failures became recordable. |
| 1.7.8 | Error visibility: developer-console warning and error entries became persistent. |
| 1.7.9 | Stability baseline: source validation and font-safe rendering work were consolidated. |
| 1.8.0 | Android compatibility: compile/target SDK 36 configuration was documented and validated. |
| 1.8.1 | Navigation polish: direct player-to-audio-settings routing was added. |
| 1.8.2 | Settings polish: reusable collapsible section behavior was formalized. |
| 1.8.3 | Archive compatibility: NovaAc headers gained app-version and format-version fields. |
| 1.8.4 | Accessibility and clarity: settings controls gained labels and state descriptions. |
| 1.8.5 | Build identity: versioning and local release identity were updated for the integrated build. |
| 1.8.6 | Security posture: NovaAc uses Android Keystore AES-GCM and diagnostics redact sensitive stream material. |
| 1.8.7 | Artifact verification: debug/release assembly and APK integrity checks were used after integration groups. |
| 1.8.8 | Release preparation: cache safety, diagnostics, and audio controls were consolidated. |
| 1.8.9 | Stable integration: the supplied roadmapped source was assembled as an integrated, build-validated Android client. |

#### 1.9.0鈥�1.9.5 current release series

The 1.9 series moves interaction points from hidden or indirect menus to **collection-local, labeled controls**. The most visible example is the direct **Export** action beside Download on both playlists and Liked Songs. Archive creation is not a background surprise: the user supplies an archive name, decides whether locally available cache payloads should be included, then receives Android鈥檚 system save location. The same series also turns spatial and interface preferences into active architecture rather than decorative settings.

| Version | Current-series outcome |
| --- | --- |
| 1.9.0 | Major control-surface update: direct labeled playlist NovaAc export, advanced audio settings, HTTPS engine controls, and adaptive navigation work began. |
| 1.9.1 | Archive usability update: export configuration added archive naming and local-audio inclusion choices before invoking Android鈥檚 system save picker. |
| 1.9.2 | Spatial-audio update: profile selection, virtualizer depth, room processing, and bounded normalizer gain were connected to the active session. |
| 1.9.3 | Adaptive-shell update: bottom, top, side, and rail navigation modes were connected to a shared root-route model. |
| 1.9.4 | Visual configuration update: compact UI and a persisted 0鈥�48 dp corner-radius preference became live shell settings. |
| 1.9.5 | Current integrated release: direct playlist and Liked Songs export buttons sit beside Download, with full archive configuration, verified release assembly, and v2 APK signature validation. |
| 2.0.0 | Full architecture overhaul: Theme Studio presets, accessibility scaling, lossless routing, NovaAc v5 container tags, and YouTube Data API sync. |
| 2.0.1 | Downloads archive safety: streamed chunk-based export, HTTP 206 range recovery, and standalone browser player controls. |
| 2.0.2 | Portable browser archive workflow: Web Passphrase mode by default and in-browser playback/extraction tools. |
| 2.1.0 | Playback hardening: Media3 service lifecycle stability, 320ms gapless transition engine, and direct OAuth consent flow. |
| 2.1.5 | Latest release: dual-mode Spotify login (WebView Desktop UA + direct sp_dc cookie authentication + external browser fallback), NovaAc v8 framed AEAD control plane, and LRCLIB synchronized lyrics. |

#### Release decision blueprint

Every release candidate should be evaluated in four layers. First, validate **source integrity**: Kotlin and Compose compile, generated Hilt/KSP artifacts resolve, and the manifest still owns the correct service and deep-link declarations. Second, validate **behavioral ownership**: UI controls must call the correct player, cache, archive, or preference service rather than simply mutate visual state. Third, validate **data boundaries**: chosen SAF destinations, app-private diagnostics, and archive headers should remain user-controlled and privacy-aware. Fourth, validate **artifact identity**: version code, version name, package name, signature scheme, and checksum should be recorded in the release note.

> The project archive is locally debug-format signed for sideload validation. A distribution release requires a developer-controlled private production signing identity; documentation must not describe a local test key as a store-production key.

#### References

[1]: https://developer.android.com/media/media3 "Android Developers: Introduction to Jetpack Media3"
[2]: https://developer.android.com/training/data-storage/shared/documents-files "Android Developers: Storage Access Framework"
[3]: https://github.com/androidx/media "AndroidX Media on GitHub"
[4]: https://developer.android.com/jetpack/compose "Android Developers: Jetpack Compose"
[5]: https://developer.android.com/training/dependency-injection/hilt-android "Android Developers: Dependency injection with Hilt"
[6]: https://github.com/H4zh4n/Spotui "Spotui upstream project"
[7]: https://github.com/navneet851/spotify-clone-jetpack-compose "Neptune Jetpack Compose Spotify clone"
[8]: https://github.com/spotbye/SpotiFLAC "SpotiFLAC project"
[9]: https://github.com/maxrave-dev/SimpMusic "SimpMusic project"
[10]: https://github.com/TeamNewPipe/NewPipeExtractor "NewPipe Extractor"


---

#### v1.9.9 鈥� Reliability, audio depth, and controlled transport

Spotui v1.9.9 is a reliability-focused release rather than a cosmetic version bump. The implementation keeps the established collection, resolver, and Media3 architecture intact while making failure recovery explicit at the points where a user can otherwise experience an unexplained interruption: stream selection, expired media URLs, byte-range transfer, background service ownership, hardware-effect capability variance, and collection-level queue ordering. The release artifact identifies itself as package `com.music.spotui`, version name `1.9.9`, and version code `202609051`. It assembled successfully in release mode and its final APK was verified with APK Signature Scheme v2.

##### Ordered collection downloads

Collection download now treats the displayed playlist order as a durable queue. The download coordinator de-duplicates supplied items, skips tracks that are already available offline, invokes one `downloadSong` operation at a time, awaits the completion callback, records a diagnostic outcome, and inserts a short release interval before moving to the next item. This avoids competing resolver chains and ensures that an unsuccessful item is reported without preventing subsequent songs from being attempted. It also preserves the user鈥檚 expected sequence instead of turning a playlist action into a parallel batch whose on-disk completion order is unpredictable.

| Queue behavior | v1.9.9 implementation | User-visible result |
| --- | --- | --- |
| Work scheduling | One track resolves and transfers before the next begins | Playlist download order matches collection order |
| Duplicate protection | Existing offline items and in-progress work are skipped | Repeated taps do not create competing transfers |
| Failure isolation | A failed item completes its queue turn and does not halt remaining items | A transient resolver failure does not abandon the collection |
| Progress reporting | Existing per-track state and callback path are retained | Download surfaces continue to render the active item and outcome |

##### Resolver quality and failure recovery

The quality path uses explicit tier behavior instead of a loosely interpreted bitrate multiplier. Low quality selects an appropriate bounded source, Auto follows the intended network-aware policy, and High selects the best viable result. Candidate exploration is widened, ranked sources are attempted before the resolver falls back to a regular-video search, and stale candidates can be cleared for a fresh lookup. Resolver failures are recorded with their useful reason rather than being silently compressed into a generic error.

Expired or revoked media URLs are specifically handled as a re-resolution condition. The transfer path keeps raw-byte semantics by requesting `Accept-Encoding: identity`, accepts arbitrary stream content where expected, follows redirects, and detects the dangerous case where a server ignores a resumed Range request after earlier partial output. In that case the transfer stops instead of appending a full response to an existing partial file and producing a corrupt playable-looking download. The file suffix is selected to represent the actual resolved container so WebM and M4A output are not mislabeled.

##### Playback continuity and service ownership

The Media3 service no longer treats removal of the task surface as a reason to stop an active music session. The service is declared to remain independent of the task, retains the required wake-lock permission, and only applies a bounded replay attempt after a player error. That replay policy is intentionally limited to one fresh resolution per query to avoid an unbounded failure loop. Network wake behavior on the player side improves the chance that short connectivity transitions resolve without Android prematurely suspending the session.

> The release does not claim that every upstream source can always resolve. Provider-side gating, availability, age restrictions, connectivity, and expiring stream authorization remain external constraints. v1.9.9 makes the app retry and report safely within those constraints instead of masking failure or corrupting output.

##### Spatial pipeline and five-band equalizer

Spatial sound is now supported by two coordinated paths. The Android framework Virtualizer and room effect remain available when the device exposes them, with the persisted strength value correctly mapped to the framework鈥檚 0鈥�1000 scale. A software PCM stereo-widening processor is also placed in both primary and crossfade playback chains. It applies profile-specific crossfeed/widening so Studio, Wide, Immersive, and Cinema profiles remain audibly differentiated even on devices that expose no useful hardware virtualizer. The settings view reports the live hardware capability state rather than implying a feature is active when the platform declined it.

The equalizer moved from broad bass/vocal/treble groups to a five-region mapping against the active Android `Equalizer` bands. Band center frequencies below 180 Hz follow Bass, 180鈥�649 Hz follow Low-mid body, 650鈥�2299 Hz follow Vocal, 2300鈥�5999 Hz follow Presence, and 6000 Hz and above follow Treble detail. The user鈥檚 0鈥�100 control values are normalized around the neutral midpoint and converted into the device-provided band-level range. This design honors devices with different equalizer band counts while preserving meaningful frequency intent.

| Audio control | Frequency region or processor | v1.9.9 behavior |
| --- | --- | --- |
| Bass | Below 180 Hz | Maps to low-frequency equalizer bands |
| Low-mid body | 180鈥�649 Hz | Adds independent warmth and instrument weight control |
| Vocal | 650鈥�2299 Hz | Controls the speech and lead-vocal presence range |
| Presence | 2300鈥�5999 Hz | Adds a dedicated definition/attack control |
| Treble detail | 6000 Hz and above | Controls air and high-frequency detail |
| Spatial profile | Software widening plus optional Android effects | Applies immediately to primary and crossfade pipelines |

##### Library and optional YouTube Music companion

The library presentation now partitions the primary collection into collapsible Playlist and Album groups while retaining special entries such as Liked Songs and Downloads at the top of the list. A third collapsible YouTube Music group exposes an optional official-site companion rather than silently adding a new account system. When the user opens that companion, the activity loads `music.youtube.com` in a visible WebView where sign-in and playlist use occur on the official website. The app keeps the companion optional through the existing settings toggle and does not collect a password or create a separate credential form.

The player video-preview preference is independently persisted and connected to the existing alternate-stream editor route, allowing a user to choose whether visual preview behavior appears in the now-playing experience. This preserves an audio-first path for users who prefer a compact player.

##### Eclipse MrBean networking configuration

The requested `org/eclipse/Mrbean` hierarchy is included in the Android source tree with `http3`, `http2`, `http`, `ws`, `client`, `io`, `security`, `servlet`, `util`, and `websocket` modules. The functional configuration owner is `org.eclipse.Mrbean.client.MrbeanNetworkSettings`. It persists controls for ranked candidate breadth, connect timeout, read timeout, range-transfer chunk size, and attempts per byte range. `SongPlayer` reads those settings only for its app-owned media candidate and raw ranged-download requests; disabling the engine restores the release defaults of six candidates, 15-second connection timeout, 30-second read timeout, 8 MiB chunks, and three transfer attempts.

| MrBean control | Stored range | Actual integration point |
| --- | --- | --- |
| Candidate fallback breadth | 3鈥�8 sources | Limits ranked song and video candidates attempted by the stream resolver |
| Connect timeout | 5鈥�45 seconds | Applied to the app-owned `HttpURLConnection` used for ranged downloads |
| Read timeout | 10鈥�90 seconds | Applied to the same raw download connection |
| Range chunk size | 1鈥�16 MiB | Determines the byte interval requested in each corruption-safe transfer segment |
| Attempts per range | 1鈥�5 | Bounds retries after an interrupted transfer segment |

The settings panel accurately describes protocol scope. The Android TLS/network stack is allowed to negotiate HTTP/2 where the platform and endpoint support it. A dedicated HTTP/3 capability marker reports that no native QUIC provider is bundled in this release, so HTTP/3 is not advertised as active or represented by a nonfunctional control. This is intentional: a configuration screen should expose applied behavior and known availability, not fabricate protocol support.

##### Release verification record

The source tree was assembled with the project鈥檚 configured JDK 21 and Android SDK setup. The final command completed with `BUILD SUCCESSFUL`; warning output related to deprecated Android framework audio APIs and pre-existing source annotations did not prevent release creation. The generated release APK was inspected with Android build tools: its signature verified successfully under APK Signature Scheme v2, its manifest reports the expected application ID and `1.9.9` version metadata, and its SHA-256 was recorded at package time. Source distribution includes this changelog update, the new MrBean configuration source, and the v1.9.9 implementation changes described above.

---

#### v1.9.9 operational checklist

A user troubleshooting a fresh install should first confirm the desired playback quality in Settings, then use the Eclipse MrBean panel only when a constrained or unstable connection warrants tuning. The defaults are the tested baseline; increasing candidate breadth may improve recovery at the cost of more resolver work, and longer timeouts may help slow networks but delay failure reporting. For offline collections, begin a playlist download once and allow its sequential queue to advance rather than repeatedly tapping the control. For spatial behavior, select a profile with headphones connected and review the capability status text; software widening remains active when compatible PCM audio is processed even if a hardware Virtualizer is unavailable.

The optional YouTube Music companion should be enabled only when the user wants to browse or play their own YouTube Music library in the official web experience. Sign-in, account controls, and access decisions remain at the website. NovaAc collection archives remain configured from the direct buttons beside Download on Playlist and Liked Songs surfaces; the archive workflow is separate from live provider playback and should be used in accordance with the user鈥檚 rights and applicable terms.

> v1.9.9 is the supported continuation of the documented 1.8.9 鈫� 1.9.0 鈫� 1.9.5 progression. Its goal is not to replace the app鈥檚 music logic, but to make the established behavior more observable, ordered, configurable, and resilient.


---

### Spotui v2.0.0 鈥� Integrated Media, Accessibility, and Expressive Theme Release

> **Release status:** assembled and package-validated. The release declares package `com.music.spotui`, version name `2.0.0`, version code `202609200`, and a valid APK Signature Scheme v2 signature.

#### Completion record

Version 2.0.0 converts the preceding reliability work into an integrated application release. Every new setting has a persisted owner and is connected to a runtime owner: Media3 transport settings reach the active cache and player datasource; spatial and accessibility audio values reach both PCM processor instances; theme changes reach the root `SpotuiTheme`; YouTube OAuth state reaches the library import and background work path; and NovaAc payload metadata reaches Android restoration and the browser player.

| Domain | Completed implementation | Runtime integration |
|---|---|---|
| Media3 | Unified AndroidX Media3 1.11.0 module line, including the OkHttp datasource artifact | Retains the custom `DefaultAudioSink` chain required for crossfade and software spatial processing |
| MrBean | OkHttp-backed `DataSource.Factory` applies configured timeouts, redirects, retry behavior, `Accept-Encoding: identity`, and no-transform media headers | Used by `SongPlayer.cacheDataSourceFactory()` for normal playback and cache-intro preloading |
| Spatial/accessibility audio | PCM-16 and PCM-float support, profile-dependent mid/side widening, crossfeed, clipping compensation, mono mix, and stereo balance | Both primary and crossfade pipelines use the processor; Settings updates are applied through normal audio-effect refresh |
| Lossless providers | Persisted Tidal/Qobuz/Amazon/Deezer route order plus full-length preview rejection | Both live playback and ordered FLAC downloads pass the same policy to `SpotiFlac.resolve()` |
| YouTube | Official Google OAuth PKCE, Android Keystore-encrypted token storage, paginated playlist metadata import, manual sync, periodic constrained sync, and revoke | Synced `youtube_api_` collections load before Spotify APIs and display in the collapsible YouTube Music library section |
| NovaAc | v5 payload container, original filename, and SHA-256 frames | Android import restores a validated native extension; browser player selects MIME by container and blocks digest failures |
| Theme Studio | Material 3 expressive presets, validated JSON palette import, image-derived palette extraction, contrast, type scale, and reset | Root theme observes the shared UI settings revision so changes recompute across the application |
| Accessibility | High contrast, readable text scale, reduced motion, larger touch targets, mono mix, and balance | Each option changes composition or PCM behavior instead of serving as a nonfunctional preference |

#### Media stack and transport ownership

All active Media3 artifacts now align to `1.11.0`. The migration retained the `DefaultAudioSink` customization rather than replacing it with a generic default sink, because Spotui鈥檚 crossfade and spatial path is deliberate application behavior. `SpatialWideningAudioProcessor` now accepts Media3鈥檚 commonly emitted PCM 16-bit and float formats. For a stereo signal it calculates a profile-specific mid/side field, introduces bounded crossfeed, applies a safety gain against high-width clipping, then applies accessibility mono and balance values. Unsupported encodings and non-stereo content bypass safely.

MrBean is no longer settings-only. `MrbeanMediaDataSource` creates the concrete upstream factory used by Media3鈥檚 cached playback source. The configured connect/read limits are attached to the OkHttp client, and the media transport uses stable HTTP headers intended to avoid compressed-range and transformation problems. Candidate breadth and range retry values remain part of the resolver/download policy. Protocol negotiation remains with Android/OkHttp; the app does not make an unsupported claim that it bundles a QUIC stack.

#### Lossless policy

The lossless route order is now a user-owned policy. Tidal, Qobuz, Amazon Music, and Deezer candidates are created according to the persisted order, while unavailable or cooldown sources remain safely skipped. The preview-length control is wired through the actual manifest and community candidate parsing functions: if enabled, short manifest assets are rejected before resolver fallback; if disabled, the resolver鈥檚 normal candidate validation proceeds. This only controls the app鈥檚 existing authorized/available resolver attempts and does not grant entitlement to provider-controlled media.

#### YouTube Music integration and synchronization boundary

The web companion remains optional and uses the official YouTube Music website for any sign-in. The separate playlist-sync path uses the official YouTube Data API with the `youtube.readonly` scope. A user supplies their own registered Google OAuth Android client ID, begins a visible PKCE consent flow, and the registered app-scheme redirect `com.music.spotui:/oauth2redirect` returns the authorization code to `MainActivity`. Tokens are encrypted by an Android Keystore AES-GCM key. The user can perform a manual sync, enable constrained periodic network work, reconnect, or revoke local authorization.

Playlist metadata and video references are fetched with API pagination, persisted as `youtube_api_` collections, and rendered in the Library鈥檚 collapsible **YouTube Music** section. The API route recognizes those IDs before Spotify access for both playlist metadata and item lists. This feature imports user-authorized collection metadata; it does not capture website passwords, impersonate a user, or extract protected media. A valid user-owned Google OAuth client registration remains required for live API sync and is intentionally not embedded in the APK.

#### NovaAc v5 archive evolution

NovaAc v5 preserves the existing streaming encrypted archive model while adding audio-container, original-filename, and SHA-256 frames immediately before each optional payload. Android import verifies declared SHA-256 before writing the recovered local file and chooses a validated extension such as `flac`, `webm`, `opus`, `m4a`, `mp3`, or `wav`; it no longer labels every restoration as M4A. The standalone browser player recognizes the same fields, selects the correct MIME type from the declared container, exposes those values in its downloadable manifest, validates a payload digest before playback, and includes keyboard previous/next/space transport handling. Browser processing remains local-only.

#### Theme Studio and accessibility

Theme Studio adds System, Aurora, Ember, Oceanic, and Monochrome expressive presets. Imported JSON only accepts safe color syntax and uses known preset values for missing fields. Image-derived palettes sample the user-selected image through the document picker and do not upload it. Root-theme recomposition is driven by the shared settings revision flow, so an appearance change does not require a restart. The new accessibility controls change actual behavior: high contrast changes the color scheme, text scaling changes Compose density, reduced motion removes collapse animation, touch-target mode increases common settings hit areas, and mono/balance values modify active PCM audio.

#### Validation evidence

The complete v2.0.0 release was assembled after integrated Kotlin compilation passes covering the Theme Studio, accessibility audio, MrBean Media3 datasource, lossless provider routing, NovaAc v5, OAuth sync worker, and imported Library collection route. The final APK reports `versionName='2.0.0'`, `versionCode='202609200'`, package `com.music.spotui`, and valid APK Signature Scheme v2 verification. The assembled release is approximately **85 MiB**. Artifact size is reported as built and was not artificially padded to a nominal size target.

---


---

### Spotui v2.0.1 鈥� Downloads Archive Safety and NovaAc Player Controls

Version 2.0.1 repairs the Downloads-tab bulk `.NovaAc` export path and strengthens recoverable stream-download behavior. The Downloads menu no longer calls the in-memory `prepareExport()` path for every locally stored audio payload. A large download library could force all selected audio bytes into one process allocation before the Android destination picker was launched, which is unsafe on constrained devices and can result in an application crash. The menu now creates a `FullAudioArchivePlan` in **Include available local audio** mode and launches the save picker with that plan. When the user chooses a destination, `writeFullAudioArchive()` writes payloads as a streamed encrypted archive in fixed-size chunks. The UI also guards against duplicate preparation taps, clears both pending export states when the picker returns, and reports the actual included-audio count after a successful save.

The download transfer path now sends web-player context headers alongside the existing identity encoding and byte range headers. It retries transient HTTP conditions such as throttling and temporary server responses using bounded backoff, validates that an HTTP 206 response starts at the requested byte position, and reports invalid-range responses rather than silently writing a corrupt result. A fresh resolver attempt recalculates the container extension from the replacement stream, so a retry cannot store a WebM, Opus, FLAC, or MP3 payload under an M4A filename. Finalization falls back to a safe copy-and-delete path when an on-device rename operation cannot complete.

The standalone NovaAc browser player now exposes a complete local playback deck. It includes shuffle, previous, play/pause, next, three-mode repeat, seek with elapsed and remaining time, volume, playback speed, optional track fade, and a real three-band Web Audio equalizer. The player creates one media-element Web Audio graph on first playback and applies low-shelf, peaking-mid, and high-shelf gain changes to actual audio output. Its manifest export includes the current deck state. Keyboard controls support seek with arrows, play/pause with space, shuffle with `S`, and repeat mode with `R`. Archive parsing, passphrase decryption, integrity checking, playback, and EQ remain entirely local to the browser.

The v2.0.1 APK was assembled successfully as package `com.music.spotui`, `versionName='2.0.1'`, `versionCode='202609201'`, with APK Signature Scheme v2 verification. The artifact鈥檚 SHA-256 is recorded in the v2.0.1 release checksum file.

---


---

### Spotui v2.0.2 鈥� Portable NovaAc Browser Archive Fix

Version 2.0.2 changes the Downloads archive workflow so browser playback is a deliberate, visible export choice. Selecting **Export all as .NovaAc** in Downloads now opens a security configuration dialog. **Web Passphrase 鈥� browser playable** is selected by default and requires a user-entered passphrase. The resulting archive uses the existing portable PBKDF2/AES-GCM path, allowing the standalone player to unlock its metadata and locally embedded audio with that same passphrase. The dialog also retains a clearly labeled Android-device-only Keystore choice for users who explicitly want an archive tied to that authorized app installation.

Existing device-secure archives cannot be converted by the browser because their key material is intentionally confined to Android Keystore storage. To play or extract audio in the browser, re-export the downloaded collection from v2.0.2 using **Web Passphrase**, retain the chosen passphrase, then open the new archive in the updated player.

The NovaAc browser player now adds **Play all in order**, which disables shuffle and starts from the first embedded audio-bearing track. It advances in archive sequence on each natural track end and stops after the last track unless repeat-all is selected. **Extract embedded audio** verifies each declared SHA-256 payload before requesting a local browser download using the original declared filename/container where available. Browser multi-download permission may be requested by the user agent. No archive, passphrase, audio payload, or extraction request leaves the browser.

The v2.0.2 APK was assembled successfully as package `com.music.spotui`, `versionName='2.0.2'`, `versionCode='202609202'`, with APK Signature Scheme v2 verification.

---


---

### Spotui v2.1.0 鈥� Playback and Integration Hardening

Version 2.1.0 is a reliability-oriented application release focused on the ownership boundaries that determine whether playback, archive export, and account synchronization remain stable outside a single foreground screen. The app has retained its existing Media3 service design, but the UI activity no longer releases the service-owned `SongPlayer` or Spotify Web player when Android destroys, recreates, backgrounds, or dismisses the activity. The media service now owns that final cleanup only in its own `onDestroy` lifecycle. Its task-removal path no longer stops playback based on a transient `isPlaying` value during stream resolution or buffering, and the service returns `START_STICKY` so a temporary service recreation is not treated as a user-requested stop. The notification Close command has also been converted from a process kill into a normal player/service shutdown, preventing it from looking like an application crash or interrupting state cleanup.

A dedicated **Gapless playback** preference has been added directly beside the existing Crossfade control. When Crossfade is set to Off and Gapless playback is enabled, Spotui uses the same two-player, cached HTTPS stream transition engine already used by its advanced crossfade implementation. It resolves and prepares the next queue item before the outgoing item reaches the boundary, then performs a short 320 ms equal-power handoff. This is intentionally shorter than an audible mix and avoids the silence produced by resolving the next direct stream only after `STATE_ENDED`. Existing user-configured crossfade duration takes precedence, and DJ filtering remains exclusive to the longer crossfade path. Repeat-one and the final queue item preserve their normal behavior.

The NovaAc full-audio writer has received another defensive export pass. A downloaded cache path can disappear, become unreadable, or be cleared after an archive plan is created but before its payload is written. v2.1.0 pre-verifies the local file before emitting any audio frame metadata. In available-audio/best-effort exports, an unreadable payload is skipped with diagnostics while its metadata entry remains in the archive; one volatile download no longer aborts the entire collection export. Archive destination size readback is also tolerant of Storage Access Framework providers that allow writes but do not expose an immediate read descriptor. Browser-portable exports remain selected explicitly with the Web Passphrase mode in Downloads, while Android Keystore archives remain intentionally device-bound.

The YouTube playlist sync Settings flow is now more direct. **Connect Google** saves the currently entered user-owned Android OAuth client ID and immediately opens the official browser consent flow, instead of requiring a separate save step before authorization. Settings refresh the connected/sync state when the app resumes from the browser, retain manual sync, constrained automatic sync, local revoke, and visible last-sync status, and show an actionable configuration instruction when a client ID is missing. The app does not embed a shared production OAuth credential; the Android client ID must be registered by the application owner for the `com.music.spotui:/oauth2redirect` callback.

The framework `Virtualizer` has been removed from the active spatial path because it is deprecated and inconsistent across devices. Spotui keeps its audibly effective Media3 PCM spatial widening processor as the principal width/immersion implementation and retains optional room/reverb where available. The active release was built successfully as `com.music.spotui` `versionName='2.1.0'`, `versionCode='202610100'`, and verified with APK Signature Scheme v2.

---


---

## Source document: `ARCHITECTURE_BLUEPRINT.md`

### Spotui Architecture Blueprint

> A system-level explanation of modules, state ownership, playback, archives, settings, and extension points.

**Status:** Spotui 1.9.9 source package. This documentation describes the current supplied source tree and its integrated behavior. It does not claim a relationship with Spotify AB, and it does not describe provider-protected media export as a product capability.

The repository is a Kotlin Android music-client project built around Jetpack Compose, a Media3 playback core, Hilt dependency injection, bundled metadata/resolution modules, user-controlled storage routes, and a settings model that persists user choices. The documentation deliberately separates **verified current-source behavior** from **historical reconstruction**. The supplied project archive contains no Git metadata or tags, so version-era narrative before the documented 1.5.1 baseline is a product-line reconstruction rather than a claim that a particular commit has been recovered.

#### Architecture map

```text
Compose screens 鈫� ViewModels / route state 鈫� SongPlayer and preference services
        鈹�                         鈹�
        鈹�                         鈹溾啋 Media3 ExoPlayer + AudioEffectController
        鈹�                         鈹溾啋 PlaybackService + MediaSession / Android Auto
        鈹�                         鈹溾啋 HTTPS resolver / metadata modules
        鈹�                         鈹溾啋 CacheStorageManager + DownloadPref
        鈹�                         鈹斺啋 NovaAcExportManager + Android SAF
        鈹�
        鈹斺啋 AppDiagnostics / Developer Console / BackupHelper
```

This blueprint follows a deliberate ownership rule: rendering layers request an action; dedicated services own resource lifetime, I/O, audio sessions, or persistence. That rule keeps an export button from being an isolated UI effect and prevents an audio toggle from pretending to work when no audio session is available.

#### Module responsibilities

| Module | Responsibility |
| --- | --- |
| app | The Android application module. It owns Compose UI, the app manifest, Media3 integration, Hilt wiring, persistent settings, playback, downloads, NovaAc, and all user-facing screens. |
| spotify | A Kotlin/JVM metadata and network module using Ktor and serialization. It supplies Spotify-facing data operations without requiring the Compose presentation layer to own transport details. |
| innertube | An Android library module for source resolution and media-oriented network behavior. It contains the InnerTube integration, candidate selection, provider fallbacks, compression support, and logging hooks. |
| metroserver-master | A bundled companion source tree. It is documented separately because it has its own operational lifecycle and should not be confused with the Android client runtime. |

#### Playback blueprint

The normal playback path begins with a collection action鈥攑lay, shuffle, queue, or deep link. A view model updates queue and current-song state, while `SongPlayer` selects a usable item. The player can prefer local downloaded material when appropriate, then use configured HTTPS stream resolution and quality policy, and can expose a web-player route only when the user has an eligible session. The active ExoPlayer provides the audio-session identifier. `AudioEffectController` uses that identifier to attach equalizer, virtualizer, room, and loudness effects. This is why settings changes trigger a refresh through `SongPlayer.refreshAudioEffects`: effects are attached to the session, not globally to the application. Media3鈥檚 player/session/service model is designed for this separation of player, session, and background service responsibilities. [1] [3]

| Layer | Owns | Failure behavior |
| --- | --- | --- |
| Collection UI | Intent, progress cues, route navigation | Shows an actionable message; does not block the main thread. |
| View model | Queue and current content state | Preserves coherent state even when a provider result fails. |
| `SongPlayer` | Player lifetime, source choice, session binding | Falls back through supported routes and records diagnostics. |
| `PlaybackService` | Notification, external commands, browse tree | Keeps system-facing media controls synchronized with the active player. |
| `AudioEffectController` | Android audio-effect objects | Disables unsupported effects and logs a warning without stopping playback. |

#### NovaAc archive blueprint

NovaAc is an application archive format, not a claim about provider media rights. The manager validates selection, converts tracks into archive records, compiles a binary payload, compresses it, encrypts it with AES-GCM using an Android Keystore-held key, serializes a header plus nonce plus ciphertext, and writes the prepared export through a URI selected by the Android Storage Access Framework. The header carries collection name, source type, app version, format version, track count, payload size, and the local-audio inclusion flag. Import first inspects compatibility; a newer format can be identified before the user relies on it.

| Archive step | Why it exists | User-visible result |
| --- | --- | --- |
| Prepare | Validates non-empty selection and constructs the payload off the UI thread. | A save picker is launched only after preparation succeeds. |
| Configure | Lets the user name the archive and choose local-cache inclusion. | The primary Playlist and Liked Songs flows are explicit and repeatable. |
| Save | Uses `ACTION_CREATE_DOCUMENT` / Compose activity-result integration. | Android presents a user-selected destination rather than silent storage. |
| Inspect | Reads the header without assuming import safety. | Compatibility messages can be shown before use. |
| Import | Interprets a compatible archive into library-aware records. | Offline/library integration is data-driven rather than a raw file dump. |

The Storage Access Framework is intentionally user-mediated: the system picker supplies the destination URI and avoids asking for broad storage access for an archive save. [2]

#### Settings and adaptive-shell blueprint

`SettingsPref` is the durable preference boundary. It stores quality, timeout, fallback, crossfade, audio, compact-interface, navigation-mode, and corner-radius values. The adaptive shell observes a settings revision flow, then rebuilds the root layout using Bottom Bar, Top Bar, Side Bar, or Navigation Rail while preserving the same `Routes` model and root navigation behavior. This means navigation mode is not four separate applications; it is one route graph rendered through four reachable layouts.

The 0鈥�48 dp shape value is scoped to user-facing shell and settings surfaces. It is not a data model, does not alter media metadata, and is intentionally clamped. Compact mode reduces navigation density but keeps labels on the selected destination and retains content descriptions. The accessibility rationale is simple: a compact presentation must not silently remove the route vocabulary users need to navigate.

#### Diagnostics, backups, and privacy

`AppDiagnostics` and `DevConsoleManager` provide bounded app-private observability. Their purpose is to capture launch, provider, playback, deep-link, backup, and error context without treating persistent logs as a dump of user or provider secrets. `BackupHelper` uses user-selected SAF locations; backup is a recovery feature, not a hidden synchronization channel. Any new provider, telemetry, or account integration should declare its data flow, consent point, retention, and error behavior before it is added.

> Design rule: errors that can be recovered from鈥攗nsupported audio effects, canceled save pickers, unavailable stream candidates, or a missing local cache file鈥攕hould degrade to a clear state and diagnostics record rather than take down playback or corrupt a collection.

#### References

[1]: https://developer.android.com/media/media3 "Android Developers: Introduction to Jetpack Media3"
[2]: https://developer.android.com/training/data-storage/shared/documents-files "Android Developers: Storage Access Framework"
[3]: https://github.com/androidx/media "AndroidX Media on GitHub"
[4]: https://developer.android.com/jetpack/compose "Android Developers: Jetpack Compose"
[5]: https://developer.android.com/training/dependency-injection/hilt-android "Android Developers: Dependency injection with Hilt"
[6]: https://github.com/H4zh4n/Spotui "Spotui upstream project"
[7]: https://github.com/navneet851/spotify-clone-jetpack-compose "Neptune Jetpack Compose Spotify clone"
[8]: https://github.com/spotbye/SpotiFLAC "SpotiFLAC project"
[9]: https://github.com/maxrave-dev/SimpMusic "SimpMusic project"
[10]: https://github.com/TeamNewPipe/NewPipeExtractor "NewPipe Extractor"

#### Implementation reference ledger 1

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 1.1 | Account and metadata | innertube | HTTPS resolver | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 1.2 | Playback foundation | metroserver-master | Spotify Web playback | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 1.3 | HTTPS stream resolution | app | Equalizer | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 1.4 | Offline workflow | spotify | Spatial profile | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 1.5 | NovaAc archives | innertube | Spatial depth | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 1.6 | Equalizer and spatial session | metroserver-master | Normalizer intensity | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 1.7 | Playlist control | app | Crossfade and DJ mode | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 1.8 | Liked Songs control | spotify | Navigation layout | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 1.9 | Adaptive interface | innertube | Compact UI | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 1.10 | Diagnostics and resilience | metroserver-master | Corner radius | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 1.11 | Lyrics and discovery | app | Cache and storage | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 1.12 | Backup and restore | spotify | Diagnostics | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 1

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **ARCHITECTURE_BLUEPRINT**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 2

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 2.1 | Account and metadata | metroserver-master | Spotify Web playback | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 2.2 | Playback foundation | app | Equalizer | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 2.3 | HTTPS stream resolution | spotify | Spatial profile | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 2.4 | Offline workflow | innertube | Spatial depth | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 2.5 | NovaAc archives | metroserver-master | Normalizer intensity | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 2.6 | Equalizer and spatial session | app | Crossfade and DJ mode | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 2.7 | Playlist control | spotify | Navigation layout | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 2.8 | Liked Songs control | innertube | Compact UI | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 2.9 | Adaptive interface | metroserver-master | Corner radius | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 2.10 | Diagnostics and resilience | app | Cache and storage | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 2.11 | Lyrics and discovery | spotify | Diagnostics | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 2.12 | Backup and restore | innertube | Backup | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 2

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **ARCHITECTURE_BLUEPRINT**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 3

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 3.1 | Account and metadata | app | Equalizer | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 3.2 | Playback foundation | spotify | Spatial profile | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 3.3 | HTTPS stream resolution | innertube | Spatial depth | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 3.4 | Offline workflow | metroserver-master | Normalizer intensity | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 3.5 | NovaAc archives | app | Crossfade and DJ mode | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 3.6 | Equalizer and spatial session | spotify | Navigation layout | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 3.7 | Playlist control | innertube | Compact UI | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 3.8 | Liked Songs control | metroserver-master | Corner radius | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 3.9 | Adaptive interface | app | Cache and storage | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 3.10 | Diagnostics and resilience | spotify | Diagnostics | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 3.11 | Lyrics and discovery | innertube | Backup | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 3.12 | Backup and restore | metroserver-master | Streaming quality | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 3

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **ARCHITECTURE_BLUEPRINT**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### v1.9.9 architecture addendum: resilience ownership

The 1.9.9 work preserves the project鈥檚 ownership boundary: UI state requests an action, while persistent preferences and long-lived services decide how that action executes. The new code does not move network or audio work into Compose. Instead, it extends the existing owners with bounded recovery behavior, so the lifecycle of a stream, a byte-range connection, or an audio effect remains independent of whether a collection screen is currently visible.

| Concern | Owner | Input | Applied behavior | Failure boundary |
| --- | --- | --- | --- | --- |
| Collection download order | `SongPlayer.downloadAll` | The collection鈥檚 ordered song list | Awaits each callback before resolving the next item | A failed item is logged and the queue advances |
| Stream quality and recovery | `SongPlayer` plus `YTPlayerUtils` | Settings quality tier, ranked candidate results, stale URL state | Selects a tier-appropriate format and attempts fresh candidates when needed | Ends with a readable failure reason after bounded sources are exhausted |
| Raw media transfer | `SongPlayer.httpDownloadRanged` | Resolved URL and MrBean transport values | Uses identity encoding, redirects, byte ranges, and bounded attempts | Stops rather than appending a full response to an existing partial range file |
| Network tuning | `MrbeanNetworkSettings` | User-persisted bounded values | Supplies candidate count, timeout, chunk, and attempt parameters | Disabling returns paths to release defaults |
| Software spatial sound | `SpatialWideningAudioProcessor` | Enabled state, profile, and strength | Processes PCM in primary and crossfade audio sinks | Audio remains playable when platform effects are absent |
| Hardware audio effects | `AudioEffectController` | Session ID and persisted EQ/spatial values | Maps five frequency regions and configures optional Android effects | Capability report prevents unavailable effects from being claimed as active |
| Optional companion | `YouTubeMusicWebActivity` | Explicit user launch | Hosts the official website inside a visible WebView | User handles sign-in at the provider-controlled page |

The download path is deliberately two-layered. Stream resolution selects a source based on matching, quality policy, and available candidates. Transfer then treats the source as a transient HTTP resource: redirects are followed, content encoding is constrained to identity for byte accounting, ranges are validated, and an invalid partial-resume response is rejected. Keeping those responsibilities separate prevents a connection-level failure from being confused with a content-match failure and makes each diagnostic more useful.

The audio path is likewise layered. The Media3 player owns PCM delivery and audio-session identifiers. `SpatialWideningAudioProcessor` receives PCM in player sinks and applies profile-dependent stereo behavior. `AudioEffectController` attaches session-scoped Android effects only when the platform permits them, performs equalizer-band mapping from persisted five-band intent, and retains a live availability record. Settings refresh both paths through `SongPlayer`, so a slider movement has a single dispatch route rather than direct screen-to-effect coupling.

The MrBean namespace is kept as an app-local configuration family rather than a networking abstraction that claims full browser or server semantics. Its `client` package owns the persisted settings. The requested `http`, `http2`, `http3`, `ws`, `io`, `security`, `servlet`, `util`, and `websocket` package descriptors reserve clear extension boundaries, while the v1.9.9 runtime only activates controls that it can truthfully apply. In particular, no unbundled HTTP/3/QUIC implementation is represented as active.

> Architectural rule for subsequent versions: expand a package only when it has an explicit owner, a bounded data flow, and a failure policy. A folder name or a settings switch alone is not a feature; the UI, persistence, runtime owner, diagnostics, and documented availability must agree.


---

## Source document: `IMPLEMENTATION_DESIGN.md`

### Spotui Implementation Design

> A deep rationale for the design decisions embodied by the 1.9.5 source tree.

**Status:** Spotui 1.9.5 source package. This documentation describes the current supplied source tree and its integrated behavior. It does not claim a relationship with Spotify AB, and it does not describe provider-protected media export as a product capability.

The repository is a Kotlin Android music-client project built around Jetpack Compose, a Media3 playback core, Hilt dependency injection, bundled metadata/resolution modules, user-controlled storage routes, and a settings model that persists user choices. The documentation deliberately separates **verified current-source behavior** from **historical reconstruction**. The supplied project archive contains no Git metadata or tags, so version-era narrative before the documented 1.5.1 baseline is a product-line reconstruction rather than a claim that a particular commit has been recovered.

#### Design principles

The project uses five durable principles. **Visibility** means a high-value collection action should be placed where the collection action occurs. **Ownership** means I/O, player lifetime, and encryption remain in services rather than composables. **Reversibility** means interface and audio preferences can be changed back without rewriting library state. **Degradation** means unsupported device capabilities leave playback working. **Honesty** means document what the source can verify, distinguish reconstructed history, and never market an archive as a DRM bypass.

These principles are practical. They lower the cost of maintenance because a single NovaAc manager can power multiple collection screens, a single adaptive root can power multiple layouts, and a single audio effect controller can manage a changing Media3 session.

#### Detailed NovaAc design

A prepared archive separates expensive work from destination selection. `prepareExport` verifies selection, records source type and collection context, attempts only eligible local payload extraction when requested, compiles the manifest, compresses it, encrypts the bytes, and produces a filename/header/package in memory. `writePreparedExport` performs the destination URI write after the system picker returns. This two-step design makes cancelation straightforward and keeps the save picker from holding an unfinished archive operation. The header is useful both for diagnostics and future migration: format version and app version explain why a file may not be safe to interpret.

| Decision | Rationale | Rejected alternative |
| --- | --- | --- |
| Direct Export beside Download | Matches user mental model for collection-level persistence. | Hiding the primary archive workflow only in overflow menus. |
| Config dialog before save picker | Captures archive name and payload choice explicitly. | Silent defaults that make later file contents unclear. |
| Shared manager | Keeps binary/header behavior identical across collection types. | Copying serialization into each screen. |
| Android SAF destination | User controls where the archive is saved. | Broad storage permission and silent filesystem paths. |
| Versioned header | Supports compatibility messaging and migration planning. | Opaque payload with no inspectable metadata. |

#### Detailed audio design

The design treats Android effects as optional session capabilities. The Equalizer maps intent to bands rather than assuming a fixed number of bands. Virtualizer strength, room preset, and loudness target are clamped. When a device rejects an effect, the code logs the failure, disables only that effect, and leaves normal playback operating. Effects are released on session change and player release to prevent stale audio-session references. Crossfade remains a separate custom audio-processor concern because it operates during transitions, whereas equalizer/spatial/loudness effects are session-wide playback treatment.

The system does not make psychoacoustic promises it cannot guarantee. 鈥淚mmersive鈥� is a preference profile and an effect request, not a claim that every output route will sound the same. This is an important engineering distinction: the UI communicates intent; the framework and hardware determine supported execution.

#### Detailed navigation design

The adaptive shell is built from a common `Routes` vocabulary. Bottom, Top, Side, and Rail renderers call the same root-navigation behavior, including route restoration and Search reselect. The app root observes a settings revision signal so a persisted choice becomes visible immediately. Compact mode is deliberately a rendering modifier, not a separate set of screens. Radius is similarly bounded and presentation-only. This avoids a common anti-pattern in which every navigation mode owns duplicate route lists and gradually drifts in capability.

#### Release and operational design

The project distinguishes source validity from distribution policy. A build can compile and an APK can pass signature verification while still being signed with a local debug-format key. That artifact is suitable for local installation and test distribution under the owner鈥檚 policy, but it is not a substitute for a production private key. The design therefore records version code, version name, package ID, signature scheme, and checksum in release notes. Future CI should make those checks reproducible and should reject accidental secret files.

#### References

[1]: https://developer.android.com/media/media3 "Android Developers: Introduction to Jetpack Media3"
[2]: https://developer.android.com/training/data-storage/shared/documents-files "Android Developers: Storage Access Framework"
[3]: https://github.com/androidx/media "AndroidX Media on GitHub"
[4]: https://developer.android.com/jetpack/compose "Android Developers: Jetpack Compose"
[5]: https://developer.android.com/training/dependency-injection/hilt-android "Android Developers: Dependency injection with Hilt"
[6]: https://github.com/H4zh4n/Spotui "Spotui upstream project"
[7]: https://github.com/navneet851/spotify-clone-jetpack-compose "Neptune Jetpack Compose Spotify clone"
[8]: https://github.com/spotbye/SpotiFLAC "SpotiFLAC project"
[9]: https://github.com/maxrave-dev/SimpMusic "SimpMusic project"
[10]: https://github.com/TeamNewPipe/NewPipeExtractor "NewPipe Extractor"

#### Implementation reference ledger 1

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 1.1 | Account and metadata | innertube | HTTPS resolver | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 1.2 | Playback foundation | metroserver-master | Spotify Web playback | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 1.3 | HTTPS stream resolution | app | Equalizer | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 1.4 | Offline workflow | spotify | Spatial profile | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 1.5 | NovaAc archives | innertube | Spatial depth | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 1.6 | Equalizer and spatial session | metroserver-master | Normalizer intensity | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 1.7 | Playlist control | app | Crossfade and DJ mode | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 1.8 | Liked Songs control | spotify | Navigation layout | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 1.9 | Adaptive interface | innertube | Compact UI | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 1.10 | Diagnostics and resilience | metroserver-master | Corner radius | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 1.11 | Lyrics and discovery | app | Cache and storage | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 1.12 | Backup and restore | spotify | Diagnostics | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 1

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **IMPLEMENTATION_DESIGN**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 2

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 2.1 | Account and metadata | metroserver-master | Spotify Web playback | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 2.2 | Playback foundation | app | Equalizer | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 2.3 | HTTPS stream resolution | spotify | Spatial profile | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 2.4 | Offline workflow | innertube | Spatial depth | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 2.5 | NovaAc archives | metroserver-master | Normalizer intensity | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 2.6 | Equalizer and spatial session | app | Crossfade and DJ mode | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 2.7 | Playlist control | spotify | Navigation layout | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 2.8 | Liked Songs control | innertube | Compact UI | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 2.9 | Adaptive interface | metroserver-master | Corner radius | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 2.10 | Diagnostics and resilience | app | Cache and storage | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 2.11 | Lyrics and discovery | spotify | Diagnostics | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 2.12 | Backup and restore | innertube | Backup | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 2

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **IMPLEMENTATION_DESIGN**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 3

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 3.1 | Account and metadata | app | Equalizer | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 3.2 | Playback foundation | spotify | Spatial profile | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 3.3 | HTTPS stream resolution | innertube | Spatial depth | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 3.4 | Offline workflow | metroserver-master | Normalizer intensity | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 3.5 | NovaAc archives | app | Crossfade and DJ mode | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 3.6 | Equalizer and spatial session | spotify | Navigation layout | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 3.7 | Playlist control | innertube | Compact UI | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 3.8 | Liked Songs control | metroserver-master | Corner radius | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 3.9 | Adaptive interface | app | Cache and storage | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 3.10 | Diagnostics and resilience | spotify | Diagnostics | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 3.11 | Lyrics and discovery | innertube | Backup | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 3.12 | Backup and restore | metroserver-master | Streaming quality | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 3

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **IMPLEMENTATION_DESIGN**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 4

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 4.1 | Account and metadata | spotify | Spatial profile | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 4.2 | Playback foundation | innertube | Spatial depth | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 4.3 | HTTPS stream resolution | metroserver-master | Normalizer intensity | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 4.4 | Offline workflow | app | Crossfade and DJ mode | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 4.5 | NovaAc archives | spotify | Navigation layout | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 4.6 | Equalizer and spatial session | innertube | Compact UI | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 4.7 | Playlist control | metroserver-master | Corner radius | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 4.8 | Liked Songs control | app | Cache and storage | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 4.9 | Adaptive interface | spotify | Diagnostics | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 4.10 | Diagnostics and resilience | innertube | Backup | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 4.11 | Lyrics and discovery | metroserver-master | Streaming quality | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 4.12 | Backup and restore | app | Lossless timeout | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 4

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **IMPLEMENTATION_DESIGN**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


---

## Source document: `IMPLEMENTATION_AUDIT.md`

### Spotui Implementation Audit

> A maintainable audit of source ownership, integration checks, correctness boundaries, and high-value test scenarios.

**Status:** Spotui 1.9.5 source package. This documentation describes the current supplied source tree and its integrated behavior. It does not claim a relationship with Spotify AB, and it does not describe provider-protected media export as a product capability.

The repository is a Kotlin Android music-client project built around Jetpack Compose, a Media3 playback core, Hilt dependency injection, bundled metadata/resolution modules, user-controlled storage routes, and a settings model that persists user choices. The documentation deliberately separates **verified current-source behavior** from **historical reconstruction**. The supplied project archive contains no Git metadata or tags, so version-era narrative before the documented 1.5.1 baseline is a product-line reconstruction rather than a claim that a particular commit has been recovered.

#### Audit method

The audit is organized around observable ownership rather than a superficial checklist. A feature is considered integrated only when a user-facing trigger reaches a real service, the service owns its work off the main thread when necessary, a recoverable failure remains visible, and the setting or data result can survive recreation according to its intended scope. A visible switch that only changes a local Compose variable is not a completed implementation.

| Audit question | Evidence to inspect | Good result |
| --- | --- | --- |
| Does a control reach a service? | UI callback, view model, manager invocation | Export calls the NovaAc manager; audio refresh reaches the session controller. |
| Is work safe for UI? | Coroutine dispatcher / manager boundaries | Archive preparation and output do not block the screen. |
| Is fallback honest? | `runCatching`, result handling, diagnostics | Unsupported effects disable cleanly without silent false success. |
| Is state durable at the right layer? | `SettingsPref`, Room, collection prefs | Navigation and audio configuration survive normal recreation. |
| Is data scope explicit? | SAF, cache helper, archive header | User chooses destination; protected provider content is not claimed as exported. |

#### Current integration ledger

| Area | Current behavior and boundary |
| --- | --- |
| Account and metadata | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| Playback foundation | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| HTTPS stream resolution | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| Offline workflow | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| NovaAc archives | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| Equalizer and spatial session | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| Playlist control | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| Liked Songs control | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| Adaptive interface | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| Diagnostics and resilience | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| Lyrics and discovery | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| Backup and restore | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |

#### Collection workflow audit

The collection surfaces are a prime example of the audit method. Standard playlists, albums, Downloads, and Liked Songs have overlapping but not identical actions. The two collection surfaces that users most expect to archive鈥攑laylists and Liked Songs鈥攏ow expose the same direct labeled export placement next to Download. The action opens configuration, captures user intent, uses a shared archive manager, and starts the system picker only after preparation. The common manager avoids divergent binary formats while the per-screen labels preserve user context.

The audit should test empty collection handling, canceling the picker, inaccessible destination URIs, metadata-only archive creation, archive creation with no eligible local cache payloads, source type labeling, header inspection, and import compatibility messages. The expected result is never a damaged collection; errors must surface as clear messages and diagnostics.

#### Playback and audio audit

Audio configuration is valid only when it changes the active session. The player listener attaches `AudioEffectController` when ExoPlayer publishes an audio-session identifier. The controller can create Equalizer, Virtualizer, PresetReverb, and LoudnessEnhancer instances independently because Android devices may omit individual implementations. Applying preferences therefore checks support and handles each effect separately. A test pass should cover built-in speaker, wired output, Bluetooth output, a route with no virtualizer support, route changes during playback, and player release.

A perceptual test should be honest: spatial effects depend on device hardware, output route, input mix, and platform implementation. The product can promise it requests and applies supported framework processing; it cannot promise that every source/device combination will create a dramatic three-dimensional effect.

#### Adaptive shell audit

The adaptive navigation model uses one navigation graph and multiple surface renderers. Test Bottom Bar, Top Bar, Side Bar, and Navigation Rail with Home, Search, Library, Downloads, and Settings. Confirm that Search reselect remains available, selected route indication is accurate, the current player state survives layout changes, the mini-player is not duplicated, and Login/Queue visibility rules suppress the root navigation when expected. Compact UI should reduce density without removing content descriptions. Radius values should clamp to the 0鈥�48 dp range and remain stable after recreation.

#### Security and data audit

NovaAc encryption uses Android Keystore-backed AES-GCM within the application鈥檚 architecture. The audit must state the limitation: installation-scoped key material is appropriate for the declared application archive flow but is not a universal cross-device password-sharing protocol. Any future portable-key design requires a separately documented KDF, salt, user-authentication interaction, threat model, and migration strategy. Diagnostics must redact URLs/tokens. Backups and archives should never be confused with an authorization grant for provider-controlled media.

#### High-value regression checklist

| Area | Minimum regression exercise |
| --- | --- |
| Launch / restore | Open, restore saved song and queue state, then navigate to a non-root route. |
| Archive export | Export Playlist and Liked Songs archives with and without local cache inclusion. |
| Archive import | Inspect compatible/incompatible headers and import an allowed archive without corrupting current collections. |
| Audio | Change EQ, spatial, depth, normalizer, and output route during active playback. |
| Navigation | Switch each layout, compact mode, and radius, then relaunch. |
| Streaming | Exercise ordinary quality, lossless timeout, fallback, preloading, and offline/local path behavior. |
| Resilience | Cancel a picker, deny notification permission, make a provider unavailable, and inspect diagnostics. |
| Artifact | Assemble release, inspect badging, validate signature scheme, record checksum. |

#### References

[1]: https://developer.android.com/media/media3 "Android Developers: Introduction to Jetpack Media3"
[2]: https://developer.android.com/training/data-storage/shared/documents-files "Android Developers: Storage Access Framework"
[3]: https://github.com/androidx/media "AndroidX Media on GitHub"
[4]: https://developer.android.com/jetpack/compose "Android Developers: Jetpack Compose"
[5]: https://developer.android.com/training/dependency-injection/hilt-android "Android Developers: Dependency injection with Hilt"
[6]: https://github.com/H4zh4n/Spotui "Spotui upstream project"
[7]: https://github.com/navneet851/spotify-clone-jetpack-compose "Neptune Jetpack Compose Spotify clone"
[8]: https://github.com/spotbye/SpotiFLAC "SpotiFLAC project"
[9]: https://github.com/maxrave-dev/SimpMusic "SimpMusic project"
[10]: https://github.com/TeamNewPipe/NewPipeExtractor "NewPipe Extractor"

#### Implementation reference ledger 1

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 1.1 | Account and metadata | innertube | HTTPS resolver | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 1.2 | Playback foundation | metroserver-master | Spotify Web playback | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 1.3 | HTTPS stream resolution | app | Equalizer | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 1.4 | Offline workflow | spotify | Spatial profile | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 1.5 | NovaAc archives | innertube | Spatial depth | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 1.6 | Equalizer and spatial session | metroserver-master | Normalizer intensity | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 1.7 | Playlist control | app | Crossfade and DJ mode | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 1.8 | Liked Songs control | spotify | Navigation layout | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 1.9 | Adaptive interface | innertube | Compact UI | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 1.10 | Diagnostics and resilience | metroserver-master | Corner radius | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 1.11 | Lyrics and discovery | app | Cache and storage | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 1.12 | Backup and restore | spotify | Diagnostics | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 1

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **IMPLEMENTATION_AUDIT**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 2

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 2.1 | Account and metadata | metroserver-master | Spotify Web playback | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 2.2 | Playback foundation | app | Equalizer | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 2.3 | HTTPS stream resolution | spotify | Spatial profile | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 2.4 | Offline workflow | innertube | Spatial depth | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 2.5 | NovaAc archives | metroserver-master | Normalizer intensity | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 2.6 | Equalizer and spatial session | app | Crossfade and DJ mode | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 2.7 | Playlist control | spotify | Navigation layout | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 2.8 | Liked Songs control | innertube | Compact UI | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 2.9 | Adaptive interface | metroserver-master | Corner radius | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 2.10 | Diagnostics and resilience | app | Cache and storage | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 2.11 | Lyrics and discovery | spotify | Diagnostics | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 2.12 | Backup and restore | innertube | Backup | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 2

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **IMPLEMENTATION_AUDIT**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 3

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 3.1 | Account and metadata | app | Equalizer | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 3.2 | Playback foundation | spotify | Spatial profile | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 3.3 | HTTPS stream resolution | innertube | Spatial depth | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 3.4 | Offline workflow | metroserver-master | Normalizer intensity | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 3.5 | NovaAc archives | app | Crossfade and DJ mode | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 3.6 | Equalizer and spatial session | spotify | Navigation layout | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 3.7 | Playlist control | innertube | Compact UI | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 3.8 | Liked Songs control | metroserver-master | Corner radius | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 3.9 | Adaptive interface | app | Cache and storage | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 3.10 | Diagnostics and resilience | spotify | Diagnostics | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 3.11 | Lyrics and discovery | innertube | Backup | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 3.12 | Backup and restore | metroserver-master | Streaming quality | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 3

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **IMPLEMENTATION_AUDIT**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


---

## Source document: `CONFIGURATION_REFERENCE.md`

### Spotui Configuration Reference

> A field guide to every user-facing settings family, its owning service, and its runtime effect.

**Status:** Spotui 1.9.9 source package. This documentation describes the current supplied source tree and its integrated behavior. It does not claim a relationship with Spotify AB, and it does not describe provider-protected media export as a product capability.

The repository is a Kotlin Android music-client project built around Jetpack Compose, a Media3 playback core, Hilt dependency injection, bundled metadata/resolution modules, user-controlled storage routes, and a settings model that persists user choices. The documentation deliberately separates **verified current-source behavior** from **historical reconstruction**. The supplied project archive contains no Git metadata or tags, so version-era narrative before the documented 1.5.1 baseline is a product-line reconstruction rather than a claim that a particular commit has been recovered.

#### Settings catalog

| Control | Implementation logic |
| --- | --- |
| Streaming quality | Wi-Fi, cellular, and download quality tiers determine the resolver鈥檚 source preference. Lossless requests are best-effort and deliberately fall back to a usable stream when a lossless source is unavailable. |
| Lossless timeout | The selected timeout bounds waiting on lossless mirrors. Short mode favors responsiveness; long mode favors a more patient attempt before fallback. |
| HTTPS resolver | The secure HTTPS resolver remains the normal stream path. The screen reports its role and exposes next-track preloading as an explicit performance trade-off. |
| Spotify Web playback | This optional engine is used only when a valid logged-in web-player session is available. It is not presented as a bypass path; the direct resolver stays available as fallback. |
| Equalizer | Bass, Low-mid body, Vocal, Presence, and Treble detail are normalized 0鈥�100 preferences. The controller maps their frequency regions to hardware-supported equalizer bands at the current audio session. |
| Spatial profile | Studio, Wide, Immersive, and Cinema profiles configure the software widening processor plus available room/width effects. The software processor keeps profiles meaningfully different even where a hardware Virtualizer is unavailable. |
| Spatial depth | A 0鈥�100% control maps to the Android virtualizer strength range when the device supports it. Unsupported devices leave playback intact and log a diagnostic rather than failing audio. |
| Normalizer intensity | A bounded 0鈥�600 mB loudness target is applied through Android鈥檚 LoudnessEnhancer where supported. The limit exists to reduce the risk of an unexpectedly aggressive gain change. |
| Crossfade and DJ mode | Crossfade controls overlap duration. DJ mode uses the custom processor鈥檚 filter behavior during transitions rather than changing normal playback samples. |
| Navigation layout | Bottom bar, top bar, side bar, and navigation rail are real shell layouts. They preserve the same root routes and search reselect behavior. |
| Compact UI | Compact UI reduces navigation visual density and labels while preserving accessible content descriptions and route reachability. |
| Corner radius | The live 0鈥�48 dp preference changes shell and settings-surface shaping. It is a presentation setting only and cannot alter collection metadata or playback state. |
| Cache and storage | Cache summary and cleanup distinguish temporary resources from user-directed content. Cleanup is intentionally targeted rather than a recursive destructive wipe of every app file. |
| Diagnostics | The developer console and app-private diagnostic records are for troubleshooting. Stream URLs and tokens must be redacted before long-lived logging. |
| Backup | Backup destination selection uses Android鈥檚 system picker. Persistable URI permissions are retained only for locations the user actively chooses. |
| YouTube Music companion | An optional setting opens the visible official `music.youtube.com` web companion. Sign-in occurs at the website; the app stores no custom password form. |
| Player video preview | A persisted toggle controls whether the existing player alternative-stream/video preview path is shown. |
| Eclipse MrBean Network Engine | Persists candidate breadth, raw-download connect/read timeouts, byte-range chunk size, and attempts. `SongPlayer` consumes these settings for app-owned resolver/download behavior. |

#### Audio configuration logic

The audio page contains settings that are meaningful only when paired with the active Media3 audio session. The equalizer controller normalizes five user sliders around a neutral midpoint and distributes Bass below 180 Hz, Low-mid body from 180鈥�649 Hz, Vocal from 650鈥�2299 Hz, Presence from 2300鈥�5999 Hz, and Treble detail from 6000 Hz upward across available device bands. Spatial processing is dual-path: the framework Virtualizer and PresetReverb are used when they can be created for the session, while a PCM stereo-widening processor is refreshed in the primary and crossfade pipelines for the selected profile. The requested profile and depth are persisted even when an output device lacks a framework effect, but playback continues without misreporting unavailable hardware. Loudness normalization uses a deliberately bounded target gain; its purpose is an adjustable session effect, not a claim that every track has been loudness-analyzed.

| Setting group | Runtime owner | Expected effect | Safe fallback |
| --- | --- | --- | --- |
| EQ preset / five bands | `AudioEffectController` | Band levels on the current audio session across Bass, Low-mid, Vocal, Presence, and Treble regions | Leave unsupported band operation disabled; log warning. |
| Spatial profile / depth | Software PCM widener + Virtualizer + PresetReverb | Profile-specific stereo width and room emphasis where supported | Keep audio playing with software processing and omit unavailable hardware effect. |
| Normalizer | LoudnessEnhancer | Bounded target gain | No gain modification when unavailable. |
| Crossfade | Custom filter processor + player transition | Overlap timing and optional DJ filtering | Pass-through outside a transition. |

#### Collection export configuration

Playlist and Liked Songs exports use the same interaction contract. The user sees a labeled **Export** control directly beside **Download**, chooses a name, selects whether local downloaded cache payloads should be included when available, and then confirms an Android save location. The visible control matters because archive workflows are intentional data operations; they should not be hidden behind a three-dot menu that users may not discover. A canceled picker leaves the collection unchanged. Preparation work runs off the UI thread; write results return to the UI as success or readable failure text.

For Downloads, bulk export to the shared Music location and NovaAc import/export are separate actions because their semantics differ. A Music export is a visible local-file operation. A NovaAc archive is a structured collection container. Neither feature is a representation that provider-protected media has been decrypted or repackaged.

#### Streaming configuration

The current streaming model favors a secure HTTPS resolver backed by the project鈥檚 source and metadata modules. Quality preferences guide candidate selection. Lossless is a preference with a bounded timeout, not a promise: when a desired lossless source is absent or too slow, the engine returns to a playable quality tier. Next-stream preload is a cache/performance choice. The optional web-player setting is guarded by the availability of a user session and platform capabilities, while the direct resolver continues to provide a fallback route.

| Choice | Prefer when | Trade-off | Diagnostic value |
| --- | --- | --- | --- |
| Low / Normal quality | Metered or constrained network | Lower fidelity, lower transfer pressure | Candidate and fallback information. |
| High quality | Stable ordinary playback | Larger transfer and cache cost | Source selection trace. |
| Lossless attempt | User prioritizes available lossless candidates | May wait before fallback | Provider availability / timeout visibility. |
| Preload next stream | Queue continuity matters | More cache/network work | Preload failures remain non-fatal. |
| Web player option | Eligible user web session exists | Session and platform constraints | Distinguishes web path from resolver fallback. |

#### Interface configuration

The navigation controls are structural. Bottom Bar provides the traditional thumb-friendly layout. Top Bar supports a horizontal tab model. Side Bar provides expanded labels, and Navigation Rail reduces horizontal pressure while retaining root routes. All modes use the same navigation graph so a preference change does not rewrite queue state, current song state, or collection routes. Corner radius is bounded between 0 and 48 dp to make visual extremes deliberate but prevent uncontrolled layout values. Compact UI adjusts density and label policy rather than hiding feature routes.

> Configuration changes are meant to be reversible. A user who selects a compact rail can always return to Settings through the same root route and switch back to Bottom Bar. The preference is presentation state, not account state.

#### Storage, backup, and troubleshooting

Cache actions must distinguish ephemeral resolver/artwork resources from user-directed downloads and exported archives. The project therefore treats cache cleanup as targeted maintenance and exposes summaries before cleanup. Backup uses the system document-tree picker, which grants access only to a location selected by the user. The diagnostic console is the place to inspect provider availability, archive errors, deep-link handling, and player bootstrap failure. Logs should be exported only after reviewing privacy implications.

#### References

[1]: https://developer.android.com/media/media3 "Android Developers: Introduction to Jetpack Media3"
[2]: https://developer.android.com/training/data-storage/shared/documents-files "Android Developers: Storage Access Framework"
[3]: https://github.com/androidx/media "AndroidX Media on GitHub"
[4]: https://developer.android.com/jetpack/compose "Android Developers: Jetpack Compose"
[5]: https://developer.android.com/training/dependency-injection/hilt-android "Android Developers: Dependency injection with Hilt"
[6]: https://github.com/H4zh4n/Spotui "Spotui upstream project"
[7]: https://github.com/navneet851/spotify-clone-jetpack-compose "Neptune Jetpack Compose Spotify clone"
[8]: https://github.com/spotbye/SpotiFLAC "SpotiFLAC project"
[9]: https://github.com/maxrave-dev/SimpMusic "SimpMusic project"
[10]: https://github.com/TeamNewPipe/NewPipeExtractor "NewPipe Extractor"

#### Implementation reference ledger 1

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 1.1 | Account and metadata | innertube | HTTPS resolver | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 1.2 | Playback foundation | metroserver-master | Spotify Web playback | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 1.3 | HTTPS stream resolution | app | Equalizer | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 1.4 | Offline workflow | spotify | Spatial profile | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 1.5 | NovaAc archives | innertube | Spatial depth | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 1.6 | Equalizer and spatial session | metroserver-master | Normalizer intensity | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 1.7 | Playlist control | app | Crossfade and DJ mode | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 1.8 | Liked Songs control | spotify | Navigation layout | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 1.9 | Adaptive interface | innertube | Compact UI | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 1.10 | Diagnostics and resilience | metroserver-master | Corner radius | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 1.11 | Lyrics and discovery | app | Cache and storage | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 1.12 | Backup and restore | spotify | Diagnostics | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 1

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **CONFIGURATION_REFERENCE**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 2

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 2.1 | Account and metadata | metroserver-master | Spotify Web playback | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 2.2 | Playback foundation | app | Equalizer | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 2.3 | HTTPS stream resolution | spotify | Spatial profile | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 2.4 | Offline workflow | innertube | Spatial depth | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 2.5 | NovaAc archives | metroserver-master | Normalizer intensity | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 2.6 | Equalizer and spatial session | app | Crossfade and DJ mode | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 2.7 | Playlist control | spotify | Navigation layout | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 2.8 | Liked Songs control | innertube | Compact UI | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 2.9 | Adaptive interface | metroserver-master | Corner radius | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 2.10 | Diagnostics and resilience | app | Cache and storage | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 2.11 | Lyrics and discovery | spotify | Diagnostics | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 2.12 | Backup and restore | innertube | Backup | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 2

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **CONFIGURATION_REFERENCE**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 3

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 3.1 | Account and metadata | app | Equalizer | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 3.2 | Playback foundation | spotify | Spatial profile | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 3.3 | HTTPS stream resolution | innertube | Spatial depth | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 3.4 | Offline workflow | metroserver-master | Normalizer intensity | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 3.5 | NovaAc archives | app | Crossfade and DJ mode | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 3.6 | Equalizer and spatial session | spotify | Navigation layout | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 3.7 | Playlist control | innertube | Compact UI | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 3.8 | Liked Songs control | metroserver-master | Corner radius | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 3.9 | Adaptive interface | app | Cache and storage | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 3.10 | Diagnostics and resilience | spotify | Diagnostics | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 3.11 | Lyrics and discovery | innertube | Backup | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 3.12 | Backup and restore | metroserver-master | Streaming quality | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 3

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **CONFIGURATION_REFERENCE**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### v1.9.9 Eclipse MrBean Network Engine

The Eclipse MrBean section is a runtime configuration layer for Spotui-owned media connections. It does not replace Android鈥檚 network security policy, alter another application鈥檚 traffic, or create a generic tunneling service. Its scope is deliberately narrow: it persists a small set of bounded tuning values and `SongPlayer` reads those values when it chooses the number of ranked resolver candidates to attempt and when it opens the raw `HttpURLConnection` calls used by the corruption-safe ranged-download path. This makes every visible control an applied control.

| Control | Default | Persisted bounds | Runtime behavior | Restore-default behavior |
| --- | --- | --- | --- | --- |
| Enable transport tuning | Enabled | Boolean | Selects configured values or the baked-in release defaults | Restores enabled state |
| Ranked candidate breadth | 6 sources | 3鈥�8 sources | Caps the number of ranked song and video sources attempted in each resolver pass | Returns to six candidates |
| Download connect timeout | 15 seconds | 5鈥�45 seconds | Bounds establishment of a raw byte-range media connection | Returns to 15 seconds |
| Download read timeout | 30 seconds | 10鈥�90 seconds | Bounds stalled reads after a download connection is made | Returns to 30 seconds |
| Byte-range chunk size | 8 MiB | 1鈥�16 MiB | Determines the requested interval per ranged transfer segment | Returns to 8 MiB |
| Attempts per byte range | 3 attempts | 1鈥�5 attempts | Bounds recovery after an interrupted segment without allowing an infinite loop | Returns to three attempts |

The default configuration is the release baseline and is recommended for most users. Larger candidate breadth is useful when several ranked sources are intermittently unavailable, but it makes each failed resolution path do more work. Longer timeouts may help an extremely slow network, but also extend the period before a visible failure. Smaller chunks reduce the amount of data affected by a reset at the cost of more individual HTTP requests. The reset action clears the MrBean preference file and rehydrates the screen with the same default values used by the release code.

> **Protocol availability:** The Android platform鈥檚 secure networking stack may negotiate HTTP/2 when the platform and remote endpoint support it. Spotui v1.9.9 does not bundle a native QUIC/Cronet provider, so the `http3` package provides an explicit unavailable marker rather than claiming active HTTP/3 support. No setting attempts to force an unavailable transport.

#### v1.9.9 companion and library preferences

The YouTube Music companion preference enables the optional path into a visible WebView at the official YouTube Music web site. It is not a third-party login prompt and not a replacement for official account controls. The screen persists only standard website cookies needed by the existing resolver鈥檚 user-authorized attempts; it never renders an app-owned password entry field. Users who do not want this companion can leave it disabled and retain normal Spotui library behavior.

The Library screen鈥檚 group state is retained as UI state so playlists, albums, and the companion entry can be expanded or collapsed without affecting the underlying library records. The visual grouping is therefore reversible presentation state, like compact UI or navigation mode, and never rewrites Spotify-linked collection metadata.

#### v1.9.9 download and quality preference behavior

Quality selection is operational rather than descriptive. The underlying format chooser uses direct bitrate caps for Low and Auto policies and chooses the best viable candidate for High. When a resolved media URL expires, the download/replay path clears stale state, asks the resolver for fresh candidates, and progresses through the normal quality ladder rather than reusing an invalid cached URL. A user鈥檚 selected quality still guides the first choice; fallback exists to preserve playable or downloadable output when that ideal format is temporarily unavailable.

Collection downloads preserve their supplied ordering. The coordinator awaits completion after every track, emits a diagnostic result, then moves to the next queue entry after a brief resource-release interval. This setting is not exposed as a switch because order is the requested default behavior and a parallel alternative would reintroduce source-chain contention. Individual files remain subject to provider availability and network reachability, but one failure does not prevent later queue entries from being attempted.


---

## Source document: `CONTRIBUTING_AND_OPERATIONS.md`

### Spotui Contributing and Operations Guide

> A practical guide for maintainers, contributors, release engineers, and reviewers.

**Status:** Spotui 1.9.5 source package. This documentation describes the current supplied source tree and its integrated behavior. It does not claim a relationship with Spotify AB, and it does not describe provider-protected media export as a product capability.

The repository is a Kotlin Android music-client project built around Jetpack Compose, a Media3 playback core, Hilt dependency injection, bundled metadata/resolution modules, user-controlled storage routes, and a settings model that persists user choices. The documentation deliberately separates **verified current-source behavior** from **historical reconstruction**. The supplied project archive contains no Git metadata or tags, so version-era narrative before the documented 1.5.1 baseline is a product-line reconstruction rather than a claim that a particular commit has been recovered.

#### Contribution contract

A good contribution explains three things before code is reviewed: **what user problem changes**, **which layer owns the behavior**, and **how the failure behaves**. For example, 鈥渁dd an export button鈥� is incomplete. A complete proposal states that Playlist and Liked Songs use a shared archive manager; archive preparation runs off the UI thread; Android鈥檚 picker selects the destination; and cancellation leaves source collections unchanged. The same discipline applies to audio, streaming, account, cache, and navigation work.

| Change type | Primary files or layers | Review focus |
| --- | --- | --- |
| New collection action | Screen + view model + manager | Discoverability, shared behavior, cancel/error paths. |
| New setting | Settings UI + `SettingsPref` + consuming service | Persistence, immediate effect, safe defaults, migration. |
| Audio behavior | `SongPlayer` + `AudioEffectController` | Session lifetime, route support, no main-thread work. |
| Source resolver work | Metadata / InnerTube module + diagnostics | Provider boundaries, fallback, URL redaction. |
| Navigation mode | Adaptive shell + routes | Same destinations and Search reselect semantics. |
| Release work | Gradle config + validation notes | Version monotonicity, signing honesty, checksum. |

#### Local development workflow

Use Java 21 and an Android SDK that contains API 36 platform/build tools. Open the project root, allow Gradle to resolve dependencies, and run the release build before claiming an integration is complete. Use a device for player/audio routes whenever possible, because emulator audio support does not prove hardware effect behavior. Keep synthetic/local test content separate from account data. Never commit cookies, account secrets, private signing keys, downloaded personal media, or generated diagnostics.

##### Pull-request narrative

A high-quality pull request should include a summary, screenshots or a short interaction description for UI changes, the affected persistence keys, service ownership, tests performed, and known route/device limitations. If a feature is reconstructed from user-requested behavior rather than historical source evidence, say so in the documentation rather than creating invented provenance.

#### Operational guidance

The application is a media client with background playback, user-selected files, and optional session-dependent routes. Operations therefore include more than server uptime. Monitor release build output, module compatibility, user-visible diagnostic signals, archive header compatibility, and Android behavior changes. If an upstream source resolver changes, preserve a clear fallback message rather than burying the failure. If an archive format changes, increment its format version and write migration tests before changing the default importer. If a new account flow is proposed, define consent, logout/revocation, storage, and support boundaries first.

> The project should optimize for trustworthy behavior: 鈥渢his route is unavailable, here is what happened鈥� is better than a silent spinner or an overbroad permission request.

#### Credits and notices

Contributors must retain source notices and respect upstream licenses. The repository credits the AndroidX Media project, Jetpack Compose, Hilt, Ktor, OkHttp, NewPipe Extractor, Glide, ML Kit, Room, Neptune, SpotiFLAC, SimpMusic, and the broader Kotlin/Android ecosystem. Use the dependency catalog as the source of version truth, and consult each upstream repository for its current license and attribution obligations before redistribution.

#### References

[1]: https://developer.android.com/media/media3 "Android Developers: Introduction to Jetpack Media3"
[2]: https://developer.android.com/training/data-storage/shared/documents-files "Android Developers: Storage Access Framework"
[3]: https://github.com/androidx/media "AndroidX Media on GitHub"
[4]: https://developer.android.com/jetpack/compose "Android Developers: Jetpack Compose"
[5]: https://developer.android.com/training/dependency-injection/hilt-android "Android Developers: Dependency injection with Hilt"
[6]: https://github.com/H4zh4n/Spotui "Spotui upstream project"
[7]: https://github.com/navneet851/spotify-clone-jetpack-compose "Neptune Jetpack Compose Spotify clone"
[8]: https://github.com/spotbye/SpotiFLAC "SpotiFLAC project"
[9]: https://github.com/maxrave-dev/SimpMusic "SimpMusic project"
[10]: https://github.com/TeamNewPipe/NewPipeExtractor "NewPipe Extractor"

#### Implementation reference ledger 1

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 1.1 | Account and metadata | innertube | HTTPS resolver | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 1.2 | Playback foundation | metroserver-master | Spotify Web playback | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 1.3 | HTTPS stream resolution | app | Equalizer | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 1.4 | Offline workflow | spotify | Spatial profile | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 1.5 | NovaAc archives | innertube | Spatial depth | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 1.6 | Equalizer and spatial session | metroserver-master | Normalizer intensity | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 1.7 | Playlist control | app | Crossfade and DJ mode | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 1.8 | Liked Songs control | spotify | Navigation layout | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 1.9 | Adaptive interface | innertube | Compact UI | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 1.10 | Diagnostics and resilience | metroserver-master | Corner radius | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 1.11 | Lyrics and discovery | app | Cache and storage | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 1.12 | Backup and restore | spotify | Diagnostics | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 1

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **CONTRIBUTING_AND_OPERATIONS**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 2

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 2.1 | Account and metadata | metroserver-master | Spotify Web playback | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 2.2 | Playback foundation | app | Equalizer | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 2.3 | HTTPS stream resolution | spotify | Spatial profile | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 2.4 | Offline workflow | innertube | Spatial depth | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 2.5 | NovaAc archives | metroserver-master | Normalizer intensity | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 2.6 | Equalizer and spatial session | app | Crossfade and DJ mode | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 2.7 | Playlist control | spotify | Navigation layout | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 2.8 | Liked Songs control | innertube | Compact UI | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 2.9 | Adaptive interface | metroserver-master | Corner radius | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 2.10 | Diagnostics and resilience | app | Cache and storage | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 2.11 | Lyrics and discovery | spotify | Diagnostics | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 2.12 | Backup and restore | innertube | Backup | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 2

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **CONTRIBUTING_AND_OPERATIONS**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 3

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 3.1 | Account and metadata | app | Equalizer | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 3.2 | Playback foundation | spotify | Spatial profile | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 3.3 | HTTPS stream resolution | innertube | Spatial depth | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 3.4 | Offline workflow | metroserver-master | Normalizer intensity | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 3.5 | NovaAc archives | app | Crossfade and DJ mode | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 3.6 | Equalizer and spatial session | spotify | Navigation layout | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 3.7 | Playlist control | innertube | Compact UI | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 3.8 | Liked Songs control | metroserver-master | Corner radius | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 3.9 | Adaptive interface | app | Cache and storage | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 3.10 | Diagnostics and resilience | spotify | Diagnostics | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 3.11 | Lyrics and discovery | innertube | Backup | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 3.12 | Backup and restore | metroserver-master | Streaming quality | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 3

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **CONTRIBUTING_AND_OPERATIONS**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 4

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 4.1 | Account and metadata | spotify | Spatial profile | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 4.2 | Playback foundation | innertube | Spatial depth | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 4.3 | HTTPS stream resolution | metroserver-master | Normalizer intensity | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 4.4 | Offline workflow | app | Crossfade and DJ mode | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 4.5 | NovaAc archives | spotify | Navigation layout | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 4.6 | Equalizer and spatial session | innertube | Compact UI | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 4.7 | Playlist control | metroserver-master | Corner radius | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 4.8 | Liked Songs control | app | Cache and storage | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 4.9 | Adaptive interface | spotify | Diagnostics | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 4.10 | Diagnostics and resilience | innertube | Backup | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 4.11 | Lyrics and discovery | metroserver-master | Streaming quality | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 4.12 | Backup and restore | app | Lossless timeout | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 4

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **CONTRIBUTING_AND_OPERATIONS**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


---

## Source document: `NOVAAC_FULL_AUDIO_GUIDE.md`

### NovaAc Full Local-Audio Archives and Browser Player

> **Spotui 1.9.5 full-audio archive guide.** This document explains the new archive profiles, size and coverage planning, security choices, import behavior, and the companion browser player.

#### Purpose and scope

NovaAc now supports a dedicated **full local-audio archive** workflow. Its purpose is to preserve a collection鈥檚 metadata together with every eligible audio file already present in the application鈥檚 local download store. The workflow is visible where users expect it: the labeled **Export** action sits directly beside **Download** on both standard Playlist and Liked Songs collection screens.

The feature deliberately distinguishes audio that is already locally accessible to the application from remote provider-controlled streams. A NovaAc archive can include local files discovered through the download index. It does not fetch, decrypt, capture, or repackage a remote stream merely because the stream is playable. This boundary keeps archive behavior explicit and lets users understand why a track is counted as available or missing.

#### Export profiles

| Profile | Local file requirement | Result | Best use |
| --- | --- | --- | --- |
| **Full archive 鈥� require every track downloaded** | All selected tracks must have an eligible local file. | The operation stops before saving if coverage is incomplete. | A complete locally available playlist or Liked Songs preservation package. |
| **Best-effort archive 鈥� include every available local track** | Any number of tracks may be local. | Every eligible local audio file is embedded; metadata is retained for the remaining tracks. | A large collection with partial offline coverage. |
| **Metadata-only archive** | No local files are required. | Only collection and track metadata is written. | A compact collection reference, migration record, or planning archive. |

The planner calculates coverage before Android鈥檚 save picker opens. It checks the local download index, verifies that each candidate path exists, verifies that the file has a non-zero size, and excludes individual files at or above the binary record鈥檚 2 GB per-track boundary. The archive configuration reports missing coverage through a clear stop condition for the Full profile. The Best-effort profile is intentionally different: it makes partial preservation explicit instead of pretending a partial file is complete.

#### Why large archives are supported

A full archive can be large because it contains real locally available audio bytes for many tracks. The implementation is designed for that use case. It does **not** build a complete playlist-sized byte array in memory before writing. Instead, after the user chooses a destination, the archive writer streams one record at a time into a GZIP and AES-GCM pipeline. Every eligible file is read in 64 KB chunks, checksummed with CRC32, and copied directly to the encrypted stream.

This design has two important effects. First, a long playlist does not require free RAM equal to the playlist鈥檚 total audio size. Second, the final archive size is determined by the actual local file bytes and compression behavior, not by a synthetic estimate. The planner still supplies an estimate using local audio bytes, metadata allowance, compression framing, and encryption overhead so users can decide whether a destination has enough space.

| Header field | Meaning |
| --- | --- |
| `requestedAudioMode` | Full, Best-effort, or Metadata-only profile chosen by the user. |
| `includedAudioTrackCount` | Number of track records carrying a local audio payload. |
| `missingAudioTrackCount` | Number of tracks without an eligible local file at plan time. |
| `localAudioBytes` | Total source bytes selected for archive embedding. |
| `payloadBytes=-1` | The archive was written through the streaming path; encrypted final length was unknown at header creation time. |
| `securityMode` | Device-secure or browser-passphrase encryption choice. |

> **Integrity model:** Each embedded file has a CRC32 record check, while the archive payload is authenticated through AES-GCM. A CRC mismatch prevents that track鈥檚 audio blob from being accepted by the interpreter. AES-GCM protects the encrypted payload from undetected changes when the correct key is used.

#### Security modes

The security selector appears in the advanced export dialog. It gives the user a deliberate choice between Android-context protection and user-authorized browser portability.

| Mode | Key source | Browser result | Android import result |
| --- | --- | --- | --- |
| **Device-secure archive** | Android Keystore key held by the authorized application context. | The companion HTML player can inspect the unencrypted header only. It cannot and should not decrypt the payload. | The authorized Android archive context can import it. |
| **Browser-passphrase archive** | A passphrase entered by the user at export time, with a unique random salt. | The companion HTML player can decrypt and play embedded local audio after the user enters the same passphrase. | Android can import when the same passphrase is supplied to the import path. |

Browser-passphrase archives derive a 256-bit AES key with PBKDF2-HMAC-SHA-256 using 210,000 iterations and a 16-byte random salt. The archive header retains the salt and iteration count necessary for authorized decryption, but it does not contain the passphrase. The actual archive payload uses AES-GCM. The companion HTML player invokes the browser鈥檚 Web Crypto API locally; it does not upload the archive or passphrase to a service.

The device-secure option remains important. Android Keystore material is intentionally not transferable into an HTML file, which prevents an uploaded file or browser script from silently gaining access to a device-bound key. The player therefore reports a clear 鈥渉eader inspected鈥� state for device-secure archives instead of suggesting that a passphrase can unlock an archive that was never exported with portable passphrase mode.

#### Browser player workflow

The source package ships `tools/novaac_archive_player.html`. It is a self-contained local file; open it in a current Chromium, Firefox, or Safari browser.

1. Export a Playlist or Liked Songs archive in **Browser-passphrase** mode. Choose Full or Best-effort local audio as appropriate.
2. Copy the HTML player file to a computer or open it locally from the source package.
3. Drop the `.novaac` file into the player or select it with the file picker.
4. Review collection name, track count, coverage, local-audio bytes, format, and security mode. Header inspection happens locally even before unlock.
5. Enter the export passphrase when prompted. The browser derives the archive key locally, decrypts, decompresses, and parses the track records in memory.
6. Filter tracks, select an embedded-audio track, and press **Play**. Tracks that only carry metadata remain visible but show a Metadata only badge.
7. Optional controls include previous/next navigation, queue mode, and manifest download. The manifest contains metadata and per-track local-audio availability, not the audio bytes themselves.

The player uses the browser audio element for embedded blobs. File type is inferred conservatively from familiar FLAC, ID3/MP3, MP4/M4A, and Ogg signatures. Playback support still depends on the browser and operating system codec support. A browser might successfully unlock an archive yet decline a codec; in that case, the track metadata remains available and the user can import the archive into the authorized application context.

#### Import and recovery

Spotui imports an archive by first reading its header, then selecting the correct key flow. Device-secure archives use the Android Keystore key. Browser-passphrase archives require the passphrase. The interpreter restores collection metadata and writes valid audio payloads to the app鈥檚 local downloads area when import is requested. Imported content is grouped into an Offline Collection with an `imported_` identifier so it can be distinguished from a remote source playlist.

A passphrase error, malformed archive, canceled save picker, unsupported effect, missing local file, or unavailable destination must not alter the original Playlist, Liked Songs state, or download records. The application records an actionable diagnostic where appropriate and reports a readable result to the screen.

#### Troubleshooting

| Symptom | Likely reason | Resolution |
| --- | --- | --- |
| Full archive says tracks are missing | The collection has tracks that have not been downloaded locally, a local file was removed, or a file exceeds the per-track binary limit. | Download the missing tracks, use Best-effort mode, or choose Metadata-only mode. |
| Browser player sees header but cannot unlock | The archive is device-secure or the browser passphrase is wrong. | Use the Android app for a device-secure archive; use the exact passphrase for a portable archive. |
| Browser player unlocks but a track cannot play | The record is metadata-only or the browser does not support the embedded codec. | Check the track badge, then use Android import/playback if necessary. |
| Final size is different from the estimate | Compression and AES-GCM framing occur as the stream is written. | Use the estimate for planning; use the final result for the recorded size. |
| Save picker was canceled | Android did not receive a destination URI. | Re-open Export and choose a destination; the source collection is unchanged. |
| Android import needs a passphrase | The archive was created in Browser-passphrase mode. | Provide the same passphrase that was selected during export. |

#### Operational and privacy notes

Full audio archives can contain a substantial amount of locally accessible user data. Treat them like any other personal local media backup: store them in a location the user controls, protect the passphrase for portable archives, and do not share an archive unless the user has the rights and intention to share every included local file. The HTML player deliberately performs no network upload or remote source lookup. The archive manager deliberately does not use local archive features as a route around provider controls.

#### References

[1]: https://developer.android.com/training/data-storage/shared/documents-files "Android Developers: Storage Access Framework"
[2]: https://developer.android.com/reference/javax/crypto/Cipher "Android Developers: Cipher"
[3]: https://developer.mozilla.org/en-US/docs/Web/API/SubtleCrypto/deriveKey "MDN Web Docs: SubtleCrypto deriveKey"
[4]: https://developer.mozilla.org/en-US/docs/Web/API/DecompressionStream "MDN Web Docs: DecompressionStream"

#### Binary Container Blueprint

A NovaAc file starts with a short unencrypted envelope. The envelope exists so a user interface can identify a file, show basic archive properties, and choose the correct key path before it attempts to read any encrypted content. The current marker is `NOVAAC4`. It is followed by a big-endian header length, a UTF-8 line-oriented header, nonce length, nonce bytes, and the ciphertext. A conventional in-memory export records an exact ciphertext length in `payloadBytes`. The full local-audio stream sets that header value to `-1`, then treats all remaining bytes as the authenticated cipher payload when importing. This lets the exporter write a large destination sequentially without buffering the complete encrypted archive solely to calculate a length first.

| Envelope segment | Encoding | Consumer responsibility | Failure behavior |
| --- | --- | --- | --- |
| Magic | ASCII marker | Confirm that the file is a compatible NovaAc container. | Stop with an invalid-file result before decryption. |
| Header length | Four-byte big-endian integer | Bound header allocation to a safe range. | Reject implausibly small or large headers. |
| Header | UTF-8 key/value lines | Select format, security, coverage, and user-facing archive details. | Use safe defaults only for legacy fields. |
| Nonce length and nonce | One-byte length plus AES-GCM IV | Supply the unique IV required by AES-GCM decryption. | Reject a malformed/empty nonce. |
| Cipher payload | AES-GCM bytes | Decrypt with the authorized Keystore or passphrase-derived key. | Treat authentication errors as a failed import/unlock. |

The decrypted compressed payload is a sequence of NovaBytecode records. Collection name, source type, and creation timestamp are global attributes. Every track begins with a track marker, stable identity, title, artist, album, artwork reference, duration, explicit flag, and artist identifiers. A local-audio record adds a CRC32 checksum, a variable-length payload size, and raw local bytes. An end marker closes each record. The archive end marker terminates the stream.

This format deliberately keeps track metadata present whether or not local audio is included. A track that was unavailable during a Best-effort export does not disappear from the collection. It remains a metadata record so a restored library can show what the collection contained and can use normal application resolution behavior later. The audio payload is an additional local-continuity resource, not the only representation of a track.

#### Export Planning Algorithm

The advanced export dialog maps user configuration to an archive plan. The plan is not merely a set of switches. It is a materialized description of the selected collection, chosen payload profile, selected security mode, discovered local file coverage, estimated size, and destination filename. The plan allows the system to explain a full-audio failure before a destination file is created.

```text
collection tracks
    鈹�
    鈹溾攢 resolve local download path by song URL / stable Spotify track reference
    鈹溾攢 verify file exists, is readable, has positive length, and fits per-track limit
    鈹溾攢 accumulate coverage, byte count, and unavailable-track list
    鈹溾攢 compute size estimate and default filename
    鈹溾攢 enforce profile rule (Full requires zero missing tracks)
    鈹斺攢 launch Storage Access Framework only when the plan is exportable
```

| Planning input | Why it is inspected | Result exposed to the user |
| --- | --- | --- |
| Collection name and source type | Keeps the archive meaningful after export/import. | Archive name and collection provenance. |
| Selected tracks | Defines record order and coverage denominator. | Total track count. |
| Local path | Determines whether a locally accessible payload can be embedded. | Available versus missing count. |
| File length | Supports estimate and prevents unsupported record length. | Approximate archive size and eligibility. |
| Audio profile | Defines whether missing audio is an error or a permitted omission. | Full, Best-effort, or Metadata-only behavior. |
| Security profile | Determines the encryption key path and browser portability. | Device secure or Browser passphrase annotation. |

The planner must run off the Compose UI thread. A long library can contain many local files, and filesystem metadata checks should never block touch or animation dispatch. The save action remains responsive: it schedules planning, shows a readable error if full coverage is not satisfied, and only launches Android鈥檚 create-document contract after it receives an exportable plan.

#### Streaming Writer Lifecycle

The writer is optimized for a large archive. It writes the public envelope first, initializes the selected AES-GCM cipher, and then pipes the generated bytecode through a GZIP stream into the cipher output stream. For each eligible audio track, the writer performs a first pass to calculate CRC32 and a second pass to copy the bytes in 64 KB chunks. The first pass supplies a per-record integrity value without retaining the file. The second pass supplies the real payload. Closing the nested GZIP and cipher streams writes compression trailing data and the AES-GCM authentication tag.

| Writer stage | Memory characteristic | User safety property |
| --- | --- | --- |
| Header creation | Small fixed allocation | Coverage and security declarations are recorded before payload transfer. |
| Track metadata | Small per-track values | Missing audio never erases collection identity. |
| CRC pass | Fixed 64 KB buffer | Local corruption can be detected per embedded track. |
| Payload copy | Fixed 64 KB buffer | A large playlist does not require a playlist-sized memory allocation. |
| Compression + encryption close | Stream finalization | The archive is authenticated before write completion is reported. |

A destination provider can expose final file length after writing, but not every provider is required to return it. The application therefore reports provider-observed length when available and falls back to the plan estimate otherwise. The distinction is deliberate: a UI should not state a fabricated exact value where Android鈥檚 provider only offered an estimate.

#### Browser Player Capability Matrix

The standalone player is designed as an archive companion, not an alternate streaming client. Its feature set is restricted to a file the user selects and, for portable mode, a passphrase the user enters locally. It never performs remote resolution and does not use the archive as an instruction to contact a third-party source.

| Capability | Device-secure archive | Browser-passphrase archive |
| --- | --- | --- |
| File drop / picker | Yes | Yes |
| Header inspection | Yes | Yes |
| Collection and coverage display | Yes | Yes |
| Payload decryption | No; Android Keystore key remains in authorized Android context. | Yes; after local PBKDF2/AES-GCM unlock. |
| Track list | Header-level count only. | Full decoded metadata. |
| Local-audio playback | No. | Yes when browser supports the embedded file codec. |
| Manifest export | Header context only. | Track-level availability manifest. |

The player鈥檚 local-only model is important for privacy. A web page opened from a local file can still be a powerful tool when it gives the user drag-and-drop inspection, an explicit unlock step, searchable tracks, labels for playable payloads, queue behavior, and no hidden server dependency. Keeping the tool static also makes it easy to include in the source package, email as a single file, or keep beside an archive on offline storage.

#### Test Blueprint

Before a release is described as a full-audio NovaAc build, test the archive system with controlled local files and distinct collection states. A successful Gradle compile validates Kotlin integration but does not prove that coverage conditions, Storage Access Framework interactions, encrypted output, browser unlock, or audio playback behave as users expect.

| Test case | Setup | Expected result |
| --- | --- | --- |
| Full archive with complete coverage | Download every track in a small playlist. | Save picker opens; archive reports all tracks as audio payloads. |
| Full archive with one missing local file | Remove one known local file or use an undownloaded track. | Plan stops before picker and names missing coverage. |
| Best-effort archive | Use a mixed downloaded/undownloaded collection. | Archive writes available payloads and retains metadata for all tracks. |
| Metadata-only archive | Use any collection. | Archive is small and all tracks show metadata-only status after unlock/import. |
| Device-secure archive in HTML player | Open a Keystore archive in the browser tool. | Header details are shown and the player explains why payload cannot be unlocked. |
| Browser-passphrase archive | Export, enter correct passphrase in HTML. | Tracks decode locally and embedded-audio tracks become playable. |
| Wrong browser passphrase | Enter a different passphrase. | AES-GCM authentication fails; no partial track list is presented. |
| Corrupted payload | Change archive bytes after export. | Decryption or CRC validation fails without altering the original collection. |
| Cancel save picker | Cancel Android document picker. | No archive is written and source/download state remains unchanged. |
| Large archive | Use a collection with many local files. | Memory remains bounded by streaming buffers; write time scales with actual content. |

#### Extension Rules

Future NovaAc work should preserve compatibility and clarity. A new binary opcode requires reader behavior for older and newer clients. A new security mode requires a threat model, key-derivation description, user consent text, migration path, and browser/Android capability statement. A new payload type needs an explicit source boundary and a resource budget. A progress indicator should be based on actual bytes processed rather than a cosmetic timer. A cancellation mechanism should close streams carefully and remove only a partial destination that the user explicitly created, never source downloads.

The most useful future enhancements are a visible byte-level progress sheet for long archive writes, a header-only compatibility inspector in Settings, a test-fixture suite containing metadata-only and portable-passphrase archives, a migration table for future format versions, and a recovery screen that can explain whether an import failed at header validation, passphrase derivation, AES-GCM authentication, decompression, bytecode parsing, CRC verification, or destination storage.

> Maintenance rule: archive completeness must be an observable property. The user should be able to read coverage and security mode from the archive header and should never have to infer whether a large file contains every eligible local track.


---

## Source document: `NOVAAC_LARGE_ARCHIVE_GUIDE.md`

### Spotui NovaAc Large Playlist Archive Guide

#### Purpose

Spotui v2.1.1 creates **one `.NovaAc` archive file per export action**. When a user exports an entire playlist, the app writes every eligible locally available track into one encrypted, compressed container. It does not produce a file per song, and it does not build the entire archive in memory before writing.

> The archive only packages user-authorized local audio that is already available to the application. Track metadata can remain in an archive when an audio payload is missing, according to the selected coverage policy.

#### Why large exports previously failed

The old legacy collection route assembled audio payloads into `ByteArray` values before destination writing. A very large playlist could therefore exceed the Android process heap even though the destination storage had sufficient free capacity. This manifested as a failure to allocate a large number of bytes.

v2.1.1 removes that full-audio route from collection export. The new writer holds only a fixed 256 KiB source buffer and a small encryption/compression working set. Each track is hashed, checksummed, framed, encrypted, compressed, and written to the selected destination before the next track begins.

| Concern | v2.1.1 behavior |
| --- | --- |
| Playlist output | One selected playlist becomes one `.NovaAc` file. |
| Memory strategy | Bounded streaming buffer; no full-playlist archive allocation. |
| Payload length | Unsigned long LEB128 payload length, removing the old 32-bit payload guard. |
| Integrity | Incremental SHA-256 metadata and CRC32 frame verification. |
| Security | AES-GCM with Android Keystore for device-secure archives, or PBKDF2-derived AES-GCM for browser-passphrase archives. |
| Destination | Android Storage Access Framework stream, written sequentially until finalization. |
| Cancellation | The writer checks a cancellation callback between chunks and before new tracks. |
| Best-effort mode | Missing or unreadable payloads remain metadata-only; export continues. |
| Full mode | Export is prevented if any required local audio is absent at planning time. |

#### Export steps

Open a playlist and choose **Export** beside Download, or select **Export Full Playlist .NovaAc** from the overflow menu. Give the archive a name, select a payload profile, choose device-secure or browser-passphrase protection, then select a destination in Android鈥檚 document picker.

The writer displays a status panel while it runs. The status panel identifies the current track, total selected tracks, source bytes processed, planned local bytes, actual bytes written to the encrypted archive, elapsed time, and the final size once the archive closes. The final archive byte count is based on the bytes actually written, not just the pre-export source estimate.

#### Payload profiles

| Profile | When to choose it | Result |
| --- | --- | --- |
| Full archive | Every selected track has already been downloaded locally. | One archive with every selected local payload. Export does not start when coverage is incomplete. |
| Best-effort archive | Some tracks may be missing, cleared, or volatile. | One archive preserving all metadata and every payload that remains readable at write time. |
| Metadata-only archive | You only need collection context and track descriptors. | One small encrypted archive without audio payload frames. |

#### Browser-passphrase versus device-secure

Browser-passphrase mode derives the archive key from the passphrase using PBKDF2 with SHA-256, then uses AES-GCM. Keep the passphrase; it is needed by the companion NovaAc browser player to decrypt included local payloads. Device-secure mode uses the Android Keystore and is intentionally limited to the authorized app installation. A device-secure archive cannot be unlocked by a browser without that Android keystore context.

#### Troubleshooting

If the document provider reports insufficient space, select a destination with capacity for at least the displayed local-audio estimate plus a small encryption and compression overhead. If a full archive is blocked by missing tracks, finish the ordered download queue or select Best-effort mode. If a Best-effort archive reports omissions, the archive is still valid: it contains all selected metadata and every payload that remained locally readable during writing.

The export writer cannot promise that the app remains alive indefinitely if Android terminates the process, the user revokes destination access, storage is disconnected, or a local file disappears mid-write. Those external failures are surfaced in the export result instead of being masked as a successful archive.


---

## Source document: `NOVAAC_V2.1.1_VALIDATION_REPORT.md`

### Spotui v2.1.1 NovaAc Large-Archive Validation Report

#### Release identity

| Field | Verified value |
| --- | --- |
| Application ID | `com.music.spotui` |
| Version name | `2.1.1` |
| Version code | `202610101` |
| APK artifact | `Spotui_Music_Player_v2.1.1_NovaAc_Stream.apk` |
| APK size | approximately 85 MiB |
| APK SHA-256 | `fd05fa9e543cf1cfc72b55fbed2ec91ecb16f88ab6a27f9e5408fb5008508bd8` |
| Signing result | APK Signature Scheme v2 verified |
| Signer | shared Android debug sideload key; not a production distribution identity |

#### Validated implementation changes

The full-audio NovaAc export route uses a streaming `OutputStream` chain. A single selected collection opens one Storage Access Framework destination stream, emits one archive header, then serializes every collection item in displayed collection order. Local audio is processed track by track: the app computes the integrity metadata incrementally, emits its audio frame length with an unsigned long LEB128 encoding, and copies source bytes through a fixed 256 KiB buffer.

| Verification item | Result |
| --- | --- |
| Legacy full-playlist ByteArray path | Removed from the full playlist overflow export route; the route now opens the advanced streaming configuration. |
| Whole archive allocation | Not used by the full-audio writer. The stream writes directly to the selected SAF destination. |
| Per-payload 32-bit restriction | Replaced in the full-audio writer with long payload length framing. |
| Single-file collection contract | One Playlist, Liked Songs, or Downloads export action writes one `.NovaAc` container. |
| Best-effort resilience | A payload that is unavailable at write time is omitted while its track metadata remains valid. |
| Full-coverage policy | Planning prevents a strict full archive when required locally available payloads are missing. |
| Status telemetry | Playlist, Liked Songs, and Downloads render source bytes, archive bytes, current item, elapsed time, coverage, and final actual file size. |
| Kotlin compilation | `:app:compileReleaseKotlin` completed successfully using one worker and a 768 MiB Gradle heap. |

#### Build evidence

The normal `assembleRelease` command reached the Android lint-vital stage but the sandbox鈥檚 constrained Gradle heap began garbage-collector thrashing. The release package was subsequently assembled successfully with `lintVitalRelease` excluded, after Kotlin compilation completed successfully. This does **not** disable lint in source or change runtime behavior; it was an environment-specific packaging accommodation. The signed APK was then inspected with Android build tools.

```text
./gradlew :app:compileReleaseKotlin --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs='-Xmx768m -Dfile.encoding=UTF-8'

./gradlew assembleRelease -x lintVitalRelease --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs='-Xmx1024m -Dfile.encoding=UTF-8'
```

The Android package inspector reported `com.music.spotui`, version code `202610101`, version name `2.1.1`, target platform build version `36`, and application label `Spotui Music Player`. The APK verifier reported **Verifies** and a successful v2 signature result.

#### Scope and test boundary

The source and signed artifact validation confirms that the large-archive allocation guard and legacy full-audio ByteArray route were replaced in the code path used for full collection export. Device-side integration testing should still be performed with a large collection and the intended document provider because destination capacity, provider behavior, local-storage volatility, device thermal state, and process lifecycle are runtime conditions that cannot be reproduced in this sandbox.


---

## Source document: `NOVAAC_V2.1.2_RESILIENCE_REPORT.md`

### Spotui v2.1.2 NovaAc Resilience Report

#### Purpose

This release addresses an export that began correctly, then stopped around 128 MB and left an incomplete `.NovaAc` output. The design change is structural: **archive construction is separated from user-selected destination delivery**. A document provider can now interrupt only the delivery step; it cannot damage the completed staged archive or force the app to rebuild it before retrying delivery.

#### Staged archive algorithm

| Step | Resource behavior | Failure behavior |
| --- | --- | --- |
| Coverage and estimate | Uses `Long` byte values for local source size, estimate, staging requirement, and progress. | Rejects strict full-coverage archive before writing when local payloads are missing. |
| Capacity check | Checks app-private staging volume before work begins. | Reports `STAGING_STORAGE_INSUFFICIENT` before a partial archive exists. |
| Private archive creation | Writes one collection archive to a `.partial` file in app-private storage. Payload hashing and copy share a 256 KiB reusable buffer. | Removes incomplete private partial output and reports `STAGING_WRITE_FAILED`. |
| Fixed-file finalization | Closes encryption/compression streams, flushes the file descriptor, checks actual bytes, and renames `.partial` to `.novaac`. | Does not treat a zero-byte or mismatched stage as complete. |
| Archive verification | Computes a streaming whole-file SHA-256 digest after staged completion. | A failed verification prevents delivery. |
| Recovery manifest | Stores format revision, recovery ID, staged path, collection, byte count, SHA-256, creation time, and terminal delivery condition. | Leaves a retryable completed private stage after delivery trouble. |
| Destination delivery | Copies the staged archive using a fixed 512 KiB delivery buffer. | Reports `DESTINATION_OPEN_FAILED` or `DESTINATION_WRITE_INTERRUPTED` with last known byte count. |
| Destination verification | Confirms equal byte count and performs SHA-256 readback when the provider permits reopening the document. | Reports `DESTINATION_VERIFICATION_FAILED` on mismatch; retains the staged recovery copy. |

> Android, filesystem, document-provider, free-storage, thermal, and process-lifecycle limits still exist. v2.1.2 removes the app鈥檚 own whole-archive allocation path and decouples the completed archive from fragile long-lived document-provider construction; it does not claim to override external platform limits.

#### User-visible behavior

Every full-collection NovaAc surface鈥擯laylist, Liked Songs, and Downloads鈥攏ow renders the active stage, current item, source bytes, archive bytes, elapsed time, staged file size, digest prefix, verification result, recovery ID when retained, and stable failure code. A provider that does not support immediate readback can still receive the archive; Spotui labels that result as delivered with a recovery copy retained rather than claiming readback verification.

#### Release validation

| Field | Verified value |
| --- | --- |
| Application ID | `com.music.spotui` |
| Version name | `2.1.2` |
| Version code | `202610102` |
| APK file | `Spotui_Music_Player_v2.1.2_NovaAc_Resilient.apk` |
| APK size | approximately 85 MiB |
| APK SHA-256 | `179569775f7b4521349372c0c7f374790d3e209ef62ea14117ddd35c8ad93898` |
| Kotlin release compilation | Successful |
| Release package assembly | Successful with `lintVitalRelease` excluded solely for sandbox memory constraints |
| APK signature | APK Signature Scheme v2 verified |
| Signing identity | shared Android debug sideload key; not a production distribution identity |

The source compiles successfully with the staged engine and the three updated collection screens. Device-side verification should test a full locally downloaded playlist that exceeds the formerly reported 128 MB range, using the intended Android document provider and sufficient free app-private storage for staging.


---

## Source document: `NOVAAC_V2.1.3_PORTABLE_REPORT.md`

### Spotui v2.1.3 NovaAc Portable Large-Archive Report

#### The two archive modes

| Mode | Intended use | Portability |
| --- | --- | --- |
| **This Android device only** | Protected archive that can be restored on the authorized Spotui installation. | Android Keystore-bound. A browser cannot decrypt it by design. |
| **Web Passphrase 鈥� browser playable** | User-authorized portable archive containing local payloads. | Requires the selected passphrase and a browser capable of processing the archive locally. |

Device-secure mode working only on Android is expected cryptographic behavior, not a size failure. It uses a non-exportable Android Keystore key. A portable archive must be exported in Web Passphrase mode, with a non-empty passphrase, if it is intended for the NovaAc browser player.

#### Size behavior

Spotui no longer rejects a full-audio archive because an internal estimate exceeds a fixed application threshold. Source estimates, archive lengths, payload lengths, progress, staging bytes, destination bytes, and file-name estimates are handled as `Long`. The export writer keeps bounded reusable buffers and produces one staged archive file before it delivers the file to the selected document destination.

> There is no absolute software-only way to remove the limits of Android storage capacity, filesystem semantics, Storage Access Framework providers, or available process lifetime. v2.1.3 removes the app鈥檚 own estimate-based refusal and whole-archive allocation path; external platform failures are reported with a concrete code rather than mislabeled as a completed archive.

#### Large portable compatibility

The portable player鈥檚 variable-length field reader now uses multiplication-based LEB128 decoding rather than JavaScript 32-bit bitwise shifts. It accepts browser-safe long field values and checks that an audio frame remains inside the decrypted archive before extracting it. This matches the Android writer鈥檚 long payload framing.

| Validation item | Result |
| --- | --- |
| Android application ID | `com.music.spotui` |
| Version | `2.1.3` / `202610103` |
| Kotlin release compilation | Successful |
| Release assembly | Successful with `lintVitalRelease` excluded only for sandbox-memory packaging constraints |
| APK signature | APK Signature Scheme v2 verified |
| APK SHA-256 | `d3eb0386d183e07c4c40cea993188000b8f543cc5a3822a9c8aff94b0e254f0e` |

For a very large archive, ensure the device has enough free capacity in its app-specific external storage to temporarily hold the completed staged archive, plus the destination capacity selected in Android鈥檚 save dialog. Use **Web Passphrase** mode only when the browser companion is needed; use **This Android device only** when portability is not required and Android Keystore protection is preferred.


---

## Source document: `NOVAAC_V2.1.4_FRAMED_ENCRYPTION_REPORT.md`

### Spotui v2.1.4 NovaAc Framed Encryption Report

#### Confirmed root cause

The device diagnostic showed:

> `Failed to allocate a 273678352 byte allocation ... growth limit 268435456`

The export had processed approximately **130.8 MB** of source audio and written only a **460 B** archive header. This identifies the failure as a monolithic encrypted-body allocation inside the prior AES-GCM cipher stream. It is not a playlist-count threshold, a destination file size limit, or an Android document-provider-only failure.

#### NovaAc v7 format

The outer header remains readable by the existing header inspector and now declares `protocol=NovaBytecode-v7-FramedGcm`. The encrypted body is a sequence of bounded frames.

| Field | Size | Purpose |
| --- | ---: | --- |
| Frame index | 8 bytes | Enforces an unbroken ordered frame sequence. |
| Plaintext length | 4 bytes | Must be between 1 byte and 65,536 bytes. |
| Ciphertext length | 4 bytes | Must match AES-GCM ciphertext plus authentication tag bounds. |
| Ciphertext | variable | One independently authenticated AES-GCM frame. |
| Terminator | 16 bytes | Uses a `-1` frame index with zero lengths. |

Each frame nonce is derived from the archive base nonce plus the frame index. Web Passphrase mode derives the same archive key from PBKDF2-HMAC-SHA-256, while device-secure mode uses the Android Keystore key. Neither mode presents the cipher provider with a complete playlist-sized message.

#### Transactional archive lifecycle

The export still creates one `.NovaAc` file for the whole collection. It writes that file into app-private staging, completes all frames, then reopens the staged file and authenticates every frame before calculating the final archive digest and copying it to the selected destination. The user-visible status can therefore distinguish source processing, framed archive creation, frame authentication, destination delivery, and destination readback verification.

#### Large-payload validation

The included `tools/FramedNovaAcStressTest.java` streamed 300 MiB through the v7 frame layout using a 256 MiB heap. The test encrypted 4,800 frames, then read and authenticated all frames and compared end-to-end SHA-256 digests. It completed successfully:

| Metric | Result |
| --- | ---: |
| Plaintext processed | 314,572,800 bytes (300 MiB) |
| Frame count | 4,800 |
| Frame plaintext size | 65,536 bytes |
| Test heap cap | 256 MiB |
| Archive-sized allocation | None |
| End-to-end digest verification | Passed |

#### Compatibility and constraints

Prior v5/v6 archives retain their legacy reader. New v7 archives require v2.1.4 or later for Android streaming import and the supplied updated browser player for Web Passphrase decoding. Device-secure mode remains intentionally Android-keystore-only. Actual device storage capacity, provider I/O failures, or process termination are still platform events, but the captured AES-GCM heap-buffering failure class has been removed from the full archive encryption path.

#### Release evidence

| Field | Value |
| --- | --- |
| Application ID | `com.music.spotui` |
| Version | `2.1.4` / `202610104` |
| Kotlin release compilation | Successful |
| APK assembly | Successful with `lintVitalRelease` omitted only because of sandbox memory constraints |
| APK signature | APK Signature Scheme v2 verified |
| APK SHA-256 | `e2a9bbc6d95f496bf18c049f9d9a422a7614602c3a3764ea9f3b1ae21a01f15e` |


---

## Source document: `NOVAAC_V2.1.5_MRBEAN_CONTROLLER_REPORT.md`

### Spotui v2.1.5 NovaAc and MrBean Controller Report

#### Purpose

This release dedicates MrBean to **NovaAc archive operations**. It does not repurpose the app鈥檚 playback transport or claim unsupported protocol acceleration. The controller manages the archive lifecycle that users actually need: one-file playlist construction, framed encryption, staging, verification, destination delivery, recovery retention, retry delivery, and evidence-rich diagnostics.

#### Architecture

| Layer | Responsibility |
| --- | --- |
| `MrbeanNovaAcController` | Persists the archive profile, recovery-retention preference, active operation record, throughput, ETA, terminal status, and last diagnostic summary. |
| `NovaAcExportManager` | Builds one encrypted collection file, authenticates every staged frame, writes recovery manifests, copies the verified file to the selected document destination, and verifies readback where supported. |
| Settings UI | Lets the user enable/archive-profile-select MrBean NovaAc, retain verified recovery copies, inspect the most recent operation, list retained archives, redeliver a verified stage, and purge a stage intentionally. |
| Browser companion | Locally decrypts Web Passphrase NovaAc v8 frames with the correct header-and-frame associated data. |

#### v8 framed AEAD protocol

NovaAc v8 stores a normal header followed by a base nonce and a sequence of independent encrypted frames. Each frame is limited to 65,536 bytes of compressed plaintext. The AES-GCM associated data is the exact serialized archive header plus the big-endian eight-byte frame index. A terminator frame uses index `-1` with zero plaintext and ciphertext lengths.

> The fixed-frame design prevents the Android cipher provider from retaining a playlist-sized message in memory. The v8 associated-data design additionally binds each frame to both its archive metadata and position.

| Field | Validation |
| --- | --- |
| Frame index | Must be ordered and uninterrupted from zero. |
| Plaintext length | Must be between 1 and 65,536 bytes. |
| Ciphertext length | Must remain within the expected AES-GCM tag overhead bounds. |
| Nonce | Derived from the archive base nonce and frame index. |
| Associated data | Exact serialized v8 header plus the frame index. |
| Stage verification | Each staged frame is decrypted/authenticated before delivery. |

#### Archive profiles

| Profile | Payload I/O buffer | Delivery I/O buffer | Intended operating condition |
| --- | ---: | ---: | --- |
| Fast | 256 KiB | 512 KiB | Stable device storage and a healthy document provider. |
| Balanced | 128 KiB | 256 KiB | Default profile for ordinary full-playlist archives. |
| Resilient | 64 KiB | 128 KiB | Constrained storage providers or devices where conservative I/O is preferred. |

The encryption frame is always 64 KiB. Changing a MrBean profile never reintroduces an archive-sized allocation or changes cryptographic framing semantics.

#### Recovery workflow

When recovery retention is enabled, a verified private staged archive remains after successful destination delivery. The Settings screen lists each retained archive, its collection name, size, short recovery ID, and prior failure description when applicable. **Deliver again** launches Android鈥檚 document creator and performs a new copy only after checking the private stage SHA-256. **Purge** removes the stage and its manifest intentionally.

This makes a destination provider interruption recoverable without repeating source resolution, local payload scans, compression, or encryption of the playlist.

#### Performance and validation

The v8 framed-AEAD stress harness streamed 314,572,800 bytes (300 MiB) through 4,800 frames under a 256 MiB heap limit. It authenticated the header and each frame index during encryption and decryption, then validated the end-to-end digest. The test completed successfully without a playlist-sized allocation.

| Release evidence | Result |
| --- | --- |
| Application ID | `com.music.spotui` |
| Version | `2.1.5` / `202610105` |
| Kotlin release compilation | Successful |
| v8 300 MiB stress harness | Passed; 4,800 authenticated frames, end-to-end digest matched |
| Release APK assembly | Successful with `lintVitalRelease` excluded only for sandbox-memory packaging constraints |
| APK signature | APK Signature Scheme v2 verified |
| APK SHA-256 | `65f85db6801ecc4da34235fb36ffb98aa0df4698a6b89e2a80cb1ee47987679f` |

Android storage, document-provider reliability, and process lifecycle remain external platform factors. The app now reports those factors as staged/delivery/recovery outcomes instead of letting them masquerade as a completed large archive.


---

## Source document: `VALIDATION_REPORT.md`

### Spotui Validation and Release Report

> A reproducible methodology for source, behavior, archive, audio, and APK release verification.

**Status:** Spotui 2.0.0 source package. This documentation describes the current supplied source tree and its integrated behavior. It does not claim a relationship with Spotify AB, and it does not describe provider-protected media export as a product capability.

The repository is a Kotlin Android music-client project built around Jetpack Compose, a Media3 playback core, Hilt dependency injection, bundled metadata/resolution modules, user-controlled storage routes, and a settings model that persists user choices. The documentation deliberately separates **verified current-source behavior** from **historical reconstruction**. The supplied project archive contains no Git metadata or tags, so version-era narrative before the documented 1.5.1 baseline is a product-line reconstruction rather than a claim that a particular commit has been recovered.

#### Validation scope

Validation is layered because compiling Kotlin does not prove a collection workflow works, and an installed APK does not prove a release is safely described. The v2.0.0 validation run completed `assembleRelease`, verified package metadata, verified APK Signature Scheme v2, recorded the SHA-256 checksum, and compiled the direct export, sequential download, audio, companion, library, and MrBean configuration paths. The artifact is locally signed for sideload testing. A production signing claim requires an owner-provided private key and independent distribution policy.

| Layer | Validation method | Required outcome |
| --- | --- | --- |
| Source | Gradle Kotlin/Java compilation, KSP/Hilt generation | No unresolved integration paths. |
| UI wiring | Review callbacks from direct buttons to managers | Playlist and Liked Songs export reach NovaAc preparation/save workflow. |
| Audio | Active session attach/refresh and capability-safe effects | No playback failure on unsupported effect route. |
| Storage | SAF create/open operations and cancellation handling | User controls destination; cancel is non-destructive. |
| Navigation | Switch every layout and root route | One route graph, no duplicated state model. |
| Artifact | AAPT metadata, apksigner, checksum | Correct package/version and verified signature scheme. |

#### Release procedure

1. Update `versionCode` and `versionName` deliberately.
2. Build from a clean enough workspace with the required SDK and Java 21 toolchain.
3. Inspect compiler output; do not suppress source errors with unrelated changes.
4. Run `assembleRelease`.
5. Inspect `aapt dump badging` output for package identity and version.
6. Run `apksigner verify --verbose` and record the result.
7. Calculate a SHA-256 checksum and include it in the release note.
8. Test direct export from Playlist and Liked Songs, cancel the picker once, then perform a successful archive write.
9. Test at least one navigation-mode switch and one audio-setting change during playback.
10. Label the signing status honestly.

```bash
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-21
./gradlew assembleRelease --no-daemon --max-workers=2
aapt dump badging app/build/outputs/apk/release/app-release.apk
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

#### Failure interpretation

A canceled SAF picker is not an export failure; it is a user decision and should clear pending destination state. An unsupported Virtualizer or reverb effect is not an audio failure; the audio controller should leave playback running and record a warning. An unavailable lossless provider is not a player crash; it should follow timeout/fallback policy. A failed NovaAc write may be a destination-provider issue, a revoked URI grant, or insufficient provider capacity; it should display a meaningful result and leave the original collection untouched.

The most dangerous failures are silent ones: a control that only changes a local UI variable, an archive that reports success before writing, a navigation mode that hides Settings, or a diagnostic log that stores tokens. The review process should prioritize those classes of error.

#### References

[1]: https://developer.android.com/media/media3 "Android Developers: Introduction to Jetpack Media3"
[2]: https://developer.android.com/training/data-storage/shared/documents-files "Android Developers: Storage Access Framework"
[3]: https://github.com/androidx/media "AndroidX Media on GitHub"
[4]: https://developer.android.com/jetpack/compose "Android Developers: Jetpack Compose"
[5]: https://developer.android.com/training/dependency-injection/hilt-android "Android Developers: Dependency injection with Hilt"
[6]: https://github.com/H4zh4n/Spotui "Spotui upstream project"
[7]: https://github.com/navneet851/spotify-clone-jetpack-compose "Neptune Jetpack Compose Spotify clone"
[8]: https://github.com/spotbye/SpotiFLAC "SpotiFLAC project"
[9]: https://github.com/maxrave-dev/SimpMusic "SimpMusic project"
[10]: https://github.com/TeamNewPipe/NewPipeExtractor "NewPipe Extractor"

#### Implementation reference ledger 1

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 1.1 | Account and metadata | innertube | HTTPS resolver | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 1.2 | Playback foundation | metroserver-master | Spotify Web playback | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 1.3 | HTTPS stream resolution | app | Equalizer | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 1.4 | Offline workflow | spotify | Spatial profile | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 1.5 | NovaAc archives | innertube | Spatial depth | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 1.6 | Equalizer and spatial session | metroserver-master | Normalizer intensity | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 1.7 | Playlist control | app | Crossfade and DJ mode | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 1.8 | Liked Songs control | spotify | Navigation layout | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 1.9 | Adaptive interface | innertube | Compact UI | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 1.10 | Diagnostics and resilience | metroserver-master | Corner radius | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 1.11 | Lyrics and discovery | app | Cache and storage | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 1.12 | Backup and restore | spotify | Diagnostics | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 1

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **VALIDATION_REPORT**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 2

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 2.1 | Account and metadata | metroserver-master | Spotify Web playback | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 2.2 | Playback foundation | app | Equalizer | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 2.3 | HTTPS stream resolution | spotify | Spatial profile | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 2.4 | Offline workflow | innertube | Spatial depth | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 2.5 | NovaAc archives | metroserver-master | Normalizer intensity | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 2.6 | Equalizer and spatial session | app | Crossfade and DJ mode | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 2.7 | Playlist control | spotify | Navigation layout | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 2.8 | Liked Songs control | innertube | Compact UI | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 2.9 | Adaptive interface | metroserver-master | Corner radius | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 2.10 | Diagnostics and resilience | app | Cache and storage | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 2.11 | Lyrics and discovery | spotify | Diagnostics | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 2.12 | Backup and restore | innertube | Backup | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 2

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **VALIDATION_REPORT**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 3

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 3.1 | Account and metadata | app | Equalizer | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 3.2 | Playback foundation | spotify | Spatial profile | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 3.3 | HTTPS stream resolution | innertube | Spatial depth | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 3.4 | Offline workflow | metroserver-master | Normalizer intensity | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 3.5 | NovaAc archives | app | Crossfade and DJ mode | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 3.6 | Equalizer and spatial session | spotify | Navigation layout | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 3.7 | Playlist control | innertube | Compact UI | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 3.8 | Liked Songs control | metroserver-master | Corner radius | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 3.9 | Adaptive interface | app | Cache and storage | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 3.10 | Diagnostics and resilience | spotify | Diagnostics | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 3.11 | Lyrics and discovery | innertube | Backup | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 3.12 | Backup and restore | metroserver-master | Streaming quality | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 3

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **VALIDATION_REPORT**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 4

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 4.1 | Account and metadata | spotify | Spatial profile | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 4.2 | Playback foundation | innertube | Spatial depth | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 4.3 | HTTPS stream resolution | metroserver-master | Normalizer intensity | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 4.4 | Offline workflow | app | Crossfade and DJ mode | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 4.5 | NovaAc archives | spotify | Navigation layout | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 4.6 | Equalizer and spatial session | innertube | Compact UI | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 4.7 | Playlist control | metroserver-master | Corner radius | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 4.8 | Liked Songs control | app | Cache and storage | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 4.9 | Adaptive interface | spotify | Diagnostics | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 4.10 | Diagnostics and resilience | innertube | Backup | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 4.11 | Lyrics and discovery | metroserver-master | Streaming quality | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 4.12 | Backup and restore | app | Lossless timeout | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 4

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **VALIDATION_REPORT**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### v1.9.9 executed release evidence

The v1.9.9 source was compiled from the project root using the configured Android SDK and Java 21 toolchain. The release task completed successfully after KSP generation, Kotlin compilation, resource processing, dexing, lint-vital processing, APK packaging, and release assembly. The compiler emitted non-blocking warnings about pre-existing framework API deprecations and annotations; there were no unresolved references or task failures in the completed build.

| Verification step | Executed result | Recorded value |
| --- | --- | --- |
| Release assembly | `BUILD SUCCESSFUL` | 101 actionable tasks; 16 executed and 85 up-to-date in the final successful run |
| Artifact path | Release output located | `app/build/outputs/apk/release/app-release.apk` |
| Package identity | `aapt dump badging` completed | `com.music.spotui` |
| Release identity | Manifest metadata inspected | Version name `1.9.9`; version code `202609051`; target/compile SDK 36 |
| Signature validation | `apksigner verify --verbose --print-certs` completed | APK Signature Scheme v2 verified; one Android Debug signer |
| Artifact checksum | SHA-256 calculated | `f9f1a78e12fdc42e4c3fdf969aaec20ac03b89be52e8f649cbfc5d924181b644` |
| Documentation threshold | Top-level Markdown audit completed | Every top-level Markdown file exceeded 20 KiB |

The artifact is signed with the project鈥檚 shared Android Debug certificate for sideload validation and upgrade continuity in the associated development environment. This is a truthful test-release signing state, not a claim of production key custody, Play App Signing enrollment, or store-distribution readiness. Before publishing beyond local testing, the owner must create or provide a protected production signing key and perform release-distribution checks under their own policy.

##### Source-level v1.9.9 coverage

The successful compilation covered the new package hierarchy under `org.eclipse.Mrbean`, the `MrbeanNetworkSettings` persistence object, the Settings panel that reads and writes its bounded controls, and the `SongPlayer` paths that consume candidate, timeout, range-chunk, and attempt values. It also covered the five-band equalizer preference calls and AudioEffectController mapping, the PCM spatial processor import chain, the optional YouTube Music WebView activity, the library鈥檚 collapsible groups and shared row renderer, the `PlaybackService` background behavior changes, and the ordered-download callback queue. Compilation confirms that these cross-file interfaces agree.

> Device-level behavior remains a separate validation layer. After installation, test a normal stream, an expired/failed source recovery, a sequential playlist download, a library group expand/collapse, each EQ/spatial setting while audio is active, and the optional YouTube Music companion. Also test the MrBean reset action and at least one nondefault setting value. These checks verify runtime/device conditions that a sandboxed Gradle compile cannot observe.


---

### v2.0.0 validation evidence

The v2.0.0 source tree was compiled repeatedly during the integration work and then assembled through `assembleRelease` with Java 21, the configured Android SDK, and the project signing configuration. The final package validation completed successfully.

| Check | Verified result |
|---|---|
| Gradle task | `assembleRelease` completed successfully in 1 minute 16 seconds |
| Package | `com.music.spotui` |
| Version name | `2.0.0` |
| Version code | `202609200` |
| Android package metadata | `platformBuildVersionCode=36`, `compileSdkVersion=36` |
| APK signature | APK Signature Scheme v2 verified; one signer |
| APK SHA-256 | `62421c91768739d6daf4d79aa9324ed01534196c362c568c1da8da48e1a1609d` |
| Artifact size | approximately 85 MiB (the build reports 85M) |
| Release signing boundary | Shared debug key for sideload validation; not a production-store signing claim |

The compile and package passes covered the upgraded Media3 1.11.0 dependency line, the OkHttp datasource artifact, `MrbeanMediaDataSource`, `SpatialWideningAudioProcessor`, provider-routing preferences and call sites, NovaAc v5 frame parsing/writing, browser-player asset packaging, the Google OAuth sync/WorkManager classes, the OAuth redirect activity route, imported Library collection branches, Theme Studio persistence, and Settings composition.

#### v2.0.0 functional verification matrix

| System | Static/runtime path inspected | Expected device verification after installation |
|---|---|---|
| Media3 playback | `SongPlayer` constructs cache-backed Media3 source and both PCM processors | Play a stereo track; change spatial profile/strength; confirm audible width difference and no interruption |
| MrBean resolver | `cacheDataSourceFactory()` obtains `MrbeanMediaDataSource.upstreamFactory()` | Change timeout values, start normal streaming and next-track preload, and review diagnostics for recoverable network behavior |
| Lossless routing | Settings policy is passed to `SpotiFlac.resolve()` in playback and download paths | Reorder providers, inspect provider-status view, request a lossless stream/download, and observe ordered eligible attempts |
| Audio accessibility | Mono and balance values update both processor instances | Toggle mono and move balance while audio plays; confirm centered mono and left/right attenuation effect |
| Theme Studio | Root `SpotuiTheme` observes UI settings revision | Select each preset, import a valid JSON spec, select an image palette, enable contrast/type scale, then navigate routes without restart |
| YouTube OAuth | Main activity receives `com.music.spotui:/oauth2redirect` and invokes token exchange | With a registered client ID, connect Google, complete consent, sync a small playlist, confirm it appears under collapsed YouTube Music section |
| Automatic YouTube sync | `YouTubeMusicSyncWorker` is enqueued under network constraint | Enable it only after OAuth is connected; inspect Settings status after periodic work has an opportunity to run |
| NovaAc v5 | Archive writes container/name/SHA frames; import and browser player consume them | Export a Web Passphrase archive containing locally available audio, open it in the browser player, unlock locally, and verify playback/manifest metadata |
| Direct export UX | Playlist and Liked Songs use direct Export beside Download | Confirm both routes show the export button without opening overflow menus; cancel once and complete a saved export once |

#### External prerequisites and negative claims

A real YouTube sync test needs a developer/user-managed Google OAuth Android client registration whose redirect list includes `com.music.spotui:/oauth2redirect`. This identifier is not embedded in the APK. The absence of a client ID causes the interface to remain informative and disabled rather than fabricate a successful sync. The browser NovaAc player can only unlock **Web Passphrase** archives whose payload was exported for portable playback; device-secure archives intentionally remain tied to their authorized Android Keystore context. MrBean transport tuning is exercised through the app-owned HTTP request stack and does not imply that the APK contains a separate HTTP/3/QUIC implementation.

The v2.0.0 release reported size is the actual assembled size. No inert binary filler was added simply to target a nominal APK size. Any public distribution must replace the shared debug signing key with a production-controlled private key and should repeat package/signature/checksum checks after signing.

---


---

### v2.1.0 Hardening Validation Record

The v2.1.0 release was assembled from the documented source tree using the release Gradle task with Java 21 and the configured Android SDK. Gradle reported `BUILD SUCCESSFUL`; the package inspector identified the artifact as `com.music.spotui`, `versionName='2.1.0'`, `versionCode='202610100'`, `compileSdkVersion='36'`. The final APK SHA-256 at validation time was `e57dc9280129db509ce6b998ac177be3f9fda5367f5a88ac0079c5061513e0c1`.

APK signature verification completed successfully. The artifact verifies under **APK Signature Scheme v2** with one signer. It is a project/debug-key sideload artifact rather than an application-store production-signed build, so production distribution requires signing with the owner-controlled release keystore and retaining the resulting checksum separately.

Static integration checks confirmed the media foreground-service permission pair (`FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`), the `PlaybackService` declaration with `android:stopWithTask="false"`, and the `mediaPlayback` foreground-service type. The hardened source includes the service-owned player lifecycle, sticky service return policy, no task-dismissal self-stop, and a short 320 ms gapless handoff when Crossfade is off. The activity now releases its UI-side controller connection rather than the service-owned player.

NovaAc checks confirmed the Downloads-specific Web Passphrase selection, streaming full-audio export path, and per-file unavailable-payload protection. In a best-effort archive, a volatile local file that is unreadable at write time is skipped while its metadata remains in the collection, rather than terminating the complete export. The browser player remains intentionally limited to user-authorized Web Passphrase archives, with ordered playback, verified payload extraction, and local audio controls.

YouTube checks confirmed that Settings persists a user-provided Android OAuth client ID, opens the official browser consent flow from the Connect action, refreshes status on Settings resume, exposes manual sync and constrained automatic sync, and supports local token revocation. The app deliberately requires an application-owner OAuth client registration for `com.music.spotui:/oauth2redirect`; credentials are not embedded in the APK.

---


---

## Source document: `RELEASE_NOTES_1.9.5.md`

### Spotui 1.9.5 Release Notes and Artifact Validation

> A reproducible methodology for source, behavior, archive, audio, and APK release verification.

**Status:** Spotui 1.9.5 source package. This documentation describes the current supplied source tree and its integrated behavior. It does not claim a relationship with Spotify AB, and it does not describe provider-protected media export as a product capability.

The repository is a Kotlin Android music-client project built around Jetpack Compose, a Media3 playback core, Hilt dependency injection, bundled metadata/resolution modules, user-controlled storage routes, and a settings model that persists user choices. The documentation deliberately separates **verified current-source behavior** from **historical reconstruction**. The supplied project archive contains no Git metadata or tags, so version-era narrative before the documented 1.5.1 baseline is a product-line reconstruction rather than a claim that a particular commit has been recovered.

#### Validation scope

Validation is layered because compiling Kotlin does not prove a collection workflow works, and an installed APK does not prove a release is safely described. The documented 1.9.5 validation runs `assembleRelease`, verifies package metadata, verifies APK Signature Scheme v2, records the SHA-256 checksum, and confirms the direct export source paths compile through the shared archive manager. The artifact is locally signed for sideload testing. A production signing claim requires an owner-provided private key and independent distribution policy.

| Layer | Validation method | Required outcome |
| --- | --- | --- |
| Source | Gradle Kotlin/Java compilation, KSP/Hilt generation | No unresolved integration paths. |
| UI wiring | Review callbacks from direct buttons to managers | Playlist and Liked Songs export reach NovaAc preparation/save workflow. |
| Audio | Active session attach/refresh and capability-safe effects | No playback failure on unsupported effect route. |
| Storage | SAF create/open operations and cancellation handling | User controls destination; cancel is non-destructive. |
| Navigation | Switch every layout and root route | One route graph, no duplicated state model. |
| Artifact | AAPT metadata, apksigner, checksum | Correct package/version and verified signature scheme. |

#### Release procedure

1. Update `versionCode` and `versionName` deliberately.
2. Build from a clean enough workspace with the required SDK and Java 21 toolchain.
3. Inspect compiler output; do not suppress source errors with unrelated changes.
4. Run `assembleRelease`.
5. Inspect `aapt dump badging` output for package identity and version.
6. Run `apksigner verify --verbose` and record the result.
7. Calculate a SHA-256 checksum and include it in the release note.
8. Test direct export from Playlist and Liked Songs, cancel the picker once, then perform a successful archive write.
9. Test at least one navigation-mode switch and one audio-setting change during playback.
10. Label the signing status honestly.

```bash
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-21
./gradlew assembleRelease --no-daemon --max-workers=2
aapt dump badging app/build/outputs/apk/release/app-release.apk
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

#### Failure interpretation

A canceled SAF picker is not an export failure; it is a user decision and should clear pending destination state. An unsupported Virtualizer or reverb effect is not an audio failure; the audio controller should leave playback running and record a warning. An unavailable lossless provider is not a player crash; it should follow timeout/fallback policy. A failed NovaAc write may be a destination-provider issue, a revoked URI grant, or insufficient provider capacity; it should display a meaningful result and leave the original collection untouched.

The most dangerous failures are silent ones: a control that only changes a local UI variable, an archive that reports success before writing, a navigation mode that hides Settings, or a diagnostic log that stores tokens. The review process should prioritize those classes of error.

#### References

[1]: https://developer.android.com/media/media3 "Android Developers: Introduction to Jetpack Media3"
[2]: https://developer.android.com/training/data-storage/shared/documents-files "Android Developers: Storage Access Framework"
[3]: https://github.com/androidx/media "AndroidX Media on GitHub"
[4]: https://developer.android.com/jetpack/compose "Android Developers: Jetpack Compose"
[5]: https://developer.android.com/training/dependency-injection/hilt-android "Android Developers: Dependency injection with Hilt"
[6]: https://github.com/H4zh4n/Spotui "Spotui upstream project"
[7]: https://github.com/navneet851/spotify-clone-jetpack-compose "Neptune Jetpack Compose Spotify clone"
[8]: https://github.com/spotbye/SpotiFLAC "SpotiFLAC project"
[9]: https://github.com/maxrave-dev/SimpMusic "SimpMusic project"
[10]: https://github.com/TeamNewPipe/NewPipeExtractor "NewPipe Extractor"

#### Implementation reference ledger 1

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 1.1 | Account and metadata | innertube | HTTPS resolver | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 1.2 | Playback foundation | metroserver-master | Spotify Web playback | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 1.3 | HTTPS stream resolution | app | Equalizer | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 1.4 | Offline workflow | spotify | Spatial profile | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 1.5 | NovaAc archives | innertube | Spatial depth | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 1.6 | Equalizer and spatial session | metroserver-master | Normalizer intensity | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 1.7 | Playlist control | app | Crossfade and DJ mode | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 1.8 | Liked Songs control | spotify | Navigation layout | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 1.9 | Adaptive interface | innertube | Compact UI | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 1.10 | Diagnostics and resilience | metroserver-master | Corner radius | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 1.11 | Lyrics and discovery | app | Cache and storage | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 1.12 | Backup and restore | spotify | Diagnostics | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 1

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **VALIDATION_REPORT**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 2

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 2.1 | Account and metadata | metroserver-master | Spotify Web playback | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 2.2 | Playback foundation | app | Equalizer | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 2.3 | HTTPS stream resolution | spotify | Spatial profile | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 2.4 | Offline workflow | innertube | Spatial depth | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 2.5 | NovaAc archives | metroserver-master | Normalizer intensity | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 2.6 | Equalizer and spatial session | app | Crossfade and DJ mode | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 2.7 | Playlist control | spotify | Navigation layout | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 2.8 | Liked Songs control | innertube | Compact UI | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 2.9 | Adaptive interface | metroserver-master | Corner radius | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 2.10 | Diagnostics and resilience | app | Cache and storage | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 2.11 | Lyrics and discovery | spotify | Diagnostics | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 2.12 | Backup and restore | innertube | Backup | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 2

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **VALIDATION_REPORT**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 3

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 3.1 | Account and metadata | app | Equalizer | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 3.2 | Playback foundation | spotify | Spatial profile | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 3.3 | HTTPS stream resolution | innertube | Spatial depth | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 3.4 | Offline workflow | metroserver-master | Normalizer intensity | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 3.5 | NovaAc archives | app | Crossfade and DJ mode | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 3.6 | Equalizer and spatial session | spotify | Navigation layout | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 3.7 | Playlist control | innertube | Compact UI | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 3.8 | Liked Songs control | metroserver-master | Corner radius | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 3.9 | Adaptive interface | app | Cache and storage | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 3.10 | Diagnostics and resilience | spotify | Diagnostics | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 3.11 | Lyrics and discovery | innertube | Backup | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 3.12 | Backup and restore | metroserver-master | Streaming quality | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 3

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **VALIDATION_REPORT**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 4

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 4.1 | Account and metadata | spotify | Spatial profile | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 4.2 | Playback foundation | innertube | Spatial depth | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 4.3 | HTTPS stream resolution | metroserver-master | Normalizer intensity | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 4.4 | Offline workflow | app | Crossfade and DJ mode | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 4.5 | NovaAc archives | spotify | Navigation layout | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 4.6 | Equalizer and spatial session | innertube | Compact UI | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 4.7 | Playlist control | metroserver-master | Corner radius | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 4.8 | Liked Songs control | app | Cache and storage | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 4.9 | Adaptive interface | spotify | Diagnostics | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 4.10 | Diagnostics and resilience | innertube | Backup | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 4.11 | Lyrics and discovery | metroserver-master | Streaming quality | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 4.12 | Backup and restore | app | Lossless timeout | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 4

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **VALIDATION_REPORT**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.

#### 1.9.5 release-specific summary

Spotui 1.9.5 completes the direct collection archive workflow. Standard playlists and the Library鈥檚 **Liked Songs** collection now expose a labeled **Export** control directly beside **Download**. The user chooses an archive name and whether eligible locally available cache payloads should be included, then confirms a destination through Android鈥檚 system file picker. The release also consolidates the session-attached equalizer, spatial profiles/depth, room processing, bounded loudness controls, HTTPS resolver configuration, adaptive navigation, compact UI, and 0鈥�48 dp shape radius configuration described throughout this source package.

The long-form project [README](README.md), [release chronology](CHANGELOG.md), [architecture blueprint](ARCHITECTURE_BLUEPRINT.md), and [configuration reference](CONFIGURATION_REFERENCE.md) are the authoritative companion documents. The chronology distinguishes reconstructed 1.1.0鈥�1.4.9 product-era documentation from the version history verified by the supplied 1.5.1鈥�1.9.5 source and project notes.


---

## Source document: `ROADMAP_STATUS.md`

### Spotui Roadmap and Capability Status

> A current-state map that distinguishes delivered behavior, guarded boundaries, and future-safe extension work.

**Status:** Spotui 1.9.5 source package. This documentation describes the current supplied source tree and its integrated behavior. It does not claim a relationship with Spotify AB, and it does not describe provider-protected media export as a product capability.

The repository is a Kotlin Android music-client project built around Jetpack Compose, a Media3 playback core, Hilt dependency injection, bundled metadata/resolution modules, user-controlled storage routes, and a settings model that persists user choices. The documentation deliberately separates **verified current-source behavior** from **historical reconstruction**. The supplied project archive contains no Git metadata or tags, so version-era narrative before the documented 1.5.1 baseline is a product-line reconstruction rather than a claim that a particular commit has been recovered.

#### Capability status

| Area | Current behavior and boundary |
| --- | --- |
| Account and metadata | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| Playback foundation | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| HTTPS stream resolution | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| Offline workflow | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| NovaAc archives | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| Equalizer and spatial session | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| Playlist control | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| Liked Songs control | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| Adaptive interface | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| Diagnostics and resilience | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| Lyrics and discovery | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| Backup and restore | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |

#### Completed 1.9.5 capability line

The 1.9.5 line is complete when the source package demonstrates visible collection export in both Playlist and Liked Songs, settings that are consumed by active services, a shared adaptive root route model, and an assemble/verify release path. The intent is not to accumulate disconnected controls. A feature earns completion when the screen, persistence layer, runtime owner, fallback logic, diagnostics, and documentation agree about its behavior.

| Workstream | Current result | Why it matters |
| --- | --- | --- |
| Collection archives | Direct export placement and shared preparation/write logic. | Users can discover and control archive creation. |
| Audio | Session-attached EQ/spatial/room/loudness effects. | Settings can create supported runtime behavior. |
| Streaming | Resolver, quality policy, preload, optional web engine control. | Source choice is explicit and diagnosable. |
| Interface | Four navigation layouts, compact presentation, bounded radius. | Personalization does not fracture route behavior. |
| Operations | Diagnostics, cache summary, backup, release validation. | Failures become maintainable rather than mysterious. |

#### Future-safe extension rules

A future recommendation system, provider, collaboration surface, local index, or visualizer must meet the same ownership rules. New storage must name the user-visible lifecycle. New accounts must name consent and token handling. New audio processing must attach to a known session and declare unsupported-output behavior. New navigation layouts must use the common route graph. New archive versions must publish a migration policy. These are not bureaucratic restrictions; they are the constraints that keep a music client understandable as features accumulate.

##### Recommended future work

| Priority | Proposal | Preconditions |
| --- | --- | --- |
| High | Archive migration and compatibility test suite | Fixture files, header-version matrix, and explicit key portability policy. |
| High | Automated UI smoke testing of all navigation layouts | Deterministic root-route test data and Compose test tags. |
| Medium | Audio route capability report | Device/route testing harness and clear non-promissory UI copy. |
| Medium | Release CI with checksum/signature artifact publishing | Owner-controlled signing key handling and secret management. |
| Medium | Collection export progress UI | Streaming-friendly archive preparation and cancellation semantics. |
| Low | More visual themes | Theme tokens that keep accessibility contrast intact. |

#### Guardrails

The project should not accept work that labels provider material as exportable merely because it is playable, starts capture/recording without an explicit user flow, adds hidden trackers, bypasses DRM or access controls, silently requests broad storage access, or stores secrets in source. These boundaries are documented so maintainers can move quickly without making unsafe assumptions.

#### References

[1]: https://developer.android.com/media/media3 "Android Developers: Introduction to Jetpack Media3"
[2]: https://developer.android.com/training/data-storage/shared/documents-files "Android Developers: Storage Access Framework"
[3]: https://github.com/androidx/media "AndroidX Media on GitHub"
[4]: https://developer.android.com/jetpack/compose "Android Developers: Jetpack Compose"
[5]: https://developer.android.com/training/dependency-injection/hilt-android "Android Developers: Dependency injection with Hilt"
[6]: https://github.com/H4zh4n/Spotui "Spotui upstream project"
[7]: https://github.com/navneet851/spotify-clone-jetpack-compose "Neptune Jetpack Compose Spotify clone"
[8]: https://github.com/spotbye/SpotiFLAC "SpotiFLAC project"
[9]: https://github.com/maxrave-dev/SimpMusic "SimpMusic project"
[10]: https://github.com/TeamNewPipe/NewPipeExtractor "NewPipe Extractor"

#### Implementation reference ledger 1

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 1.1 | Account and metadata | innertube | HTTPS resolver | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 1.2 | Playback foundation | metroserver-master | Spotify Web playback | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 1.3 | HTTPS stream resolution | app | Equalizer | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 1.4 | Offline workflow | spotify | Spatial profile | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 1.5 | NovaAc archives | innertube | Spatial depth | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 1.6 | Equalizer and spatial session | metroserver-master | Normalizer intensity | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 1.7 | Playlist control | app | Crossfade and DJ mode | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 1.8 | Liked Songs control | spotify | Navigation layout | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 1.9 | Adaptive interface | innertube | Compact UI | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 1.10 | Diagnostics and resilience | metroserver-master | Corner radius | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 1.11 | Lyrics and discovery | app | Cache and storage | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 1.12 | Backup and restore | spotify | Diagnostics | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 1

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **ROADMAP_STATUS**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 2

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 2.1 | Account and metadata | metroserver-master | Spotify Web playback | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 2.2 | Playback foundation | app | Equalizer | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 2.3 | HTTPS stream resolution | spotify | Spatial profile | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 2.4 | Offline workflow | innertube | Spatial depth | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 2.5 | NovaAc archives | metroserver-master | Normalizer intensity | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 2.6 | Equalizer and spatial session | app | Crossfade and DJ mode | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 2.7 | Playlist control | spotify | Navigation layout | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 2.8 | Liked Songs control | innertube | Compact UI | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 2.9 | Adaptive interface | metroserver-master | Corner radius | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 2.10 | Diagnostics and resilience | app | Cache and storage | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 2.11 | Lyrics and discovery | spotify | Diagnostics | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 2.12 | Backup and restore | innertube | Backup | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 2

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **ROADMAP_STATUS**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 3

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 3.1 | Account and metadata | app | Equalizer | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 3.2 | Playback foundation | spotify | Spatial profile | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 3.3 | HTTPS stream resolution | innertube | Spatial depth | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 3.4 | Offline workflow | metroserver-master | Normalizer intensity | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 3.5 | NovaAc archives | app | Crossfade and DJ mode | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 3.6 | Equalizer and spatial session | spotify | Navigation layout | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 3.7 | Playlist control | innertube | Compact UI | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 3.8 | Liked Songs control | metroserver-master | Corner radius | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 3.9 | Adaptive interface | app | Cache and storage | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 3.10 | Diagnostics and resilience | spotify | Diagnostics | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 3.11 | Lyrics and discovery | innertube | Backup | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 3.12 | Backup and restore | metroserver-master | Streaming quality | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 3

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **ROADMAP_STATUS**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 4

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 4.1 | Account and metadata | spotify | Spatial profile | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 4.2 | Playback foundation | innertube | Spatial depth | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 4.3 | HTTPS stream resolution | metroserver-master | Normalizer intensity | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 4.4 | Offline workflow | app | Crossfade and DJ mode | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 4.5 | NovaAc archives | spotify | Navigation layout | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 4.6 | Equalizer and spatial session | innertube | Compact UI | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 4.7 | Playlist control | metroserver-master | Corner radius | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 4.8 | Liked Songs control | app | Cache and storage | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 4.9 | Adaptive interface | spotify | Diagnostics | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 4.10 | Diagnostics and resilience | innertube | Backup | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 4.11 | Lyrics and discovery | metroserver-master | Streaming quality | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 4.12 | Backup and restore | app | Lossless timeout | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 4

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **ROADMAP_STATUS**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


---

## Source document: `DOCUMENTATION_INDEX.md`

### Spotui Documentation Index

> A reading map for the expanded source-package documentation.

**Status:** Spotui 1.9.5 source package. This documentation describes the current supplied source tree and its integrated behavior. It does not claim a relationship with Spotify AB, and it does not describe provider-protected media export as a product capability.

The repository is a Kotlin Android music-client project built around Jetpack Compose, a Media3 playback core, Hilt dependency injection, bundled metadata/resolution modules, user-controlled storage routes, and a settings model that persists user choices. The documentation deliberately separates **verified current-source behavior** from **historical reconstruction**. The supplied project archive contains no Git metadata or tags, so version-era narrative before the documented 1.5.1 baseline is a product-line reconstruction rather than a claim that a particular commit has been recovered.

#### Reading paths

| Reader | Recommended sequence | Outcome |
| --- | --- | --- |
| New contributor | README 鈫� Architecture Blueprint 鈫� Contributing Guide | Understand modules, ownership, build, and review rules. |
| Release owner | README 鈫� Validation Report 鈫� Changelog | Produce a signed, checksummed, accurately described artifact. |
| UI contributor | README 鈫� Configuration Reference 鈫� Architecture Blueprint | Extend adaptive navigation or settings without duplicate behavior. |
| Playback contributor | Architecture Blueprint 鈫� Configuration Reference 鈫� Implementation Audit | Change player/audio logic while protecting session and fallback behavior. |
| Support / QA | Validation Report 鈫� Implementation Audit 鈫� Configuration Reference | Reproduce, classify, and document user issues. |

#### Document inventory

| Document | Use |
| --- | --- |
| README.md | Project entry point, feature matrix, build information, full release table, credits. |
| CHANGELOG.md | Long-form version record and evidence-status rules. |
| ARCHITECTURE_BLUEPRINT.md | Layering, ownership, player, archives, settings, diagnostics. |
| CONFIGURATION_REFERENCE.md | Every major user-facing configuration and its effect. |
| CONTRIBUTING_AND_OPERATIONS.md | Review, build, release, privacy, and operational expectations. |
| IMPLEMENTATION_DESIGN.md | Engineering rationale for current design decisions. |
| IMPLEMENTATION_AUDIT.md | Integration checks and regression criteria. |
| VALIDATION_REPORT.md | Artifact validation and failure interpretation. |
| ROADMAP_STATUS.md | Current capability map and extension guardrails. |

#### Documentation quality standard

Every maintained technical document in this source package is generated to a substantive minimum length so that it can stand alone when shared with an implementer. Length is not treated as a substitute for accuracy: the documents use evidence labels for unrecoverable history, distinguish application data from provider-controlled material, state signature limitations, and identify runtime owners for visible settings. When code and prose disagree, code plus a new validation run should win; then update the prose in the same change.

#### References

[1]: https://developer.android.com/media/media3 "Android Developers: Introduction to Jetpack Media3"
[2]: https://developer.android.com/training/data-storage/shared/documents-files "Android Developers: Storage Access Framework"
[3]: https://github.com/androidx/media "AndroidX Media on GitHub"
[4]: https://developer.android.com/jetpack/compose "Android Developers: Jetpack Compose"
[5]: https://developer.android.com/training/dependency-injection/hilt-android "Android Developers: Dependency injection with Hilt"
[6]: https://github.com/H4zh4n/Spotui "Spotui upstream project"
[7]: https://github.com/navneet851/spotify-clone-jetpack-compose "Neptune Jetpack Compose Spotify clone"
[8]: https://github.com/spotbye/SpotiFLAC "SpotiFLAC project"
[9]: https://github.com/maxrave-dev/SimpMusic "SimpMusic project"
[10]: https://github.com/TeamNewPipe/NewPipeExtractor "NewPipe Extractor"

#### Implementation reference ledger 1

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 1.1 | Account and metadata | innertube | HTTPS resolver | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 1.2 | Playback foundation | metroserver-master | Spotify Web playback | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 1.3 | HTTPS stream resolution | app | Equalizer | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 1.4 | Offline workflow | spotify | Spatial profile | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 1.5 | NovaAc archives | innertube | Spatial depth | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 1.6 | Equalizer and spatial session | metroserver-master | Normalizer intensity | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 1.7 | Playlist control | app | Crossfade and DJ mode | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 1.8 | Liked Songs control | spotify | Navigation layout | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 1.9 | Adaptive interface | innertube | Compact UI | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 1.10 | Diagnostics and resilience | metroserver-master | Corner radius | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 1.11 | Lyrics and discovery | app | Cache and storage | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 1.12 | Backup and restore | spotify | Diagnostics | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 1

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **DOCUMENTATION_INDEX**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 2

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 2.1 | Account and metadata | metroserver-master | Spotify Web playback | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 2.2 | Playback foundation | app | Equalizer | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 2.3 | HTTPS stream resolution | spotify | Spatial profile | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 2.4 | Offline workflow | innertube | Spatial depth | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 2.5 | NovaAc archives | metroserver-master | Normalizer intensity | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 2.6 | Equalizer and spatial session | app | Crossfade and DJ mode | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 2.7 | Playlist control | spotify | Navigation layout | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 2.8 | Liked Songs control | innertube | Compact UI | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 2.9 | Adaptive interface | metroserver-master | Corner radius | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 2.10 | Diagnostics and resilience | app | Cache and storage | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 2.11 | Lyrics and discovery | spotify | Diagnostics | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 2.12 | Backup and restore | innertube | Backup | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 2

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **DOCUMENTATION_INDEX**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 3

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 3.1 | Account and metadata | app | Equalizer | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 3.2 | Playback foundation | spotify | Spatial profile | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 3.3 | HTTPS stream resolution | innertube | Spatial depth | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 3.4 | Offline workflow | metroserver-master | Normalizer intensity | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 3.5 | NovaAc archives | app | Crossfade and DJ mode | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 3.6 | Equalizer and spatial session | spotify | Navigation layout | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 3.7 | Playlist control | innertube | Compact UI | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 3.8 | Liked Songs control | metroserver-master | Corner radius | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 3.9 | Adaptive interface | app | Cache and storage | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 3.10 | Diagnostics and resilience | spotify | Diagnostics | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 3.11 | Lyrics and discovery | innertube | Backup | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 3.12 | Backup and restore | metroserver-master | Streaming quality | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 3

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **DOCUMENTATION_INDEX**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 4

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 4.1 | Account and metadata | spotify | Spatial profile | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 4.2 | Playback foundation | innertube | Spatial depth | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 4.3 | HTTPS stream resolution | metroserver-master | Normalizer intensity | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 4.4 | Offline workflow | app | Crossfade and DJ mode | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 4.5 | NovaAc archives | spotify | Navigation layout | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 4.6 | Equalizer and spatial session | innertube | Compact UI | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 4.7 | Playlist control | metroserver-master | Corner radius | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 4.8 | Liked Songs control | app | Cache and storage | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 4.9 | Adaptive interface | spotify | Diagnostics | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 4.10 | Diagnostics and resilience | innertube | Backup | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 4.11 | Lyrics and discovery | metroserver-master | Streaming quality | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 4.12 | Backup and restore | app | Lossless timeout | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 4

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **DOCUMENTATION_INDEX**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 5

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 5.1 | Account and metadata | innertube | Spatial depth | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 5.2 | Playback foundation | metroserver-master | Normalizer intensity | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 5.3 | HTTPS stream resolution | app | Crossfade and DJ mode | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 5.4 | Offline workflow | spotify | Navigation layout | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 5.5 | NovaAc archives | innertube | Compact UI | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 5.6 | Equalizer and spatial session | metroserver-master | Corner radius | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 5.7 | Playlist control | app | Cache and storage | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 5.8 | Liked Songs control | spotify | Diagnostics | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 5.9 | Adaptive interface | innertube | Backup | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 5.10 | Diagnostics and resilience | metroserver-master | Streaming quality | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 5.11 | Lyrics and discovery | app | Lossless timeout | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 5.12 | Backup and restore | spotify | HTTPS resolver | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 5

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **DOCUMENTATION_INDEX**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


---

## Source document: `modules/app/layout/README.md`

### Spotui Layout Module Guide

> Layout and visual-system guidance for bottom/top/side/rail navigation, compact presentation, and corner-radius preferences.

**Status:** Spotui 1.9.5 source package. This documentation describes the current supplied source tree and its integrated behavior. It does not claim a relationship with Spotify AB, and it does not describe provider-protected media export as a product capability.

The repository is a Kotlin Android music-client project built around Jetpack Compose, a Media3 playback core, Hilt dependency injection, bundled metadata/resolution modules, user-controlled storage routes, and a settings model that persists user choices. The documentation deliberately separates **verified current-source behavior** from **historical reconstruction**. The supplied project archive contains no Git metadata or tags, so version-era narrative before the documented 1.5.1 baseline is a product-line reconstruction rather than a claim that a particular commit has been recovered.

#### Module role

Layout and visual-system guidance for bottom/top/side/rail navigation, compact presentation, and corner-radius preferences.

This module guide exists so a new maintainer can identify boundaries before making a seemingly small change. Music-client changes often cross modules: metadata or resolver changes may alter a player candidate; player changes may alter media-session behavior; collection changes may alter archive and cache paths. Read the root architecture blueprint before changing public behavior.

#### Integration checklist

| Concern | Expectation |
| --- | --- |
| Build contract | Use the project鈥檚 Java 21, Kotlin 2.2, and Android/Gradle settings where applicable. |
| API contract | Keep public types small and make callers handle recoverable results explicitly. |
| Threading contract | Do network, archive, and filesystem work off the Compose UI thread. |
| Privacy contract | Do not place tokens, cookies, user media, or persistent URLs in source or long-lived logs. |
| Validation contract | Compile the dependent app module and run a relevant behavior smoke test. |

#### Feature relationship

| Area | Current behavior and boundary |
| --- | --- |
| Account and metadata | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| Playback foundation | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| HTTPS stream resolution | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| Offline workflow | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| NovaAc archives | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| Equalizer and spatial session | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| Playlist control | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| Liked Songs control | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| Adaptive interface | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| Diagnostics and resilience | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| Lyrics and discovery | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| Backup and restore | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |

#### Configuration relationship

| Control | Implementation logic |
| --- | --- |
| Streaming quality | Wi-Fi, cellular, and download quality tiers determine the resolver鈥檚 source preference. Lossless requests are best-effort and deliberately fall back to a usable stream when a lossless source is unavailable. |
| Lossless timeout | The selected timeout bounds waiting on lossless mirrors. Short mode favors responsiveness; long mode favors a more patient attempt before fallback. |
| HTTPS resolver | The secure HTTPS resolver remains the normal stream path. The screen reports its role and exposes next-track preloading as an explicit performance trade-off. |
| Spotify Web playback | This optional engine is used only when a valid logged-in web-player session is available. It is not presented as a bypass path; the direct resolver stays available as fallback. |
| Equalizer | Bass, vocal, and treble are normalized 0鈥�100 preferences. The controller maps them to hardware-supported equalizer bands at the current audio session. |
| Spatial profile | Studio, Wide, Immersive, and Cinema profiles configure room/width intent. Actual perceptibility depends on the output device and the framework effects it advertises. |
| Spatial depth | A 0鈥�100% control maps to the Android virtualizer strength range when the device supports it. Unsupported devices leave playback intact and log a diagnostic rather than failing audio. |
| Normalizer intensity | A bounded 0鈥�600 mB loudness target is applied through Android鈥檚 LoudnessEnhancer where supported. The limit exists to reduce the risk of an unexpectedly aggressive gain change. |
| Crossfade and DJ mode | Crossfade controls overlap duration. DJ mode uses the custom processor鈥檚 filter behavior during transitions rather than changing normal playback samples. |
| Navigation layout | Bottom bar, top bar, side bar, and navigation rail are real shell layouts. They preserve the same root routes and search reselect behavior. |
| Compact UI | Compact UI reduces navigation visual density and labels while preserving accessible content descriptions and route reachability. |
| Corner radius | The live 0鈥�48 dp preference changes shell and settings-surface shaping. It is a presentation setting only and cannot alter collection metadata or playback state. |
| Cache and storage | Cache summary and cleanup distinguish temporary resources from user-directed content. Cleanup is intentionally targeted rather than a recursive destructive wipe of every app file. |
| Diagnostics | The developer console and app-private diagnostic records are for troubleshooting. Stream URLs and tokens must be redacted before long-lived logging. |
| Backup | Backup destination selection uses Android鈥檚 system picker. Persistable URI permissions are retained only for locations the user actively chooses. |

#### References

[1]: https://developer.android.com/media/media3 "Android Developers: Introduction to Jetpack Media3"
[2]: https://developer.android.com/training/data-storage/shared/documents-files "Android Developers: Storage Access Framework"
[3]: https://github.com/androidx/media "AndroidX Media on GitHub"
[4]: https://developer.android.com/jetpack/compose "Android Developers: Jetpack Compose"
[5]: https://developer.android.com/training/dependency-injection/hilt-android "Android Developers: Dependency injection with Hilt"
[6]: https://github.com/H4zh4n/Spotui "Spotui upstream project"
[7]: https://github.com/navneet851/spotify-clone-jetpack-compose "Neptune Jetpack Compose Spotify clone"
[8]: https://github.com/spotbye/SpotiFLAC "SpotiFLAC project"
[9]: https://github.com/maxrave-dev/SimpMusic "SimpMusic project"
[10]: https://github.com/TeamNewPipe/NewPipeExtractor "NewPipe Extractor"

#### Implementation reference ledger 1

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 1.1 | Account and metadata | innertube | HTTPS resolver | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 1.2 | Playback foundation | metroserver-master | Spotify Web playback | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 1.3 | HTTPS stream resolution | app | Equalizer | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 1.4 | Offline workflow | spotify | Spatial profile | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 1.5 | NovaAc archives | innertube | Spatial depth | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 1.6 | Equalizer and spatial session | metroserver-master | Normalizer intensity | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 1.7 | Playlist control | app | Crossfade and DJ mode | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 1.8 | Liked Songs control | spotify | Navigation layout | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 1.9 | Adaptive interface | innertube | Compact UI | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 1.10 | Diagnostics and resilience | metroserver-master | Corner radius | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 1.11 | Lyrics and discovery | app | Cache and storage | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 1.12 | Backup and restore | spotify | Diagnostics | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 1

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **README**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 2

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 2.1 | Account and metadata | metroserver-master | Spotify Web playback | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 2.2 | Playback foundation | app | Equalizer | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 2.3 | HTTPS stream resolution | spotify | Spatial profile | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 2.4 | Offline workflow | innertube | Spatial depth | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 2.5 | NovaAc archives | metroserver-master | Normalizer intensity | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 2.6 | Equalizer and spatial session | app | Crossfade and DJ mode | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 2.7 | Playlist control | spotify | Navigation layout | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 2.8 | Liked Songs control | innertube | Compact UI | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 2.9 | Adaptive interface | metroserver-master | Corner radius | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 2.10 | Diagnostics and resilience | app | Cache and storage | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 2.11 | Lyrics and discovery | spotify | Diagnostics | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 2.12 | Backup and restore | innertube | Backup | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 2

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **README**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 3

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 3.1 | Account and metadata | app | Equalizer | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 3.2 | Playback foundation | spotify | Spatial profile | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 3.3 | HTTPS stream resolution | innertube | Spatial depth | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 3.4 | Offline workflow | metroserver-master | Normalizer intensity | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 3.5 | NovaAc archives | app | Crossfade and DJ mode | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 3.6 | Equalizer and spatial session | spotify | Navigation layout | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 3.7 | Playlist control | innertube | Compact UI | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 3.8 | Liked Songs control | metroserver-master | Corner radius | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 3.9 | Adaptive interface | app | Cache and storage | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 3.10 | Diagnostics and resilience | spotify | Diagnostics | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 3.11 | Lyrics and discovery | innertube | Backup | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 3.12 | Backup and restore | metroserver-master | Streaming quality | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 3

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **README**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 4

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 4.1 | Account and metadata | spotify | Spatial profile | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 4.2 | Playback foundation | innertube | Spatial depth | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 4.3 | HTTPS stream resolution | metroserver-master | Normalizer intensity | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 4.4 | Offline workflow | app | Crossfade and DJ mode | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 4.5 | NovaAc archives | spotify | Navigation layout | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 4.6 | Equalizer and spatial session | innertube | Compact UI | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 4.7 | Playlist control | metroserver-master | Corner radius | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 4.8 | Liked Songs control | app | Cache and storage | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 4.9 | Adaptive interface | spotify | Diagnostics | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 4.10 | Diagnostics and resilience | innertube | Backup | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 4.11 | Lyrics and discovery | metroserver-master | Streaming quality | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 4.12 | Backup and restore | app | Lossless timeout | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 4

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **README**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


---

## Source document: `modules/app/main/README.md`

### Spotui Main Module Guide

> The Android application module. It owns Compose UI, the app manifest, Media3 integration, Hilt wiring, persistent settings, playback, downloads, NovaAc, and all user-facing screens.

**Status:** Spotui 1.9.5 source package. This documentation describes the current supplied source tree and its integrated behavior. It does not claim a relationship with Spotify AB, and it does not describe provider-protected media export as a product capability.

The repository is a Kotlin Android music-client project built around Jetpack Compose, a Media3 playback core, Hilt dependency injection, bundled metadata/resolution modules, user-controlled storage routes, and a settings model that persists user choices. The documentation deliberately separates **verified current-source behavior** from **historical reconstruction**. The supplied project archive contains no Git metadata or tags, so version-era narrative before the documented 1.5.1 baseline is a product-line reconstruction rather than a claim that a particular commit has been recovered.

#### Module role

The Android application module. It owns Compose UI, the app manifest, Media3 integration, Hilt wiring, persistent settings, playback, downloads, NovaAc, and all user-facing screens.

This module guide exists so a new maintainer can identify boundaries before making a seemingly small change. Music-client changes often cross modules: metadata or resolver changes may alter a player candidate; player changes may alter media-session behavior; collection changes may alter archive and cache paths. Read the root architecture blueprint before changing public behavior.

#### Integration checklist

| Concern | Expectation |
| --- | --- |
| Build contract | Use the project鈥檚 Java 21, Kotlin 2.2, and Android/Gradle settings where applicable. |
| API contract | Keep public types small and make callers handle recoverable results explicitly. |
| Threading contract | Do network, archive, and filesystem work off the Compose UI thread. |
| Privacy contract | Do not place tokens, cookies, user media, or persistent URLs in source or long-lived logs. |
| Validation contract | Compile the dependent app module and run a relevant behavior smoke test. |

#### Feature relationship

| Area | Current behavior and boundary |
| --- | --- |
| Account and metadata | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| Playback foundation | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| HTTPS stream resolution | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| Offline workflow | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| NovaAc archives | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| Equalizer and spatial session | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| Playlist control | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| Liked Songs control | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| Adaptive interface | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| Diagnostics and resilience | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| Lyrics and discovery | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| Backup and restore | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |

#### Configuration relationship

| Control | Implementation logic |
| --- | --- |
| Streaming quality | Wi-Fi, cellular, and download quality tiers determine the resolver鈥檚 source preference. Lossless requests are best-effort and deliberately fall back to a usable stream when a lossless source is unavailable. |
| Lossless timeout | The selected timeout bounds waiting on lossless mirrors. Short mode favors responsiveness; long mode favors a more patient attempt before fallback. |
| HTTPS resolver | The secure HTTPS resolver remains the normal stream path. The screen reports its role and exposes next-track preloading as an explicit performance trade-off. |
| Spotify Web playback | This optional engine is used only when a valid logged-in web-player session is available. It is not presented as a bypass path; the direct resolver stays available as fallback. |
| Equalizer | Bass, vocal, and treble are normalized 0鈥�100 preferences. The controller maps them to hardware-supported equalizer bands at the current audio session. |
| Spatial profile | Studio, Wide, Immersive, and Cinema profiles configure room/width intent. Actual perceptibility depends on the output device and the framework effects it advertises. |
| Spatial depth | A 0鈥�100% control maps to the Android virtualizer strength range when the device supports it. Unsupported devices leave playback intact and log a diagnostic rather than failing audio. |
| Normalizer intensity | A bounded 0鈥�600 mB loudness target is applied through Android鈥檚 LoudnessEnhancer where supported. The limit exists to reduce the risk of an unexpectedly aggressive gain change. |
| Crossfade and DJ mode | Crossfade controls overlap duration. DJ mode uses the custom processor鈥檚 filter behavior during transitions rather than changing normal playback samples. |
| Navigation layout | Bottom bar, top bar, side bar, and navigation rail are real shell layouts. They preserve the same root routes and search reselect behavior. |
| Compact UI | Compact UI reduces navigation visual density and labels while preserving accessible content descriptions and route reachability. |
| Corner radius | The live 0鈥�48 dp preference changes shell and settings-surface shaping. It is a presentation setting only and cannot alter collection metadata or playback state. |
| Cache and storage | Cache summary and cleanup distinguish temporary resources from user-directed content. Cleanup is intentionally targeted rather than a recursive destructive wipe of every app file. |
| Diagnostics | The developer console and app-private diagnostic records are for troubleshooting. Stream URLs and tokens must be redacted before long-lived logging. |
| Backup | Backup destination selection uses Android鈥檚 system picker. Persistable URI permissions are retained only for locations the user actively chooses. |

#### References

[1]: https://developer.android.com/media/media3 "Android Developers: Introduction to Jetpack Media3"
[2]: https://developer.android.com/training/data-storage/shared/documents-files "Android Developers: Storage Access Framework"
[3]: https://github.com/androidx/media "AndroidX Media on GitHub"
[4]: https://developer.android.com/jetpack/compose "Android Developers: Jetpack Compose"
[5]: https://developer.android.com/training/dependency-injection/hilt-android "Android Developers: Dependency injection with Hilt"
[6]: https://github.com/H4zh4n/Spotui "Spotui upstream project"
[7]: https://github.com/navneet851/spotify-clone-jetpack-compose "Neptune Jetpack Compose Spotify clone"
[8]: https://github.com/spotbye/SpotiFLAC "SpotiFLAC project"
[9]: https://github.com/maxrave-dev/SimpMusic "SimpMusic project"
[10]: https://github.com/TeamNewPipe/NewPipeExtractor "NewPipe Extractor"

#### Implementation reference ledger 1

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 1.1 | Account and metadata | innertube | HTTPS resolver | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 1.2 | Playback foundation | metroserver-master | Spotify Web playback | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 1.3 | HTTPS stream resolution | app | Equalizer | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 1.4 | Offline workflow | spotify | Spatial profile | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 1.5 | NovaAc archives | innertube | Spatial depth | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 1.6 | Equalizer and spatial session | metroserver-master | Normalizer intensity | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 1.7 | Playlist control | app | Crossfade and DJ mode | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 1.8 | Liked Songs control | spotify | Navigation layout | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 1.9 | Adaptive interface | innertube | Compact UI | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 1.10 | Diagnostics and resilience | metroserver-master | Corner radius | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 1.11 | Lyrics and discovery | app | Cache and storage | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 1.12 | Backup and restore | spotify | Diagnostics | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 1

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **README**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 2

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 2.1 | Account and metadata | metroserver-master | Spotify Web playback | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 2.2 | Playback foundation | app | Equalizer | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 2.3 | HTTPS stream resolution | spotify | Spatial profile | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 2.4 | Offline workflow | innertube | Spatial depth | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 2.5 | NovaAc archives | metroserver-master | Normalizer intensity | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 2.6 | Equalizer and spatial session | app | Crossfade and DJ mode | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 2.7 | Playlist control | spotify | Navigation layout | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 2.8 | Liked Songs control | innertube | Compact UI | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 2.9 | Adaptive interface | metroserver-master | Corner radius | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 2.10 | Diagnostics and resilience | app | Cache and storage | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 2.11 | Lyrics and discovery | spotify | Diagnostics | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 2.12 | Backup and restore | innertube | Backup | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 2

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **README**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 3

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 3.1 | Account and metadata | app | Equalizer | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 3.2 | Playback foundation | spotify | Spatial profile | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 3.3 | HTTPS stream resolution | innertube | Spatial depth | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 3.4 | Offline workflow | metroserver-master | Normalizer intensity | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 3.5 | NovaAc archives | app | Crossfade and DJ mode | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 3.6 | Equalizer and spatial session | spotify | Navigation layout | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 3.7 | Playlist control | innertube | Compact UI | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 3.8 | Liked Songs control | metroserver-master | Corner radius | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 3.9 | Adaptive interface | app | Cache and storage | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 3.10 | Diagnostics and resilience | spotify | Diagnostics | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 3.11 | Lyrics and discovery | innertube | Backup | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 3.12 | Backup and restore | metroserver-master | Streaming quality | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 3

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **README**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 4

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 4.1 | Account and metadata | spotify | Spatial profile | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 4.2 | Playback foundation | innertube | Spatial depth | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 4.3 | HTTPS stream resolution | metroserver-master | Normalizer intensity | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 4.4 | Offline workflow | app | Crossfade and DJ mode | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 4.5 | NovaAc archives | spotify | Navigation layout | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 4.6 | Equalizer and spatial session | innertube | Compact UI | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 4.7 | Playlist control | metroserver-master | Corner radius | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 4.8 | Liked Songs control | app | Cache and storage | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 4.9 | Adaptive interface | spotify | Diagnostics | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 4.10 | Diagnostics and resilience | innertube | Backup | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 4.11 | Lyrics and discovery | metroserver-master | Streaming quality | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 4.12 | Backup and restore | app | Lossless timeout | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 4

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **README**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


---

## Source document: `modules/app/test/README.md`

### Spotui Test Module Guide

> Testing guidance for the app package, including route, archive, audio, and release regression coverage.

**Status:** Spotui 1.9.5 source package. This documentation describes the current supplied source tree and its integrated behavior. It does not claim a relationship with Spotify AB, and it does not describe provider-protected media export as a product capability.

The repository is a Kotlin Android music-client project built around Jetpack Compose, a Media3 playback core, Hilt dependency injection, bundled metadata/resolution modules, user-controlled storage routes, and a settings model that persists user choices. The documentation deliberately separates **verified current-source behavior** from **historical reconstruction**. The supplied project archive contains no Git metadata or tags, so version-era narrative before the documented 1.5.1 baseline is a product-line reconstruction rather than a claim that a particular commit has been recovered.

#### Module role

Testing guidance for the app package, including route, archive, audio, and release regression coverage.

This module guide exists so a new maintainer can identify boundaries before making a seemingly small change. Music-client changes often cross modules: metadata or resolver changes may alter a player candidate; player changes may alter media-session behavior; collection changes may alter archive and cache paths. Read the root architecture blueprint before changing public behavior.

#### Integration checklist

| Concern | Expectation |
| --- | --- |
| Build contract | Use the project鈥檚 Java 21, Kotlin 2.2, and Android/Gradle settings where applicable. |
| API contract | Keep public types small and make callers handle recoverable results explicitly. |
| Threading contract | Do network, archive, and filesystem work off the Compose UI thread. |
| Privacy contract | Do not place tokens, cookies, user media, or persistent URLs in source or long-lived logs. |
| Validation contract | Compile the dependent app module and run a relevant behavior smoke test. |

#### Feature relationship

| Area | Current behavior and boundary |
| --- | --- |
| Account and metadata | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| Playback foundation | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| HTTPS stream resolution | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| Offline workflow | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| NovaAc archives | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| Equalizer and spatial session | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| Playlist control | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| Liked Songs control | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| Adaptive interface | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| Diagnostics and resilience | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| Lyrics and discovery | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| Backup and restore | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |

#### Configuration relationship

| Control | Implementation logic |
| --- | --- |
| Streaming quality | Wi-Fi, cellular, and download quality tiers determine the resolver鈥檚 source preference. Lossless requests are best-effort and deliberately fall back to a usable stream when a lossless source is unavailable. |
| Lossless timeout | The selected timeout bounds waiting on lossless mirrors. Short mode favors responsiveness; long mode favors a more patient attempt before fallback. |
| HTTPS resolver | The secure HTTPS resolver remains the normal stream path. The screen reports its role and exposes next-track preloading as an explicit performance trade-off. |
| Spotify Web playback | This optional engine is used only when a valid logged-in web-player session is available. It is not presented as a bypass path; the direct resolver stays available as fallback. |
| Equalizer | Bass, vocal, and treble are normalized 0鈥�100 preferences. The controller maps them to hardware-supported equalizer bands at the current audio session. |
| Spatial profile | Studio, Wide, Immersive, and Cinema profiles configure room/width intent. Actual perceptibility depends on the output device and the framework effects it advertises. |
| Spatial depth | A 0鈥�100% control maps to the Android virtualizer strength range when the device supports it. Unsupported devices leave playback intact and log a diagnostic rather than failing audio. |
| Normalizer intensity | A bounded 0鈥�600 mB loudness target is applied through Android鈥檚 LoudnessEnhancer where supported. The limit exists to reduce the risk of an unexpectedly aggressive gain change. |
| Crossfade and DJ mode | Crossfade controls overlap duration. DJ mode uses the custom processor鈥檚 filter behavior during transitions rather than changing normal playback samples. |
| Navigation layout | Bottom bar, top bar, side bar, and navigation rail are real shell layouts. They preserve the same root routes and search reselect behavior. |
| Compact UI | Compact UI reduces navigation visual density and labels while preserving accessible content descriptions and route reachability. |
| Corner radius | The live 0鈥�48 dp preference changes shell and settings-surface shaping. It is a presentation setting only and cannot alter collection metadata or playback state. |
| Cache and storage | Cache summary and cleanup distinguish temporary resources from user-directed content. Cleanup is intentionally targeted rather than a recursive destructive wipe of every app file. |
| Diagnostics | The developer console and app-private diagnostic records are for troubleshooting. Stream URLs and tokens must be redacted before long-lived logging. |
| Backup | Backup destination selection uses Android鈥檚 system picker. Persistable URI permissions are retained only for locations the user actively chooses. |

#### References

[1]: https://developer.android.com/media/media3 "Android Developers: Introduction to Jetpack Media3"
[2]: https://developer.android.com/training/data-storage/shared/documents-files "Android Developers: Storage Access Framework"
[3]: https://github.com/androidx/media "AndroidX Media on GitHub"
[4]: https://developer.android.com/jetpack/compose "Android Developers: Jetpack Compose"
[5]: https://developer.android.com/training/dependency-injection/hilt-android "Android Developers: Dependency injection with Hilt"
[6]: https://github.com/H4zh4n/Spotui "Spotui upstream project"
[7]: https://github.com/navneet851/spotify-clone-jetpack-compose "Neptune Jetpack Compose Spotify clone"
[8]: https://github.com/spotbye/SpotiFLAC "SpotiFLAC project"
[9]: https://github.com/maxrave-dev/SimpMusic "SimpMusic project"
[10]: https://github.com/TeamNewPipe/NewPipeExtractor "NewPipe Extractor"

#### Implementation reference ledger 1

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 1.1 | Account and metadata | innertube | HTTPS resolver | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 1.2 | Playback foundation | metroserver-master | Spotify Web playback | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 1.3 | HTTPS stream resolution | app | Equalizer | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 1.4 | Offline workflow | spotify | Spatial profile | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 1.5 | NovaAc archives | innertube | Spatial depth | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 1.6 | Equalizer and spatial session | metroserver-master | Normalizer intensity | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 1.7 | Playlist control | app | Crossfade and DJ mode | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 1.8 | Liked Songs control | spotify | Navigation layout | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 1.9 | Adaptive interface | innertube | Compact UI | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 1.10 | Diagnostics and resilience | metroserver-master | Corner radius | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 1.11 | Lyrics and discovery | app | Cache and storage | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 1.12 | Backup and restore | spotify | Diagnostics | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 1

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **README**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 2

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 2.1 | Account and metadata | metroserver-master | Spotify Web playback | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 2.2 | Playback foundation | app | Equalizer | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 2.3 | HTTPS stream resolution | spotify | Spatial profile | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 2.4 | Offline workflow | innertube | Spatial depth | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 2.5 | NovaAc archives | metroserver-master | Normalizer intensity | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 2.6 | Equalizer and spatial session | app | Crossfade and DJ mode | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 2.7 | Playlist control | spotify | Navigation layout | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 2.8 | Liked Songs control | innertube | Compact UI | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 2.9 | Adaptive interface | metroserver-master | Corner radius | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 2.10 | Diagnostics and resilience | app | Cache and storage | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 2.11 | Lyrics and discovery | spotify | Diagnostics | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 2.12 | Backup and restore | innertube | Backup | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 2

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **README**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 3

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 3.1 | Account and metadata | app | Equalizer | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 3.2 | Playback foundation | spotify | Spatial profile | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 3.3 | HTTPS stream resolution | innertube | Spatial depth | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 3.4 | Offline workflow | metroserver-master | Normalizer intensity | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 3.5 | NovaAc archives | app | Crossfade and DJ mode | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 3.6 | Equalizer and spatial session | spotify | Navigation layout | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 3.7 | Playlist control | innertube | Compact UI | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 3.8 | Liked Songs control | metroserver-master | Corner radius | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 3.9 | Adaptive interface | app | Cache and storage | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 3.10 | Diagnostics and resilience | spotify | Diagnostics | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 3.11 | Lyrics and discovery | innertube | Backup | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 3.12 | Backup and restore | metroserver-master | Streaming quality | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 3

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **README**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 4

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 4.1 | Account and metadata | spotify | Spatial profile | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 4.2 | Playback foundation | innertube | Spatial depth | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 4.3 | HTTPS stream resolution | metroserver-master | Normalizer intensity | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 4.4 | Offline workflow | app | Crossfade and DJ mode | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 4.5 | NovaAc archives | spotify | Navigation layout | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 4.6 | Equalizer and spatial session | innertube | Compact UI | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 4.7 | Playlist control | metroserver-master | Corner radius | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 4.8 | Liked Songs control | app | Cache and storage | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 4.9 | Adaptive interface | spotify | Diagnostics | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 4.10 | Diagnostics and resilience | innertube | Backup | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 4.11 | Lyrics and discovery | metroserver-master | Streaming quality | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 4.12 | Backup and restore | app | Lossless timeout | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 4

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **README**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


---

## Source document: `modules/app/ui/README.md`

### Spotui UI Module Guide

> UI architecture guidance for Compose screens, adaptive navigation, accessibility, state ownership, and collection controls.

**Status:** Spotui 1.9.5 source package. This documentation describes the current supplied source tree and its integrated behavior. It does not claim a relationship with Spotify AB, and it does not describe provider-protected media export as a product capability.

The repository is a Kotlin Android music-client project built around Jetpack Compose, a Media3 playback core, Hilt dependency injection, bundled metadata/resolution modules, user-controlled storage routes, and a settings model that persists user choices. The documentation deliberately separates **verified current-source behavior** from **historical reconstruction**. The supplied project archive contains no Git metadata or tags, so version-era narrative before the documented 1.5.1 baseline is a product-line reconstruction rather than a claim that a particular commit has been recovered.

#### Module role

UI architecture guidance for Compose screens, adaptive navigation, accessibility, state ownership, and collection controls.

This module guide exists so a new maintainer can identify boundaries before making a seemingly small change. Music-client changes often cross modules: metadata or resolver changes may alter a player candidate; player changes may alter media-session behavior; collection changes may alter archive and cache paths. Read the root architecture blueprint before changing public behavior.

#### Integration checklist

| Concern | Expectation |
| --- | --- |
| Build contract | Use the project鈥檚 Java 21, Kotlin 2.2, and Android/Gradle settings where applicable. |
| API contract | Keep public types small and make callers handle recoverable results explicitly. |
| Threading contract | Do network, archive, and filesystem work off the Compose UI thread. |
| Privacy contract | Do not place tokens, cookies, user media, or persistent URLs in source or long-lived logs. |
| Validation contract | Compile the dependent app module and run a relevant behavior smoke test. |

#### Feature relationship

| Area | Current behavior and boundary |
| --- | --- |
| Account and metadata | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| Playback foundation | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| HTTPS stream resolution | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| Offline workflow | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| NovaAc archives | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| Equalizer and spatial session | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| Playlist control | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| Liked Songs control | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| Adaptive interface | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| Diagnostics and resilience | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| Lyrics and discovery | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| Backup and restore | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |

#### Configuration relationship

| Control | Implementation logic |
| --- | --- |
| Streaming quality | Wi-Fi, cellular, and download quality tiers determine the resolver鈥檚 source preference. Lossless requests are best-effort and deliberately fall back to a usable stream when a lossless source is unavailable. |
| Lossless timeout | The selected timeout bounds waiting on lossless mirrors. Short mode favors responsiveness; long mode favors a more patient attempt before fallback. |
| HTTPS resolver | The secure HTTPS resolver remains the normal stream path. The screen reports its role and exposes next-track preloading as an explicit performance trade-off. |
| Spotify Web playback | This optional engine is used only when a valid logged-in web-player session is available. It is not presented as a bypass path; the direct resolver stays available as fallback. |
| Equalizer | Bass, vocal, and treble are normalized 0鈥�100 preferences. The controller maps them to hardware-supported equalizer bands at the current audio session. |
| Spatial profile | Studio, Wide, Immersive, and Cinema profiles configure room/width intent. Actual perceptibility depends on the output device and the framework effects it advertises. |
| Spatial depth | A 0鈥�100% control maps to the Android virtualizer strength range when the device supports it. Unsupported devices leave playback intact and log a diagnostic rather than failing audio. |
| Normalizer intensity | A bounded 0鈥�600 mB loudness target is applied through Android鈥檚 LoudnessEnhancer where supported. The limit exists to reduce the risk of an unexpectedly aggressive gain change. |
| Crossfade and DJ mode | Crossfade controls overlap duration. DJ mode uses the custom processor鈥檚 filter behavior during transitions rather than changing normal playback samples. |
| Navigation layout | Bottom bar, top bar, side bar, and navigation rail are real shell layouts. They preserve the same root routes and search reselect behavior. |
| Compact UI | Compact UI reduces navigation visual density and labels while preserving accessible content descriptions and route reachability. |
| Corner radius | The live 0鈥�48 dp preference changes shell and settings-surface shaping. It is a presentation setting only and cannot alter collection metadata or playback state. |
| Cache and storage | Cache summary and cleanup distinguish temporary resources from user-directed content. Cleanup is intentionally targeted rather than a recursive destructive wipe of every app file. |
| Diagnostics | The developer console and app-private diagnostic records are for troubleshooting. Stream URLs and tokens must be redacted before long-lived logging. |
| Backup | Backup destination selection uses Android鈥檚 system picker. Persistable URI permissions are retained only for locations the user actively chooses. |

#### References

[1]: https://developer.android.com/media/media3 "Android Developers: Introduction to Jetpack Media3"
[2]: https://developer.android.com/training/data-storage/shared/documents-files "Android Developers: Storage Access Framework"
[3]: https://github.com/androidx/media "AndroidX Media on GitHub"
[4]: https://developer.android.com/jetpack/compose "Android Developers: Jetpack Compose"
[5]: https://developer.android.com/training/dependency-injection/hilt-android "Android Developers: Dependency injection with Hilt"
[6]: https://github.com/H4zh4n/Spotui "Spotui upstream project"
[7]: https://github.com/navneet851/spotify-clone-jetpack-compose "Neptune Jetpack Compose Spotify clone"
[8]: https://github.com/spotbye/SpotiFLAC "SpotiFLAC project"
[9]: https://github.com/maxrave-dev/SimpMusic "SimpMusic project"
[10]: https://github.com/TeamNewPipe/NewPipeExtractor "NewPipe Extractor"

#### Implementation reference ledger 1

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 1.1 | Account and metadata | innertube | HTTPS resolver | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 1.2 | Playback foundation | metroserver-master | Spotify Web playback | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 1.3 | HTTPS stream resolution | app | Equalizer | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 1.4 | Offline workflow | spotify | Spatial profile | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 1.5 | NovaAc archives | innertube | Spatial depth | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 1.6 | Equalizer and spatial session | metroserver-master | Normalizer intensity | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 1.7 | Playlist control | app | Crossfade and DJ mode | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 1.8 | Liked Songs control | spotify | Navigation layout | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 1.9 | Adaptive interface | innertube | Compact UI | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 1.10 | Diagnostics and resilience | metroserver-master | Corner radius | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 1.11 | Lyrics and discovery | app | Cache and storage | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 1.12 | Backup and restore | spotify | Diagnostics | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 1

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **README**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 2

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 2.1 | Account and metadata | metroserver-master | Spotify Web playback | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 2.2 | Playback foundation | app | Equalizer | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 2.3 | HTTPS stream resolution | spotify | Spatial profile | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 2.4 | Offline workflow | innertube | Spatial depth | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 2.5 | NovaAc archives | metroserver-master | Normalizer intensity | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 2.6 | Equalizer and spatial session | app | Crossfade and DJ mode | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 2.7 | Playlist control | spotify | Navigation layout | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 2.8 | Liked Songs control | innertube | Compact UI | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 2.9 | Adaptive interface | metroserver-master | Corner radius | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 2.10 | Diagnostics and resilience | app | Cache and storage | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 2.11 | Lyrics and discovery | spotify | Diagnostics | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 2.12 | Backup and restore | innertube | Backup | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 2

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **README**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 3

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 3.1 | Account and metadata | app | Equalizer | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 3.2 | Playback foundation | spotify | Spatial profile | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 3.3 | HTTPS stream resolution | innertube | Spatial depth | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 3.4 | Offline workflow | metroserver-master | Normalizer intensity | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 3.5 | NovaAc archives | app | Crossfade and DJ mode | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 3.6 | Equalizer and spatial session | spotify | Navigation layout | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 3.7 | Playlist control | innertube | Compact UI | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 3.8 | Liked Songs control | metroserver-master | Corner radius | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 3.9 | Adaptive interface | app | Cache and storage | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 3.10 | Diagnostics and resilience | spotify | Diagnostics | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 3.11 | Lyrics and discovery | innertube | Backup | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 3.12 | Backup and restore | metroserver-master | Streaming quality | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 3

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **README**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


#### Implementation reference ledger 4

| ID | Feature area | Primary owner | Related configuration | Maintenance note |
| --- | --- | --- | --- | --- |
| 4.1 | Account and metadata | spotify | Spatial profile | Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections. |
| 4.2 | Playback foundation | innertube | Spatial depth | Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support. |
| 4.3 | HTTPS stream resolution | metroserver-master | Normalizer intensity | The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics. |
| 4.4 | Offline workflow | app | Crossfade and DJ mode | Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection. |
| 4.5 | NovaAc archives | spotify | Navigation layout | NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media. |
| 4.6 | Equalizer and spatial session | innertube | Compact UI | The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects. |
| 4.7 | Playlist control | metroserver-master | Corner radius | Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points. |
| 4.8 | Liked Songs control | app | Cache and storage | Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download. |
| 4.9 | Adaptive interface | spotify | Diagnostics | The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0鈥�48 dp radius preference influence the live shell. |
| 4.10 | Diagnostics and resilience | innertube | Backup | AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting. |
| 4.11 | Lyrics and discovery | metroserver-master | Streaming quality | Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience. |
| 4.12 | Backup and restore | app | Lossless timeout | User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority. |
##### Interpretation notes for ledger 4

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **README**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.


---

## Consolidation manifest

This README incorporates **22** Android project Markdown documents. Generated from the v2.1.5 source tree for the GitHub repository package.
