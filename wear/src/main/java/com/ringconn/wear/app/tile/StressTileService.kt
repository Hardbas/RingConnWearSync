package com.ringconn.wear.app.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.ringconn.wear.app.R
import com.ringconn.wear.app.complication.RingConnComplicationProvider
import com.ringconn.wear.app.data.RingConnVitals
import com.ringconn.wear.app.ui.StressDetailActivity

/**
 * Dedicated One UI Watch 8 Stress Tile for Samsung Galaxy Watch 8.
 * Renders the 176dp x 126dp Stadium Capsule layout with stress level color,
 * score, progress bar, and localized system strings without runtime layoutConstraint crashes.
 */
class StressTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        return Futures.immediateFuture(
            buildTile(this, requestParams.deviceConfiguration)
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(buildResources())
    }

    companion object {
        const val RESOURCES_VERSION = "oneui8-stress-v4"
        private const val RES_BG_STRESS_CAPSULE = "oneui_bg_stress_capsule"
        private const val RES_IC_STRESS = "ic_comp_stress"

        fun buildResources(): ResourceBuilders.Resources {
            return ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping(
                    RES_BG_STRESS_CAPSULE,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.oneui_bg_stress_capsule)
                                .build()
                        ).build()
                )
                .addIdToImageMapping(
                    RES_IC_STRESS,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_comp_stress)
                                .build()
                        ).build()
                )
                .build()
        }

        fun buildTile(
            context: Context,
            deviceParams: DeviceParameters,
            vitals: RingConnVitals = RingConnComplicationProvider.getLatestVitals(context)
        ): TileBuilders.Tile {
            val score = vitals.stressScore.coerceIn(0, 100)
            val scoreStr = if (score > 0) "$score" else "--"

            val statusText = when {
                score < 30 -> context.getString(R.string.stress_relaxed)
                score < 60 -> context.getString(R.string.stress_balanced)
                score < 80 -> context.getString(R.string.stress_moderate)
                else -> context.getString(R.string.stress_high)
            }

            val statusColorInt = when {
                score < 30 -> 0xFF00E676.toInt()
                score < 60 -> 0xFF00E5FF.toInt()
                score < 80 -> 0xFFFFD600.toInt()
                else -> 0xFFFF5252.toInt()
            }

            val hrvSubtitle = if (vitals.lastHrv > 0) {
                context.getString(R.string.stress_hrv_format, vitals.lastHrv)
            } else {
                context.getString(R.string.stress_resting)
            }

            val whiteColor = argb(0xFFFFFFFF.toInt())
            val silverColor = argb(0xFFB0BEC5.toInt())
            val statusColor = argb(statusColorInt)
            val translucentGlassBlack = argb(0x80000000.toInt())

            val clickAction = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName(StressDetailActivity::class.java.name)
                        .build()
                ).build()

            val mainCapsuleClickable = ModifiersBuilders.Clickable.Builder()
                .setOnClick(clickAction)
                .setId("open_stress_detail")
                .build()

            // 1. One UI Watch 8 Circular Fit Stadium Capsule (190dp x 136dp)
            val bgImage = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_BG_STRESS_CAPSULE)
                .setWidth(dp(190f))
                .setHeight(dp(136f))
                .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FILL_BOUNDS)
                .build()

            // 2. Title Text (13.5sp Bold White)
            val titleText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.stress_title))
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(13.5f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            // 3. Subtitle Row (Status · HRV)
            val subtitleText = LayoutElementBuilders.Text.Builder()
                .setText("$statusText  ·  $hrvSubtitle")
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(11f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                        .setColor(statusColor)
                        .build()
                )
                .build()

            // 4. Hero Score Row (Icon + Big Score + /100)
            val stressIcon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_STRESS)
                .setWidth(dp(16f))
                .setHeight(dp(16f))
                .build()

            val scoreNumberText = LayoutElementBuilders.Text.Builder()
                .setText(scoreStr)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(24f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            val scoreUnitText = LayoutElementBuilders.Text.Builder()
                .setText("/100")
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(12f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                        .setColor(silverColor)
                        .build()
                )
                .build()

            val heroScoreRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(stressIcon)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(5f)).setHeight(dp(1f)).build())
                .addContent(scoreNumberText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(3f)).setHeight(dp(1f)).build())
                .addContent(scoreUnitText)
                .build()

            // 5. One UI 8 Static Deterministic Stress Bar (136dp x 10dp) - ZERO DynamicFloat crash risk
            val barWidthDp = 136f
            val barHeightDp = 10f
            val scale = barWidthDp / 100f
            val progressWidthDp = (score * scale).coerceIn(4f, barWidthDp)
            val dotPosDp = ((score * scale) - 3.5f).coerceIn(0f, barWidthDp - 7f)

            val barTrackBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(barWidthDp))
                .setHeight(dp(barHeightDp))
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(translucentGlassBlack)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(5f)).build())
                                .build()
                        ).build()
                )
                .build()

            val activeProgressBar = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(progressWidthDp))
                .setHeight(dp(barHeightDp))
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(statusColor)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(5f)).build())
                                .build()
                        ).build()
                )
                .build()

            val activeProgressRow = LayoutElementBuilders.Row.Builder()
                .setWidth(dp(barWidthDp))
                .setHeight(dp(barHeightDp))
                .addContent(activeProgressBar)
                .build()

            val dotIndicator = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(7f))
                .setHeight(dp(7f))
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(whiteColor)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(3.5f)).build())
                                .build()
                        ).build()
                )
                .build()

            val dotIndicatorRow = LayoutElementBuilders.Row.Builder()
                .setWidth(dp(barWidthDp))
                .setHeight(dp(barHeightDp))
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(
                    LayoutElementBuilders.Box.Builder()
                        .setWidth(dp(dotPosDp))
                        .setHeight(dp(barHeightDp))
                        .build()
                )
                .addContent(dotIndicator)
                .build()

            val stressChartBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(barWidthDp))
                .setHeight(dp(barHeightDp))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(barTrackBox)
                .addContent(activeProgressRow)
                .addContent(dotIndicatorRow)
                .build()

            // 6. Action Pill Button (92dp x 24dp, radius 12dp, 50% glass)
            val actionButtonText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.details))
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(12f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            val actionPillBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(92f))
                .setHeight(dp(24f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(mainCapsuleClickable)
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(translucentGlassBlack)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(12f)).build())
                                .build()
                        )
                        .build()
                )
                .addContent(actionButtonText)
                .build()

            // Assemble inside the 190dp x 136dp Stadium Capsule
            val capsuleContentColumn = LayoutElementBuilders.Column.Builder()
                .setWidth(dp(190f))
                .setHeight(dp(136f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).setWidth(dp(1f)).build())
                .addContent(titleText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(2f)).setWidth(dp(1f)).build())
                .addContent(subtitleText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).setWidth(dp(1f)).build())
                .addContent(heroScoreRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).setWidth(dp(1f)).build())
                .addContent(stressChartBox)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(7f)).setWidth(dp(1f)).build())
                .addContent(actionPillBox)
                .build()

            val stadiumCapsuleBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(190f))
                .setHeight(dp(136f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(mainCapsuleClickable)
                        .build()
                )
                .addContent(bgImage)
                .addContent(capsuleContentColumn)
                .build()

            val rootBox = LayoutElementBuilders.Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(stadiumCapsuleBox)
                .build()

            val layout = LayoutElementBuilders.Layout.Builder().setRoot(rootBox).build()

            val timelineEntry = TimelineBuilders.TimelineEntry.Builder()
                .setLayout(layout)
                .build()

            val timeline = TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(timelineEntry)
                .build()

            return TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(timeline)
                .setFreshnessIntervalMillis(60_000L)
                .build()
        }
    }
}
