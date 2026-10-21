package io.github.hardbas.wearsync.app.tile

import android.content.Context
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.hardbas.wearsync.app.R
import io.github.hardbas.wearsync.app.complication.RingConnComplicationProvider
import io.github.hardbas.wearsync.app.data.RingConnVitals
import io.github.hardbas.wearsync.app.tile.design.AndroidTileStrings
import io.github.hardbas.wearsync.app.tile.design.TileChrome
import io.github.hardbas.wearsync.app.tile.design.TileDesign
import io.github.hardbas.wearsync.app.tile.design.TileGauge
import io.github.hardbas.wearsync.app.tile.design.TileMeter
import io.github.hardbas.wearsync.app.tile.design.TileMetric
import io.github.hardbas.wearsync.app.tile.design.TileStrings
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated Circular Bezel Arc Heart Rate Tile for Samsung Galaxy Watch (Wear OS 5 / 6).
 *
 * Drawing comes entirely from the shared design system: the bezel gauge, the
 * backdrop, the type scale and the chrome are the same objects the other four
 * tiles use, so the carousel reads as one app. This file only supplies meaning:
 * the reading, the zone and the range.
 */
class HeartRateTileService : TileService() {

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
        const val RESOURCES_VERSION = "oneui8-hr-circular-v1"
        private const val RES_IC_HR = "ic_vitals_hr"
        private const val DETAIL_ACTIVITY = "io.github.hardbas.wearsync.app.ui.HeartRateDetailActivity"

        fun buildResources(): ResourceBuilders.Resources {
            return ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping(
                    RES_IC_HR,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_vitals_hr)
                                .build()
                        ).build()
                )
                .build()
        }

        fun buildTile(
            context: Context,
            deviceParams: DeviceParameters,
            vitals: RingConnVitals = RingConnComplicationProvider.getLatestVitals(context)
        ): TileBuilders.Tile = buildTile(AndroidTileStrings(context), context.packageName, deviceParams, vitals)

        fun buildTile(
            strings: TileStrings,
            packageName: String,
            deviceParams: DeviceParameters,
            vitals: RingConnVitals
        ): TileBuilders.Tile {
            val hasHr = vitals.lastHeartRate > 0
            val hrVal = vitals.lastHeartRate
            val hrValue = if (hasHr) "$hrVal" else "--"

            // Z1 Resting -> Z5 Peak. Index drives both the ladder and the accent.
            val zoneIndex = when {
                !hasHr -> 0
                hrVal < 100 -> 0
                hrVal <= 120 -> 1
                hrVal <= 140 -> 2
                hrVal <= 160 -> 3
                else -> 4
            }
            val zoneAccent = if (hasHr) TileDesign.Accent.heartRate[zoneIndex] else TileDesign.Text.MUTED

            val fraction = if (hasHr) ((hrVal - 40f) / 130f).coerceIn(0f, 1f) else 0f

            val timeText = if (vitals.refreshTimestamp > 0L) {
                DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
                    .format(Date(vitals.refreshTimestamp * 1000L))
            } else {
                strings.text(R.string.just_now)
            }

            val clickable = TileChrome.launchDetail(packageName, DETAIL_ACTIVITY)

            // Block 1 — header: icon + ONE short uppercase metric word and nothing
            // else. A 192dp tile is a circle, so a row sitting ~67dp above centre
            // only has ~119dp of usable width; the timestamp would have pushed the
            // label into the bezel arc, so it moved into the frosted card below.
            val headerIcon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_HR)
                .setWidth(dp(14f))
                .setHeight(dp(14f))
                .setColorFilter(
                    LayoutElementBuilders.ColorFilter.Builder()
                        .setTint(argb(zoneAccent))
                        .build()
                )
                .build()

            val headerLabel = LayoutElementBuilders.Text.Builder()
                .setText(strings.text(R.string.tile_heart_rate_title).uppercase(Locale.getDefault()))
                .setMaxLines(1)
                .setFontStyle(TileDesign.Type.label(zoneAccent))
                .build()

            val headerRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(headerIcon)
                .addContent(TileChrome.hSpacer(4f))
                .addContent(headerLabel)
                .build()

            // Hero: 40sp tabular number + unit. Nothing else shares this row, so
            // the number keeps its full optical weight inside the round bezel.
            val heroNumber = LayoutElementBuilders.Text.Builder()
                .setText(hrValue)
                .setMaxLines(1)
                .setFontStyle(TileDesign.Type.hero())
                .build()

            val heroUnit = LayoutElementBuilders.Text.Builder()
                .setText(strings.text(R.string.bpm_unit).lowercase(Locale.getDefault()))
                .setMaxLines(1)
                .setFontStyle(TileDesign.Type.unit(zoneAccent))
                .build()

            val heroRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(heroNumber)
                .addContent(TileChrome.hSpacer(3f))
                .addContent(heroUnit)
                .build()

            // Block 3 — the meter block, wrapped in frosted glass so the sub-graphic
            // sits *on* something instead of floating on the gradient. Line 1 is the
            // sub-graphic (five-zone ladder), then the small readings, then the sync
            // timestamp that used to crowd the header.
            val hasMinMax = vitals.minHeartRate > 0 && vitals.maxHeartRate > 0
            val rangeText = if (hasMinMax) "${vitals.minHeartRate}–${vitals.maxHeartRate}" else "--"

            val cardReadings = LayoutElementBuilders.Text.Builder()
                .setText(rangeText)
                .setMaxLines(1)
                .setFontStyle(TileDesign.Type.caption(zoneAccent))
                .build()

            val cardCaption = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(cardReadings)
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(" · ")
                        .setMaxLines(1)
                        .setFontStyle(TileDesign.Type.caption(TileDesign.Text.MUTED))
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(timeText)
                        .setMaxLines(1)
                        .setFontStyle(TileDesign.Type.caption(TileDesign.Text.MUTED))
                        .build()
                )
                .build()

            val cardContent = LayoutElementBuilders.Column.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(TileMeter.ladder(count = 5, activeIndex = zoneIndex, accent = zoneAccent))
                .addContent(TileChrome.spacer(4f))
                .addContent(cardCaption)
                .build()

            val meterCard = TileChrome.card(cardContent)

            val actionPill = TileChrome.actionPill(
                LayoutElementBuilders.Text.Builder()
                    .setText(strings.text(R.string.measure))
                    .setMaxLines(1)
                    .setFontStyle(TileDesign.Type.caption(TileDesign.Text.PRIMARY))
                    .build(),
                clickable
            )

            val contentColumn = LayoutElementBuilders.Column.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(headerRow)
                .addContent(TileChrome.spacer(3f))
                .addContent(heroRow)
                .addContent(TileChrome.spacer(5f))
                .addContent(meterCard)
                .addContent(TileChrome.spacer(6f))
                .addContent(actionPill)
                .build()

            val gauge = TileGauge.elements(
                accent = zoneAccent,
                targetSweep = TileGauge.sweepFor(fraction)
            )

            val rootBox = LayoutElementBuilders.Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(TileChrome.rootModifiers(TileMetric.HEART_RATE, clickable))
                .addContent(gauge[0])
                .addContent(gauge[1])
                .addContent(contentColumn)
                .build()

            val timelineEntry = TimelineBuilders.TimelineEntry.Builder()
                .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(rootBox).build())
                .build()

            val timeline = TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(timelineEntry)
                .build()

            return TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(timeline)
                .setFreshnessIntervalMillis(30_000L)
                .build()
        }
    }
}
