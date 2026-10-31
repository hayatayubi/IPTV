# iPhone and iPad app: setup and install

The iOS app lives in `ios/`. It is SwiftUI; the shared Kotlin code (playlists, Xtream, guide)
joins it in a later phase. iOS apps can only be built on a Mac, with Xcode.

## 1. Install Xcode and XcodeGen (once)

1. Install **Xcode** from the Mac App Store (free, about 15 GB). Open it once: accept the licence,
   and when it asks which platforms to download, tick **iOS**.
2. In Terminal: `brew install xcodegen`.
   XcodeGen builds `Lumen.xcodeproj` from `ios/project.yml`. The project file isn't in git: it's a
   huge machine-written file that clashes on every merge, so it's rebuilt instead.

## 2. Sign in with your Apple ID (once)

Xcode → **Settings → Accounts** → **+** → Apple ID. This gives you a free **Personal Team**.
No $99 needed for now. What the free team means:

- An installed app **stops opening after 7 days**. Plug in and press Run again to renew it
  (your data in the app stays).
- At most 3 of your own apps on a device at a time.

## 3. Get the project and open it

```sh
cd ~/IPTV && git fetch && git checkout <branch> && git pull
cd ios && ./generate.sh && open Lumen.xcodeproj
```

Run `./generate.sh` again after every pull (it picks up new files).

## 4. First run on your iPhone or iPad

1. Plug the iPhone/iPad into the Mac with a cable. Unlock it and tap **Trust**.
2. **Turn on Developer Mode** on the device: Settings → Privacy & Security → **Developer Mode** → on.
   It restarts and asks you to confirm. (The option only appears after the device has been
   connected to Xcode once.)
3. In Xcode, click **Lumen** at the top of the left sidebar → target **Lumen** →
   **Signing & Capabilities** → **Team**: pick your name (Personal Team). Only needed the first
   time: run `./generate.sh` once afterwards and it remembers your team in
   `ios/Config/Local.xcconfig` (not committed).
   - If Xcode says the bundle id `com.hayat.lumen` isn't available, change
     `PRODUCT_BUNDLE_IDENTIFIER` in `ios/project.yml` to e.g. `com.hayat.lumen.app`.
4. At the top of the Xcode window, choose your device from the device menu, then press **Run** (▶).
5. The first time, the phone refuses to open the app: Settings → General →
   **VPN & Device Management** → your Apple ID → **Trust**. Then open Lumen.

Later runs can go over Wi-Fi: Xcode → Window → Devices and Simulators → your device →
**Connect via network**.

No device to hand? Pick an iPhone or iPad **simulator** in the same device menu.

## 5. What to check

- The four tabs (Live TV, Movies, Series, Settings); on iPad they also fold into a sidebar.
- Settings → **Test video playback** plays Apple's test stream with sound, rotates to full screen,
  and Picture in Picture works.

## App Store notes (for later)

What the iOS build does differently from Android, because of App Store rules:

- Ships with no channels, playlists or provider links ("Lumen doesn't provide any content").
- No built-in BBC/ITV/Channel 4/Sky logo tiles; ratings as plain text, no IMDb/Rotten Tomatoes marks.
- No self-updater: updates come from TestFlight or the App Store.
- `NSAllowsArbitraryLoads` is on because users type their own http servers; say so in the
  review notes.
- `PrivacyInfo.xcprivacy`: no tracking, no data collected. App Privacy label: "Data not collected".
- Reviewers get a legal test playlist (to be hosted on lumen-tv.github.io), never a real login.
