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
 * Dedicated Circular Bezel Arc Vitals Tile for Samsung Galaxy Watch (Wear OS 5 / 6).
 *
 * Same design system as the other four tiles: shared gauge, shared backdrop,
 * shared type scale. Its own second read is the SpO2 bar, because oxygen
 * saturation is the vitals metric you glance at without reading a number.
 */
class VitalsTileService : TileService() {

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
        const val RESOURCES_VERSION = "oneui8-vitals-circular-v1"
        private const val RES_IC_HR = "ic_vitals_hr"
        private const val DETAIL_ACTIVITY = "io.github.hardbas.wearsync.app.ui.VitalsDetailActivity"

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
            val accent = TileDesign.Accent.vitals

            val hasHr = vitals.lastHeartRate > 0
            val hrVal = vitals.lastHeartRate
            val hrValue = if (hasHr) "$hrVal" else "--"

            val hasSpo2 = vitals.lastSpo2 > 0
            val spo2Value = if (hasSpo2) "${vitals.lastSpo2}%" else "--"

            val hasBattery = vitals.ringBattery > 0
            val batteryValue = if (hasBattery) "${vitals.ringBattery}%" else "--"

            val fraction = if (hasHr) ((hrVal - 40f) / 120f).coerceIn(0f, 1f) else 0f

            val timeText = if (vitals.refreshTimestamp > 0L) {
                DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
                    .format(Date(vitals.refreshTimestamp * 1000L))
            } else {
                strings.text(R.string.just_now)
            }

            val clickable = TileChrome.launchDetail(packageName, DETAIL_ACTIVITY)

            // Block 1 — header: icon + ONE short uppercase metric word. It has to
            // fit the chord of the 192dp circle at ~67dp above centre (~119dp), so
            // the timestamp moved into the frosted card instead of riding here.
            val headerIcon = LayoutElementBuilders.Image.Builder()
                .setResourceId(RES_IC_HR)
                .setWidth(dp(14f))
                .setHeight(dp(14f))
                .setColorFilter(
                    LayoutElementBuilders.ColorFilter.Builder()
                        .setTint(argb(accent))
                        .build()
                )
                .build()

            val headerLabel = LayoutElementBuilders.Text.Builder()
                .setText(strings.text(R.string.vitals_short).uppercase(Locale.getDefault()))
                .setMaxLines(1)
                .setFontStyle(TileDesign.Type.label(accent))
                .build()

            val headerRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(headerIcon)
                .addContent(TileChrome.hSpacer(4f))
                .addContent(headerLabel)
                .build()

            // Hero: 40sp tabular number + unit, alone in its row.
            val heroNumber = LayoutElementBuilders.Text.Builder()
                .setText(hrValue)
                .setMaxLines(1)
                .setFontStyle(TileDesign.Type.hero())
                .build()

            val heroUnit = LayoutElementBuilders.Text.Builder()
                .setText(strings.text(R.string.bpm_unit).lowercase(Locale.getDefault()))
                .setMaxLines(1)
                .setFontStyle(TileDesign.Type.unit(accent))
                .build()

            val heroRow = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(heroNumber)
                .addContent(TileChrome.hSpacer(3f))
                .addContent(heroUnit)
                .build()

            // Block 3 — meter on frosted glass: the SpO2 bar on the first line, the
            // small readings (SpO2 and ring charge) under it, and the sync timestamp
            // as the quietest caption. The bar plus its readings is one block.
            val meterCaptions = LayoutElementBuilders.Row.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText("${strings.text(R.string.blood_oxygen_short)} $spo2Value")
                        .setMaxLines(1)
                        .setFontStyle(TileDesign.Type.caption(TileDesign.Accent.spo2))
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(" · ")
                        .setMaxLines(1)
                        .setFontStyle(TileDesign.Type.caption(TileDesign.Text.MUTED))
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText("${strings.text(R.string.ring_short)} ${batteryValue}")
                        .setMaxLines(1)
                        .setFontStyle(TileDesign.Type.caption(TileDesign.Accent.vitals))
                        .build()
                )
                .build()

            val cardTimestamp = LayoutElementBuilders.Text.Builder()
                .setText(timeText)
                .setMaxLines(1)
                .setFontStyle(TileDesign.Type.caption(TileDesign.Text.MUTED))
                .build()

            val cardContent = LayoutElementBuilders.Column.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(
                    TileMeter.progress(
                        fraction = if (hasSpo2) vitals.lastSpo2 / 100f else 0f,
                        accent = TileDesign.Accent.spo2
                    )
                )
                .addContent(TileChrome.spacer(3f))
                .addContent(meterCaptions)
                .addContent(TileChrome.spacer(2f))
                .addContent(cardTimestamp)
                .build()

            val meterCard = TileChrome.card(cardContent)

            val actionPill = TileChrome.actionPill(
                LayoutElementBuilders.Text.Builder()
                    .setText(strings.text(R.string.measure_now))
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
                accent = accent,
                targetSweep = TileGauge.sweepFor(fraction)
            )

            val rootBox = LayoutElementBuilders.Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(TileChrome.rootModifiers(TileMetric.VITALS, clickable))
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
