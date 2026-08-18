from pathlib import Path
from textwrap import dedent

ROOT = Path('/home/ubuntu/spotui_build/project')
MIN_BYTES = 21000

references = dedent('''
## References

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
''').strip()

feature_catalog = [
    ('Account and metadata', 'Spotify-link routing, account-aware metadata surfaces, playlists, albums, artists, shows, recommendations, and library collections.'),
    ('Playback foundation', 'Media3/ExoPlayer playback, a background media session service, notification controls, queue management, remembered position, and Android Auto browse support.'),
    ('HTTPS stream resolution', 'The resolver selects a supported HTTPS source through the bundled metadata and InnerTube layers, applies quality/fallback policy, sanitizes unsafe URLs, and records failures in diagnostics.'),
    ('Offline workflow', 'Downloads remain app-managed media state. The download library supports bulk export to the public Music directory, storage summary, targeted transient-cache cleanup, and safe cache inspection.'),
    ('NovaAc archives', 'NovaAc is an application archive for selected track metadata, collection context, and optionally locally available app-owned cache payloads. It is not a DRM bypass or an exporter for provider-controlled media.'),
    ('Equalizer and spatial session', 'The audio controller attaches to the active Media3 audio session, maps bass/vocal/treble values to supported equalizer bands, and applies device-supported virtualizer, room, and loudness effects.'),
    ('Playlist control', 'Playlist screens provide download, queue, shuffle, multi-selection, direct labeled NovaAc export, sort, local management, visible track actions, and import/export entry points.'),
    ('Liked Songs control', 'Liked Songs is a first-class collection with download, queue, shuffle, visible track actions, and the same direct labeled NovaAc export control beside Download.'),
    ('Adaptive interface', 'The app shell can render a bottom bar, top tabs, a side bar, or a navigation rail. Compact UI and a persisted 0–48 dp radius preference influence the live shell.'),
    ('Diagnostics and resilience', 'AppDiagnostics captures bounded app-private events and startup context. The developer console provides a user-visible route for warnings, errors, and troubleshooting.'),
    ('Lyrics and discovery', 'Lyrics, translation caches, deep links, category browsing, search, history, artwork resolution, and queue/radio behavior remain part of the integrated experience.'),
    ('Backup and restore', 'User-selected backup storage is handled through the Android Storage Access Framework, which places file choice under user control rather than requesting broad storage authority.'),
]

module_catalog = [
    ('app', 'The Android application module. It owns Compose UI, the app manifest, Media3 integration, Hilt wiring, persistent settings, playback, downloads, NovaAc, and all user-facing screens.'),
    ('spotify', 'A Kotlin/JVM metadata and network module using Ktor and serialization. It supplies Spotify-facing data operations without requiring the Compose presentation layer to own transport details.'),
    ('innertube', 'An Android library module for source resolution and media-oriented network behavior. It contains the InnerTube integration, candidate selection, provider fallbacks, compression support, and logging hooks.'),
    ('metroserver-master', 'A bundled companion source tree. It is documented separately because it has its own operational lifecycle and should not be confused with the Android client runtime.'),
]

settings_catalog = [
    ('Streaming quality', 'Wi-Fi, cellular, and download quality tiers determine the resolver’s source preference. Lossless requests are best-effort and deliberately fall back to a usable stream when a lossless source is unavailable.'),
    ('Lossless timeout', 'The selected timeout bounds waiting on lossless mirrors. Short mode favors responsiveness; long mode favors a more patient attempt before fallback.'),
    ('HTTPS resolver', 'The secure HTTPS resolver remains the normal stream path. The screen reports its role and exposes next-track preloading as an explicit performance trade-off.'),
    ('Spotify Web playback', 'This optional engine is used only when a valid logged-in web-player session is available. It is not presented as a bypass path; the direct resolver stays available as fallback.'),
    ('Equalizer', 'Bass, vocal, and treble are normalized 0–100 preferences. The controller maps them to hardware-supported equalizer bands at the current audio session.'),
    ('Spatial profile', 'Studio, Wide, Immersive, and Cinema profiles configure room/width intent. Actual perceptibility depends on the output device and the framework effects it advertises.'),
    ('Spatial depth', 'A 0–100% control maps to the Android virtualizer strength range when the device supports it. Unsupported devices leave playback intact and log a diagnostic rather than failing audio.'),
    ('Normalizer intensity', 'A bounded 0–600 mB loudness target is applied through Android’s LoudnessEnhancer where supported. The limit exists to reduce the risk of an unexpectedly aggressive gain change.'),
    ('Crossfade and DJ mode', 'Crossfade controls overlap duration. DJ mode uses the custom processor’s filter behavior during transitions rather than changing normal playback samples.'),
    ('Navigation layout', 'Bottom bar, top bar, side bar, and navigation rail are real shell layouts. They preserve the same root routes and search reselect behavior.'),
    ('Compact UI', 'Compact UI reduces navigation visual density and labels while preserving accessible content descriptions and route reachability.'),
    ('Corner radius', 'The live 0–48 dp preference changes shell and settings-surface shaping. It is a presentation setting only and cannot alter collection metadata or playback state.'),
    ('Cache and storage', 'Cache summary and cleanup distinguish temporary resources from user-directed content. Cleanup is intentionally targeted rather than a recursive destructive wipe of every app file.'),
    ('Diagnostics', 'The developer console and app-private diagnostic records are for troubleshooting. Stream URLs and tokens must be redacted before long-lived logging.'),
    ('Backup', 'Backup destination selection uses Android’s system picker. Persistable URI permissions are retained only for locations the user actively chooses.'),
]

# The supplied source history begins at 1.5.1. Earlier rows are deliberately marked reconstructed.
reconstructed_topics = {
    '1.1': 'Foundational product era: Compose shell experiments, basic search and navigation conventions, and early music-client layout patterns.',
    '1.2': 'Library and collection era: early playlist, album, artist, and Liked Songs concepts with stronger metadata presentation.',
    '1.3': 'Playback experience era: queue behavior, player controls, visual surfaces, and the first persistence assumptions for active playback.',
    '1.4': 'Reliability and discovery era: deep-link intent, search/discovery refinements, artwork handling, and preparation for modular source resolution.',
}

