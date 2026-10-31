# SandPause

A PIN-protected parental lock for Android. It limits a child’s screen time, and protects airtime, mobile data and purchases.

- Fully offline: no account, no backend, no analytics, no INTERNET permission.
- Kotlin, Jetpack Compose (Material 3), single activity, MVVM, Room, DataStore, Hilt, AlarmManager, WorkManager, TextToSpeech.
- `minSdk` 26, `targetSdk` 36. English and Luganda (Luganda is a placeholder pending native review).

## Build

### Get the APK from CI

Every push to `claude/lucid-euler-v9ppyx` that touches `sandpause/` runs the **SandPause APK** workflow (`.github/workflows/sandpause-apk.yml`). It runs the core unit tests, builds the debug APK, and uploads it as the `SandPause-debug-apk` artifact. Debug APKs are signed with the debug key, so you can install one directly.

### Build locally

Requirements: JDK 17 (JDK 21 also works), Android SDK with `platforms;android-36` and `build-tools;36.0.0`.

```bash
cd sandpause
./gradlew :core:test                 # pure-Kotlin unit tests
./gradlew :app:assembleDebug         # debug APK → app/build/outputs/apk/debug/
./gradlew :app:assembleRelease       # minified release APK, unsigned unless you add a signing config
```

Or open the `sandpause/` folder in Android Studio and press Run.

### Project layout

```
sandpause/
├── core/                       Pure Kotlin/JVM logic, unit-tested on the JVM
│   └── …/core/
│       ├── time/               ElapsedDeadline, ClockGuard, TimeScale
│       ├── security/           PinHasher (PBKDF2), PinPolicy, AttemptLimiter
│       ├── schedule/           LockSchedule, ScheduleEvaluator, Allowance
│       ├── policy/             AccessPolicy, KnownPackages
│       ├── lock/               LockSession
│       └── hourglass/          HourglassMachine, VoiceLine
└── app/src/main/java/com/sandpause/app/
    ├── admin/                  Device owner (DPM), permission checks, DeviceAdminReceiver
    ├── data/                   Room (db/), DataStore settings (settings/), repositories (repo/)
    ├── lock/                   LockEngine, LockService, ChildLockActivity, AppGuardService,
    │                           ForegroundGuard, LockOverlay, BootReceiver, DataBudgetEnforcer
    ├── scheduling/             Exact alarms, schedule receiver, WatchdogWorker
    ├── security/               PinManager, ParentSession
    ├── voice/                  TextToSpeech announcer with captions
    ├── ui/                     theme/, common/, child/ (hourglass, lock screen), parent/, nav/
    └── debug/                  DebugHooks (fast time). Debug builds only; release has a no-op.
```

## Locking modes

SandPause picks the strongest mode the phone supports.

1. **Full lock (device owner, primary).** When SandPause is device owner it pins the lock screen with Lock Task Mode. Only SandPause, the parent-approved apps and the telephony in-call UI are allowed. Uninstalling SandPause, safe mode and factory reset from Settings are blocked, and Lock Task features are set to none, which blocks Home, Recents, notifications and the status bar.
2. **Standard lock (fallback).** Without device owner, an accessibility service detects the foreground app. Any blocked app sends the child back to the lock screen, and a full-screen overlay covers the blocked app if the overlay permission is granted. Usage access is a backup detector.

### Set device owner (one time, with adb)

Device owner can only be set on a phone with **no accounts added** (remove Google and other accounts first, or factory-reset the phone). With USB debugging on:

```bash
adb shell dpm set-device-owner com.sandpause.app/.admin.SandPauseAdminReceiver
```

The app shows this command, with a copy button, in **Parent area → Full lock setup**. After it succeeds, open SandPause and tap **Apply protection settings now**.

To remove device owner later: `adb shell dpm remove-active-admin com.sandpause.app/.admin.SandPauseAdminReceiver`.

### Standard-mode permissions

| Permission | Why |
| --- | --- |
| Show over other apps (`SYSTEM_ALERT_WINDOW`) | Full-screen lock overlay over a blocked app |
| Usage access (`PACKAGE_USAGE_STATS`) | Backup way to detect the foreground app; screen time |
| Accessibility service | Main detector in standard mode. `canRetrieveWindowContent` is **false**: the service only learns which package is in front |
| Exact alarms | Schedules start at the exact minute |
| Battery optimisation exemption | Keeps the service alive; OEM steps are in the app |
| Phone calls (`CALL_PHONE`) | Emergency and parent-contact calls from the lock screen |
| Notifications | Required foreground-service notification |

