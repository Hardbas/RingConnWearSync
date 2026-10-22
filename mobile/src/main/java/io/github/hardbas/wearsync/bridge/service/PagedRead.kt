package io.github.hardbas.wearsync.bridge.service

/**
 * One page of records plus the cursor to the next one.
 *
 * Mirrors the shape of Health Connect's `ReadRecordsResponse` without depending
 * on it, so the page-walking logic can be tested off-device.
 */
data class Page<T>(val records: List<T>, val nextPageToken: String?)

/**
 * Walks a cursor-paged read to the end.
 *
 * Health Connect caps a read at its page size (1000 records by default) and
 * answers with a token instead of the remaining records. A wider time window
 * therefore loses data silently unless the token is followed — and a lost page
 * looks exactly like a night with fewer samples.
 */
object PagedRead {

    /**
     * Hard stop for a server that keeps answering with a token. At the default
     * 1000 records per page this is 50 000 records, far beyond a two-day window,
     * so hitting it means something is wrong rather than that data is missing.
     */
    const val MAX_PAGES = 50

    /**
     * Concatenates pages in the order they arrive, starting with a null token.
     *
     * @param fetch asks for one page; the argument is the token of the page to
     *   read, null for the first one.
     * @param maxPages safety cap, [MAX_PAGES] by default.
     */
    suspend fun <T> collect(
        maxPages: Int = MAX_PAGES,
        fetch: suspend (pageToken: String?) -> Page<T>
    ): List<T> {
        val records = mutableListOf<T>()
        var token: String? = null
        var pages = 0
        do {
            val page = fetch(token)
            records += page.records
            token = page.nextPageToken
            pages++
        } while (token != null && pages < maxPages)
        return records
    }
}