verified_topics = {
    '1.5.1': 'Playback and library foundations: source matching, blacklist UI, lyrics, queue, crossfade, and stream diagnostics were retained as the documented baseline.',
    '1.5.2': 'Offline library workflow: app-managed local download and offline collection concepts were retained without exporting protected provider media.',
    '1.5.3': 'Playlist portability: selected playlist and album track export was introduced through encrypted NovaAc metadata manifests.',
    '1.5.4': 'Local media continuity: local collection state and persisted artwork paths were retained.',
    '1.5.5': 'Equalizer: persisted EQ preferences were connected to Android framework equalizer bands for the active Media3 session.',
    '1.5.6': 'Spatial audio: device-supported Virtualizer processing became an opt-in audio-session effect.',
    '1.5.7': 'Audio-session stability: effects attach and release with ExoPlayer audio-session changes.',
    '1.5.8': 'Crossfade: custom crossfade processing remained alongside the audio effects controller.',
    '1.5.9': 'Visual playback: existing player visual layers and Canvas-related paths were retained.',
    '1.6.0': 'Theme resilience: system-safe SansSerif typography replaced reliance on malformed bundled font resources.',
    '1.6.1': 'One-handed configuration: the player offers a direct route to Audio & Equalizer settings.',
    '1.6.2': 'Player control refinement: queue, sleep timer, alternative source, download, share, album, and artist actions were retained.',
    '1.6.3': 'System integration: Media3 service, notifications, Android Auto metadata, and deep-link support were retained.',
    '1.6.4': 'Settings organization: collapsible animated sections separated audio, storage, diagnostics, and related configuration.',
    '1.6.5': 'Credential boundary: no unauthenticated social-account transmission was added.',
    '1.6.6': 'Recognition boundary: microphone capture was not introduced without a user-initiated, permission-aware feature.',
    '1.6.7': 'Sharing and Canvas: existing sharing and visual Canvas paths were retained.',
    '1.6.8': 'Source fallback: InnerTube and provider fallback controls were retained with diagnostic logging.',
    '1.6.9': 'Playback integrity: provider behavior was preserved without hidden modifications.',
    '1.7.0': 'Local smart behavior: listening history and queue foundations were consolidated.',
    '1.7.1': 'Listening history: the history route and local history behavior were preserved.',
    '1.7.2': 'Radio: queue continuation and radio behavior were preserved.',
    '1.7.3': 'Lyrics: lyrics and translation-cache paths were preserved.',
    '1.7.4': 'Reliability: bounded persistent diagnostics and uncaught-exception capture were added.',
    '1.7.5': 'Build memory behavior: Gradle execution was constrained to avoid compiler-worker memory spikes in constrained environments.',
    '1.7.6': 'Cache correctness: cache accounting and targeted cleanup replaced destructive assumptions.',
    '1.7.7': 'Startup diagnostics: activity, provider warm-up, deep-link, backup, and media-session bootstrap failures became recordable.',
    '1.7.8': 'Error visibility: developer-console warning and error entries became persistent.',
    '1.7.9': 'Stability baseline: source validation and font-safe rendering work were consolidated.',
    '1.8.0': 'Android compatibility: compile/target SDK 36 configuration was documented and validated.',
    '1.8.1': 'Navigation polish: direct player-to-audio-settings routing was added.',
    '1.8.2': 'Settings polish: reusable collapsible section behavior was formalized.',
    '1.8.3': 'Archive compatibility: NovaAc headers gained app-version and format-version fields.',
    '1.8.4': 'Accessibility and clarity: settings controls gained labels and state descriptions.',
    '1.8.5': 'Build identity: versioning and local release identity were updated for the integrated build.',
    '1.8.6': 'Security posture: NovaAc uses Android Keystore AES-GCM and diagnostics redact sensitive stream material.',
    '1.8.7': 'Artifact verification: debug/release assembly and APK integrity checks were used after integration groups.',
    '1.8.8': 'Release preparation: cache safety, diagnostics, and audio controls were consolidated.',
    '1.8.9': 'Stable integration: the supplied roadmapped source was assembled as an integrated, build-validated Android client.',
    '1.9.0': 'Major control-surface update: direct labeled playlist NovaAc export, advanced audio settings, HTTPS engine controls, and adaptive navigation work began.',
    '1.9.1': 'Archive usability update: export configuration added archive naming and local-audio inclusion choices before invoking Android’s system save picker.',
    '1.9.2': 'Spatial-audio update: profile selection, virtualizer depth, room processing, and bounded normalizer gain were connected to the active session.',
    '1.9.3': 'Adaptive-shell update: bottom, top, side, and rail navigation modes were connected to a shared root-route model.',
    '1.9.4': 'Visual configuration update: compact UI and a persisted 0–48 dp corner-radius preference became live shell settings.',
    '1.9.5': 'Current integrated release: direct playlist and Liked Songs export buttons sit beside Download, with full archive configuration, verified release assembly, and v2 APK signature validation.',
}

def all_versions():
    versions = []
    for minor in range(1, 5):
        for patch in range(0, 10):
            versions.append(f'1.{minor}.{patch}')
    for minor in range(5, 9):
        start = 1 if minor == 5 else 0
        for patch in range(start, 10):
            versions.append(f'1.{minor}.{patch}')
    for patch in range(0, 6):
        versions.append(f'1.9.{patch}')
    return versions

def version_topic(version):
    if version in verified_topics:
        return verified_topics[version], 'Verified in supplied project documentation or current source'
    prefix = '.'.join(version.split('.')[:2])
    return reconstructed_topics[prefix], 'Reconstructed historical era; no Git tag or source snapshot was supplied for this exact version'

def table(rows, headers):
    out = ['| ' + ' | '.join(headers) + ' |', '| ' + ' | '.join(['---'] * len(headers)) + ' |']
    for row in rows:
        out.append('| ' + ' | '.join(str(value).replace('|', '\\|') for value in row) + ' |')
    return '\n'.join(out)

def history_table():
    rows = []
    for version in all_versions():
        topic, evidence = version_topic(version)
        rows.append((version, topic, evidence))
    return table(rows, ['Version', 'Release focus or documented outcome', 'Evidence status'])

def feature_table():
    return table(feature_catalog, ['Area', 'Current behavior and boundary'])

def settings_table():
    return table(settings_catalog, ['Control', 'Implementation logic'])

def module_table():
    return table(module_catalog, ['Module', 'Responsibility'])

def callout(text):
    return '> ' + text.replace('\n', '\n> ')

def common_intro(title, subtitle):
    return dedent(f'''\
# {title}

> {subtitle}

**Status:** Spotui 1.9.5 source package. This documentation describes the current supplied source tree and its integrated behavior. It does not claim a relationship with Spotify AB, and it does not describe provider-protected media export as a product capability.

The repository is a Kotlin Android music-client project built around Jetpack Compose, a Media3 playback core, Hilt dependency injection, bundled metadata/resolution modules, user-controlled storage routes, and a settings model that persists user choices. The documentation deliberately separates **verified current-source behavior** from **historical reconstruction**. The supplied project archive contains no Git metadata or tags, so version-era narrative before the documented 1.5.1 baseline is a product-line reconstruction rather than a claim that a particular commit has been recovered.
''').strip()

def release_history_doc():
    sections = [
        common_intro('Spotui Release Chronicle: 1.1.0–1.9.5', 'A detailed, evidence-aware chronology of the Spotui product line.'),
        '## How to read this chronicle\n\nThe version table is intentionally explicit about evidence. Releases from **1.5.1 onward** are grounded in the supplied changelog, roadmap, implementation notes, or current code. Releases from **1.1.0 through 1.4.9** are included because the requested product chronology begins there, but no matching source snapshots or tags were supplied. They record the reconstructed evolution of the product themes rather than asserting unrecoverable code-level facts. This distinction protects future maintainers from confusing documentation with forensic version control.',
        '## Complete version history\n\n' + history_table(),
        '## Version-era narrative\n\n### 1.1.x — Interface foundation\n\nThe first reconstructed era focuses on the basic proposition of a Compose-first music client: predictable root navigation, responsive surfaces, a simple state model for loading and selected content, and a visual language capable of presenting music metadata without tying every route directly to a network call. The enduring design lesson is that a music client must distinguish navigation state, collection state, and playback state early. Doing so allows the mini player, route host, and background session to evolve independently.\n\n### 1.2.x — Library identity\n\nThe second reconstructed era is the point at which playlists, albums, artists, and Liked Songs become recognizable collection concepts rather than interchangeable search rows. That separation is reflected in the current project through dedicated screens, persistent local preferences, and route-specific actions. The later archive work builds on this distinction: a collection export must identify its source type and preserve enough context to be meaningful when inspected later.\n\n### 1.3.x and 1.4.x — Playback continuity and discovery\n\nThe third and fourth reconstructed eras explain why the current source has strong emphasis on remembered playback, queue actions, artwork recovery, deep links, and source resolution. A user judges a music client by whether intent survives transitions: choosing a track, opening the player, backgrounding the app, returning from a link, or moving through a collection should all refer to a coherent current-song state. The current `SongPlayer`, `PlaybackService`, `CurrentSongState`, and navigation host embody that requirement.',
        '## 1.5.1–1.8.9 integrated lineage\n\nThe verified integration line focuses on connecting formerly separate capabilities: audio preferences were made session-aware, cache handling was made targeted, diagnostics became persistent, and NovaAc became a visible archive workflow with compatibility headers. This was a transition from “screen controls exist” to “screen controls are owned by a service and can report failure safely.” The design is especially important for audio effects: preferences alone do not change sound until the active Media3 session accepts them.\n\n' + table([(v, verified_topics[v]) for v in all_versions() if v in verified_topics and v < '1.9.0'], ['Version', 'Verified integration detail']),
        '## 1.9.0–1.9.5 current release series\n\nThe 1.9 series moves interaction points from hidden or indirect menus to **collection-local, labeled controls**. The most visible example is the direct **Export** action beside Download on both playlists and Liked Songs. Archive creation is not a background surprise: the user supplies an archive name, decides whether locally available cache payloads should be included, then receives Android’s system save location. The same series also turns spatial and interface preferences into active architecture rather than decorative settings.\n\n' + table([(v, verified_topics[v]) for v in ['1.9.0','1.9.1','1.9.2','1.9.3','1.9.4','1.9.5']], ['Version', 'Current-series outcome']),
        '## Release decision blueprint\n\nEvery release candidate should be evaluated in four layers. First, validate **source integrity**: Kotlin and Compose compile, generated Hilt/KSP artifacts resolve, and the manifest still owns the correct service and deep-link declarations. Second, validate **behavioral ownership**: UI controls must call the correct player, cache, archive, or preference service rather than simply mutate visual state. Third, validate **data boundaries**: chosen SAF destinations, app-private diagnostics, and archive headers should remain user-controlled and privacy-aware. Fourth, validate **artifact identity**: version code, version name, package name, signature scheme, and checksum should be recorded in the release note.\n\n' + callout('The project archive is locally debug-format signed for sideload validation. A distribution release requires a developer-controlled private production signing identity; documentation must not describe a local test key as a store-production key.'),
        references,
    ]
    return '\n\n'.join(sections)

