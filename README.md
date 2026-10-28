# Lumen

A beautifully simple IPTV player for Android TV, Google TV and Fire TV (Fire OS 8), designed to feel like it came with
your TV. Written in Kotlin with Jetpack Compose for TV. Website: https://lumen-tv.github.io

**Lumen is only a player: it comes with no channels, films or playlists.** You add a playlist or Xtream login
from your own provider. Lumen collects no data (see the [privacy policy](PRIVACY.md)).

## Features

- **Playlists**: add an M3U link or an Xtream login (server, username, password). A pasted `get.php` link is
  loaded through the Xtream API automatically, which also gives proper categories.
- **Live TV**: categories on the left, channels on the right, with channel logos and what's on now.
- **Player**: full-screen live playback. Up/Down or CH+/CH- change channel; OK shows the info overlay.
- **Favourites on launch**: hold OK on a channel to favourite it. The app opens on Home, a grid of large channel
  cards (eight fit on screen) with the current programme, and a muted live preview on the focused card.
- **TV guide**: XMLTV guide from the playlist, a separate URL, or Xtream. A scrollable grid shows channels down and
  the next 24 hours across; OK on a programme plays the channel.
- **Hide what you don't watch**: hold OK on a category to hide it, or use Live → Categories to tick categories
  and whole playlists on and off.
- **Clean look**: dark theme, tvOS-blue focus with a lift and sheen, tidied channel names, and built-in logo tiles
  for the main BBC, ITV, Channel 4 and Sky Sports channels.

## Install on the TV

Lumen isn't on the Play Store. You install it once with the free **Downloader** app, and after that it updates
itself.

1. On the TV, install **Downloader** (by AFTVnews) from its app store.
2. Allow it to install apps (most TVs ask the first time). Android TV / Google TV: *Settings → Apps → Security &
   restrictions → Unknown sources → Downloader*. Fire TV: *Settings → My Fire TV → Developer options → Install unknown
   apps → Downloader*.
3. Open Downloader and enter `lumen-tv.github.io/get` (it always gives the newest version).
4. Choose **Install**. Lumen appears in your apps.

**Updates:** when a new version is out, Lumen's Home screen shows an "Update to …" button. The first time, it asks you
to allow Lumen to install updates (one switch), then each update is one click. Your playlists and favourites are kept.

### With adb (developers)

You need `adb` and wireless debugging on the TV; the walkthrough is in [docs/SETUP.md](docs/SETUP.md).

```sh
adb connect <tv-ip>:<port>
adb -s <tv-ip>:<port> install -r lumen.apk
```

## Releasing an update

Bump `lumenVersion` in `app/build.gradle.kts` and merge to main. The Release workflow builds the APK, signs it with
the private release key from the repo's Actions secrets, and publishes it as a GitHub Release named `v<version>`
with `lumen.apk` attached. TVs pick it up within a few hours (or straight away with Live → Refresh).
Setting up the key: [docs/RELEASING.md](docs/RELEASING.md).

## Build

Requires JDK 17+ and the Android SDK (`platforms;android-37.0`).

```sh
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk
./gradlew installDebug         # build and install a debug build on the connected TV
./gradlew testDebugUnitTest    # unit tests
```

Use release builds to judge smoothness: debug Compose builds stutter on the Chromecast. Local builds (debug and
release) are signed with the shared key in `keystore/`, so either installs over the other, but not over a published
release, which uses the private key.

## Project layout

- `app/src/main/java/com/hayat/lumen/data`: Room database, M3U and Xtream import, TV guide (EPG).
- `app/src/main/java/com/hayat/lumen/playback`: the single shared ExoPlayer.
- `app/src/main/java/com/hayat/lumen/ui`: screens (Home, Live, Guide, Categories, Player, Movies, Series, Update) and shared components.
- `CLAUDE.md`: design decisions and conventions for contributors.

## Privacy

Playlist logins stay on the device: backups are turned off and credentials are never logged.

## Credits

- [Inter](https://rsms.me/inter/) font, SIL Open Font License (`third_party/inter`).
- UK channel logos from [tv-logo/tv-logos](https://github.com/tv-logo/tv-logos), Creative Commons ShareAlike;
  the logos are trademarks of their owners (`third_party/tv-logos`).
