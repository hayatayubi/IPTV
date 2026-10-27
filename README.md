# Lumen

A minimal, Apple TV–style IPTV player for Google TV, written in Kotlin with Jetpack Compose for TV.
Built for a personal setup on a Chromecast with Google TV (4K).

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

You need `adb` on your computer and wireless debugging turned on on the TV. The full walkthrough
(developer mode, pairing, Mac tools) is in [docs/SETUP.md](docs/SETUP.md).

```sh
adb connect <tv-ip>:<port>
adb -s <tv-ip>:<port> install -r lumen-release.apk
```

## Build

Requires JDK 17+ and the Android SDK (`platforms;android-37.0`).

```sh
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk
./gradlew installDebug         # build and install a debug build on the connected TV
./gradlew testDebugUnitTest    # unit tests
```

Use release builds to judge smoothness: debug Compose builds stutter on the Chromecast. Debug and release are
signed with the same shared key in `keystore/`, so either installs over the other.

## Project layout

- `app/src/main/java/com/hayat/lumen/data`: Room database, M3U and Xtream import, TV guide (EPG).
- `app/src/main/java/com/hayat/lumen/playback`: the single shared ExoPlayer.
- `app/src/main/java/com/hayat/lumen/ui`: screens (Home, Live, Guide, Categories, Player) and shared components.
- `CLAUDE.md`: design decisions and conventions for contributors.

## Privacy

Playlist logins stay on the device: backups are turned off and credentials are never logged.

## Credits

- [Inter](https://rsms.me/inter/) font, SIL Open Font License (`third_party/inter`).
- UK channel logos from [tv-logo/tv-logos](https://github.com/tv-logo/tv-logos), Creative Commons ShareAlike;
  the logos are trademarks of their owners (`third_party/tv-logos`).
