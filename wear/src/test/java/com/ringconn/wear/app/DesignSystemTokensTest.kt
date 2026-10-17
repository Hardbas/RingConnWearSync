package com.ringconn.wear.app

import com.ringconn.wear.app.tile.TileType
import com.ringconn.wear.app.ui.TextBig
import com.ringconn.wear.app.ui.TextBody
import com.ringconn.wear.app.ui.TextCaption
import com.ringconn.wear.app.ui.TextHero
import com.ringconn.wear.app.ui.TextLabel
import com.ringconn.wear.app.ui.VitalsGold
import com.ringconn.wear.app.ui.VitalsGradientEnd
import com.ringconn.wear.app.ui.VitalsGradientStart
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

class DesignSystemTokensTest {

    @Test
    fun tileType_hasStrictOneUiTokens() {
        assertEquals(12f, TileType.LABEL, 0.001f)
        assertEquals(14f, TileType.TITLE, 0.001f)
        assertEquals(16f, TileType.VALUE, 0.001f)
        assertEquals(26f, TileType.HERO, 0.001f)
    }

    @Test
    fun composeTypography_hasUnifiedScale() {
        assertEquals(10f, TextCaption.value, 0.001f)
        assertEquals(12f, TextLabel.value, 0.001f)
        assertEquals(14f, TextBody.value, 0.001f)
        assertEquals(24f, TextBig.value, 0.001f)
        assertEquals(36f, TextHero.value, 0.001f)
    }

    @Test
    fun vitalsIdentity_usesGoldTitaniumPalette() {
        // Vitals palette distinct from Heart Rate crimson
        assertEquals(0xFF6E5A2F.toInt(), VitalsGradientStart.toArgb())
        assertEquals(0xFF2B2514.toInt(), VitalsGradientEnd.toArgb())
        assertEquals(0xFFEDCB8B.toInt(), VitalsGold.toArgb())
    }
}
