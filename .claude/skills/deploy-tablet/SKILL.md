---
name: deploy-tablet
description: Build Readana and install/run it on the Xiaomi Pad 8 over adb, including HyperOS-specific troubleshooting. Use for "put it on my tablet", "install on the pad", "deploy and run".
---

# Deploy to the Xiaomi Pad 8

```bash
./gradlew :app:assembleDebug
adb devices                       # expect exactly one "device" (not "unauthorized")
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.readana.debug/dev.readana.MainActivity
adb logcat -v color -s Readana:V AndroidRuntime:E   # watch for crashes; Ctrl+C when satisfied
```

## Troubleshooting (HyperOS quirks — full detail in docs/DEVICE.md)

| Symptom | Fix |
|---|---|
| `adb devices` empty | Cable/Wi-Fi debugging off. Wireless: Developer options → Wireless debugging → pair with `adb pair <ip>:<port>` then `adb connect <ip>:<port>` |
| `unauthorized` | Accept the RSA prompt on the tablet |
| `INSTALL_FAILED_USER_RESTRICTED` | Developer options → "Install via USB" must be ON (needs Mi account sign-in; may need mobile data/hotspot during verification) |
| App killed in background during downloads | Settings → Apps → Readana → Battery saver → **No restrictions**; enable Autostart; these can reset after HyperOS OTAs |
| Screen rotation/size oddities | Test in HyperOS Workstation mode too — the app must handle free resizing (API 36 ignores orientation locks on tablets) |

Debug builds install as `dev.readana.debug` next to any release build (`dev.readana`).
Report to the user: install result + first logcat lines (or the crash stack if it crashed).
