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
│                              Phone Bridge (io.github.hardbas.wearsync)                                 │
│                                                                                                        │
│  • RingConnForegroundService      24/7 unkillable foreground service (exempt from battery throttling)  │
│  • RingConnAccessibilityService   Zero-overhead node observer extracting raw ring vitals               │
│  • RingConnTelemetryParser        High-precision regex parser for HR, SpO2, HRV, Stress & Skin Temp    │
│  • WearDataSyncService            Dual transport: DataClient (reliable) + MessageClient RPC (instant) │
└──────────────────────────────────────────────────┬─────────────────────────────────────────────────────┘
                                                   │ Bluetooth Wearable RPC (<100ms)
                                                   ▼
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                              Wear OS Suite (io.github.hardbas.wearsync)                                │
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
3. **Sleep & Nocturnal Temperature Tile (`SleepTileService`)**: Celestial starry night aesthetic with animated sweep bezel arc, hero score, duration chip, and integrated nocturnal skin temperature recovery capsule. Tap opens `SleepDetailActivity`.
4. **Daily Activity Tile (`DailySummaryTileService`)**: Concentric activity rings, steps, calories, active time, and daily goal progress capsule. Tap leads to `DailySummaryDetailActivity`.
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

## 🔒 Security & Device Integrity

- **100% User-Space Sideloading**: Does **NOT** require unlocking the bootloader, rooting your phone, or rooting your watch.
- **Knox Warranty Safe**: Samsung Knox warranty bit is **NOT** tripped (`0x0` remains intact). Samsung Pay / Google Wallet continue working normally.
- **Temporary ADB Requirement**: ADB (Android Debug Bridge) is used strictly for initial sideloading. Once the APKs are installed and permissions granted, **ADB debugging can be disabled immediately** on both the phone and the watch.
- **Zero Cloud Leaks**: Operates 100% locally on-device. No telemetry, credentials, or personal biometrics are transmitted to external servers.

---

## 📱 Hardware & OS Compatibility

- **Watches**:
  - **Samsung Galaxy Watch** (Watch 4, Watch 5, Watch 6, Watch 7, Watch Ultra, Watch 8 Classic) running Wear OS 4, Wear OS 5, or One UI Watch 5/6/8. *(Full support for custom One UI 8 stadium tiles and AndroidX complications)*.
  - **Other Wear OS Devices** (Google Pixel Watch 1/2/3, OnePlus Watch 2, TicWatch): Compatible with AndroidX complications and detail screens.
  - **Apple Watch / iOS**: **Not supported** (Apple watchOS does not support Android sideloading or Android Wearable Data Layer).
- **Phones**:
  - Android 12+ (Android 14/15/16 recommended) with Health Connect support.

---

## 📊 Telemetry Sourcing & Metric Provenance

To ensure total transparency, the following table details the technical origin of each metric:

| Metric | Primary Source | Underlying Mechanism | Technical Details |
|---|---|---|---|
| **Heart Rate (BPM)** | Google Health Connect | Official Health Connect API (`HeartRateRecord`) | User-authorized standard OS health sync |
| **Blood Oxygen (SpO2)** | Google Health Connect | Official Health Connect API (`OxygenSaturationRecord`) | User-authorized standard OS health sync |
| **Sleep Stages & Duration** | Google Health Connect | Official Health Connect API (`SleepSessionRecord`) | Nocturnal session duration and sleep stages |
| **Daily Steps** | Google Health Connect | Official Health Connect API (`StepsRecord`) | Cumulative daily step counts |
| **Daily Calories** | Google Health Connect | Official Health Connect API (`TotalCaloriesBurnedRecord`) | Energy expenditure records |
| **Sleep Score (0–100)** | RingConn Official Widget | Android `AppWidgetHost` (`RemoteViews` inspection) | Extracted from widget (Health Connect has no standard sleep score field) |
| **HRV (RMSSD)** | RingConn Official Widget | Android `AppWidgetHost` (`RemoteViews` inspection) | Extracted from widget (RingConn does not export HRV to Health Connect) |
| **Stress Index** | RingConn Official Widget | Android `AppWidgetHost` (`RemoteViews` inspection) | Extracted from widget (RingConn does not export Stress to Health Connect) |
| **Ring Battery Level** | RingConn Official Widget | Android `AppWidgetHost` (`RemoteViews` inspection) | Extracted from widget (RingConn does not export Battery to Health Connect) |
| **Skin Temperature** | RingConn Official Widget | Android `AppWidgetHost` (`RemoteViews` inspection) | Nocturnal baseline variation reading from widget |

---

## ⚖️ Legal & Trademark Notice

**WearSync for RingConn** is an independent, unofficial open-source interoperability bridge and is NOT affiliated with, endorsed by, or sponsored by Guangdong JiuZhi Technology Co., Ltd. (RingConn) or Samsung Electronics Co., Ltd.

- **Trademark Attribution**: "RingConn" is a trademark of Guangdong JiuZhi Technology Co., Ltd. "Samsung", "Galaxy Watch", and "One UI" are trademarks of Samsung Electronics Co., Ltd. All trademarks belong to their respective owners and are used strictly under nominative fair use to describe interoperability and device compatibility.
- **Local-Only Architecture**: The project has no custom cloud backend. It reads health data locally through Health Connect with user-granted permissions and, for selected metrics currently unavailable through that integration, uses an experimental widget-hosting mechanism (`AppWidgetHost`) to access information exposed by the official RingConn widget.
- **Device & Integrity Safety**: The application does not require root access, bootloader unlocking, or modification of the official RingConn application. It does not directly access RingConn's private application files or process memory.
- **Widget-Based Extraction**: Data extraction from the widget depends on the layout and implementation of the official RingConn widget and may stop working or require parser updates if RingConn alters its widget design in future app releases. The project does not distribute RingConn's proprietary source code or assets.
- **Privacy First**: Health data is intended to remain strictly on the user's device. The project itself does not intentionally transmit health readings to external servers. Users are encouraged to review the source code and permissions before installation.

---

## 📜 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
Free for personal and educational use.
