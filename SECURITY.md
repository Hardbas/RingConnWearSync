# Security & Privacy Policy

We take the security and privacy of RingConnWearSync users seriously. This document outlines our security architecture, supported versions, and the process for responsibly reporting vulnerabilities.

---

## Supported Versions

We actively provide security patches and stability updates for the following versions:

| Version | Supported          | Status |
| ------- | ------------------ | ------ |
| `1.9.x` | :white_check_mark: | Active production release branch |
| `< 1.9` | :x:                | Deprecated — please upgrade to 1.9.x |

---

## Security & Privacy Architecture

RingConnWearSync is engineered around a **zero-trust, local-first privacy model**:

1. **100% Local On-Device Processing**:
   - Biometric telemetry (Heart Rate, SpO2, Sleep stages, HRV, Skin Temperature) never leaves your personal devices.
   - All heuristics, trend calculations, and data formatting execute strictly within local Android process memory.
2. **Zero External Cloud Infrastructure**:
   - There are no intermediary servers, cloud databases, analytics SDKs, telemetry beacons, or crash reporters included in this software.
3. **Zero RingConn Account Credentials Stored**:
   - The application does not store, request, or handle RingConn login passwords, authentication tokens, or personal profile credentials.
   - All data intake operates through standard Android OS channels (Health Connect, `AppWidgetHost`, or user-granted accessibility events).
4. **Device Integrity & Rootless Safety**:
   - No root access (`su`), bootloader unlocking, or system partition modification is required.
   - Samsung Knox warranty bit remains intact (`0x0`). Secure system services (such as Google Wallet and Samsung Pay) are never compromised.
5. **Secure Peer-to-Peer Inter-Device Transport**:
   - Telemetry transmission between the smartphone and the Wear OS watch relies exclusively on the Google Play Services Wearable Data Layer over encrypted Bluetooth BLE connections.

---

## Reporting a Vulnerability

If you discover a potential security vulnerability or privacy flaw in RingConnWearSync, please report it responsibly:

1. **Do NOT report security vulnerabilities via public GitHub issues.**
2. Send an email with full technical details, reproduction steps, and potential impact to:
   - **`hardbas.security@proton.me`** (or open a private GitHub Security Advisory via the repository's Security tab if enabled).
3. Please include:
   - Device models (Watch and Phone)
   - Android & Wear OS OS versions
   - RingConnWearSync version
   - Proof of Concept (PoC) code or step-by-step reproduction guide

### Response Commitment
- **Initial Acknowledgement**: Within 48 hours of receipt.
- **Triage & Assessment**: Within 5 business days.
- **Fix & Public Disclosure**: Coordinated release following confirmation and patch verification.