The app explains each one on the Permissions screen. The Accessibility option needs the prominent disclosure and an "I understand and agree" switch before its Settings button enables.

## Play Store policy notes

Parental-control apps have strict Play policies. Before publishing:

- **Accessibility API.** Google Play requires a prominent in-app disclosure and consent before the Accessibility settings open, plus a use case that matches the declared purpose. SandPause shows both. Its use is limited to detecting the foreground app, with `canRetrieveWindowContent=false`. Describe this exactly in the Play Console declaration form.
- **QUERY_ALL_PACKAGES.** Allowed for parental-control apps that must list every installed app, but Play requires a declaration form.
- **Device admin and device owner.** Device owner is set by an administrator with adb, not by the Play install flow. Do not ask consumers to set it through a non-adb route.
- **Foreground service type.** Declared as `specialUse`, with a PROPERTY_SPECIAL_USE_FGS_SUBTYPE explanation in the manifest.
- **Data safety.** No data leaves the device. Declare that in the Data safety form.
- **Child-directed content.** The app is a tool for parents; review Play's Families policy before listing.

## Anti-tamper and reliability

- **Clock-proof timers.** Lock time is measured on `SystemClock.elapsedRealtime`, persisted with its boot ID, and never shortened by changes to the wall clock or time zone. Clock jumps are detected and logged (`CLOCK_CHANGED`).
- **Restore on boot.** `BOOT_COMPLETED` and `LOCKED_BOOT_COMPLETED` restore a running lock. Settings and the lock snapshot live in device-protected storage, so this works before the first unlock.
- **Service restart.** The foreground service returns `START_STICKY`, reschedules itself when swiped away, and a 15-minute WorkManager watchdog restarts it.
- **OEM guidance.** The Full lock screen gives step-by-step battery and auto-start instructions for Tecno, Infinix, Itel, Xiaomi, Redmi, POCO and Samsung.
- **Disabling protection.** Turning off device admin is logged and brings the lock back. Turning off accessibility is logged, and the lock returns when the service comes back.
- **PIN security.** 4–6 digits, hashed with PBKDF2-HMAC-SHA256 (120 000 iterations, 16-byte salt). Five wrong attempts start a 5-minute cooldown, and each further lockout doubles it. The recovery-question path shares the same limiter.

## Testing

| Layer | What | How |
| --- | --- | --- |
| Core unit tests | Timers (`ElapsedDeadline`, reboot handling, clock-jump detection), PIN hashing and cooldowns, schedule evaluation (overnight windows, next start), allowance, access policy, hourglass yellow → red → yellow → green, voice milestones | `./gradlew :core:test` (38 tests, run in CI) |
| UI test | Hourglass transitions through the real Compose composable, with touch input and a fake clock | `./gradlew :app:connectedDebugAndroidTest` on an emulator or device (compiled in CI, not run there) |

## Debug "fast time"

Debug builds have a **Fast time** switch in the parent home screen. When it is on, 1 minute = 5 seconds, so a 30-minute lock lasts 2.5 minutes. The release source set has a no-op `DebugHooks`, so the switch does not exist in release builds.

## Known limits

- **Device-wide mobile data.** `NetworkStatsManager` may refuse mobile totals to apps without privileged phone-state access. SandPause then falls back to `TrafficStats`, which counts device-wide mobile bytes from the first reading of the day. Per-app data is not shown.
- **Luganda voice.** Speech uses Android TextToSpeech. If the device has no Luganda voice, the line is spoken in English and the on-screen caption stays Luganda. The Luganda strings are placeholders marked for native review (`values-lg/strings.xml`).
- **Emergency and in-call screens.** How in-call screens behave inside Lock Task Mode varies by manufacturer. Test emergency calling on each target phone.
- **Schedule starts use the wall clock.** A schedule window is detected from the wall clock, so a clock change at exactly the wrong moment can skip one start. The change is logged. Lock length is always measured on the monotonic clock.
- **Bundled audio clips.** The `res/raw` clip hook is not wired; speech uses TTS only.
