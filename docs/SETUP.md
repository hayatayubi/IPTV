# Setup: tools, ADB to the TV, install

Written for macOS. Device: **Chromecast with Google TV (4K)**.

## 1. Check what you already have

Run in Terminal:

```sh
ls /Applications | grep -i "android studio"   # Android Studio installed?
adb version                                   # adb on your PATH?
java -version                                 # only needed for ./gradlew outside Android Studio
```

## 2. Install what's missing

- **Android Studio** (recommended: you'll want it for logcat, the layout inspector and debugging):
  - `brew install --cask android-studio`, or download it from developer.android.com/studio.
  - On first launch the setup wizard installs the Android SDK and **platform-tools** (which contains `adb`).
  - It bundles its own JDK, so you don't need to install Java separately.
- **Put `adb` on your PATH.** Add these lines to `~/.zshrc`, then open a new terminal:
  ```sh
  export ANDROID_HOME="$HOME/Library/Android/sdk"
  export PATH="$ANDROID_HOME/platform-tools:$PATH"
  # Lets ./gradlew use Android Studio's bundled JDK:
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  ```
- **Only want to install APKs, not build them?** `brew install --cask android-platform-tools` gives you `adb` on its own.

## 3. Enable developer mode on the TV

1. **Settings → System → About**. Scroll to **Android TV OS build** and press **OK 7 times** until it says *"You are now a developer"*.
2. **Settings → System → Developer options** → turn on **USB debugging**.
   - The Chromecast has no usable USB data port. Turning this on is what enables ADB over the network.
3. Find the TV's IP address: **Settings → Network & Internet → (your Wi-Fi network)**. It looks like `192.168.1.42`.
   - Tip: give it a fixed IP (DHCP reservation) on your router, so it doesn't change.

## 4. Connect ADB over Wi-Fi

Your Mac and the TV must be on the same network.

### Option A: plain `adb connect` (most Chromecast builds)

```sh
adb connect 192.168.1.42:5555
```

- The TV shows **"Allow USB debugging?"**. Tick **Always allow from this computer** and press OK.
- If the first attempt says `failed to authenticate`, run the same command again after accepting the prompt.

### Option B: Wireless debugging with a pairing code (newer Google TV builds)

Use this if Developer options has a **Wireless debugging** entry, or if Option A is refused.

1. **Developer options → Wireless debugging** → turn it on → **Pair device with pairing code**.
2. The TV shows an IP:port and a 6-digit code. The **pairing port is different** from the connection port.
   ```sh
   adb pair 192.168.1.42:37123      # the port from the pairing dialog; enter the 6-digit code
   ```
3. Back on the Wireless debugging screen, note the IP:port under **IP address & Port**, then:
   ```sh
   adb connect 192.168.1.42:41234   # the port from the main Wireless debugging screen
   ```

### Check it worked

```sh
adb devices
# List of devices attached
# 192.168.1.42:5555    device        <- "device" is good; "unauthorized" means accept the prompt on the TV
```

## 5. Install and run

- **From an APK file** (for example one built in a cloud session):
  ```sh
  adb install -r app-debug.apk        # -r = replace the existing install, keeping its data
  ```
- **From source:**
  ```sh
  ./gradlew installDebug
  ```
- **Launch:** on the TV, go to **Apps → See all apps → Lumen**. From the terminal:
  ```sh
  adb shell am start -n com.hayat.lumen/.MainActivity
  ```
- **Logs:** (the Logcat tool window in Android Studio is nicer)
  ```sh
  adb logcat --pid=$(adb shell pidof com.hayat.lumen)
  ```
- **Uninstall:** `adb uninstall com.hayat.lumen`

## Troubleshooting

- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`**: the installed app was signed with a different key. Builds from this repo use the shared key in `keystore/`, so this only happens if something was installed from elsewhere. Run `adb uninstall com.hayat.lumen` first. This wipes the app's data.
- **Connection drops after the TV sleeps**: run `adb connect …` again. On Option B the port can change after a reboot.
- **Developer verification** (Google's sideload rules, rolling out 2026–27): if an install is ever blocked with a verification message, register the signing key with a free limited-distribution account in the Android Developer Console.
