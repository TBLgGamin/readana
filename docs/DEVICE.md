# Target device: Xiaomi Pad 8

Facts gathered 2026-08-20; verify against the tablet when something looks off.

## Hardware

| | Xiaomi Pad 8 | Xiaomi Pad 8 Pro |
|---|---|---|
| SoC | Snapdragon 8s Gen 4 (SM8735) | Snapdragon 8 Elite (SM8750-AB) |
| RAM / storage | 8/128, 8/256, 12/256 GB (UFS 3.1/4.1) | up to 12/512 GB global |
| Display | 11.2" IPS LCD, **3200 × 2136, 3:2**, ~345 ppi, 144 Hz adaptive, 800 nits, HDR10 / Dolby Vision | same |
| Battery | 9200 mAh, 45 W | 9200 mAh, 67 W |
| Stylus | Focus Pen Pro (magnetic; 16 384 pressure levels, hover) | same |
| microSD | no | no |

Sources: [GSMArena Pad 8](https://www.gsmarena.com/xiaomi_pad_8-14184.php),
[GSMArena Pad 8 Pro](https://www.gsmarena.com/xiaomi_pad_8_pro-14183.php),
[Gizmochina global launch](https://www.gizmochina.com/2026/02/28/xiaomi-pad-8-and-pad-8-pro-launched-globally-specs-price/).

Implications for the app:

- 3:2 is closer to a paper page than 16:10; two-page landscape spreads fit well.
- Window size classes: portrait ≈ *Medium/Expanded* width, landscape ≈ *Expanded/Large*. Use
  `material3-adaptive` (`NavigationSuiteScaffold` picks a rail automatically) and list-detail scenes.
- 144 Hz: page-turn animations should be cheap; avoid per-frame allocation in the reader.
- Stylus hover/pressure come through standard `MotionEvent`s (Xiaomi pen gestures are Xiaomi-app only).

## Software

- Ships **HyperOS 3 on Android 16 (API 36)**; HyperOS 3.1 (still Android 16) rolled out from
  March/April 2026. **HyperOS 4 / Android 17 (API 37)** entered China beta on 2026-08-14 with Pad 8
  in the first wave — no stable date yet. `targetSdk` is therefore 36; bump to 37 deliberately.
- Global units ship Google Play / GMS, so Play-distributed libraries and the Play Store work. Readana
  is side-loaded anyway.
- Targeting API 36+ on sw ≥ 600 dp: the system **ignores orientation/resizability/aspect-ratio
  locks**; edge-to-edge is mandatory; predictive back is on by default. Design every screen to
  resize (HyperOS *Workstation mode* runs apps in freely resizable floating windows).

Sources: [Android 16 behaviour changes](https://developer.android.com/about/versions/16/behavior-changes-16),
[HyperOS 3.1 rollout](https://www.gizmochina.com/2026/03/11/xiaomi-hyperos-3-1-rollout-timeline/),
[HyperOS 4 devices](https://www.gizmochina.com/2026/06/20/xiaomi-hyperos-4-update-supported-devices-release-date-and-more/).

## Developer setup on the tablet

1. **Developer options**: Settings → About tablet → tap **OS version** 7 times.
2. Settings → Additional settings → Developer options → enable **USB debugging** and
   **Wireless debugging** (pairing code flow works like stock Android: `adb pair host:port`).
3. **Install via USB** (needed for `adb install`) and **USB debugging (Security settings)** require
   signing in to a **Mi account**; users report the check sometimes fails on Wi-Fi only and needs a
   phone hotspot. Without it installs fail with `INSTALL_FAILED_USER_RESTRICTED`. Alternative:
   copy the APK to the tablet and install it from a file manager ("Install unknown apps").
   Sources: [HyperOS USB debugging](https://gsmchina.com/how-to-enable-usb-debugging-mode-on-hyperos-6162/),
   [INSTALL_FAILED_USER_RESTRICTED](https://www.repeato.app/troubleshooting-install-failed-user-restricted-error-on-redmi-devices/).
4. **Background work**: Xiaomi is one of the most aggressive OEMs at killing background apps
   ([dontkillmyapp.com/xiaomi](https://dontkillmyapp.com/xiaomi)). For downloads and conversion:
   - Settings → Apps → Readana → Battery saver → **No restrictions**;
   - enable **Autostart** / background autostart; lock the app in Recents if downloads die;
   - settings can reset after OTA updates.
   In code: WorkManager + foreground service with `dataSync` type, jobs kept short
   (Android 15+ caps `dataSync` foreground services at 6 h per 24 h).
5. **Google developer verification** (sideloading gate) starts 2026-09 in BR/ID/SG/TH and 2027
   globally; ADB installs are exempt and a free limited-distribution account covers 20 devices.
   [Google help](https://support.google.com/android-developer-console/answer/16561738).

## Handy commands

```bash
adb devices                                   # pairing done?
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.readana.debug/dev.readana.MainActivity
adb logcat -v color -s Readana:V AndroidRuntime:E
adb shell dumpsys deviceidle whitelist +dev.readana.debug   # exempt from Doze while testing downloads
```
