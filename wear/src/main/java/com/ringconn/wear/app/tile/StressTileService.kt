package com.ringconn.wear.app.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.StateBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.expression.AnimationParameterBuilders
import androidx.wear.protolayout.expression.AppDataKey
import androidx.wear.protolayout.expression.DynamicBuilders
import androidx.wear.protolayout.expression.DynamicDataBuilders
import androidx.wear.tiles.EventBuilders
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
 * Renders an atmospheric HUD tachometer gauge layout with side-by-side
 * hero score, live VFC telemetry, and hot-to-cold alarm dial indicator.
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

    override fun onTileEnterEvent(tileEnterEvent: EventBuilders.TileEnterEvent) {
        super.onTileEnterEvent(tileEnterEvent)
    }

    companion object {
        const val RESOURCES_VERSION = "oneui8-stress-v10"
        private const val RES_BG_STRESS_CAPSULE = "oneui_bg_stress_capsule"
        private const val RES_IC_STRESS = "ic_comp_stress"
        private const val RES_GAUGE_RELAXED = "oneui_ic_gauge_relaxed"
        private const val RES_GAUGE_BALANCED = "oneui_ic_gauge_balanced"
        private const val RES_GAUGE_MODERATE = "oneui_ic_gauge_moderate"
        private const val RES_GAUGE_HIGH = "oneui_ic_gauge_high"

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
                .addIdToImageMapping(
                    RES_GAUGE_RELAXED,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.oneui_ic_gauge_relaxed)
                                .build()
                        ).build()
                )
                .addIdToImageMapping(
                    RES_GAUGE_BALANCED,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.oneui_ic_gauge_balanced)
                                .build()
                        ).build()
                )
                .addIdToImageMapping(
                    RES_GAUGE_MODERATE,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.oneui_ic_gauge_moderate)
                                .build()
                        ).build()
                )
                .addIdToImageMapping(
                    RES_GAUGE_HIGH,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.oneui_ic_gauge_high)
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
                score < 30 -> 0xFF00E676.toInt() // Emerald Green
                score < 60 -> 0xFF00E5FF.toInt() // Sky Cyan
                score < 80 -> 0xFFFFD600.toInt() // Sunny Amber
                else -> 0xFFFF3D00.toInt()       // Coral Red
            }

            val statusBgInt = when {
                score < 30 -> 0x3300E676.toInt()
                score < 60 -> 0x3300E5FF.toInt()
                score < 80 -> 0x33FFD600.toInt()
                else -> 0x33FF3D00.toInt()
            }

            val gaugeResId = when {
                score < 30 -> RES_GAUGE_RELAXED
                score < 60 -> RES_GAUGE_BALANCED
                score < 80 -> RES_GAUGE_MODERATE
                else -> RES_GAUGE_HIGH
            }

            val hrvSubtitle = if (vitals.lastHrv > 0) {
                context.getString(R.string.stress_hrv_format, vitals.lastHrv)
            } else {
                context.getString(R.string.stress_resting)
            }

            val sleepLabelColor = 0xFFE9D5FF.toInt()      // Soft Pastel Lilac
            val cyanAccentColor = 0xFF5EEAD4.toInt()      // Electric Aquamarine Mint

            val whiteColor = argb(0xFFFFFFFF.toInt())
            val lavenderColor = argb(sleepLabelColor)
            val statusColor = argb(statusColorInt)
            val translucentGlassBlack = argb(0x66000000.toInt())

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

            // 1. One UI Watch 8 Enlarged Stadium Capsule Background (206dp x 146dp)
            val bgImage = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_BG_STRESS_CAPSULE)
                .setWidth(dp(206f))
                .setHeight(dp(146f))
                .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FILL_BOUNDS)
                .build()

            // 2. Top Header Row: Title on Left, Status Pill on Right (width 152dp)
            val titleText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.stress_title))
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.TITLE))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                        .setColor(lavenderColor)
                        .build()
                )
                .build()

            val stressPulseAnimSpec = AnimationParameterBuilders.AnimationSpec.Builder()
                .setAnimationParameters(
                    AnimationParameterBuilders.AnimationParameters.Builder()
                        .setDurationMillis(800L)
                        .setEasing(AnimationParameterBuilders.Easing.cubicBezier(0.4f, 0.0f, 0.2f, 1.0f))
                        .build()
                )
                .setRepeatable(
                    AnimationParameterBuilders.Repeatable.Builder()
                        .setRepeatMode(AnimationParameterBuilders.REPEAT_MODE_REVERSE)
                        .build()
                )
                .build()

            val dynamicStatusColor = DynamicBuilders.DynamicColor.animate(
                statusColorInt,
                0x60FFFFFF.toInt(),
                stressPulseAnimSpec
            )

            val dynamicStatusTint = androidx.wear.protolayout.ColorBuilders.ColorProp.Builder(statusColorInt)
                .setDynamicValue(dynamicStatusColor)
                .build()

            val statusChipText = LayoutElementBuilders.Text.Builder()
                .setText(statusText)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.LABEL))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(dynamicStatusTint)
                        .build()
                )
                .build()

            val statusChipBox = LayoutElementBuilders.Box.Builder()
                .setWidth(wrap())
                .setHeight(dp(18f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(argb(statusBgInt))
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(9f)).build())
                                .build()
                        )
                        .setPadding(
                            ModifiersBuilders.Padding.Builder()
                                .setStart(dp(8f))
                                .setEnd(dp(8f))
                                .build()
                        )
                        .build()
                )
                .addContent(statusChipText)
                .build()

            val topHeaderRow = LayoutElementBuilders.Row.Builder()
                .setWidth(dp(132f))
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(titleText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(10f)).setHeight(dp(1f)).build())
                .addContent(statusChipBox)
                .build()

            // 3. Center Hero Row: Score & VFC on Left, Semicircle Tachometer on Right (width 156dp)
            val scoreNumberText = LayoutElementBuilders.Text.Builder()
                .setText(scoreStr)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.HERO))
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
                        .setSize(sp(TileType.LABEL))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(argb(0xFFD8B4FE.toInt()))
                        .build()
                )
                .build()

            val scoreNumbersRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
                .addContent(scoreNumberText)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(3f)).setHeight(dp(1f)).build())
                .addContent(scoreUnitText)
                .build()

            val hrvText = LayoutElementBuilders.Text.Builder()
                .setText(hrvSubtitle)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.LABEL))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                        .setColor(argb(cyanAccentColor))
                        .build()
                )
                .build()

            val sweepAnimSpec = AnimationParameterBuilders.AnimationSpec.Builder()
                .setAnimationParameters(
                    AnimationParameterBuilders.AnimationParameters.Builder()
                        .setDurationMillis(600L)
                        .setEasing(AnimationParameterBuilders.Easing.cubicBezier(0.22f, 0.25f, 0.0f, 1.0f))
                        .build()
                )
                .build()

            // Dynamic mini progress bar for Stress (0 to 100 mapped across 76dp track)
            val maxStressBarWidth = 76f
            val targetStressBarWidth = if (score > 0) {
                ((score / 100f) * maxStressBarWidth).coerceIn(4f, maxStressBarWidth)
            } else {
                4f
            }
            val dynamicStressBarWidth = DimensionBuilders.DpProp.Builder(targetStressBarWidth)
                .setDynamicValue(DynamicBuilders.DynamicFloat.animate(AppDataKey("stress_bar_width"), sweepAnimSpec))
                .build()

            val stressMiniProgressBar = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(maxStressBarWidth))
                .setHeight(dp(3.5f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(argb(0x2EFFFFFF.toInt()))
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(2f)).build())
                                .build()
                        )
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Box.Builder()
                        .setWidth(dynamicStressBarWidth)
                        .setHeight(dp(3.5f))
                        .setModifiers(
                            ModifiersBuilders.Modifiers.Builder()
                                .setBackground(
                                    ModifiersBuilders.Background.Builder()
                                        .setColor(statusColor)
                                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(2f)).build())
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()

            val leftHeroColumn = LayoutElementBuilders.Column.Builder()
                .setWidth(dp(88f))
                .setHeight(wrap())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                .addContent(scoreNumbersRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(3f)).setWidth(dp(1f)).build())
                .addContent(stressMiniProgressBar)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(3f)).setWidth(dp(1f)).build())
                .addContent(hrvText)
                .build()

            val gaugeImage = LayoutElementBuilders.Image.Builder()
                .setResourceId(gaugeResId)
                .setWidth(dp(62f))
                .setHeight(dp(46.5f))
                .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FIT)
                .build()

            val centerHeroRow = LayoutElementBuilders.Row.Builder()
                .setWidth(dp(156f))
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(leftHeroColumn)
                .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(6f)).setHeight(dp(1f)).build())
                .addContent(gaugeImage)
                .build()

            // 4. Action Pill Button (94dp x 24dp, translucent glass)
            val actionButtonText = LayoutElementBuilders.Text.Builder()
                .setText(context.getString(R.string.details))
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(TileType.LABEL))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(whiteColor)
                        .build()
                )
                .build()

            val actionPillBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(94f))
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

            // 5. Assemble inside the 206dp x 146dp Stadium Capsule
            val capsuleContentColumn = LayoutElementBuilders.Column.Builder()
                .setWidth(dp(206f))
                .setHeight(dp(146f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(12f)).setWidth(dp(1f)).build())
                .addContent(topHeaderRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).setWidth(dp(1f)).build())
                .addContent(centerHeroRow)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).setWidth(dp(1f)).build())
                .addContent(actionPillBox)
                .build()

            val stadiumCapsuleBox = LayoutElementBuilders.Box.Builder()
                .setWidth(dp(206f))
                .setHeight(dp(146f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(mainCapsuleClickable)
                        .setContentUpdateAnimation(
                            ModifiersBuilders.AnimatedVisibility.Builder()
                                .setEnterTransition(ModifiersBuilders.DefaultContentTransitions.fadeIn())
                                .build()
                        )
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

            val tileState = StateBuilders.State.Builder()
                .addKeyToValueMapping(AppDataKey("stress_bar_width"), DynamicDataBuilders.DynamicDataValue.fromFloat(targetStressBarWidth))
                .build()

            return TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(timeline)
                .setState(tileState)
                .setFreshnessIntervalMillis(30_000L)
                .build()
        }
    }
}
