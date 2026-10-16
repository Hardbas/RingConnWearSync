# RingConnWearSync

> **Real-time Telemetry Bridge & Samsung One UI Watch 8 Suite for RingConn Smart Ring**

RingConnWearSync is a production-grade Android and Wear OS companion suite that bridges real-time biometric telemetry from the **RingConn Smart Ring (Gen 1 / Gen 2)** directly into **Samsung Galaxy Watch (One UI Watch 8 / Wear OS 5)**.

It was engineered to solve the **wrist tattoo optical blockage problem**: dark tattoo ink completely blocks optical PPG sensors on the Galaxy Watch. RingConnWearSync establishes the RingConn smart ring as the sole biometric source of truth, rendering live vitals seamlessly across native Samsung One UI stadium tiles, detail views, and watch face complications.

---

## 🏛️ Architecture Overview

The system consists of two tightly coupled Android modules communicating over the Bluetooth Wearable Data Layer:

```
┌─────────────────────────────────┐           Bluetooth BLE           ┌─────────────────────────────────┐
│     RingConn Smart Ring         │ ────────────────────────────────► │     Official RingConn App       │
└─────────────────────────────────┘                                   │   (com.gdjztech.ringconn)       │
                                                                      └────────────────┬────────────────┘
                                                                                       │ Android Accessibility &
                                                                                       │ UI Node Inspection
                                                                                       ▼
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                              Phone Bridge (com.ringconn.wear.app)                                      │
│                                                                                                        │
│  • RingConnForegroundService      24/7 unkillable foreground service (exempt from battery throttling)  │
│  • RingConnAccessibilityService   Zero-overhead node observer extracting raw ring vitals               │
│  • RingConnTelemetryParser        High-precision regex parser for HR, SpO2, HRV, Stress & Skin Temp    │
│  • WearDataSyncService            Dual transport: DataClient (reliable) + MessageClient RPC (instant) │
└──────────────────────────────────────────────────┬─────────────────────────────────────────────────────┘
                                                   │ Bluetooth Wearable RPC (<100ms)
                                                   ▼
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                              Wear OS Suite (com.ringconn.wear.app)                                     │
│                                                                                                        │
│  • WearDataListenerService        Dual RPC listener updating local SharedPreferences cache             │
│  • ComplicationDataFactory        Universal AndroidX Complication engine supporting all 6 slot types   │
│  • 7 Native Complications         Battery, Heart Rate, SpO2, Sleep, Steps, Stress, Skin Temperature    │
│  • 5 One UI 8 Tiles               190dp × 136dp Stadium Capsules reverse-engineered from S-Health      │
│  • Dedicated Detail Activities    Compose Material 3 screens with native swipe-to-dismiss navigation   │
└────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## ⌚ One UI Watch 8 Native Tiles

All tiles replicate Samsung Health One UI Watch 8 metrics down to exact device density specifications:
- **Hero Stadium Dimensions:** `190dp × 136dp` with `68dp` corner radius.
- **Progress Arc / Track:** `136dp` horizontal bar with glassmorphic track (`0x33FFFFFF`).
- **Pill Buttons:** `92dp × 24dp` capsule buttons for direct action ("Measure", "Breathe", "Details").
- **Visual Typography:** Scaled primary value (28sp semi-bold), unit (11sp medium), status insight (12sp medium).

### Available Tiles:
1. **Heart Rate Tile (`HeartRateTileService`)**: Live BPM, resting HR, min/max range, tap leads to `HeartRateDetailActivity`.
2. **Stress Index Tile (`StressTileService`)**: Live stress score (0–100), autonomic zone (Relaxed / Balanced / Moderate / High), HRV vagal tone metric, tap leads to `StressDetailActivity`.
3. **Skin Temperature Tile (`TemperatureTileService`)**: Nocturnal baseline variation (`+0.2°`, `-0.1°`), metabolic stability status, tap leads to `TemperatureDetailActivity`.
4. **Daily Summary Tile (`DailySummaryTileService`)**: Concentric activity rings and sleep duration capsules, tap leads to `DailySummaryDetailActivity`.
5. **Vitals Tile (`VitalsTileService`)**: Unified biometrics overview.

---

## 🎯 7 Universal Watch Face Complications

Every complication is compatible with Samsung's **Watch Face Format (WFF)** and legacy Wear OS 3/4 watch faces, supporting all slot types:
- `SHORT_TEXT` (value + title)
- `RANGED_VALUE` (circular gauge / progress arc)
- `GOAL_PROGRESS` (daily targets)
- `LONG_TEXT` (text banner with status)
- `MONOCHROMATIC_IMAGE` (theme-reactive vector icon)
- `SMALL_IMAGE` (high-contrast icon)

| Complication | Provider | Supported Types | Tap Action |
|---|---|---|---|
| **Ring Battery** | `RingBatteryComplicationProvider` | All 6 types | Launches Wear MainActivity |
| **Heart Rate** | `RingConnComplicationProvider` | All 6 types | Launches HeartRateDetailActivity |
| **Blood Oxygen (SpO2)** | `Spo2ComplicationProvider` | All 6 types | Launches VitalsDetailActivity |
| **Sleep Score** | `SleepScoreComplicationProvider` | All 6 types | Launches SleepDetailActivity |
| **Daily Steps** | `StepsComplicationProvider` | All 6 types | Launches DailySummaryDetailActivity |
| **Stress Index** | `StressComplicationProvider` | All 6 types | Launches StressDetailActivity |
| **Skin Temperature** | `TemperatureComplicationProvider` | All 6 types | Launches TemperatureDetailActivity |

---

## 🔧 Extensibility & Future Patching

Because the extraction layer is decoupled from presentation, future changes from RingConn or Samsung can be patched cleanly:

1. **RingConn App UI Changes**:
   - The extraction parser in `RingConnTelemetryParser.kt` uses pattern tokenization. If RingConn alters label IDs or strings, only the regex matchers in `parse(...)` need adjustment.
2. **Direct Bluetooth Low Energy (BLE) Fallback**:
   - The transport layer is ready to receive packets directly from the ring via GATT characteristic notifications if direct ring-to-watch pairing is enabled.
3. **Multi-language Support**:
   - Clean separation of UI strings in `values/strings.xml` (English default) and `values-es/strings.xml` (Spanish).

---

## 🛠️ Building & Deploying

### Prerequisites:
- Android Studio Ladybug+ / JDK 21 (`$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`)
- Android SDK Platform 35, Build Tools 35.0.0
- ADB over Wi-Fi paired to Galaxy Watch 8 Classic (`SM-L500`)

### Compile & Install:
```powershell
# 1. Compile Release APKs
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew assembleRelease

# 2. Deploy Mobile Bridge to Phone
adb -s <PHONE_SERIAL> install -r mobile/build/outputs/apk/release/mobile-release.apk

# 3. Deploy Wear App to Watch
adb -s <WATCH_IP:PORT> install -r wear/build/outputs/apk/release/wear-release.apk
```

---

## 📜 License
Private & Proprietary. All rights reserved.
