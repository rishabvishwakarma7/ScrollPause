# ScrollPause

> "Notice your reels. Own your time."

ScrollPause is a native Android app that counts Instagram Reels you view, displays a floating counter, and shows a seven-day history — all stored locally on your device. No accounts, no internet connection, no data leaves your phone.

---

## Setup

### Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Android Studio | 2025.1.3 (Meerkat) or later | Provides JDK 21 (JBR) |
| Android SDK | API 26–36 | Installed via SDK Manager |
| Build Tools | 36.1.0 | Installed via SDK Manager |

### 1. Open the project

1. Launch Android Studio.
2. **File → Open** → select the `ScrollPause` folder inside this repository.
3. Wait for Gradle sync to complete (first sync downloads dependencies — ~5 min on a new machine).

### 2. Set the Gradle JDK

Android Studio sometimes needs to be told which JDK to use for Gradle builds:

1. **File → Settings → Build, Execution, Deployment → Build Tools → Gradle**
2. Set **Gradle JDK** to `jbr-21 (JetBrains Runtime)` — this is the JDK bundled with Android Studio.
3. Click **OK** and wait for sync.

### 3. Build the debug APK (Windows)

Open a PowerShell window in the `ScrollPause` project folder and run:

```powershell
# Tell Windows where to find Java (Android Studio's bundled JBR)
$env:JAVA_HOME = "D:\Program Files\Android\Android Studio\jbr"
# OR if Android Studio is on C:
# $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

.\gradlew.bat assembleDebug
```

**Output APK location:**

```
app\build\outputs\apk\debug\app-debug.apk
```

### 4. Install on a physical Android device

**Requirements:** Android 8.0+ (API 26+), USB debugging enabled.

```powershell
# Check your device is connected
C:\Users\risha\AppData\Local\Android\Sdk\platform-tools\adb.exe devices

# Install the debug APK
C:\Users\risha\AppData\Local\Android\Sdk\platform-tools\adb.exe install -r "app\build\outputs\apk\debug\app-debug.apk"
```

**Enable USB debugging on your phone:**
Settings → About phone → tap "Build number" 7 times → go back → Developer options → USB debugging: On

---

## Permissions Setup (on device)

After installing, open ScrollPause. The onboarding flow will guide you through:

### 1. Accessibility access (required)

1. ScrollPause → Onboarding → tap **Enable Accessibility Access**
2. Find **ScrollPause Reel Counter** in the list.
3. Toggle it **On** and accept the confirmation dialog.

> **What it reads:** Screen structure metadata (class names, window transitions, scroll events) for `com.instagram.android` only. It never reads your messages, captions, or personal content.

### 2. Display over apps (optional — for floating counter)

1. Tap **Allow Display Over Apps** in onboarding (or Settings → Permissions).
2. Find ScrollPause and allow.

> Note: If you skip this, the floating counter won't appear. Everything else works.

---

## Architecture

```
com.scrollpause.app
├── ui/                     ← Jetpack Compose screens + ViewModels
│   ├── onboarding/         ← 3-page onboarding with permission guidance
│   ├── home/               ← Dashboard: count, progress, 7-day chart
│   ├── history/            ← Daily stats list
│   ├── settings/           ← All configurable options
│   ├── diagnostics/        ← Debug-only: live detection state log
│   ├── navigation/         ← Route definitions
│   └── theme/              ← Material 3 colors, typography
├── service/                ← AccessibilityService and sub-components
│   ├── ScrollPauseAccessibilityService.kt
│   ├── detector/           ← State machine (OUTSIDE_APP → REELS_VIEWER)
│   ├── counter/            ← Dedup + Room write on confirmed exposure
│   ├── session/            ← Monotonic-clock session timing
│   └── overlay/            ← Floating bubble (TYPE_ACCESSIBILITY_OVERLAY)
├── data/
│   ├── db/                 ← Room entities + DAOs + AppDatabase
│   ├── prefs/              ← DataStore preferences repository
│   └── repository/         ← ReelRepository (bridge service ↔ UI)
├── domain/model/           ← DailyStats, UserPreferences (UI domain objects)
├── AppContainer.kt         ← Manual DI — creates and holds all singletons
└── ScrollPauseApplication.kt  ← Application class, notification channels
```

### Key engineering decisions

**No Hilt DI** — One module, one Activity, one Service. `AppContainer` is a plain class instantiated in Application. If the project grows, Hilt is a natural addition.

**TYPE_ACCESSIBILITY_OVERLAY for the bubble** — Does not require `SYSTEM_ALERT_WINDOW` permission. The overlay is created inside the AccessibilityService process. Least-privilege approach.

**Monotonic clock for session timing** — `SystemClock.elapsedRealtime()` is immune to timezone changes and NTP adjustments, making elapsed duration accurate.

**No foreground service** — The AccessibilityService is kept alive by Android as long as it's enabled by the user. A separate foreground service is not needed.

