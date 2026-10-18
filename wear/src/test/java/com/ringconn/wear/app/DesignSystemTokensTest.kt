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

    @Test
    fun dynamicState_compilesAndBuildsCorrectly() {
        val key = androidx.wear.protolayout.expression.AppDataKey<androidx.wear.protolayout.expression.DynamicBuilders.DynamicFloat>("test_key")
        val state = androidx.wear.protolayout.StateBuilders.State.Builder()
            .addKeyToValueMapping(key, androidx.wear.protolayout.expression.DynamicDataBuilders.DynamicDataValue.fromFloat(42f))
            .build()
        val tile = androidx.wear.tiles.TileBuilders.Tile.Builder()
            .setState(state)
            .build()
        org.junit.Assert.assertNotNull(tile.state)
    }

    @Test
    fun repeatablePulse_compilesAndBuildsCorrectly() {
        val pulseSpec = androidx.wear.protolayout.expression.AnimationParameterBuilders.AnimationSpec.Builder()
            .setAnimationParameters(
                androidx.wear.protolayout.expression.AnimationParameterBuilders.AnimationParameters.Builder()
                    .setDurationMillis(800L)
                    .build()
            )
            .setRepeatable(
                androidx.wear.protolayout.expression.AnimationParameterBuilders.Repeatable.Builder()
                    .setRepeatMode(androidx.wear.protolayout.expression.AnimationParameterBuilders.REPEAT_MODE_REVERSE)
                    .build()
            )
            .build()
        val dynamicPulse = androidx.wear.protolayout.expression.DynamicBuilders.DynamicFloat.animate(6f, 9f, pulseSpec)
        org.junit.Assert.assertNotNull(dynamicPulse)
    }

    @Test
    fun arcLine_compilesAndBuildsCorrectly() {
        val arcLine = androidx.wear.protolayout.LayoutElementBuilders.ArcLine.Builder()
            .setLength(androidx.wear.protolayout.DimensionBuilders.degrees(180f))
            .setThickness(androidx.wear.protolayout.DimensionBuilders.dp(8f))
            .setColor(androidx.wear.protolayout.ColorBuilders.argb(0xFFFF5D99.toInt()))
            .build()
        val arc = androidx.wear.protolayout.LayoutElementBuilders.Arc.Builder()
            .addContent(arcLine)
            .setAnchorAngle(androidx.wear.protolayout.DimensionBuilders.degrees(180f))
            .setAnchorType(androidx.wear.protolayout.LayoutElementBuilders.ARC_ANCHOR_START)
            .build()
        org.junit.Assert.assertNotNull(arc)
    }

    @Test
    fun arcLine_withDynamicLength_compiles() {
        val sweepAnimSpec = androidx.wear.protolayout.expression.AnimationParameterBuilders.AnimationSpec.Builder()
            .setAnimationParameters(
                androidx.wear.protolayout.expression.AnimationParameterBuilders.AnimationParameters.Builder()
                    .setDurationMillis(600L)
                    .build()
            )
            .build()
        val dynamicDegrees = androidx.wear.protolayout.DimensionBuilders.DegreesProp.Builder(90f)
            .setDynamicValue(
                androidx.wear.protolayout.expression.DynamicBuilders.DynamicFloat.animate(
                    androidx.wear.protolayout.expression.AppDataKey("hr_arc_sweep"),
                    sweepAnimSpec
                )
            )
            .build()
        val angularConstraint = androidx.wear.protolayout.DimensionBuilders.AngularLayoutConstraint.Builder(180f)
            .build()
        val arcLine = androidx.wear.protolayout.LayoutElementBuilders.ArcLine.Builder()
            .setLength(dynamicDegrees)
            .setLayoutConstraintsForDynamicLength(angularConstraint)
            .setThickness(androidx.wear.protolayout.DimensionBuilders.dp(8f))
            .setColor(androidx.wear.protolayout.ColorBuilders.argb(0xFFFF5D99.toInt()))
            .build()
        org.junit.Assert.assertNotNull(arcLine)
    }

    @Test
    fun allTiles_buildResources_haveValidVersionsAndMappings() {
        val hrRes = com.ringconn.wear.app.tile.HeartRateTileService.buildResources()
        org.junit.Assert.assertEquals("oneui8-hr-circular-v1", hrRes.version)

        val dailyRes = com.ringconn.wear.app.tile.DailySummaryTileService.buildResources()
        org.junit.Assert.assertEquals("oneui8-summary-circular-v1", dailyRes.version)

        val stressRes = com.ringconn.wear.app.tile.StressTileService.buildResources()
        org.junit.Assert.assertEquals("oneui8-stress-circular-v1", stressRes.version)

        val tempRes = com.ringconn.wear.app.tile.TemperatureTileService.buildResources()
        org.junit.Assert.assertEquals("oneui8-temp-circular-v1", tempRes.version)

        val vitalsRes = com.ringconn.wear.app.tile.VitalsTileService.buildResources()
        org.junit.Assert.assertEquals("oneui8-vitals-circular-v1", vitalsRes.version)
    }
}





