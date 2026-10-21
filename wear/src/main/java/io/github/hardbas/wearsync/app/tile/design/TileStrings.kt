package io.github.hardbas.wearsync.app.tile.design

import android.content.Context

/**
 * Text lookup seam for the tiles.
 *
 * A tile needs exactly one thing from Android: `getString`. Taking a
 * `Context` for that alone made every tile untestable on the JVM, which is why
 * their layouts had no regression net. Tiles now take this interface, the
 * framework entry point wraps the real context, and tests pass a map.
 */
fun interface TileStrings {
    /** Returns the string for [resId]; a missing id renders as an empty string. */
    fun text(resId: Int): String
}

/** Production binding: the tile's real resources. */
class AndroidTileStrings(private val context: Context) : TileStrings {
    override fun text(resId: Int): String = context.getString(resId)
}
