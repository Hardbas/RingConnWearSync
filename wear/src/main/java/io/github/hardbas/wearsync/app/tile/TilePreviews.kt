package io.github.hardbas.wearsync.app.tile

import android.content.Context
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import io.github.hardbas.wearsync.app.data.RingConnVitals

@Preview(name = "Circular Bezel - Heart Rate & Vitals", device = WearDevices.LARGE_ROUND)
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

@Preview(name = "Circular Bezel - Daily Activity", device = WearDevices.LARGE_ROUND)
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

@Preview(name = "Circular Bezel - Solitary Heart Rate", device = WearDevices.LARGE_ROUND)
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

@Preview(name = "Circular Bezel - Stress", device = WearDevices.LARGE_ROUND)
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

@Preview(name = "Circular Bezel - Starry Night Sleep", device = WearDevices.LARGE_ROUND)
fun sleepTilePreview(context: Context): TilePreviewData {
    val sampleVitals = RingConnVitals(
        sleepScore = 86,
        sleepDeepMins = 85,
        sleepRemMins = 110,
        sleepLightMins = 230,
        sleepAwakeMins = 25,
        skinTempOffset = 0.2f,
        skinTempBaseline = 36.1f
    )
    return TilePreviewData(
        onTileResourceRequest = { SleepTileService.buildResources() },
        onTileRequest = { requestParams ->
            SleepTileService.buildTile(context, requestParams.deviceConfiguration, sampleVitals)
        }
    )
}
