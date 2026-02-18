# Virtual Coverage Map Android App

Production-ready Android application for crowdsourcing mobile network signal strength data.

## Project Structure

```
android-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/virtualcoverage/signalmap/
│   │   │   ├── SignalMapApp.kt (Hilt Application)
│   │   │   ├── service/
│   │   │   │   └── SignalCollectionService.kt
│   │   │   └── presentation/
│   │   │       └── MainActivity.kt
│   │   ├── AndroidManifest.xml
│   │   └── res/
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

## Features Implemented

### ✅ Phase 1 Complete
- [x] Gradle project structure with Kotlin
- [x] Hilt dependency injection
- [x] AndroidX and Material Design
- [x] osmdroid for maps (no API key required)
- [x] H3 spatial indexing library

### ✅ Phase 2 Complete - SignalCollectionService
- [x] Foreground service for background execution
- [x] **Dual-SIM support** using SubscriptionManager
- [x] TelephonyManager integration for signal metrics
- [x] 5G NR support (SS-RSRP, SS-SINR, CSI-RSRP)
- [x] 4G LTE support (RSRP, RSRQ, SINR)
- [x] 2G/3G GSM support
- [x] **5G SA vs NSA detection**
- [x] FusedLocationProvider for GPS
- [x] Permission handling (Android 10+)

## How to Build

### Requirements
- Android Studio Ladybug or later
- Java JDK 17
- Android SDK with API levels 29, 34, 35

### Steps

1. **Open in Android Studio:**
   ```
   File → Open → Select "android-app" folder
   ```

2. **Sync Gradle:**
   - Android Studio will automatically sync
   - Wait for dependencies to download (~5 minutes first time)

3. **Build APK:**
   ```
   Build → Build Bundle(s) / APK(s) → Build APK(s)
   ```

   Or use Gradle command:
   ```powershell
   ./gradlew assembleDebug
   ```

4. **Install on Device:**
   - Connect Android device via USB
   - Enable USB Debugging
   - Run:
   ```
   ./gradlew installDebug
   ```

## How to Test

1. **Install the app** on a physical Android device (emulator won't have real cell signals)
2. **Grant permissions** when prompted:
   - Location (Fine & Coarse)
   - Phone State
   - Notifications
3. **Tap "Start Signal Collection"**
4. **Check Logcat** for signal data:
   ```
   adb logcat -s SignalCollectionService
   ```

### Expected Logcat Output

```
SignalCollectionService: === Starting signal collection ===
SignalCollectionService: Processing SIM: Jio (SubId: 2)
SignalCollectionService: 5G NR Signal Data:
  Carrier: Jio
  Network: 5G_SA
  SS-RSRP: -92 dBm
  SS-SINR: 18 dB
  PCI: 456
  Location: 28.7041, 77.1025
```

## Next Steps

- [ ] Implement Room database for offline storage
- [ ] Add data upload to backend
- [ ] Implement H3 hexagon aggregation
- [ ] Create map visualization
- [ ] Add carrier comparison UI

## Troubleshooting

**Build fails with "SDK not found":**
- Open SDK Manager in Android Studio
- Install Android SDK Build-Tools and Platform-Tools

**Permissions denied:**
- Go to Settings → Apps → Virtual Coverage Map → Permissions
- Enable all permissions manually

**No signal data in logs:**
- Ensure you're on a physical device (not emulator)
- Check that SIM card is active and has network connection
