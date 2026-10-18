package com.ringconn.wear.app.tile

import android.content.Context
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import com.ringconn.wear.app.data.RingConnVitals

@Preview(name = "One UI Watch 8 - Heart Rate & Vitals")
fun vitalsTilePreview(context: Context): TilePreviewData {
    val sampleVitals = RingConnVitals(
        lastHeartRate = 74,
        lastSpo2 = 98,
        ringBattery = 84,
        sleepScore = 86,
        activityScore = 78,
        stressScore = 24,
        liveSteps = 6420,
        targetSteps = 10000
    )
    return TilePreviewData(
        onTileResourceRequest = { VitalsTileService.buildResources() },
        onTileRequest = { requestParams ->
            VitalsTileService.buildTile(context, requestParams.deviceConfiguration, sampleVitals)
        }
    )
}

@Preview(name = "One UI Watch 8 - Daily Activity & Sleep")
fun dailySummaryTilePreview(context: Context): TilePreviewData {
    val sampleVitals = RingConnVitals(
        lastHeartRate = 74,
        lastSpo2 = 98,
        ringBattery = 84,
        sleepScore = 86,
        activityScore = 78,
        stressScore = 24,
        liveSteps = 6420,
        targetSteps = 10000
    )
    return TilePreviewData(
        onTileResourceRequest = { DailySummaryTileService.buildResources() },
        onTileRequest = { requestParams ->
            DailySummaryTileService.buildTile(context, requestParams.deviceConfiguration, sampleVitals)
        }
    )
}

@Preview(name = "One UI Watch 8 - Solitary Heart Rate")
fun heartRateTilePreview(context: Context): TilePreviewData {
    val sampleVitals = RingConnVitals(
        lastHeartRate = 74,
        minHeartRate = 58,
        maxHeartRate = 112,
        avgHeartRate = 68,
        lastSpo2 = 98,
        lastHrv = 49,
        ringBattery = 84,
        refreshTimestamp = System.currentTimeMillis() / 1000L - 120L
    )
    return TilePreviewData(
        onTileResourceRequest = { HeartRateTileService.buildResources() },
        onTileRequest = { requestParams ->
            HeartRateTileService.buildTile(context, requestParams.deviceConfiguration, sampleVitals)
        }
    )
}

@Preview(name = "One UI Watch 8 - Stress")
fun stressTilePreview(context: Context): TilePreviewData {
    val sampleVitals = RingConnVitals(
        stressScore = 26,
        lastHrv = 55
    )
    return TilePreviewData(
        onTileResourceRequest = { StressTileService.buildResources() },
        onTileRequest = { requestParams ->
            StressTileService.buildTile(context, requestParams.deviceConfiguration, sampleVitals)
        }
    )
}

@Preview(name = "One UI Watch 8 - Skin Temperature")
fun temperatureTilePreview(context: Context): TilePreviewData {
    val sampleVitals = RingConnVitals(
        skinTempOffset = 0.2f,
        skinTempBaseline = 36.1f
    )
    return TilePreviewData(
        onTileResourceRequest = { TemperatureTileService.buildResources() },
        onTileRequest = { requestParams ->
            TemperatureTileService.buildTile(context, requestParams.deviceConfiguration, sampleVitals)
        }
    )
}

