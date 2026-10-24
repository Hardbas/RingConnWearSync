package io.github.hardbas.wearsync.bridge.service

/**
 * Wraps an in-app report with the build and the device it came from.
 *
 * The app can already say what it sees (see [SleepDiagnostics]); until now that text lived in a
 * scrollable dialog and the only way out of the phone was retyping it. A report copied a week
 * after the fact is also ambiguous without its build, because the parser changes every release
 * and "the sleep stages are wrong" describes every version that ever shipped.
 *
 * Formatting lives here, apart from Android, so the envelope is testable: the clipboard must
 * receive the header plus the report and nothing else, and a header that silently loses a line
 * would be worse than no header. Reading the build values and writing the clipboard stay in the
 * activity, where they need a Context.
 */
internal object DiagnosticsReport {

    /** Separates the envelope from the report so a pasted block stays readable and greppable. */
    const val SEPARATOR: String = "---\n"

    data class Build(
        val versionName: String,
        val versionCode: Long,
        val sdkInt: Int,
        val manufacturer: String,
        val model: String
    )

    fun header(build: Build): String = buildString {
        appendLine("RingConnWearSync diagnostics")
        appendLine("versionName=${build.versionName}")
        appendLine("versionCode=${build.versionCode}")
        appendLine("android=${build.sdkInt}")
        appendLine("device=${deviceLabel(build)}")
    }

    fun compose(build: Build, body: String): String =
        header(build) + SEPARATOR + body.trimEnd() + "\n"

    /**
     * `Samsung SM-S938B`, but `Google Pixel Watch 3` instead of `Google Google Pixel Watch 3`:
     * several OEMs already put their name in the model string, and a duplicated brand in the one
     * line that identifies the device makes the report look fabricated.
     */
    private fun deviceLabel(build: Build): String {
        val manufacturer = build.manufacturer.trim()
        val model = build.model.trim()
        return when {
            manufacturer.isEmpty() -> model
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
    }
}