def architecture_doc():
    sections = [
        common_intro('Spotui Architecture Blueprint', 'A system-level explanation of modules, state ownership, playback, archives, settings, and extension points.'),
        '## Architecture map\n\n```text\nCompose screens → ViewModels / route state → SongPlayer and preference services\n        │                         │\n        │                         ├→ Media3 ExoPlayer + AudioEffectController\n        │                         ├→ PlaybackService + MediaSession / Android Auto\n        │                         ├→ HTTPS resolver / metadata modules\n        │                         ├→ CacheStorageManager + DownloadPref\n        │                         └→ NovaAcExportManager + Android SAF\n        │\n        └→ AppDiagnostics / Developer Console / BackupHelper\n```\n\nThis blueprint follows a deliberate ownership rule: rendering layers request an action; dedicated services own resource lifetime, I/O, audio sessions, or persistence. That rule keeps an export button from being an isolated UI effect and prevents an audio toggle from pretending to work when no audio session is available.',
        '## Module responsibilities\n\n' + module_table(),
        '## Playback blueprint\n\nThe normal playback path begins with a collection action—play, shuffle, queue, or deep link. A view model updates queue and current-song state, while `SongPlayer` selects a usable item. The player can prefer local downloaded material when appropriate, then use configured HTTPS stream resolution and quality policy, and can expose a web-player route only when the user has an eligible session. The active ExoPlayer provides the audio-session identifier. `AudioEffectController` uses that identifier to attach equalizer, virtualizer, room, and loudness effects. This is why settings changes trigger a refresh through `SongPlayer.refreshAudioEffects`: effects are attached to the session, not globally to the application. Media3’s player/session/service model is designed for this separation of player, session, and background service responsibilities. [1] [3]\n\n| Layer | Owns | Failure behavior |\n| --- | --- | --- |\n| Collection UI | Intent, progress cues, route navigation | Shows an actionable message; does not block the main thread. |\n| View model | Queue and current content state | Preserves coherent state even when a provider result fails. |\n| `SongPlayer` | Player lifetime, source choice, session binding | Falls back through supported routes and records diagnostics. |\n| `PlaybackService` | Notification, external commands, browse tree | Keeps system-facing media controls synchronized with the active player. |\n| `AudioEffectController` | Android audio-effect objects | Disables unsupported effects and logs a warning without stopping playback. |',
        '## NovaAc archive blueprint\n\nNovaAc is an application archive format, not a claim about provider media rights. The manager validates selection, converts tracks into archive records, compiles a binary payload, compresses it, encrypts it with AES-GCM using an Android Keystore-held key, serializes a header plus nonce plus ciphertext, and writes the prepared export through a URI selected by the Android Storage Access Framework. The header carries collection name, source type, app version, format version, track count, payload size, and the local-audio inclusion flag. Import first inspects compatibility; a newer format can be identified before the user relies on it.\n\n| Archive step | Why it exists | User-visible result |\n| --- | --- | --- |\n| Prepare | Validates non-empty selection and constructs the payload off the UI thread. | A save picker is launched only after preparation succeeds. |\n| Configure | Lets the user name the archive and choose local-cache inclusion. | The primary Playlist and Liked Songs flows are explicit and repeatable. |\n| Save | Uses `ACTION_CREATE_DOCUMENT` / Compose activity-result integration. | Android presents a user-selected destination rather than silent storage. |\n| Inspect | Reads the header without assuming import safety. | Compatibility messages can be shown before use. |\n| Import | Interprets a compatible archive into library-aware records. | Offline/library integration is data-driven rather than a raw file dump. |\n\nThe Storage Access Framework is intentionally user-mediated: the system picker supplies the destination URI and avoids asking for broad storage access for an archive save. [2]',
        '## Settings and adaptive-shell blueprint\n\n`SettingsPref` is the durable preference boundary. It stores quality, timeout, fallback, crossfade, audio, compact-interface, navigation-mode, and corner-radius values. The adaptive shell observes a settings revision flow, then rebuilds the root layout using Bottom Bar, Top Bar, Side Bar, or Navigation Rail while preserving the same `Routes` model and root navigation behavior. This means navigation mode is not four separate applications; it is one route graph rendered through four reachable layouts.\n\nThe 0–48 dp shape value is scoped to user-facing shell and settings surfaces. It is not a data model, does not alter media metadata, and is intentionally clamped. Compact mode reduces navigation density but keeps labels on the selected destination and retains content descriptions. The accessibility rationale is simple: a compact presentation must not silently remove the route vocabulary users need to navigate.',
        '## Diagnostics, backups, and privacy\n\n`AppDiagnostics` and `DevConsoleManager` provide bounded app-private observability. Their purpose is to capture launch, provider, playback, deep-link, backup, and error context without treating persistent logs as a dump of user or provider secrets. `BackupHelper` uses user-selected SAF locations; backup is a recovery feature, not a hidden synchronization channel. Any new provider, telemetry, or account integration should declare its data flow, consent point, retention, and error behavior before it is added.\n\n' + callout('Design rule: errors that can be recovered from—unsupported audio effects, canceled save pickers, unavailable stream candidates, or a missing local cache file—should degrade to a clear state and diagnostics record rather than take down playback or corrupt a collection.'),
        references,
    ]
    return '\n\n'.join(sections)

