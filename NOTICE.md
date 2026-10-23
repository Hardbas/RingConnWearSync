# Legal Notice & Statutory Safe Harbor Compliance

**RingConnWearSync**  
Copyright (c) 2024–2026 Hardbas & Contributors.

---

## 1. Clean-Room Interoperability & Safe Harbor

This project is an independent interoperability tool developed under the protection of statutory reverse-engineering and interoperability exceptions, including:

- **United States**: Title 17 of the United States Code, Section 1201(f) (17 U.S.C. § 1201(f) - Reverse Engineering for Interoperability of Computer Programs).
- **European Union**: Directive 2009/24/EC of the European Parliament and of the Council of 23 April 2009 on the legal protection of computer programs, Article 6 (Decompilation for Interoperability).

All functionality implemented in RingConnWearSync relies on publicly exposed Android operating system interfaces, including:
- Standard Android Health Connect APIs (`androidx.health.connect:connect-client`)
- Android App Widget Host interfaces (`android.appwidget.AppWidgetHost`)
- Android Accessibility Service event interfaces (`android.accessibilityservice.AccessibilityService`)
- Google Play Services Wearable Data Layer (`com.google.android.gms:play-services-wearable`)

---

## 2. Nominative Fair Use of Trademarks

- **RingConn** is a registered trademark of Guangdong JiuZhi Technology Co., Ltd.
- **Samsung**, **Galaxy Watch**, and **One UI** are registered trademarks of Samsung Electronics Co., Ltd.
- **Wear OS** and **Android** are trademarks of Google LLC.

All product names, logos, brands, and trademarks displayed or mentioned in this repository are the property of their respective trademark holders. Their use here does not imply affiliation with, sponsorship of, or endorsement by them. These terms are used strictly under **nominative fair use** to describe hardware interoperability, platform compatibility, and visual layout parity.

---

## 3. Absence of Proprietary Code or Binaries

This repository does **not** host, distribute, or incorporate:
- Any proprietary source code, headers, or binary libraries from Guangdong JiuZhi Technology Co., Ltd. or Samsung Electronics Co., Ltd.
- Any private API signing keys, certificates, or tokens.
- Any extracted DEX, APK, or compiled shared object (`.so`) libraries.

All code, schemas, and UI elements in this repository were produced for this project in Kotlin and XML without copying or deriving from any proprietary RingConn or Samsung implementation. AI-assisted authorship is disclosed in [README.md](README.md).

---

## 4. Privacy & Telemetry Statement

RingConnWearSync functions entirely locally on the user's personal devices (smartphone and smartwatch). It does not maintain any remote cloud backend, does not transmit health metrics to external servers, and does not collect tracking or usage analytics.
