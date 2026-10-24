# Contributing to RingConnWearSync

Thank you for your interest in contributing to **RingConnWearSync**! We welcome contributions from developers, designers, and testers who want to enhance the open-source companion experience for Wear OS and Android.

Please review this document to understand our contribution guidelines, clean-room standards, and development workflow.

---

## Project Architecture

RingConnWearSync is organized into two Gradle modules:

- **`:mobile` (Android Companion Bridge)**:
  - Background synchronization service managing biometric data intake.
  - Interoperability via Android OS standards: Google Health Connect (`HealthConnectClient`), widget hosting (`AppWidgetHost`), and accessibility nodes (`AccessibilityService`).
  - Google Play Services Wearable Data Layer (`DataClient`, `MessageClient`) for low-latency transmission to the watch.
- **`:wear` (Wear OS Companion Suite)**:
  - Samsung One UI Watch dynamic stadium tiles (`TileService`, AndroidX ProtoLayout).
  - Watch face complications (`SuspendingComplicationDataSourceService`, ComplicationData).
  - Native Jetpack Compose for Wear OS detail activities with swipe-to-dismiss navigation.

---

## Clean-Room & Legal Interoperability Policy (MANDATORY)

RingConnWearSync is an independent, community-driven project created under legal interoperability provisions (including **17 U.S.C. § 1201(f)** in the United States and **Directive 2009/24/EC Article 6** in the European Union).

To maintain absolute legal integrity, all contributors must strictly adhere to the following clean-room rules:

1. **Standard OS APIs Only**:
   - All integrations must rely solely on publicly documented Android OS APIs, such as Android Health Connect, `AppWidgetHost`, `AccessibilityService`, and the Google Wearable Data Layer.
2. **100% Original Assets**:
   - All icons, vector drawables, color palettes, animations, and typography layouts must be original creations or licensed under permissive open-source licenses compatible with MIT.
   - Never import, copy, or adapt proprietary asset files, bitmaps, or proprietary vector drawables.
3. **No Proprietary Code or Binaries**:
   - Pull requests must **never** include decompiled third-party code, reverse-engineered internal symbol dumps, private API keys, proprietary SDKs, or third-party APK/SO binaries.
   - Any contribution suspected of containing proprietary intellectual property will be closed immediately.

---

## Development & Testing Workflow

### Prerequisites
- **JDK 21** (Eclipse Temurin or Android Studio bundled JBR recommended)
- **Android Studio Ladybug (2024.2+)** or command-line Android SDK Build-Tools 35
- Gradle 8.11+ (using `./gradlew` wrapper)

### Building the Project
```bash
# Build mobile debug APK
./gradlew :mobile:assembleDebug

# Build wear debug APK
./gradlew :wear:assembleDebug
```

### Running Unit Tests
All proposed changes must pass existing unit tests before submission:
```bash
./gradlew :mobile:testDebugUnitTest :wear:testDebugUnitTest --console=plain
```

When introducing new business logic, data models, or tile layout calculations, companion unit tests under `mobile/src/test/` or `wear/src/test/` are required.

---

## Commit Conventions

We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>(<scope>): <short imperative description>
```

### Allowed Types:
- `feat`: A new user-facing feature or enhancement
- `fix`: A bug fix
- `docs`: Documentation updates or legal notices
- `style`: Formatting or cosmetic adjustments without functional change
- `refactor`: Code reorganization without fixing a bug or adding a feature
- `test`: Adding or updating test cases
- `chore`: Build scripts, dependencies, or tool configuration

### Rules:
- Write commit descriptions in **English**, using the imperative mood (e.g., `feat(tile): add haptic feedback to score tap`).
- **AI assistance goes in the disclosure, not in trailers**: do not include `Co-Authored-By` lines or generator signatures in commit messages. AI-assisted contributions are expected in this project; the project-level disclosure is maintained in [README.md](README.md).

---

## Cutting a Release

Releases follow the rules in [RELEASING.md](RELEASING.md): one tag per defensible version, `version.properties` is the single source of truth, and `scripts/check-version-tag.sh` must pass. The Release Gate workflow refuses a tag whose version does not match the one the build stamps into the APKs.

## Submitting a Pull Request

1. Fork the repository and create a descriptive branch from `master` (e.g., `feat/ambient-mode-contrast`).
2. Implement your changes adhering to Clean-Room guidelines.
3. Ensure all tests pass: `./gradlew :mobile:testDebugUnitTest :wear:testDebugUnitTest`.
4. Open a Pull Request against `master` using our Pull Request template.
5. Provide clear testing steps, device model information (e.g., Galaxy Watch 6 / Pixel Watch 2), and screenshots if UI changes were made.