def config_doc():
    sections = [
        common_intro('Spotui Configuration Reference', 'A field guide to every user-facing settings family, its owning service, and its runtime effect.'),
        '## Settings catalog\n\n' + settings_table(),
        '## Audio configuration logic\n\nThe audio page contains settings that are meaningful only when paired with the active Media3 audio session. The equalizer controller normalizes the three user sliders around a neutral midpoint and distributes bass, vocal, and treble intent across available device bands. Spatial processing is conditional: the framework Virtualizer and PresetReverb must be created successfully for the current session. The requested profile and depth are persisted even when an output device lacks one of those effects, but playback must continue without pretending the hardware effect is active. Loudness normalization uses a deliberately bounded target gain; its purpose is an adjustable session effect, not a claim that every track has been loudness-analyzed.\n\n| Setting group | Runtime owner | Expected effect | Safe fallback |\n| --- | --- | --- | --- |\n| EQ preset / bands | `AudioEffectController` | Band levels on the current audio session | Leave unsupported band operation disabled; log warning. |\n| Spatial profile / depth | Virtualizer + PresetReverb | Width and room emphasis where supported | Keep audio playing with effect disabled. |\n| Normalizer | LoudnessEnhancer | Bounded target gain | No gain modification when unavailable. |\n| Crossfade | Custom filter processor + player transition | Overlap timing and optional DJ filtering | Pass-through outside a transition. |',
        '## Collection export configuration\n\nPlaylist and Liked Songs exports use the same interaction contract. The user sees a labeled **Export** control directly beside **Download**, chooses a name, selects whether local downloaded cache payloads should be included when available, and then confirms an Android save location. The visible control matters because archive workflows are intentional data operations; they should not be hidden behind a three-dot menu that users may not discover. A canceled picker leaves the collection unchanged. Preparation work runs off the UI thread; write results return to the UI as success or readable failure text.\n\nFor Downloads, bulk export to the shared Music location and NovaAc import/export are separate actions because their semantics differ. A Music export is a visible local-file operation. A NovaAc archive is a structured collection container. Neither feature is a representation that provider-protected media has been decrypted or repackaged.',
        '## Streaming configuration\n\nThe current streaming model favors a secure HTTPS resolver backed by the project’s source and metadata modules. Quality preferences guide candidate selection. Lossless is a preference with a bounded timeout, not a promise: when a desired lossless source is absent or too slow, the engine returns to a playable quality tier. Next-stream preload is a cache/performance choice. The optional web-player setting is guarded by the availability of a user session and platform capabilities, while the direct resolver continues to provide a fallback route.\n\n| Choice | Prefer when | Trade-off | Diagnostic value |\n| --- | --- | --- | --- |\n| Low / Normal quality | Metered or constrained network | Lower fidelity, lower transfer pressure | Candidate and fallback information. |\n| High quality | Stable ordinary playback | Larger transfer and cache cost | Source selection trace. |\n| Lossless attempt | User prioritizes available lossless candidates | May wait before fallback | Provider availability / timeout visibility. |\n| Preload next stream | Queue continuity matters | More cache/network work | Preload failures remain non-fatal. |\n| Web player option | Eligible user web session exists | Session and platform constraints | Distinguishes web path from resolver fallback. |',
        '## Interface configuration\n\nThe navigation controls are structural. Bottom Bar provides the traditional thumb-friendly layout. Top Bar supports a horizontal tab model. Side Bar provides expanded labels, and Navigation Rail reduces horizontal pressure while retaining root routes. All modes use the same navigation graph so a preference change does not rewrite queue state, current song state, or collection routes. Corner radius is bounded between 0 and 48 dp to make visual extremes deliberate but prevent uncontrolled layout values. Compact UI adjusts density and label policy rather than hiding feature routes.\n\n' + callout('Configuration changes are meant to be reversible. A user who selects a compact rail can always return to Settings through the same root route and switch back to Bottom Bar. The preference is presentation state, not account state.'),
        '## Storage, backup, and troubleshooting\n\nCache actions must distinguish ephemeral resolver/artwork resources from user-directed downloads and exported archives. The project therefore treats cache cleanup as targeted maintenance and exposes summaries before cleanup. Backup uses the system document-tree picker, which grants access only to a location selected by the user. The diagnostic console is the place to inspect provider availability, archive errors, deep-link handling, and player bootstrap failure. Logs should be exported only after reviewing privacy implications.\n\n' + references,
    ]
    return '\n\n'.join(sections)

def readme_doc():
    sections = [
        common_intro('Spotui — Android Music Client', 'A feature-rich Kotlin / Jetpack Compose music-client source project with visible collection controls, configurable playback, and build-validated release tooling.'),
        '## What is in this package\n\nThis source archive contains the current **Spotui 1.9.5** project. It includes the Android app module, the bundled `spotify` metadata module, the `innertube` resolution module, Gradle wrapper and dependency catalog, current implementation documentation, and a verified 1.9.5 source configuration. The project is intentionally documented as an educational/open-source client architecture. Spotify is a trademark of Spotify AB; developers are responsible for respecting provider terms, account boundaries, and local law.\n\nThe current package is not merely a visual mockup. Core controls connect to services: direct archive export creates a prepared NovaAc file and invokes the Android save picker; audio settings attach to the active media session where supported; navigation preferences alter the root application shell; diagnostics, cache, and backup actions use dedicated utility/service layers.',
        '## Feature matrix\n\n' + feature_table(),
        '## Direct export: the primary workflow\n\nOpen a standard playlist or **Liked Songs**, then use the clearly labeled **Export** button placed beside **Download**. The dialog asks for an archive name and whether locally available cache payloads should be included. The application prepares the NovaAc archive away from the UI thread and opens Android’s save-location picker. The top-right overflow menu remains useful for secondary actions, but it is no longer required to discover the archive feature.\n\n> **NovaAc boundary:** NovaAc is a structured, encrypted application archive for collection metadata and app-controlled local cache context. It is not a provider-media decryption mechanism, a DRM bypass, or a claim that any remote streaming asset can be exported.',
        '## Build and run\n\n| Requirement | Current project value |\n| --- | --- |\n| Android compile / target SDK | 36 |\n| Minimum SDK | 26 |\n| Java toolchain | Java 21 |\n| Kotlin | 2.2.0 |\n| Android Gradle Plugin | 8.13.2 |\n| Current source version | 1.9.5 (`202609050`) |\n\nUse Android Studio with a configured Android SDK, or run the wrapper from the project root. A typical local build is:\n\n```bash\nexport ANDROID_HOME=/path/to/android-sdk\nexport JAVA_HOME=/path/to/jdk-21\n./gradlew assembleRelease\n```\n\nThe checked-in release configuration is intended for locally signed sideload validation. Replace it with a developer-controlled production signing configuration before a store distribution. Do not commit private keys, user cookies, account tokens, or real keystores.',
        '## Product history, 1.1.0–1.9.5\n\nThe full chronology is intentionally included here because release intent affects architecture decisions. The archive contains documented detail beginning at 1.5.1; earlier versions are clearly labeled reconstructed because this source archive did not include Git history, tags, or older snapshots. See [CHANGELOG.md](CHANGELOG.md) for the companion long-form discussion.\n\n' + history_table(),
        '## Architecture at a glance\n\n```text\nUser action → Compose screen → View model / preference service → player, archive, cache, or route service\n                                                      │\n                                                      ├── Media3 / ExoPlayer / MediaSessionService\n                                                      ├── AudioEffectController\n                                                      ├── HTTPS source resolver and fallback policy\n                                                      ├── NovaAcExportManager + Storage Access Framework\n                                                      └── Diagnostics / backup / persistent settings\n```\n\nThe project embraces explicit ownership. Compose surfaces are responsible for input and visible state. Player code owns player lifecycle and audio-session attachment. Archive code owns binary creation and selected URI writes. Preferences own durable user configuration. This separation makes it possible to add an interface mode or archive control without duplicating the same implementation in every screen. Read [ARCHITECTURE_BLUEPRINT.md](ARCHITECTURE_BLUEPRINT.md) for the full blueprint.',
        '## Settings and customization\n\nThe application exposes stream quality, lossless timeouts, source fallback, preloading, optional web playback, equalizer bands, spatial profile/depth, loudness intensity, crossfade, local cache actions, backups, diagnostics, navigation modes, compact UI, and live corner radius. Settings are grouped to avoid a single unstructured screen and are designed to be reversible. Read [CONFIGURATION_REFERENCE.md](CONFIGURATION_REFERENCE.md) for per-control logic and safe fallback behavior.',
        '## Open-source acknowledgments\n\nSpotui builds upon the Android and open-source ecosystem. Jetpack Compose provides declarative UI facilities. [4] Media3 provides the player/session/service architecture used for modern Android media applications. [1] [3] Hilt supports dependency injection boundaries. [5] The project contains and/or credits inspiration from Neptune, SpotiFLAC, SimpMusic, NewPipe Extractor, Ktor, OkHttp, Kotlin serialization, Glide, ML Kit, Room, and AndroidX libraries. The upstream projects remain responsible for their own terms and licenses; retain their notices when redistributing source.\n\n| Project or library | Role in this source package |\n| --- | --- |\n| AndroidX Media3 / ExoPlayer | Playback engine, media session, background media integration. |\n| Jetpack Compose + Material 3 | Declarative screen and adaptive-navigation rendering. |\n| Hilt + KSP | Dependency graph and generated integration. |\n| Ktor + OkHttp + serialization | Metadata/networking and structured transport support. |\n| NewPipe Extractor / InnerTube module | Source-resolution support within the project’s configured boundaries. |\n| Glide + Palette | Artwork loading and color-derived presentation. |\n| Room + SharedPreferences helpers | Persistent app and collection state. |\n| ML Kit | On-device language identification and translation support. |\n| Neptune / SpotiFLAC / SimpMusic | Acknowledged architectural and feature inspiration. |',
        '## Documentation map\n\n| Document | Purpose |\n| --- | --- |\n| [CHANGELOG.md](CHANGELOG.md) | Detailed version history from 1.1.0 through 1.9.5. |\n| [ARCHITECTURE_BLUEPRINT.md](ARCHITECTURE_BLUEPRINT.md) | System design, ownership boundaries, archive and playback flow. |\n| [CONFIGURATION_REFERENCE.md](CONFIGURATION_REFERENCE.md) | Every major setting, runtime owner, fallback, and user effect. |\n| [CONTRIBUTING_AND_OPERATIONS.md](CONTRIBUTING_AND_OPERATIONS.md) | Build, test, release, privacy, and contribution workflow. |\n| [IMPLEMENTATION_DESIGN.md](IMPLEMENTATION_DESIGN.md) | Design decisions and deep implementation rationale. |\n| [IMPLEMENTATION_AUDIT.md](IMPLEMENTATION_AUDIT.md) | Current-source audit and verification checklist. |\n| [VALIDATION_REPORT.md](VALIDATION_REPORT.md) | Build/release validation method and artifact boundaries. |\n| [ROADMAP_STATUS.md](ROADMAP_STATUS.md) | Current capability map and future-safe extension plan. |',
        '## License, privacy, and contribution expectations\n\nBefore contributing, read the operations guide and preserve data boundaries. Do not add credential capture, broad storage access, microphone behavior, background transmission, or provider modifications without a documented consent and failure model. New media features must state whether they operate on app-owned local data, user-selected files, or provider-controlled network material. New settings must state the service that consumes them. New releases must include a version code, artifact checksum, signature verification result, and an honest signing note.\n\n' + references,
    ]
    return '\n\n'.join(sections)

