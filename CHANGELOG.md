# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Releases are gated: a tag must name the version in `version.properties`, its unit
tests must pass and both release APKs must assemble, or the tag does not ship.
`scripts/audit-watch-strings.py` additionally fails the build when a one-line
watch label is too long for the space its surface leaves.

## [1.9.27] - 2026-10-24

### Fixed

- Sixteen more card titles were cut mid-word where they share a row with a value:
  stand hours, total TDEE, phase heart rate, cardiac coherence, circadian blocks,
  immune radar, daily range, baseline temperature, autonomic tone and the body
  battery status. Titles now wrap to two lines. Value texts are deliberately left
  on one line, since a number split across two lines is worse than a truncation.

## [1.9.26] - 2026-10-24

Removes fabricated biometric data from two screens. Found by auditing the app
against its own documentation; no user report exposed it.

### Fixed

- **Stress detail** drew a twenty-four bar timeline from an hour formula and
  printed three fixed percentages (`74%`, `26%`, `64`) for every user regardless
  of their data. RingConn records four circadian stress blocks a day and nothing
  finer, so there was never an hourly series to draw. The card now renders the
  four measured blocks, treats an unmeasured block as a gap rather than a measured
  zero, and summarises the lowest, spread and peak of the blocks that exist.
- **Measure button** waited a fixed 3.5 s and then persisted the previous heart
  rate plus a random offset of −2 to +3 bpm, stamped with a fresh timestamp, which
  made an invented number indistinguishable from a measured one. Both entry points
  now route the request to the phone and require the reading's own
  `refreshTimestamp` to advance before claiming a refresh. An unreachable phone
  fails in 3 seconds, and a failed attempt says so instead of returning to silence.

### Changed

- Card titles wrap to two lines instead of being cut mid-word; labels that share a
  row with a value use short copy. Affected: HR zones, heart-rate rhythm and HRV
  titles, blood oxygen, strain radar, stress timeline and the nocturnal
  temperature curve.

### Known limitations

- The measurement gate requires a completion signal **and** an advancing reading,
  but cannot prove the signal belongs to the request that asked for it. A
  concurrent sync that persists a reading satisfies both conditions. Correlating
  request and response needs the phone to echo a request identifier, which is a
  change in the mobile module.
- `WearDataListenerService` publishes the completion signal before it persists the
  payload, so the gate can observe a timestamp that has not been updated yet.
- Parts of the UI are still written as `isSpanish` ternaries or English-only
  literals inside Kotlin (`Optimal Recovery`, `Balanced Charge`), so they are
  neither translatable through resources nor visible to the string audit.

### Infrastructure

- `version.properties` is the single source of the app version; a release gate
  workflow refuses a tag whose version disagrees with it.
- Every push uploads the debug APKs as workflow artifacts, so testing on a device
  no longer requires publishing a release.
- The release certificate fingerprint is documented in the README.

## [1.9.25] - 2026-10-23

### Added

- Heart rate measurement time shown on the main Heart Rate tile.

Older releases predate this changelog; their notes live on the
[releases page](https://github.com/Hardbas/RingConnWearSync/releases).
