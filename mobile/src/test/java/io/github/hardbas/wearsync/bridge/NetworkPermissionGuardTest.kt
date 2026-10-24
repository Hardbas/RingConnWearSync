package io.github.hardbas.wearsync.bridge

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source audit: the bridge must not ask Android for network access it never uses.
 *
 * The manifest declared `INTERNET`, `ACCESS_NETWORK_STATE` and `CHANGE_NETWORK_STATE` while
 * neither module contains a single network call, so the permission dialog and the manifest on
 * GitHub described an app that could reach the internet, against a README that promises "no
 * telemetry, never contacts a server". The Wearable Data Layer moves messages over the paired
 * Bluetooth connection and does not need any of the three.
 *
 * A guard that passes because it could not find the file is worse than no guard, so this test
 * proves it read a real manifest before it clears it.
 *
 * This audit reads the source manifest only. The merged manifest still carries
 * `ACCESS_NETWORK_STATE` because `androidx.work:work-runtime` declares it, and the single
 * WorkManager request in this app sets `setRequiresBatteryNotLow` with no network constraint, so
 * the permission is never exercised. Stripping it with `tools:node="remove"` would trade a
 * declared library contract for a cosmetic win, so it stays and is documented in
 * AndroidManifest.xml and README.md instead.
 */
class NetworkPermissionGuardTest {

    private val manifest = File("src/main/AndroidManifest.xml")

    private val forbidden = listOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.CHANGE_NETWORK_STATE"
    )

    @Test
    fun `the audit is reading a real manifest`() {
        assertTrue("AndroidManifest.xml is missing at ${manifest.absolutePath}", manifest.isFile)
        val declared = manifest.readText()
        assertTrue(
            "The audit read an empty manifest, so every assertion below would pass for free.",
            declared.contains("android.permission.FOREGROUND_SERVICE")
        )
    }

    @Test
    fun `no network permission is declared`() {
        val declared = manifest.readText()
        val offenders = forbidden.filter { declared.contains(it) }
        assertFalse(
            "The bridge makes no network request, so these permissions only contradict the " +
                "privacy statement in README.md: $offenders",
            offenders.isNotEmpty()
        )
    }
}
