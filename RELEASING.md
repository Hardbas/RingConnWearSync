# Releasing RingConnWearSync

A tag is a promise: *this exact tree was tested and it works*. The repository
enforces the parts of that promise a machine can check.

## The rule

**One tag, one defensible version.** Do not tag an iteration, a save point or a
"warm2". If you cannot say in one sentence what a user gets by updating to it,
it is not a release.

## How to cut a release

1. Bump `version.properties` — both fields, in the same commit as the change
   that earns the release:

   ```properties
   versionCode=61
   versionName=1.9.26
   ```

   `versionCode` must increase. Both modules read this file, so the phone app and
   the watch app can never disagree about the version they belong to.

2. Verify locally, with the same commands the gate runs:

   ```bash
   ./gradlew :mobile:testDebugUnitTest :wear:testDebugUnitTest
   ./gradlew :mobile:assembleRelease :wear:assembleRelease
   bash scripts/check-version-tag.sh v1.9.26
   ```

3. Install both APKs on real hardware and confirm the behaviour you are about to
   promise. Automated tests prove the code compiles and the contracts hold; they
   do not prove a tile renders correctly on a watch.

4. Tag the commit and push the tag:

   ```bash
   git tag v1.9.26
   git push origin v1.9.26
   ```

5. The **Release Gate** workflow now runs. It fails if the tag does not match
   `versionName` in `version.properties`, if any unit test fails, or if either
   release APK does not assemble. A tag whose gate failed must be deleted, not
   reused:

   ```bash
   git push origin --delete v1.9.26
   git tag -d v1.9.26
   ```

6. Publish the GitHub Release manually, attaching the two APKs. Until the signing
   decision below is made, that step is human on purpose.

## Rehearsing the gate without tagging

The gate is also dispatchable: **Actions → Release Gate → Run workflow** and type
 the tag you intend to publish (for example `v1.9.26`). It runs the same guard,
the same tests and the same `assembleRelease`, so a rehearsal failure tells you a
real tag would fail too.

The guard's own regression tests live in `scripts/test-check-version-tag.sh` and
run in both workflows, so a change that weakens the guard breaks CI instead of
silently shipping.

## Known caveat: release APKs are signed with the debug key

Both modules currently sign `release` builds with the Android **debug**
keystore:

```kotlin
signingConfig = signingConfigs.getByName("debug")
```

This has two consequences worth knowing before automating publication:

- A debug keystore is generated per machine. An APK built on CI carries a
  different key than one built on the maintainer's laptop, so a user who
  installed the laptop build cannot update in place over the CI build: Android
  rejects the signature and the user has to uninstall first.
- Debug keys are not a secret. For a sideloaded app distributed through GitHub
  Releases, that means anyone can produce an APK signed well enough to be
  accepted as an update for the same `applicationId`.

That is why the gate workflow assembles and uploads release *candidates* as
workflow artifacts, and never publishes a GitHub Release itself.

The fix, when it is worth doing: generate one stable release keystore, keep it
out of the repository, and expose it to CI as four secrets
(`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). The build
then prefers that keystore when it is present and falls back to debug when it is
not, and publication can be automated safely.

## Version history note

Tags before `v1.9.25` were created before this gate existed and include
iteration tags (`v1.9.16-rc1`, `v1.9.17-warm1` … `warm5`). They are kept as
history. They are not a precedent.