**Room transactions for counts** — The `incrementReelCount` DAO function is wrapped in a `@Transaction` to prevent lost increments from concurrent events.

---

## Detection

ScrollPause uses Android's AccessibilityService API to detect when Instagram's Reels full-screen viewer is active. It observes:

- `TYPE_WINDOW_STATE_CHANGED` — detects Activity/screen transitions
- `TYPE_VIEW_SCROLLED` — detects Reel navigation (with 400ms debounce + dedup)
- `TYPE_WINDOWS_CHANGED` — detects overlay dialogs (comments, share sheet → OBSCURED state)

**Accuracy limitations:**

1. Instagram changes its internal Activity class names with app updates. The detection heuristics need to be validated and updated using the DiagnosticsScreen in a debug build.
2. The detector is conservative — when it cannot confidently identify the Reels viewer, it enters IN_APP_UNKNOWN and does not count.
3. Counts are approximate. The app displays them as awareness data, not exact measurements.

**To validate detection on a real device:**

1. Build the debug APK and install it.
2. Enable Accessibility in Settings.
3. Open ScrollPause → navigate to DiagnosticsScreen (debug builds only — look in the nav drawer or add a route).
4. Open Instagram and navigate to Reels.
5. Observe the event log for `WINDOW_STATE_CHANGED` events — note the `className` values.
6. Update `REELS_WINDOW_CLASSES` in `ReelDetector.kt` with the observed class name fragments.

---

## Building a Signed Release APK

The debug APK is for testing only. For a release APK:

1. Generate a keystore (do this once; store it securely, NOT in source control):
   ```powershell
   $env:JAVA_HOME = "D:\Program Files\Android\Android Studio\jbr"
   & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v -keystore scrollpause-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias scrollpause
   ```
2. Add signing config to `app/build.gradle.kts` (using environment variables or a local, gitignored `keystore.properties` file — never hardcode the password).
3. Run:
   ```powershell
   .\gradlew.bat assembleRelease
   ```

See the [official signing guide](https://developer.android.com/studio/publish/app-signing) for details.

**⚠ Never commit your keystore file or passwords to git.**

---

## Known Limitations

| # | Limitation | Impact |
|---|-----------|--------|
| L-01 | Instagram class names change with app updates | Detection may fail after major Instagram updates; use DiagnosticsScreen to re-calibrate |
| L-02 | TYPE_ACCESSIBILITY_OVERLAY Z-order varies by Android version | Bubble may appear under Instagram's toolbar on some versions |
| L-03 | No emulator support for Accessibility detection | Real device required for detection testing |
| L-04 | Session timer doesn't split across midnight | A session started at 11:50pm and ending at 12:10am attributes all time to the start day |
| L-05 | Play Store submission | Apps using AccessibilityService for non-assistive purposes require a Prominent Disclosure form |
| L-06 | Process death during active session | Session checkpoint (DataStore) mitigates but ~30s of data may be lost |

---

## Physical Device Test Checklist

Run these manually on a real device with Instagram installed.

| # | Test | Expected |
|---|------|----------|
| D-01 | Open Instagram → scroll to Reels tab | Counter appears, state = REELS_VIEWER |
| D-02 | Swipe to next Reel | Counter +1 |
| D-03 | Swipe rapidly through 5 Reels | Counter ≤ 5 (dedup may merge rapid events) |
| D-04 | Swipe backward | Counter +1 (documented re-exposure) |
| D-05 | Let video loop without swiping | Counter unchanged |
| D-06 | Tap to open comments | Counter unchanged, bubble disappears |
| D-07 | Dismiss comments | Bubble reappears, counter unchanged |
| D-08 | Tap share → dismiss | Counter unchanged |
| D-09 | Press Home, return to Instagram Reels | Counter unchanged |
| D-10 | Lock screen while in Reels | Session timer stops |
| D-11 | Unlock screen, return to Reels | Session timer resumes |
| D-12 | Revoke Accessibility permission | App shows warning on Home screen |
| D-13 | Pause tracking in app | Counter stops; overlay hides |
| D-14 | Resume tracking | Counter and timer resume |
| D-15 | Navigate to Instagram DMs | State → IN_APP_UNKNOWN, overlay hidden |
| D-16 | Disable floating counter in Settings | Overlay no longer shows in Reels viewer |
| D-17 | Reach daily target | Single notification shown |
| D-18 | Continue past target | No additional notification |
| D-19 | Delete history in Settings | History screen empty, today's count reset |
| D-20 | Force-kill app; reopen | Today's count preserved from Room |

---

## Privacy

ScrollPause stores only:
- Reel counts per day
- Session durations per day
- Your preferences (daily target, reminder settings, etc.)

It does NOT store: messages, captions, usernames, screenshots, or any personal content from Instagram or any other app.

It has no INTERNET permission. All data stays on your device.
