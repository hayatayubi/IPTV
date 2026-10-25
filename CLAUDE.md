# Lumen: minimal IPTV player for Google TV

Personal, Apple/tvOS-inspired IPTV player. The spec is the handover doc (kept outside the repo).
The owner knows iOS/SwiftUI and is new to Android: explain Android-specific steps,
and comment where Compose differs from SwiftUI in a non-obvious way.

## Current phase

**Phase 0: Setup.** Done, verified on the TV 2026-10-25 (install, D-pad focus, OK / long-press, focus restore, Back).
Next: **Phase 1, Live basics.**

## Decisions

- App name **Lumen**, package `com.hayat.lumen` (placeholders, rename freely before Phase 1).
- Device: Chromecast with Google TV **4K** (~2 GB RAM), at `192.168.4.72`. Connects via **Wireless debugging**
  (already paired with the laptop; the connect port changes after a reboot, so read it from the TV). Sources: **M3U + Xtream**. EPG: **separate XMLTV URL**.
- Accent **#0A84FF** (tvOS blue). Dark only. Font: Inter (bundled in `res/font`, OFL, license in `third_party/inter`).
- Versions (checked 2026-10-25, latest stable): AGP 9.4.1, Gradle 9.8.2, Kotlin 2.4.21, Compose BOM 2026.09.00,
  tv-material 1.1.0, compileSdk/targetSdk 37, minSdk 29. All in `gradle/libs.versions.toml`.
- AGP 9 "built-in Kotlin": no `org.jetbrains.kotlin.android` plugin.
- Use standard `LazyRow`/`LazyColumn`/`LazyVerticalGrid`, not tv-foundation lazy layouts (deprecated).
- **Shared debug keystore in `keystore/`**, used for debug and (for now) release, so APKs built in cloud sessions
  and on the laptop can be installed over each other without wiping data.
- Backups disabled (`allowBackup=false` + `data_extraction_rules.xml`): credentials stay on the device.
- One focus component, `ui/components/LumenCard.kt` (tv-material `Card`: scale 1.06, accent glow, dims when unfocused).
  Motion: `LumenMotion.focus()` = 180 ms ease-out. Safe area: `SafeArea` (48 dp × 27 dp).

## Open decisions

- DI: Hilt vs manual constructor injection (`AppContainer`). Decide before Phase 1.

## Known issues

- tv-material Card's own scale animation timing isn't configurable; only our dimming uses `LumenMotion`. Revisit in Phase 4.
- Placeholder banner/icon.

## Working notes

- Build: `./gradlew assembleDebug` · Lint: `./gradlew lintDebug` · Install: `./gradlew installDebug` (see `docs/SETUP.md`).
- **Judge smoothness only on release builds.** Debug Compose builds are visibly janky on the Chromecast; release
  (`./gradlew assembleRelease`, R8 + same shared key, installs over debug) was confirmed smooth on the TV.
  After sideloading, `adb shell cmd package compile -f -m speed-profile com.hayat.lumen` applies the baseline profile right away.
- Cloud sessions: Maven Central rate-limits (429) the shared egress IP. Use a Gradle init script in
  `~/.gradle/init.d/` that puts `https://maven-central.storage-download.googleapis.com/maven2/` first.
  Never put it in the repo.
- Never log URLs containing credentials.
