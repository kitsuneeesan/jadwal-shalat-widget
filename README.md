<div align="center">

<br/>
<br/>

<img src="docs/logo.png" alt="Jadwal Shalat icon" width="160" />

# Jadwal Shalat

### Glassmorphism Prayer Times Widgets for Android

<br/>

[![Version](https://img.shields.io/badge/version-0.2.0-A8E6B4?style=for-the-badge&labelColor=0d1117)](https://github.com/kitsuneeesan/jadwal-shalat-widget/raw/main/release/jadwal-shalat.apk)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white&labelColor=0d1117)](#download)
[![Kotlin](https://img.shields.io/badge/Kotlin-no%20dependencies-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white&labelColor=0d1117)](#build)
[![Data](https://img.shields.io/badge/data-EQuran.id-F2A65A?style=for-the-badge&labelColor=0d1117)](https://equran.id/apidev/shalat)

<br/>

[**Download**](#download) · [**Features**](#features) · [**Usage**](#usage) · [**Build**](#build) · [**Disclaimer**](#disclaimer)

</div>

> [!IMPORTANT]
> Prayer times come from the public [EQuran.id](https://equran.id/apidev/shalat) API and cover regencies/cities in Indonesia only. This project is not affiliated with EQuran.id.

---

<div align="center">

<img src="docs/banner.png" alt="Widgets in dark and light mode" width="100%" />

<h1><a id="features"></a>Features</h1>

<table>
  <tr>
    <td width="50%" valign="top">

#### Widgets
- **One widget, every size.** It rearranges itself as you resize it, from a 2x1 glance up to a full 4x2 card.
- **Focus on the prayer happening now.** The running prayer is highlighted with time left until its end. Fajr ends at sunrise, Isha at the next Fajr.
- **Deadline bar** that shifts from green to amber to red as the prayer's time runs out.
- **Next prayer at a glance** in the schedule list.
- **Tap to refresh.** Tapping reloads the schedule and re-detects your location without opening anything.

    </td>
    <td width="50%" valign="top">

#### Look & Feel
- **Frosted glass** that follows the system theme: milky glass in light mode, smoky glass in dark mode.
- **Minimal line icons** for each prayer.
- **Speaks your language.** English, or Indonesian when the phone is set to Indonesian.
- **No app icon clutter.** Settings live behind the widget (long-press → Settings).

#### Smart Defaults
- **Automatic location** from the phone, matched to the API's regency/city list. A manual picker is available too.
- **Offline-friendly.** The month's schedule is cached on the phone.

    </td>
  </tr>
</table>

</div>

---

<div align="center">

<h1><a id="download"></a>Download</h1>

<a href="https://github.com/kitsuneeesan/jadwal-shalat-widget/raw/main/release/jadwal-shalat.apk"><img src="https://cdn.jsdelivr.net/gh/JMcrafter26/badges@main/src/assets/supported/android/cozy.svg" alt="Download for Android" height="55"/></a>

Grab [`release/jadwal-shalat.apk`](https://github.com/kitsuneeesan/jadwal-shalat-widget/raw/main/release/jadwal-shalat.apk) (Android 8.0+).
Sideloading requires enabling "Install unknown apps" for whichever app you open it with.

</div>

---

<div align="center">

<h1><a id="usage"></a>Usage</h1>

</div>

1. Install the APK.
2. Long-press the home screen → **Widgets** → **Prayer Times**, then pick a size (you can resize it later).
3. In the settings screen that opens, allow location (or turn off *Automatic location* and pick your province/city), then tap **Done**.

Two optional extras are in the widget's settings:

| Option | Why |
|---|---|
| **Allow countdown to update every minute** | Grants exact alarms, so the countdown never lags behind the clock. |
| **Follow location while app is closed** | Grants background location, so the widget can follow you while you travel. |

---

<div align="center">

<h1><a id="build"></a>Build</h1>

</div>

Requires JDK 17 and the Android SDK (platform 36). The app has no third-party dependencies.

```sh
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Widget layouts are generated from `tools/gen_widgets.py`; edit it and run `python3 tools/gen_widgets.py`.
Colours live in `res/values/colors.xml` (light) and `res/values-night/colors.xml` (dark).

Debug builds include two extra screens:

```sh
adb shell am start -n com.rizki.jadwalshalat/.PreviewActivity                      # every layout
adb shell am start -n com.rizki.jadwalshalat/.ShowcaseActivity --es scene night    # screenshots (night | day | logo)
```

---

<div align="center">

<h1><a id="disclaimer"></a>Disclaimer</h1>

</div>

- **Check with your local mosque.** Times are as published by the API. Some regions follow different conventions.
- **Privacy:** your location is only used to work out your regency/city name, through Android's Geocoder (which may use an online service). The app only sends the province/city name to EQuran.id.
- **APK signing:** the APK is a debug-signed build.
