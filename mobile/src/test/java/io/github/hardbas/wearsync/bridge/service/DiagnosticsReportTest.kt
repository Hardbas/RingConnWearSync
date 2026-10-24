package io.github.hardbas.wearsync.bridge.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The diagnostic already proved what the app sees; nobody could get it out of the phone.
 *
 * Reported from hardware: the sleep numbers were wrong, the maintainer asked for the
 * diagnostic, and the user had to retype a scrollable dialog into a chat window. A report
 * without the build it came from is also unusable a week later, because the parser changes
 * every release and "the sleep stages are wrong" describes eleven different versions.
 *
 * These tests pin the envelope, not the platform: what the clipboard receives must be the
 * header plus the report, byte for byte, so a user pasting it into a thread never loses the
 * part that identifies which build produced it.
 */
class DiagnosticsReportTest {

    private val build = DiagnosticsReport.Build(
        versionName = "1.9.38",
        versionCode = 73L,
        sdkInt = 36,
        manufacturer = "Samsung",
        model = "SM-S938B"
    )

    @Test
    fun `the header names the build and the device`() {
        val header = DiagnosticsReport.header(build)
        val lines = header.lines()

        assertTrue("header lost the app name line: $header", lines[0] == "RingConnWearSync diagnostics")
        assertTrue("header lost versionName: $header", lines.contains("versionName=1.9.38"))
        assertTrue("header lost versionCode: $header", lines.contains("versionCode=73"))
        assertTrue("header lost the API level: $header", lines.contains("android=36"))
        assertTrue(
            "header must identify the device, because a watch-only bug and a phone-only bug " +
                "produce the same complaint: $header",
            lines.contains("device=Samsung SM-S938B")
        )
    }

    @Test
    fun `the header is stable across calls`() {
        assertEquals(DiagnosticsReport.header(build), DiagnosticsReport.header(build))
    }

    @Test
    fun `the composed report is the header then the body unchanged`() {
        val body = "sessionsInWindow=1\ncountedForToday=1\npersisted=deep=55 rem=60"
        val composed = DiagnosticsReport.compose(build, body)

        assertEquals(
            DiagnosticsReport.header(build) + DiagnosticsReport.SEPARATOR + body + "\n",
            composed
        )
        assertTrue("the body must survive verbatim: $composed", composed.endsWith(body + "\n"))
    }

    /**
     * A report that failed while it was being collected arrives as an error message, not as a
     * diagnostic. It still has to travel: an envelope that drops it leaves the user with a
     * clipboard entry that says only which phone it was, and the maintainer with nothing.
     */
    @Test
    fun `an empty body still composes`() {
        val composed = DiagnosticsReport.compose(build, "")

        assertEquals(DiagnosticsReport.header(build) + DiagnosticsReport.SEPARATOR + "\n", composed)
    }

    @Test
    fun `an unknown version does not fabricate one`() {
        val unknown = build.copy(versionName = "?", versionCode = 0L)
        val header = DiagnosticsReport.header(unknown)

        assertTrue(header, header.contains("versionName=?"))
        assertTrue(header, header.contains("versionCode=0"))
    }

    @Test
    fun `the manufacturer is not repeated when it already prefixes the model`() {
        val google = build.copy(manufacturer = "Google", model = "Google Pixel Watch 3")

        assertTrue(
            DiagnosticsReport.header(google),
            DiagnosticsReport.header(google).contains("device=Google Pixel Watch 3")
        )
    }
}
