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

This is a legitimate choice for an open-source, sideloaded app. It has nothing to
do with hiding source code: the keystore is a file on the maintainer's machine,
never in the repository, and it identifies the app to Android rather than
authenticating to any server. Open-source projects are signed with real keys as a
rule; F-Droid, for instance, signs its own builds with its own key.

What debug signing actually costs, precisely:

- **Update continuity.** Android identifies an app by `applicationId` plus the
  signing certificate, not by the alias used to sign it. Every machine generates
  its own debug keypair, so an APK built on CI carries a different certificate
  than one built on the maintainer's laptop. Installing the second over the
  first fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and the user must
  uninstall — losing Health Connect grants and any widget or complication setup.
- **No verifiable fingerprint.** Without a stable certificate there is nothing to
  publish that lets a user confirm an APK came from this project. A stable key
  allows exactly that: publish the certificate's SHA-256 in the README and any
  user can run `apksigner verify --print-certs` against the downloaded APK.
- **A switch forces an uninstall.** Changing the signing key at any point makes
  every existing install non-updatable. It is cheapest now, while the audience is
  small.

What it does **not** cost: nobody can forge an update for this app. A debug
keystore generated elsewhere produces a different keypair, and Android compares
certificates, so a third-party APK signed with *their* debug key is rejected as
an update. The real exposure is local: `~/.android/debug.keystore` is an
unencrypted file with the well-known password `android`, so anyone with access to
the maintainer's machine or backups can sign as them.

That is why the gate workflow assembles and uploads release *candidates* as
workflow artifacts, and never publishes a GitHub Release itself.

### If the project stays on the debug key

That is a defensible end state. Two things keep it honest:

1. **Back up `~/.android/debug.keystore`.** It is the identity of every release
   published so far. Losing it means every existing user must uninstall. It is not
   in the repository and it is not in any cloud account by default.
2. **Publish its fingerprint** in the README next to the download links, so users
   can verify what they install.

### If publication is ever automated

Generate one stable release keystore, keep it out of the repository, and expose it
to CI as four secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`). The build then prefers that keystore when it is present and falls
back to debug when it is not.

## Version history note

Tags before `v1.9.25` were created before this gate existed and include
iteration tags (`v1.9.16-rc1`, `v1.9.17-warm1` … `warm5`). They are kept as
history. They are not a precedent.