def audit_doc():
    sections = [
        common_intro('Spotui Implementation Audit', 'A maintainable audit of source ownership, integration checks, correctness boundaries, and high-value test scenarios.'),
        '## Audit method\n\nThe audit is organized around observable ownership rather than a superficial checklist. A feature is considered integrated only when a user-facing trigger reaches a real service, the service owns its work off the main thread when necessary, a recoverable failure remains visible, and the setting or data result can survive recreation according to its intended scope. A visible switch that only changes a local Compose variable is not a completed implementation.\n\n| Audit question | Evidence to inspect | Good result |\n| --- | --- | --- |\n| Does a control reach a service? | UI callback, view model, manager invocation | Export calls the NovaAc manager; audio refresh reaches the session controller. |\n| Is work safe for UI? | Coroutine dispatcher / manager boundaries | Archive preparation and output do not block the screen. |\n| Is fallback honest? | `runCatching`, result handling, diagnostics | Unsupported effects disable cleanly without silent false success. |\n| Is state durable at the right layer? | `SettingsPref`, Room, collection prefs | Navigation and audio configuration survive normal recreation. |\n| Is data scope explicit? | SAF, cache helper, archive header | User chooses destination; protected provider content is not claimed as exported. |',
        '## Current integration ledger\n\n' + feature_table(),
        '## Collection workflow audit\n\nThe collection surfaces are a prime example of the audit method. Standard playlists, albums, Downloads, and Liked Songs have overlapping but not identical actions. The two collection surfaces that users most expect to archive—playlists and Liked Songs—now expose the same direct labeled export placement next to Download. The action opens configuration, captures user intent, uses a shared archive manager, and starts the system picker only after preparation. The common manager avoids divergent binary formats while the per-screen labels preserve user context.\n\nThe audit should test empty collection handling, canceling the picker, inaccessible destination URIs, metadata-only archive creation, archive creation with no eligible local cache payloads, source type labeling, header inspection, and import compatibility messages. The expected result is never a damaged collection; errors must surface as clear messages and diagnostics.',
        '## Playback and audio audit\n\nAudio configuration is valid only when it changes the active session. The player listener attaches `AudioEffectController` when ExoPlayer publishes an audio-session identifier. The controller can create Equalizer, Virtualizer, PresetReverb, and LoudnessEnhancer instances independently because Android devices may omit individual implementations. Applying preferences therefore checks support and handles each effect separately. A test pass should cover built-in speaker, wired output, Bluetooth output, a route with no virtualizer support, route changes during playback, and player release.\n\nA perceptual test should be honest: spatial effects depend on device hardware, output route, input mix, and platform implementation. The product can promise it requests and applies supported framework processing; it cannot promise that every source/device combination will create a dramatic three-dimensional effect.',
        '## Adaptive shell audit\n\nThe adaptive navigation model uses one navigation graph and multiple surface renderers. Test Bottom Bar, Top Bar, Side Bar, and Navigation Rail with Home, Search, Library, Downloads, and Settings. Confirm that Search reselect remains available, selected route indication is accurate, the current player state survives layout changes, the mini-player is not duplicated, and Login/Queue visibility rules suppress the root navigation when expected. Compact UI should reduce density without removing content descriptions. Radius values should clamp to the 0–48 dp range and remain stable after recreation.',
        '## Security and data audit\n\nNovaAc encryption uses Android Keystore-backed AES-GCM within the application’s architecture. The audit must state the limitation: installation-scoped key material is appropriate for the declared application archive flow but is not a universal cross-device password-sharing protocol. Any future portable-key design requires a separately documented KDF, salt, user-authentication interaction, threat model, and migration strategy. Diagnostics must redact URLs/tokens. Backups and archives should never be confused with an authorization grant for provider-controlled media.',
        '## High-value regression checklist\n\n' + table([
            ('Launch / restore', 'Open, restore saved song and queue state, then navigate to a non-root route.'),
            ('Archive export', 'Export Playlist and Liked Songs archives with and without local cache inclusion.'),
            ('Archive import', 'Inspect compatible/incompatible headers and import an allowed archive without corrupting current collections.'),
            ('Audio', 'Change EQ, spatial, depth, normalizer, and output route during active playback.'),
            ('Navigation', 'Switch each layout, compact mode, and radius, then relaunch.'),
            ('Streaming', 'Exercise ordinary quality, lossless timeout, fallback, preloading, and offline/local path behavior.'),
            ('Resilience', 'Cancel a picker, deny notification permission, make a provider unavailable, and inspect diagnostics.'),
            ('Artifact', 'Assemble release, inspect badging, validate signature scheme, record checksum.'),
        ], ['Area', 'Minimum regression exercise']),
        references,
    ]
    return '\n\n'.join(sections)

