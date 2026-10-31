# Deploying Mega Launcher

This guide covers getting the source, building the APK, installing it on a Google TV / Android TV, and making it the default home screen.

Source code: <https://github.com/mhzworld/megalauncher> (MIT License)

- [1. Requirements](#1-requirements)
- [2. Build the APK](#2-build-the-apk)
  - [Get the source](#get-the-source)
  - [Option A – Android Studio](#option-a--android-studio)
  - [Option B – Command line](#option-b--command-line)
  - [Signed release APK](#signed-release-apk)
- [3. Prepare the TV](#3-prepare-the-tv)
- [4. Install the APK](#4-install-the-apk)
- [5. Set Mega Launcher as the home screen](#5-set-mega-launcher-as-the-home-screen)
- [6. Updating](#6-updating)
- [7. Uninstall / restore the Google TV launcher](#7-uninstall--restore-the-google-tv-launcher)
- [8. Troubleshooting](#8-troubleshooting)

---

## 1. Requirements

| Item | Version |
|---|---|
| JDK | 17 or 21 (Android Studio's bundled JDK 21 works) |
| Android SDK | Platform 35, Build-tools 35.0.0, Platform-tools |
| Gradle | 8.11.1 (via the included wrapper – do **not** upgrade to Gradle 9 while using AGP 8.7.3) |
| Git | Any recent version (to clone the repository) |
| TV | Google TV / Android TV 8.0 (API 26) or newer |

---

## 2. Build the APK

### Get the source

```powershell
git clone https://github.com/mhzworld/megalauncher.git
cd megalauncher
```

No Git? On the GitHub page choose **Code → Download ZIP** and extract it.

To update an existing copy later: `git pull`.

### Option A – Android Studio

1. Install [Android Studio](https://developer.android.com/studio) (includes the Android SDK and JDK).
2. **File → Open** → select the cloned `megalauncher` folder.
3. Wait for Gradle sync. If prompted to upgrade Gradle or the Android Gradle Plugin, choose **Don't remind me / Cancel**.
4. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
5. Click **locate** in the notification. Output:

   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

### Option B – Command line

1. Download **Command line tools only** from https://developer.android.com/studio (bottom of the page) and extract it so this file exists:

   ```
   %LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat
   ```

2. Install the SDK components and accept licences (PowerShell):

   ```powershell
   $sdk = "$env:LOCALAPPDATA\Android\Sdk"
   & "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
   & "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
   ```

3. Create `local.properties` in the project root (not committed to git):

   ```properties
   sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
   ```

4. Build the debug APK:

   ```powershell
   .\gradlew.bat assembleDebug
   ```

   Output: `app\build\outputs\apk\debug\app-debug.apk`

On macOS / Linux use `./gradlew assembleDebug` and `sdk.dir=/path/to/Android/sdk`.

> **Use a release build on the TV.** Debug builds of Compose apps are noticeably laggy. The release build is optimised with R8 and is signed with your local debug key, so it installs directly:
>
> ```powershell
> .\gradlew.bat assembleRelease
> ```
>
> Output: `app\build\outputs\apk\release\app-release.apk`. In Android Studio choose **Build → Select Build Variant → release** before Run/Build.

### Signed release APK (own key)

The release build above uses the debug key, which is fine for personal use. To sign with your own key instead:

1. Create a keystore once and **keep it safe** – the same key is needed for every future update:

   ```powershell
   keytool -genkeypair -v -keystore megalauncher.jks -alias megalauncher -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Build:
   - **Android Studio**: **Build → Generate Signed App Bundle / APK → APK**, select the keystore, choose `release`.
   - Output: `app/build/outputs/apk/release/app-release.apk`

> Never commit the keystore or its passwords to source control.

---

## 3. Prepare the TV

1. **Settings → System → About** → select **Android TV OS build** 7 times until "You are now a developer" appears.
2. **Settings → System → Developer options**:
   - Turn on **USB debugging**.
   - On some models also turn on **Network debugging** / **Wireless debugging**.
3. Find the TV's IP address: **Settings → Network & Internet → (your network)**.

The PC and TV must be on the same network.

---

## 4. Install the APK

### Via adb (recommended)

`adb` is in `%LOCALAPPDATA%\Android\Sdk\platform-tools`.

```powershell
adb connect <TV-IP>:5555
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

- Accept the **Allow USB debugging?** prompt on the TV the first time.
- On Android TV 11+ with *Wireless debugging*, use **Pair device with pairing code** on the TV and run `adb pair <TV-IP>:<pair-port>` first, then `adb connect <TV-IP>:<port>`.

### Without adb

- Copy the APK to a USB drive and open it with a file manager on the TV, or
- Use an app such as *Send Files to TV* or *Downloader*.
- Allow **Install unknown apps** for that app when prompted.

---

## 5. Set Mega Launcher as the home screen

Google TV usually ignores third-party launchers for the Home button. Disable the stock launcher so the Home button opens Mega Launcher:

```powershell
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
```

Then press **Home** on the remote. If asked, choose **Mega Launcher** and **Always**.

> On some devices the stock launcher package may differ. List candidates with:
>
> ```powershell
> adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME
> ```

Optional – also disable the Google TV setup/home helper if Home still opens the stock launcher:

```powershell
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

Optional – let Mega Launcher switch the TV's screensaver to the one you choose in its settings:

```powershell
adb shell pm grant com.megalauncher android.permission.WRITE_SECURE_SETTINGS
```

Optional – allow Mega Launcher to add Android app widgets as gadgets (Android TV has no on-screen prompt for this):

```powershell
adb shell appwidget grantbind --package com.megalauncher --user 0
```

**No PC?** Both optional permissions can be granted on the TV itself: install [Shizuku](https://github.com/RikkaApps/Shizuku/releases), start it via *Wireless debugging* (Android 11+), then open **Launcher settings → Permissions → Allow app widgets & screensaver switching**. Shizuku does not root the TV.

---

## 6. Updating

Get the latest source (`git pull`), build a new APK and install over the existing one (settings and profiles are kept):

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Debug and release APKs are signed with different keys – switching between them requires uninstalling first (settings will be lost).

---

## 7. Uninstall / restore the Google TV launcher

```powershell
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith   # only if you disabled it
adb uninstall com.megalauncher
```

Reboot the TV afterwards.

---

## 8. Troubleshooting

| Problem | Fix |
|---|---|
| Gradle sync fails with a Gradle 9 error | Make sure `gradle/wrapper/gradle-wrapper.properties` points to `gradle-8.11.1-bin.zip`. |
| `SDK location not found` | Create `local.properties` with `sdk.dir=...` (see step 2). |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | An APK with a different signing key is installed. Run `adb uninstall com.megalauncher` and install again. |
| Home button still opens Google TV | Disable the stock launcher (step 5) and reboot. |
| `adb connect` fails | Check the IP, that debugging is enabled, and that the TV and PC are on the same network. |
| Weather shows "Weather unavailable" | Check the weather.com API key, or clear it to use Open-Meteo. |
| Prayer times don't load | Check internet access to `www.e-solat.gov.my`, then use *Refresh prayer times now* in settings. |
| "Browse images" shows nothing | Copy images to the TV's *Pictures* or *Download* folder and allow the storage permission. |
| No system file picker | Many Google TV devices don't include one – use *Browse images on this TV* or a web link. |
| Video background doesn't play | A message explains why (e.g. the TV can't decode the format). Use an **H.264 MP4 at 1080p** – the most widely supported format; 4K, HEVC 10-bit or AV1 videos only play on TVs whose decoder supports them. |
