# Lumen: minimal IPTV player for Google TV

Personal, Apple/tvOS-inspired IPTV player. The spec is the handover doc (kept outside the repo).
The owner knows iOS/SwiftUI and is new to Android: explain Android-specific steps,
and comment where Compose differs from SwiftUI in a non-obvious way.

## Current phase

- **Phase 0: Setup.** Done, verified on the TV 2026-10-25.
- **Phase 1: Live basics.** Built (branch `phase-1-live`). Waiting on a real-TV check with the owner's M3U.
  - Add M3U source → Live (groups | channels) → full-screen player, Up/Down zapping, OK info overlay.
  - Not in Phase 1: Left sidebar and Right mini guide in the player (Phases 3/6), logos (Phase 4), settings/source editing (Phase 6).

## Decisions

- App name **Lumen**, package `com.hayat.lumen`.
- Device: Chromecast with Google TV **4K** (~2 GB RAM), at `192.168.4.72`. Connects via **Wireless debugging**
  (already paired with the laptop; the connect port changes after a reboot, so read it from the TV).
  Sources: **M3U + Xtream**. EPG: **separate XMLTV URL**.
- Accent **#0A84FF** (tvOS blue). Dark only. Font: Inter (bundled in `res/font`, OFL, license in `third_party/inter`).
- Versions (checked 2026-10-25, latest stable) are all in `gradle/libs.versions.toml`.
- AGP 9 "built-in Kotlin": no `org.jetbrains.kotlin.android` plugin. KSP 2.3.x runs Room's compiler.
- **DI: by hand.** `AppContainer` (on `LumenApp`) builds OkHttp, Room DB, repositories, `PlayerHolder`;
  composables get it via `LocalAppContainer`; ViewModels via `viewModel { Foo(container.x) }`.
- **Navigation 3**: the back stack is a `mutableStateListOf<Route>` in `ui/LumenRoot.kt`. Back pops; Back at root exits.
- **Room 2.8** (not Room 3, which is new; migration later is mostly renames). Schemas exported to `app/schemas`.
  **No destructive migration fallback**: every schema change needs a migration (prefer auto-migrations), so
  favourites etc. are never wiped.
- **Paging 3 deferred.** Lists are plain Room `Flow<List<…>>` of slim projections (`ChannelRow` has no URL).
  A 10k-channel group is ~2 MB. Revisit if a real playlist feels slow.
- **Import** (`data/LiveRepository.kt`): OkHttp stream → `M3uParser.entries()` (lazy `Sequence`) → batches of 500 rows
  under a new `generation` → one short transaction swaps generations. Old data stays if an import fails.
  Queries only read rows where `channel.generation == source.generation`.
- **Xtream** (`data/xtream/`): login = server + username + password, stored on `SourceEntity` (type `XTREAM`).
  Import uses `player_api.php` (account check, `get_live_categories`, `get_live_streams`, streamed with
  `android.util.JsonReader`) and builds `/live/<user>/<pass>/<id>.m3u8` (or `.ts` if HLS isn't allowed).
  A pasted `get.php?username=…&password=…` link is stored as Xtream automatically: the owner's provider
  blocks `get.php` (answers HTTP 884) but allows the API, which also gives proper groups.
- **EPG** (`data/epg/`): one guide URL per source = user-entered `epgUrl`, else the playlist's `url-tvg`
  (stored into `epgUrl` on import), else Xtream `xmltv.php` (built on the fly, never shown: it holds the password).
  `XmltvParser` streams `<programme>`s (gzip files detected by magic bytes); only channels whose
  `lower(tvgId)` is in the playlist, and only now−2 h … now+36 h, are stored in `programmes` under a new `batch`,
  then `epg_state.batch` is switched in one transaction (same idea as channel generations).
  Auto-update: at launch if older than 12 h, after adding a playlist, and with Live's Refresh. Settings: Live → TV guide.
  Reading: `EpgRepository.nowNextForGroup` (stored guide only), `nowNextForChannels`/`nowNext` (small lists:
  player, focused row, favourites) fall back to Xtream `get_short_epg` per channel, cached in memory.
  No name matching yet: channels without a tvg-id get no guide.
- M3U entries whose URL contains `/movie/` or `/series/` are skipped (VOD comes via Xtream in Phase 5).
- **Stable keys** (`data/m3u/StableKeys.kt`): `tvg:<id>`, else a hash of name + URL with credentials stripped.
  Duplicates get `|name`, then `#n`. Phase 2 user state must key on `(sourceId, stableKey)`, **not** row ids:
  channel ids change on every refresh.
- Credentials: `util/UrlRedaction.kt`. Error messages show `hostOnly(url)`; never log raw URLs.
- Cleartext http allowed app-wide (`res/xml/network_security_config.xml`): IPTV is mostly http.
- **Player** (`playback/PlayerHolder.kt`): one ExoPlayer, created lazily, released in `MainActivity.onStop`.
  Starts after 1 s of buffer. OkHttp data source (follows http→https redirects).
  Zapping is debounced 350 ms, and the old stream is stopped at once (providers often allow 1 connection).
  Up = previous in list, Down = next; CH+/CH- keys = next/previous.
- Shared debug keystore in `keystore/`, used for debug and release, so cloud and laptop builds install over each other.
- Backups disabled (`allowBackup=false` + `data_extraction_rules.xml`): credentials stay on the device.
- UI components (`ui/components`):
  - `LumenListItem`: focused row turns light with dark text.
  - `LumenButton`.
  - `LumenTextField`: the keyboard opens only on OK, not on focus.
  - `Monogram`: fallback tile.
  - `LumenCard`: poster tile for Phase 4.
- Motion: `LumenMotion.focus()` = 180 ms ease-out. Safe area: `SafeArea` (48 dp × 27 dp).

## Known issues

- Not verified on device yet (Phase 1): Back priority between `BackHandler` in the player and NavDisplay;
  focus return to the last channel after leaving the player.
- `type=m3u` Xtream URLs have no group/logo attributes. Moot for `get.php` links now: they go through the Xtream API.
- EPG import and `get_short_epg` not verified on a real provider yet.
- Xtream JSON parsing has no unit tests (android.util.JsonReader is a stub on the JVM); it is checked on the TV.
- tv-material Card/Surface scale animation timing isn't configurable. Revisit in Phase 4.
- Placeholder banner/icon. No channel logos until Phase 4 (monograms only).

## Working notes

- Build: `./gradlew assembleDebug` · Tests: `./gradlew testDebugUnitTest` · Lint: `./gradlew lintDebug`.
- **Judge smoothness only on release builds.** Debug Compose builds are visibly janky on the Chromecast; release
  (`./gradlew assembleRelease`, R8 + same shared key, installs over debug) was confirmed smooth on the TV.
  After sideloading, `adb shell cmd package compile -f -m speed-profile com.hayat.lumen` applies the baseline profile right away.
- Typing a URL on the TV: open the field with OK, then `adb shell "input text 'http://…'"` from the laptop
  (inner single quotes stop the device shell from eating `&`).
- Cloud sessions: Maven Central rate-limits (429) the shared egress IP. Use a Gradle init script in
  `~/.gradle/init.d/` that puts `https://maven-central.storage-download.googleapis.com/maven2/` first.
  Never put it in the repo.
- Never commit or log playlist URLs or credentials (the owner's test URL included).