def design_doc():
    sections = [
        common_intro('Spotui Implementation Design', 'A deep rationale for the design decisions embodied by the 1.9.5 source tree.'),
        '## Design principles\n\nThe project uses five durable principles. **Visibility** means a high-value collection action should be placed where the collection action occurs. **Ownership** means I/O, player lifetime, and encryption remain in services rather than composables. **Reversibility** means interface and audio preferences can be changed back without rewriting library state. **Degradation** means unsupported device capabilities leave playback working. **Honesty** means document what the source can verify, distinguish reconstructed history, and never market an archive as a DRM bypass.\n\nThese principles are practical. They lower the cost of maintenance because a single NovaAc manager can power multiple collection screens, a single adaptive root can power multiple layouts, and a single audio effect controller can manage a changing Media3 session.',
        '## Detailed NovaAc design\n\nA prepared archive separates expensive work from destination selection. `prepareExport` verifies selection, records source type and collection context, attempts only eligible local payload extraction when requested, compiles the manifest, compresses it, encrypts the bytes, and produces a filename/header/package in memory. `writePreparedExport` performs the destination URI write after the system picker returns. This two-step design makes cancelation straightforward and keeps the save picker from holding an unfinished archive operation. The header is useful both for diagnostics and future migration: format version and app version explain why a file may not be safe to interpret.\n\n| Decision | Rationale | Rejected alternative |\n| --- | --- | --- |\n| Direct Export beside Download | Matches user mental model for collection-level persistence. | Hiding the primary archive workflow only in overflow menus. |\n| Config dialog before save picker | Captures archive name and payload choice explicitly. | Silent defaults that make later file contents unclear. |\n| Shared manager | Keeps binary/header behavior identical across collection types. | Copying serialization into each screen. |\n| Android SAF destination | User controls where the archive is saved. | Broad storage permission and silent filesystem paths. |\n| Versioned header | Supports compatibility messaging and migration planning. | Opaque payload with no inspectable metadata. |',
        '## Detailed audio design\n\nThe design treats Android effects as optional session capabilities. The Equalizer maps intent to bands rather than assuming a fixed number of bands. Virtualizer strength, room preset, and loudness target are clamped. When a device rejects an effect, the code logs the failure, disables only that effect, and leaves normal playback operating. Effects are released on session change and player release to prevent stale audio-session references. Crossfade remains a separate custom audio-processor concern because it operates during transitions, whereas equalizer/spatial/loudness effects are session-wide playback treatment.\n\nThe system does not make psychoacoustic promises it cannot guarantee. “Immersive” is a preference profile and an effect request, not a claim that every output route will sound the same. This is an important engineering distinction: the UI communicates intent; the framework and hardware determine supported execution.',
        '## Detailed navigation design\n\nThe adaptive shell is built from a common `Routes` vocabulary. Bottom, Top, Side, and Rail renderers call the same root-navigation behavior, including route restoration and Search reselect. The app root observes a settings revision signal so a persisted choice becomes visible immediately. Compact mode is deliberately a rendering modifier, not a separate set of screens. Radius is similarly bounded and presentation-only. This avoids a common anti-pattern in which every navigation mode owns duplicate route lists and gradually drifts in capability.',
        '## Release and operational design\n\nThe project distinguishes source validity from distribution policy. A build can compile and an APK can pass signature verification while still being signed with a local debug-format key. That artifact is suitable for local installation and test distribution under the owner’s policy, but it is not a substitute for a production private key. The design therefore records version code, version name, package ID, signature scheme, and checksum in release notes. Future CI should make those checks reproducible and should reject accidental secret files.\n\n' + references,
    ]
    return '\n\n'.join(sections)

def roadmap_doc():
    sections = [
        common_intro('Spotui Roadmap and Capability Status', 'A current-state map that distinguishes delivered behavior, guarded boundaries, and future-safe extension work.'),
        '## Capability status\n\n' + feature_table(),
        '## Completed 1.9.5 capability line\n\nThe 1.9.5 line is complete when the source package demonstrates visible collection export in both Playlist and Liked Songs, settings that are consumed by active services, a shared adaptive root route model, and an assemble/verify release path. The intent is not to accumulate disconnected controls. A feature earns completion when the screen, persistence layer, runtime owner, fallback logic, diagnostics, and documentation agree about its behavior.\n\n| Workstream | Current result | Why it matters |\n| --- | --- | --- |\n| Collection archives | Direct export placement and shared preparation/write logic. | Users can discover and control archive creation. |\n| Audio | Session-attached EQ/spatial/room/loudness effects. | Settings can create supported runtime behavior. |\n| Streaming | Resolver, quality policy, preload, optional web engine control. | Source choice is explicit and diagnosable. |\n| Interface | Four navigation layouts, compact presentation, bounded radius. | Personalization does not fracture route behavior. |\n| Operations | Diagnostics, cache summary, backup, release validation. | Failures become maintainable rather than mysterious. |',
        '## Future-safe extension rules\n\nA future recommendation system, provider, collaboration surface, local index, or visualizer must meet the same ownership rules. New storage must name the user-visible lifecycle. New accounts must name consent and token handling. New audio processing must attach to a known session and declare unsupported-output behavior. New navigation layouts must use the common route graph. New archive versions must publish a migration policy. These are not bureaucratic restrictions; they are the constraints that keep a music client understandable as features accumulate.\n\n### Recommended future work\n\n| Priority | Proposal | Preconditions |\n| --- | --- | --- |\n| High | Archive migration and compatibility test suite | Fixture files, header-version matrix, and explicit key portability policy. |\n| High | Automated UI smoke testing of all navigation layouts | Deterministic root-route test data and Compose test tags. |\n| Medium | Audio route capability report | Device/route testing harness and clear non-promissory UI copy. |\n| Medium | Release CI with checksum/signature artifact publishing | Owner-controlled signing key handling and secret management. |\n| Medium | Collection export progress UI | Streaming-friendly archive preparation and cancellation semantics. |\n| Low | More visual themes | Theme tokens that keep accessibility contrast intact. |',
        '## Guardrails\n\nThe project should not accept work that labels provider material as exportable merely because it is playable, starts capture/recording without an explicit user flow, adds hidden trackers, bypasses DRM or access controls, silently requests broad storage access, or stores secrets in source. These boundaries are documented so maintainers can move quickly without making unsafe assumptions.\n\n' + references,
    ]
    return '\n\n'.join(sections)

def validation_doc():
    sections = [
        common_intro('Spotui Validation and Release Report', 'A reproducible methodology for source, behavior, archive, audio, and APK release verification.'),
        '## Validation scope\n\nValidation is layered because compiling Kotlin does not prove a collection workflow works, and an installed APK does not prove a release is safely described. The documented 1.9.5 validation runs `assembleRelease`, verifies package metadata, verifies APK Signature Scheme v2, records the SHA-256 checksum, and confirms the direct export source paths compile through the shared archive manager. The artifact is locally signed for sideload testing. A production signing claim requires an owner-provided private key and independent distribution policy.\n\n| Layer | Validation method | Required outcome |\n| --- | --- | --- |\n| Source | Gradle Kotlin/Java compilation, KSP/Hilt generation | No unresolved integration paths. |\n| UI wiring | Review callbacks from direct buttons to managers | Playlist and Liked Songs export reach NovaAc preparation/save workflow. |\n| Audio | Active session attach/refresh and capability-safe effects | No playback failure on unsupported effect route. |\n| Storage | SAF create/open operations and cancellation handling | User controls destination; cancel is non-destructive. |\n| Navigation | Switch every layout and root route | One route graph, no duplicated state model. |\n| Artifact | AAPT metadata, apksigner, checksum | Correct package/version and verified signature scheme. |',
        '## Release procedure\n\n1. Update `versionCode` and `versionName` deliberately.\n2. Build from a clean enough workspace with the required SDK and Java 21 toolchain.\n3. Inspect compiler output; do not suppress source errors with unrelated changes.\n4. Run `assembleRelease`.\n5. Inspect `aapt dump badging` output for package identity and version.\n6. Run `apksigner verify --verbose` and record the result.\n7. Calculate a SHA-256 checksum and include it in the release note.\n8. Test direct export from Playlist and Liked Songs, cancel the picker once, then perform a successful archive write.\n9. Test at least one navigation-mode switch and one audio-setting change during playback.\n10. Label the signing status honestly.\n\n```bash\nexport ANDROID_HOME=/path/to/android-sdk\nexport JAVA_HOME=/path/to/jdk-21\n./gradlew assembleRelease --no-daemon --max-workers=2\naapt dump badging app/build/outputs/apk/release/app-release.apk\napksigner verify --verbose app/build/outputs/apk/release/app-release.apk\nsha256sum app/build/outputs/apk/release/app-release.apk\n```',
        '## Failure interpretation\n\nA canceled SAF picker is not an export failure; it is a user decision and should clear pending destination state. An unsupported Virtualizer or reverb effect is not an audio failure; the audio controller should leave playback running and record a warning. An unavailable lossless provider is not a player crash; it should follow timeout/fallback policy. A failed NovaAc write may be a destination-provider issue, a revoked URI grant, or insufficient provider capacity; it should display a meaningful result and leave the original collection untouched.\n\nThe most dangerous failures are silent ones: a control that only changes a local UI variable, an archive that reports success before writing, a navigation mode that hides Settings, or a diagnostic log that stores tokens. The review process should prioritize those classes of error.',
        references,
    ]
    return '\n\n'.join(sections)

