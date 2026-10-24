# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Releases are gated: a tag must name the version in `version.properties`, its unit
tests must pass and both release APKs must assemble, or the tag does not ship.
`scripts/audit-watch-strings.py` additionally fails the build when a one-line
watch label is too long for the space its surface leaves.

## [1.9.32] - 2026-10-24

### Changed

- **The sleep diagnostic reports stage types, not only their count.** Every session from
  the hardware report carried exactly one stage record, and the reader maps only five
  types; a fragment with an unmapped type is silently dropped and the night reads empty
  even though Health Connect holds it. Each session now prints `types=[...]`, and the
  report closes with the longest session and the distinct stage types received, so it can
  be read at a glance instead of sixty-six lines.

## [1.9.31] - 2026-10-24

### Fixed

- **The phone screen was in Spanish for everyone.** Not a locale problem: the UI was
  written as 46 Spanish literals inside Kotlin, and text in code has no language, so
  Android never had anything to translate. All of it now lives in resources with
  English as the default and Spanish in `values-es`; interpolated strings use
  positional placeholders. A source audit fails the build while any user-facing
  literal still carries Spanish, and names every offender.

## [1.9.30] - 2026-10-24

### Added

- **In-app sleep diagnostic** on the phone: a "Diagnóstico de sueño" button reads the
  sleep sessions of the last 36 hours and reports, for each one, its start, end,
  duration, stage count and whether it falls inside the window the reader uses. It
  exists because "the watch shows the wrong night" could not be resolved remotely:
  with no cable and no adb, the app has to be able to say what it sees. The window
  rule is a pure function with five tests.

## [1.9.29] - 2026-10-24

### Fixed

- **Sleep stages stayed wrong after 1.9.28 even with a correct Health Connect
  record.** The score matched the official app because it comes from the widget;
  the stages did not, because their source was chosen by "whichever claims the most
  sleep" — widget, then cache, then Health Connect. A cache left inflated by the
  earlier merge bug outbid the corrected record permanently, and a per-field
  fallback could build a night from two different sources. Health Connect now owns
  the night whenever it holds one, the cache only covers a night Health Connect has
  not received, and no field is taken from a second source.

## [1.9.28] - 2026-10-24

Published as a pre-release: the sleep fix has not been verified on hardware yet.

### Fixed

- **Sleep stages read wrong even when Health Connect held the data.** The merge
  resolved overlapping segments by `lastModified`, so the newest segment won each
  epoch. RingConn rewrites a coarse night-long segment when it syncs, and that
  rewrite carries the newest timestamp, so it overwrote the short deep, REM and
  awake segments: the night collapsed into one stage and deep/REM read as zero.
  Specificity now wins over recency — a shorter segment owns the epochs it covers,
  recency only breaks ties between segments of equal length, and arrival order
  cannot change the result (`SleepStageMerger`, 5 tests).

### Changed

- **Overnight polling backs off.** The in-service poll ran every five minutes
  around the clock, waking the RingConn app each cycle for data that only changes
  every 15-30 minutes. Between 23:00 and 06:00 the interval is now 150 minutes, so
  a night costs three wake cycles instead of about eighty-four. Reported as 6%
  overnight drain where the same night used to cost 2-3%.

### Known limitations

- Nothing on the phone checks that a captured value is present before sending it:
  `DataMap.getInt` returns 0 for a missing key, so an unmeasured stage and a
  measured zero are indistinguishable on the watch.
- The morning capture still opens the RingConn app for up to 24 seconds, aborting
  after 6 if the app is not in front — which happens when the phone is locked.

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
