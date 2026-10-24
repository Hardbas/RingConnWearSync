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
   bash scripts/check-release-signing-identity.sh ~/.android/debug.keystore
   ```

   The last one is the one that cannot be undone later: it compares the key you
   are about to sign with against the certificate in
   `signing-identity.properties`, which is the certificate every published APK
   already carries. If it fails, stop — read the backup section below before
   building anything.

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
`scripts/test-check-release-signing-identity.sh`, and run in both workflows, so a
change that weakens a guard breaks CI instead of silently shipping.

## Known caveat: the identity is an Android debug certificate

The certificate every APK carries is the one Android Studio generates for debugging,
which is why `apksigner` reports `CN=Android Debug`. Since 1.9.39 the key is re-wrapped
under a real password and supplied through `keystore.properties` (see below), so the
public password is gone; the certificate lineage is the same, which is what keeps every
existing install updatable. The build falls back to the raw debug keystore, with a warning
in the log, only when `keystore.properties` is absent:

```kotlin
signingConfig = if (keystorePropertiesFile.isFile) signingConfigs.getByName("release")
                else signingConfigs.getByName("debug")
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

### The release key: same identity, its own password

The certificate every APK has carried since v1.1.0 stays. What changes is the file around it:
the debug keystore opens with the password `android`, which every Android developer on earth
knows, so anyone holding a copy could sign an update Android would accept. A keystore
password protects the container, not the identity, so the key can be re-wrapped under a real
password without changing a single certificate:

```bash
# One time. Asks for the password twice with the terminal echo off, keeps it out of the shell
# history and out of the process list, proves the identity survived, and writes
# keystore.properties for you.
bash scripts/rewrap-release-key.sh
```

It refuses rather than guessing: an existing destination, an existing `keystore.properties`,
passwords that do not match, or fewer than six characters all stop it with exit 1 and nothing
written. Underneath it runs:

```bash
keytool -importkeystore     -srckeystore ~/.android/debug.keystore -srcstorepass android -srcalias androiddebugkey     -destkeystore ~/.android/release.p12 -deststoretype PKCS12 -deststorepass:env RSW_KEYSTORE_PASSWORD     -destalias wearsync

KEYSTORE_PASSWORD=... KEY_ALIAS=wearsync     bash scripts/check-release-signing-identity.sh ~/.android/release.p12
# ok: ... carries the published release certificate (1ccf5348...)
```

The certificate digest is the point of the exercise: if `check-release-signing-identity.sh`
prints a different one, the re-wrap did something other than copy the key and nothing should
be published.

Then point the build at it. Two locations are accepted, `keystore.properties` at the repository
root and `~/.android/wearsync-keystore.properties`, and the script writes the second on purpose:
this repository is usually inside a synced folder, and a password written in there is uploaded by
the sync client even though git ignores it.

```properties
storeFile=C:/Users/you/.android/release.p12
storePassword=<a real password>
keyAlias=wearsync
keyPassword=<a real password>
```

With that file present both modules sign `release` with it. Without it they fall back to the
debug key and say so in the build log: that is what CI does, because every runner generates a
throwaway keystore and publishes nothing. **A published APK must carry the certificate in
`signing-identity.properties`** — check it, do not assume it:

```bash
bash scripts/check-release-signing-identity.sh path/to/app.apk
```

Two things keep the identity honest:

1. **Back up the keystore.** It is the identity of every release published so far, and
   losing it means every existing user must uninstall. Three copies, two media, one of them
   off-site, all encrypted, stored with the password and the alias in the same password-manager
   item: the file on its own is useless and the password on its own is worse.

   ```bash
   gpg -c --cipher-algo AES256 ~/.android/release.p12        # -> release.p12.gpg

   # Restore it somewhere and prove the restored copy is the identity. The decrypted file keeps
   # a .p12 extension on purpose: the guard refuses what it cannot recognise as a keystore, and a
   # backup check that fails over a filename is a backup nobody finishes.
   gpg -d -o /tmp/restore-check.p12 ~/.android/release.p12.gpg
   KEYSTORE_PASSWORD='<the password>' KEY_ALIAS=wearsync bash scripts/check-release-signing-identity.sh /tmp/restore-check.p12
   rm -f /tmp/restore-check.p12
   ```

   Keep both the key and the properties file out of the repository folder. This repository lives
   inside OneDrive: git would ignore them, OneDrive would upload them.

   A backup that has never been restored is not a backup. The procedure:

   ```bash
   # 1. Copy the file off this machine, to two places you do not need this laptop for.
   #    Any password manager or encrypted drive works; it is 2.6 KB.

   # 2. Prove the copy is the identity, from the copy itself:
   bash scripts/check-release-signing-identity.sh /path/to/backup/debug.keystore
   # ok: ... carries the published release certificate (1ccf5348...)
   ```

   The same command against `~/.android/debug.keystore` proves the working copy is
   still the right one. Run it before every release: the Android SDK silently
   regenerates a debug keystore whenever the original is missing, and a freshly
   generated one has the same subject line (`CN=Android Debug`) and a different
   fingerprint, so nothing else in the build would notice.

2. **Publish its fingerprint** in the README next to the download links, so users
   can verify what they install. `signing-identity.properties` holds the value;
   if the README and that file ever disagree, the file wins.

### If publication is ever automated

Generate one stable release keystore, keep it out of the repository, and expose it
to CI as four secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`). The build then prefers that keystore when it is present and falls
back to debug when it is not.

## Version history note

Tags before `v1.9.25` were created before this gate existed and include
iteration tags (`v1.9.16-rc1`, `v1.9.17-warm1` … `warm5`). They are kept as
history. They are not a precedent.
