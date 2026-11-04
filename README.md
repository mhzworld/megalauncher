# Mega Launcher

A simple, ad-free and tracker-free home screen launcher for **Google TV / Android TV**, built as a replacement for the stock Android / Google TV launcher.

- No ads, no analytics, no Google Play Services dependency.
- Network requests are only made for the features you enable (weather, prayer times, background downloads).
- Current version: **0.1.1** (shown in *Launcher settings → About*).
- Source code: **[https://github.com/mhzworld/megalauncher](https://github.com/mhzworld/megalauncher)**
- License: **[MIT](LICENSE)** – free to use, modify and share.

> Deployment and installation steps are in **[DEPLOY.md](DEPLOY.md)**.

## Getting the source

```powershell
git clone https://github.com/mhzworld/megalauncher.git
```

Then open the folder in Android Studio (see [DEPLOY.md](DEPLOY.md)).

---

## Features

### Top bar

| Position | Item                                                                                                  |
| -------- | ----------------------------------------------------------------------------------------------------- |
| Left     | Profile avatar (opens the profile switcher), **Search**, **Home**, **Apps**         |
| Right    | Screensaver, **Launcher settings** (sliders icon), **All Android settings** (gear), clock |

The clock shows the time on top (12pt) and the date below (8pt). Time can be 24-hour (`17:05`) or 12-hour (`5:05 PM`); the date can be `Sat, 31 Oct 2026`, `Saturday, 31 October 2026`, `31 Oct 2026`, `Sat, Oct 31`, `31/10/2026`, `10/31/2026` or `2026-10-31`. Weather and prayer gadget times follow the same 12/24-hour setting.

### Profiles

- Press the avatar to open **Who's watching?** and switch profiles, or add a new one.
- Each profile has its own favourites, rows, gadgets, background, clock format and other launcher settings.
- Profile picture: **Default** (initial letter, or a person icon if the name is empty), one of 8 built-in icons (smile, cat, robot, alien, panda, ghost, rocket, crescent moon), or your own image. Choose *Default* at any time to go back to the initial.
  - Your own image can be picked with *Browse images on this TV* or the system file picker. It is center-cropped to a square, resized to 256 px and stored privately on the TV.
- Rename, change picture, add or delete profiles in *Launcher settings → Profile*. Deleting a profile also removes its widgets, background files and picture. The last profile can't be deleted.
- Upgrading from an older version: your existing settings become the first profile, named after your old profile name.

### Favourites row

- Round app icons that fill the circle edge-to-edge, in a compact horizontal row.
- **Hold OK** on an icon for: Open, Move, Remove from favourites, View in Play Store, App info, Uninstall.
- **Move mode**: use ◀ ▶ to reorder, press OK (or Back) when done.
- The row ends with **All apps** and **Add favourite** buttons.

### Gadgets row

Weather and Prayer times are shown by default; World clock and System monitor are off by default.

- **Weather** – current temperature (°C / °F), description, feels-like, humidity and the next 5 hours.
  - Uses **The Weather Company (weather.com)** when an API key is set, otherwise **Open-Meteo** (free, no key).
  - Location: automatic (IP-based) or a city you search for.
  - Cached on disk; auto-refreshed every hour by default (15 min – 6 h, or manual only) when online. Press OK on the gadget to refresh any time. When offline, the cached forecast is shown with an "offline" label.
- **Prayer times (JAKIM e-Solat)** – Subuh, Syuruk, Zohor, Asar, Maghrib, Isyak.
  - The current prayer is highlighted and enlarged (e.g. at 4:55 PM with Asar at 4:24 PM, Asar is highlighted).
  - Zone: automatic (nearest JAKIM zone to your location) or choose any of the JAKIM zones.
  - About one month of times is cached and refreshed automatically when fewer than 7 days remain.
- **Android app widgets** from installed apps (needs a one-time permission – see [Permissions](#permissions)).
- **World clock** (off by default – turn it on in *Launcher settings → World clock* or via *Add gadget*) – two cities side by side, each with an analog face (light by day, dark at night), the digital time (follows the 12/24-hour setting) and the date in small text. Choose the cities in settings or via *Hold OK → Change clock 1/2*: popular cities (incl. Mecca, Medina, Beijing) or search every time zone on the TV. Updates once a minute.
- **System monitor** (off by default – turn it on in *Launcher settings → Gadgets* or via *Add gadget*) – gauges updated every 2 seconds while the home screen is visible (paused otherwise):
  - **RAM** used / total and **Storage** used / total.
  - **CPU**: real load where the TV allows it; Android 8+ hides this from apps, so most TVs show the current clock speed as a share of the maximum (with GHz).
  - **Temp**: the chip (CPU/SoC) temperature from the TV's thermal sensors, in °C or °F (follows the weather unit setting). Shows *N/A* on TVs that block these sensors.
  - **Power** in watts where the device reports it (battery-powered boxes). TVs don't report power draw, so it shows **Thermal** load instead (how close the chip is to throttling), or *N/A*.
  - Show or hide each gauge in *Launcher settings → Gadgets* or via *Hold OK → Show / hide gauges*. Hidden gauges aren't read at all, and the gadget shrinks to fit. Hiding every gauge turns the gadget off (it comes back with all gauges when turned on again).
- **Background opacity** (0–100%, default 60%) for the whole gadget row, or per gadget via *Hold OK → Background opacity*.
- Gadgets can be refreshed (OK), moved or removed (hold OK).

### App rows

- Create your own rows (e.g. "Movies", "Kids"), add apps, rename, reorder apps, move rows up/down, or delete rows.
- Apps are shown as TV banners when available.

### Background

- **Default** (black), **Solid colour**, **Gradient** (Vertical, Horizontal, Center, Square, Circle), **Image / GIF / WebP** or **Video (MP4 / WebM)**.
- Supported files:
  - Images: JPEG, PNG, WebP (incl. animated), GIF, BMP, HEIC/HEIF, AVIF (HEIF/AVIF need Android 9+).
  - Videos: MP4, M4V, WebM, MKV, 3GP, MOV.
- Pick files from the TV (MediaStore **and** a direct scan of *Pictures, Download, DCIM, Movies* – so files copied via USB/adb show up immediately), the system file picker, or an `https://` link.
- Fit: *Stretch to fill screen* or *Zoom to fill (keep aspect ratio)*.
- **Blur** (0–10): a camera-style lens (bokeh) blur – each point is spread over a disc in linear light, like an out-of-focus lens, so colours and highlights stay rich instead of looking low-resolution. Level 1 is a gentle softening; each step is stronger. Computed once when the image loads so scrolling stays smooth. GIFs/videos use Android's live blur (Android 12+).
- **Opacity** (10–100%).
- Animated GIF / WebP play as live backgrounds (Android 9+); videos play muted and loop, pausing while another app is open.
- If no image/video has been chosen, the solid colour is shown instead.
- **OLED TV** option: the background slowly drifts a few pixels to reduce burn-in.

### Screensaver

- **MHz Night Sky with clock** (built-in, default): twinkling stars drifting in layers across a black sky, a moon (full, crescent or gibbous) slowly travelling across the screen, and occasional shooting stars, plus a digital clock with seconds and the date that fades to a new random position every 30 seconds. The clock follows the profile's 12/24-hour setting. Everything keeps moving, so nothing stays in one place (safe for OLED TVs).
- **MHz Night Sky**: the same sky without the clock.
- The moon button (or *Launcher settings → Screensaver → Start screensaver now*) starts the chosen screensaver. Press any button to close it.
- **Start screensaver after**: 1, 5, **10 (default)**, 15, 30 or 60 minutes without a button press on the launcher, or *Never*. This timer runs while Mega Launcher is on screen (an open launcher dialog postpones it); inside other apps, the TV's own screensaver setting applies. If the chosen screensaver can't be started automatically, the timer starts Night Sky instead of opening a settings screen.
- Built-in screensavers and *TV default screensaver* need no permission. Other installed screensavers (e.g. Ambient Mode, Aerial Views) need a one-time permission to switch the TV's active screensaver (see [Permissions](#permissions)).
- Both Night Sky variants are also listed in the TV's own screensaver settings, so the TV can use them when idle in any app.

### Permissions

Android TV only lets built-in apps add app widgets and change the screensaver, and has no on-screen prompt for other launchers. Grant both once, either:

- **On the TV (no PC):** install [Shizuku](https://github.com/RikkaApps/Shizuku/releases), start it via *Wireless debugging* (Android 11+), then use *Launcher settings → Permissions → Allow app widgets & screensaver switching*. Shizuku does **not** root the TV.
- **From a PC:**
  ```
  adb shell appwidget grantbind --package com.megalauncher --user 0
  adb shell pm grant com.megalauncher android.permission.WRITE_SECURE_SETTINGS
  ```

The grants survive reboots and app updates (they are removed only when the app is uninstalled).

### Launcher settings

Open with the sliders icon (top right):

- Profile: name, picture, switch, add, delete
- Clock: 24-hour time on/off, date format
- World clock: on/off, clock 1 city, clock 2 city
- Weather on/off, °F on/off, location, weather.com API key, auto-refresh interval, refresh now
- Prayer times on/off, automatic zone, zone, highlight current prayer, show Syuruk, refresh
- Background type, colours, image/video, fit, blur, opacity, OLED protection
- Gadget background opacity (all gadgets), system monitor on/off and which gauges to show
- Screensaver (MHz Night Sky, with clock, TV default or another installed one), start after (idle time), start now
- Permissions (Shizuku)
- Shortcut to Android settings
- About: app version, source code link, license (MIT), AI credit (built with GitHub Copilot · Claude Opus 5.5)

### Other

- **Apps** screen: grid of every installed app, with the same hold-OK menu plus "Add to row".
- **Search**: filters installed apps as you type.
- The **Home** button always returns to the top of the home screen.
- Smooth TV-style focus scrolling: the focused row stays near the top of the screen; rows scroll only near their edges.
- Holding OK to open a menu never triggers a menu item on release.

---

## Data sources and privacy

| Feature            | Service                                                       | When it is contacted                                                                                          |
| ------------------ | ------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| Weather            | `api.weather.com` (with your key) or `api.open-meteo.com` | At the chosen auto-refresh interval (default hourly) while the weather gadget is enabled, or when you refresh |
| City search        | `geocoding-api.open-meteo.com`                              | Only when you search for a city                                                                               |
| Automatic location | `ipwho.is` (IP lookup)                                      | At most every 12 h, only when location is set to automatic                                                    |
| Prayer times       | `www.e-solat.gov.my` (JAKIM)                                | About once a month, or when the zone changes                                                                  |
| Background link    | The URL you enter                                             | Only when you import an image or video                                                                        |

No other servers are contacted. Settings, profiles, profile pictures, backgrounds and caches are stored only on the TV. Shizuku (if used) runs locally on the TV.

---

## Project structure

```
app/src/main/java/com/megalauncher/
├── MainActivity.kt            Home activity (HOME + LEANBACK_LAUNCHER)
├── LauncherViewModel.kt       State, layout editing, refresh/caching logic
├── data/
│   ├── AppRepository.kt       Installed apps, icons, launch/uninstall/store intents, screensavers
│   ├── BackgroundRepository.kt Background image/video import, decoding, blur, media browsing
│   ├── LocationRepository.kt  IP location + city search
│   ├── Models.kt              Persisted configuration (kotlinx.serialization)
│   ├── PrayerRepository.kt    JAKIM e-Solat fetch + 1-month cache
│   ├── PrayerZones.kt         JAKIM zone list + nearest-zone lookup
│   ├── ProfileRepository.kt   Profile list + profile pictures
│   ├── ShizukuHelper.kt       One-time permission grants via Shizuku
│   ├── Storage.kt             Config store, file cache, HTTP helpers
│   ├── SystemStats.kt         RAM, CPU, temperature, power/thermal and storage readings
│   ├── WeatherRepository.kt   weather.com / Open-Meteo + cache
│   ├── WidgetHostManager.kt   Android AppWidget hosting
│   └── WorldClocks.kt         World clock cities and time zones
├── screensaver/
│   ├── NightSky.kt            Night Sky activity (launcher) + DreamServices (TV screensaver)
│   └── NightSkyView.kt        Animated stars, moon, shooting stars and moving clock
└── ui/
    ├── Background.kt          Background rendering, OLED drift, colour & media pickers
    ├── Components.kt          Shared focusable TV components, dialogs, scroll behaviour
    ├── Dialogs.kt             Gadget, opacity, city, zone, screensaver and row dialogs
    ├── Gadgets.kt             Weather, prayer and app-widget gadgets
    ├── Home.kt                Home screen rows
    ├── LoopingVideoView.kt    Muted looping video background (TextureView + MediaPlayer)
    ├── ProfileUi.kt           Avatars, profile switcher, profile picture picker
    ├── Screens.kt             Top bar, clock, Apps, Search, Settings
    ├── SystemGadget.kt        System monitor gauges
    ├── Theme.kt               Colours and icons
    └── WorldClockGadget.kt    Analog + digital world clocks

app/src/main/res/drawable/
└── avatar_*.xml               8 built-in profile icons (vector drawables)

LICENSE                        MIT License
DEPLOY.md                      Build, install and troubleshooting guide
```

## Tech stack

- Kotlin 2.1, Jetpack Compose, Compose for TV (`androidx.tv:tv-material`)
- Android Gradle Plugin 8.7.3, Gradle 8.11.1, JDK 17 or 21
- `minSdk 26` (Android 8.0), `targetSdk 35`
- Shizuku API 13.1.5 (optional permission helper)

## Versioning

The version is set in [app/build.gradle.kts](app/build.gradle.kts) (`defaultConfig`):

```kotlin
versionCode = 1        // +1 for every APK you install as an update
versionName = "0.1.1"  // the version shown to users
```

## Configuration notes

- **weather.com API key**: The Weather Company does not offer a free public API. Enter your own key in *Launcher settings → Weather → Weather.com API key*. Without a key, Open-Meteo is used.
- **Prayer zone accuracy**: automatic zone detection uses approximate zone centres. Near zone borders, choose your zone manually.
- **Screen resolutions**: works on 720p, 1080p and 4K TVs. Android TV lays the UI out in density-independent units (720p ≈ 1.33×, 1080p 2×, 4K up to 4×), so every screen shows the same layout. App icons and banners are rendered for the screen's density (capped on 4K to save RAM). Background images keep full 4K sharpness on Android 9+; blurred images (and all images on Android 8) are processed at up to 1080p to avoid running out of memory – the blur hides the difference. 4K videos play only if the TV's decoder supports them.
- **Performance**: use the **release** build on the TV – debug builds (and the emulator) are noticeably slower. To keep RAM and CPU use low:
  - App banners are loaded only for apps shown in rows (with a small memory cache); app-list reloads are batched and reuse unchanged apps.
  - Weather and prayer data are only re-read when stale; prayer times are cached per zone.
  - Animated backgrounds pause while another app is open; Night Sky runs at 30 fps.
  - The TV media browser decodes small thumbnails a few at a time (video thumbnails are taken directly at thumbnail size).
  - The world clock redraws once a minute; the system monitor reads stats only while the home screen is visible, and its reader is only created once the gadget is shown.

## Building and installing

See **[DEPLOY.md](DEPLOY.md)**.

## Contributing

Issues and pull requests are welcome at [https://github.com/mhzworld/megalauncher](https://github.com/mhzworld/megalauncher). Please describe the TV model and Android version when reporting a problem.

## Credits

- Built with AI assistance: **GitHub Copilot · Claude Opus 5.5** (also shown in *Launcher settings → About*).
- Weather: [Open-Meteo](https://open-meteo.com) and The Weather Company.
- Prayer times: JAKIM e-Solat.
- Optional permission helper: [Shizuku](https://github.com/RikkaApps/Shizuku).

## License

Mega Launcher is open source under the **[MIT License](LICENSE)** © 2026 mhzworld – [https://github.com/mhzworld/megalauncher](https://github.com/mhzworld/megalauncher).

Third-party libraries (AndroidX / Jetpack Compose, Kotlin, kotlinx, Shizuku API) are used under the Apache License 2.0. Weather data comes from Open-Meteo (CC BY 4.0) or The Weather Company; prayer times come from JAKIM e-Solat.
