package io.github.hardbas.wearsync.app.data

/**
 * Whether a measurement attempt refreshed the reading.
 *
 * Two facts are needed and neither is sufficient alone: a sync completion signal
 * arrived, and the reading on the watch is newer than it was before the request.
 * A signal by itself proves nothing, because an unrelated sync can land while we
 * wait.
 *
 * What this does NOT prove: that the signal belongs to the attempt that asked for
 * it. A concurrent foreign sync that persists a reading advances both facts and
 * is indistinguishable here. Correlating a request with its response needs the
 * phone to echo a request identifier, which the wear side cannot do alone. So the
 * gate rejects stale and non-advancing attempts; it does not establish ownership.
 */
object MeasureOutcome {

    fun refreshed(before: Long, after: Long, syncSignalSeen: Boolean): Boolean =
        syncSignalSeen && after > before
}