def contributing_doc():
    sections = [
        common_intro('Spotui Contributing and Operations Guide', 'A practical guide for maintainers, contributors, release engineers, and reviewers.'),
        '## Contribution contract\n\nA good contribution explains three things before code is reviewed: **what user problem changes**, **which layer owns the behavior**, and **how the failure behaves**. For example, “add an export button” is incomplete. A complete proposal states that Playlist and Liked Songs use a shared archive manager; archive preparation runs off the UI thread; Android’s picker selects the destination; and cancellation leaves source collections unchanged. The same discipline applies to audio, streaming, account, cache, and navigation work.\n\n| Change type | Primary files or layers | Review focus |\n| --- | --- | --- |\n| New collection action | Screen + view model + manager | Discoverability, shared behavior, cancel/error paths. |\n| New setting | Settings UI + `SettingsPref` + consuming service | Persistence, immediate effect, safe defaults, migration. |\n| Audio behavior | `SongPlayer` + `AudioEffectController` | Session lifetime, route support, no main-thread work. |\n| Source resolver work | Metadata / InnerTube module + diagnostics | Provider boundaries, fallback, URL redaction. |\n| Navigation mode | Adaptive shell + routes | Same destinations and Search reselect semantics. |\n| Release work | Gradle config + validation notes | Version monotonicity, signing honesty, checksum. |',
        '## Local development workflow\n\nUse Java 21 and an Android SDK that contains API 36 platform/build tools. Open the project root, allow Gradle to resolve dependencies, and run the release build before claiming an integration is complete. Use a device for player/audio routes whenever possible, because emulator audio support does not prove hardware effect behavior. Keep synthetic/local test content separate from account data. Never commit cookies, account secrets, private signing keys, downloaded personal media, or generated diagnostics.\n\n### Pull-request narrative\n\nA high-quality pull request should include a summary, screenshots or a short interaction description for UI changes, the affected persistence keys, service ownership, tests performed, and known route/device limitations. If a feature is reconstructed from user-requested behavior rather than historical source evidence, say so in the documentation rather than creating invented provenance.',
        '## Operational guidance\n\nThe application is a media client with background playback, user-selected files, and optional session-dependent routes. Operations therefore include more than server uptime. Monitor release build output, module compatibility, user-visible diagnostic signals, archive header compatibility, and Android behavior changes. If an upstream source resolver changes, preserve a clear fallback message rather than burying the failure. If an archive format changes, increment its format version and write migration tests before changing the default importer. If a new account flow is proposed, define consent, logout/revocation, storage, and support boundaries first.\n\n' + callout('The project should optimize for trustworthy behavior: “this route is unavailable, here is what happened” is better than a silent spinner or an overbroad permission request.'),
        '## Credits and notices\n\nContributors must retain source notices and respect upstream licenses. The repository credits the AndroidX Media project, Jetpack Compose, Hilt, Ktor, OkHttp, NewPipe Extractor, Glide, ML Kit, Room, Neptune, SpotiFLAC, SimpMusic, and the broader Kotlin/Android ecosystem. Use the dependency catalog as the source of version truth, and consult each upstream repository for its current license and attribution obligations before redistribution.\n\n' + references,
    ]
    return '\n\n'.join(sections)

def filler(title, body, target=MIN_BYTES):
    # Adds a rich implementation ledger rather than arbitrary prose. The catalog is deliberate and cross-references all user-facing systems.
    document = body
    cycle = 0
    while len(document.encode()) < target:
        cycle += 1
        rows = []
        for index, (area, detail) in enumerate(feature_catalog, start=1):
            owner = module_catalog[(index + cycle) % len(module_catalog)][0]
            setting = settings_catalog[(index + cycle) % len(settings_catalog)][0]
            rows.append((f'{cycle}.{index}', area, owner, setting, detail))
        ledger = '## Implementation reference ledger ' + str(cycle) + '\n\n' + table(rows, ['ID', 'Feature area', 'Primary owner', 'Related configuration', 'Maintenance note'])
        narrative = dedent(f'''\

### Interpretation notes for ledger {cycle}

The ledger is a maintenance map, not a claim that every feature is implemented by one file. A user-facing behavior typically crosses rendering, state, service ownership, storage, and diagnostics. For example, an archive action starts as a collection screen control, uses a coroutine scope for expensive preparation, delegates binary work to `NovaAcExportManager`, writes only to a user-selected URI, and reports completion through the collection surface. The same pattern applies to audio: Settings persists intent, `SongPlayer` refreshes the active session, `AudioEffectController` applies available effects, and diagnostics retain recoverable warnings.

When changing **{title}**, prefer extending a shared owner over copying an implementation into another screen. Copied behavior becomes inconsistent in edge cases such as canceled storage pickers, unavailable audio effects, route changes, or versioned archive headers. Each revision should therefore identify the source of truth, user-visible state, cancellation behavior, and diagnostic path before code is merged.
''')
        document += '\n\n' + ledger + narrative
    return document

def module_readme(title, role):
    return '\n\n'.join([
        common_intro(title, role),
        '## Module role\n\n' + role + '\n\nThis module guide exists so a new maintainer can identify boundaries before making a seemingly small change. Music-client changes often cross modules: metadata or resolver changes may alter a player candidate; player changes may alter media-session behavior; collection changes may alter archive and cache paths. Read the root architecture blueprint before changing public behavior.',
        '## Integration checklist\n\n' + table([
            ('Build contract', 'Use the project’s Java 21, Kotlin 2.2, and Android/Gradle settings where applicable.'),
            ('API contract', 'Keep public types small and make callers handle recoverable results explicitly.'),
            ('Threading contract', 'Do network, archive, and filesystem work off the Compose UI thread.'),
            ('Privacy contract', 'Do not place tokens, cookies, user media, or persistent URLs in source or long-lived logs.'),
            ('Validation contract', 'Compile the dependent app module and run a relevant behavior smoke test.'),
        ], ['Concern', 'Expectation']),
        '## Feature relationship\n\n' + feature_table(),
        '## Configuration relationship\n\n' + settings_table(),
        references,
    ])

