package io.github.hardbas.wearsync.bridge

import io.github.hardbas.wearsync.bridge.service.Page
import io.github.hardbas.wearsync.bridge.service.PagedRead
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A read that stops at the first page looks identical to a night with fewer
 * samples, so the cursor has to be followed — and has to stop when the server
 * misbehaves.
 */
class PagedReadTest {

    @Test
    fun `a single page is returned and the cursor is read once`() = runBlocking {
        val tokens = mutableListOf<String?>()

        val records = PagedRead.collect { token ->
            tokens += token
            Page(listOf("a", "b"), null)
        }

        assertEquals(listOf("a", "b"), records)
        assertEquals(listOf<String?>(null), tokens)
    }

    @Test
    fun `pages are concatenated in order and every cursor is passed back`() = runBlocking {
        val tokens = mutableListOf<String?>()
        val pages = mapOf(
            null to Page(listOf("a"), "p2"),
            "p2" to Page(listOf("b", "c"), "p3"),
            "p3" to Page(listOf("d"), null)
        )

        val records = PagedRead.collect { token ->
            tokens += token
            pages.getValue(token)
        }

        assertEquals(listOf("a", "b", "c", "d"), records)
        assertEquals(listOf<String?>(null, "p2", "p3"), tokens)
    }

    @Test
    fun `an empty page with a cursor still advances`() = runBlocking {
        val pages = mapOf(
            null to Page(emptyList<String>(), "p2"),
            "p2" to Page(listOf("x"), null)
        )

        val records = PagedRead.collect { token -> pages.getValue(token) }

        assertEquals(listOf("x"), records)
    }

    @Test
    fun `a server that never finishes is capped`() = runBlocking {
        var calls = 0

        val records = PagedRead.collect(maxPages = 3) { token ->
            calls++
            Page(listOf("$token-$calls"), "next")
        }

        assertEquals(3, calls)
        assertEquals(listOf("null-1", "next-2", "next-3"), records)
    }

    @Test
    fun `the default cap leaves room for a two day window`() {
        assertEquals(50, PagedRead.MAX_PAGES)
    }
}