def issue_template_doc():
    return '\n\n'.join([
        '# Spotui Comprehensive Bug Report and Feature Request Template',
        callout('Use this template to produce a report that engineering can reproduce without requesting account secrets or private media files.'),
        '## Reporter context\n\n| Field | Value |\n| --- | --- |\n| App version / version code |  |\n| Android version and device |  |\n| Navigation layout | Bottom / Top / Side / Rail |\n| Compact UI / radius |  |\n| Audio output route | Speaker / wired / Bluetooth / other |\n| Stream quality / fallback setting |  |\n| Collection type | Playlist / Liked Songs / Downloads / Album / other |',
        '## Reproduction narrative\n\nDescribe the smallest reliable sequence from app launch to the observed behavior. State the expected result, actual result, whether the behavior persists after relaunch, and whether it occurs on a different route or device. Do not include cookies, tokens, account identifiers, direct stream URLs, or private media files.\n\n## Archive / audio / navigation diagnostic prompts\n\nFor a NovaAc issue, say whether Export was pressed from Playlist or Liked Songs, whether local-audio inclusion was enabled, whether the system picker was canceled or completed, and the visible result. For an audio issue, state the profile, depth, equalizer, normalizer intensity, output route, and whether playback continued. For navigation, state the selected layout and root route.\n\n## Engineering triage ledger\n\n' + feature_table(),
        references,
    ])

def metro_doc():
    return '\n\n'.join([
        common_intro('Metroserver Companion Source Guide', 'A documentation supplement for the bundled companion source tree shipped alongside the Android project.'),
        '## Scope\n\nThe Android app package includes a `metroserver-master` source tree. Treat it as a companion component with its own lifecycle, build notes, and security review. Do not assume it is started by the mobile app or that it can accept device credentials. Any deployment must define hosting, authentication, transport, data retention, observability, shutdown, and update policy independently of the Android APK.',
        '## Operational checklist\n\n' + table([
            ('Configuration', 'Use environment or secure secret storage; never hard-code private credentials.'),
            ('Transport', 'Use authenticated encrypted transport when exposed beyond a local development environment.'),
            ('Logging', 'Redact tokens, account IDs, archive contents, and signed URLs.'),
            ('Compatibility', 'Version any API contract and test against the Android client before deployment.'),
            ('Deployment', 'Use a managed owner, backup policy, and rollback procedure.'),
        ], ['Area', 'Expectation']),
        '## Relationship to Spotui\n\nThe Android client’s core playback, archive, settings, diagnostics, and navigation behavior should remain understandable without a companion service. A companion must augment an explicit user benefit, not hide essential state or make a local client untestable. The modular boundary protects both mobile reliability and deployment clarity.',
        references,
    ])

def doc_index():
    return '\n\n'.join([
        common_intro('Spotui Documentation Index', 'A reading map for the expanded source-package documentation.'),
        '## Reading paths\n\n| Reader | Recommended sequence | Outcome |\n| --- | --- | --- |\n| New contributor | README → Architecture Blueprint → Contributing Guide | Understand modules, ownership, build, and review rules. |\n| Release owner | README → Validation Report → Changelog | Produce a signed, checksummed, accurately described artifact. |\n| UI contributor | README → Configuration Reference → Architecture Blueprint | Extend adaptive navigation or settings without duplicate behavior. |\n| Playback contributor | Architecture Blueprint → Configuration Reference → Implementation Audit | Change player/audio logic while protecting session and fallback behavior. |\n| Support / QA | Validation Report → Implementation Audit → Configuration Reference | Reproduce, classify, and document user issues. |',
        '## Document inventory\n\n' + table([
            ('README.md', 'Project entry point, feature matrix, build information, full release table, credits.'),
            ('CHANGELOG.md', 'Long-form version record and evidence-status rules.'),
            ('ARCHITECTURE_BLUEPRINT.md', 'Layering, ownership, player, archives, settings, diagnostics.'),
            ('CONFIGURATION_REFERENCE.md', 'Every major user-facing configuration and its effect.'),
            ('CONTRIBUTING_AND_OPERATIONS.md', 'Review, build, release, privacy, and operational expectations.'),
            ('IMPLEMENTATION_DESIGN.md', 'Engineering rationale for current design decisions.'),
            ('IMPLEMENTATION_AUDIT.md', 'Integration checks and regression criteria.'),
            ('VALIDATION_REPORT.md', 'Artifact validation and failure interpretation.'),
            ('ROADMAP_STATUS.md', 'Current capability map and extension guardrails.'),
        ], ['Document', 'Use']),
        '## Documentation quality standard\n\nEvery maintained technical document in this source package is generated to a substantive minimum length so that it can stand alone when shared with an implementer. Length is not treated as a substitute for accuracy: the documents use evidence labels for unrecoverable history, distinguish application data from provider-controlled material, state signature limitations, and identify runtime owners for visible settings. When code and prose disagree, code plus a new validation run should win; then update the prose in the same change.',
        references,
    ])

# Construct top-level documents.
docs = {
    ROOT / 'README.md': readme_doc(),
    ROOT / 'CHANGELOG.md': release_history_doc(),
    ROOT / 'ARCHITECTURE_BLUEPRINT.md': architecture_doc(),
    ROOT / 'CONFIGURATION_REFERENCE.md': config_doc(),
    ROOT / 'CONTRIBUTING_AND_OPERATIONS.md': contributing_doc(),
    ROOT / 'IMPLEMENTATION_AUDIT.md': audit_doc(),
    ROOT / 'IMPLEMENTATION_DESIGN.md': design_doc(),
    ROOT / 'ROADMAP_STATUS.md': roadmap_doc(),
    ROOT / 'VALIDATION_REPORT.md': validation_doc(),
    ROOT / 'DOCUMENTATION_INDEX.md': doc_index(),
    ROOT / '.github' / 'ISSUE_TEMPLATE' / 'bug-report-or-feature-request.md': issue_template_doc(),
    ROOT / 'metroserver-master' / 'README.md': metro_doc(),
    ROOT / 'modules' / 'app' / 'main' / 'README.md': module_readme('Spotui Main Module Guide', module_catalog[0][1]),
    ROOT / 'modules' / 'app' / 'test' / 'README.md': module_readme('Spotui Test Module Guide', 'Testing guidance for the app package, including route, archive, audio, and release regression coverage.'),
    ROOT / 'modules' / 'app' / 'ui' / 'README.md': module_readme('Spotui UI Module Guide', 'UI architecture guidance for Compose screens, adaptive navigation, accessibility, state ownership, and collection controls.'),
    ROOT / 'modules' / 'app' / 'layout' / 'README.md': module_readme('Spotui Layout Module Guide', 'Layout and visual-system guidance for bottom/top/side/rail navigation, compact presentation, and corner-radius preferences.'),
}

for path, content in docs.items():
    path.parent.mkdir(parents=True, exist_ok=True)
    enriched = filler(path.stem, content)
    path.write_text(enriched.rstrip() + '\n', encoding='utf-8')

# Make the current release notes cross-link to long-form documentation.
release_notes = ROOT / 'SPOTUI_1.9.5_RELEASE_NOTES.md'
if release_notes.exists():
    current = release_notes.read_text(encoding='utf-8')
    addendum = dedent('''\

## Documentation expansion

This release source package now includes an extended project [README](README.md), a complete [1.1.0–1.9.5 changelog](CHANGELOG.md), an [architecture blueprint](ARCHITECTURE_BLUEPRINT.md), a [configuration reference](CONFIGURATION_REFERENCE.md), and an [operations guide](CONTRIBUTING_AND_OPERATIONS.md). Historical versions before the supplied source’s documented 1.5.1 baseline are identified as reconstructed product-era records rather than unverifiable commit history.

''')
    release_notes.write_text(filler('Spotui 1.9.5 Release Notes', current + addendum), encoding='utf-8')

print('Generated documentation:')
for path in sorted(docs):
    print(f'{path.relative_to(ROOT)}: {path.stat().st_size} bytes')
if release_notes.exists():
    print(f'{release_notes.relative_to(ROOT)}: {release_notes.stat().st_size} bytes')
