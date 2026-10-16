package com.ringconn.wear.bridge.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.ringconn.wear.bridge.data.RingConnVitals
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private data class RawStageInterval(
    val start: Instant,
    val end: Instant,
    val stageType: Int,
    val lastModified: Instant = Instant.EPOCH
)

object RingConnTelemetryReader {
    private const val TAG = "RingConnReader"
    const val PREFS_NAME = "HomeWidgetPreferences"
    const val RINGCONN_PKG = "com.gdjztech.ringconn"

    // Preference keys
    const val KEY_LASTLY_HR = "keyLastlyHr"
    const val KEY_AVG_HR = "keyAvgHr"
    const val KEY_LASTLY_SPO = "keyLastlySpo"
    const val KEY_AVG_SPO = "keyLAvgSpo"
    const val KEY_LASTLY_HRV = "keyLastlyHrv"
    const val KEY_AVG_HRV = "keyAvgHrv"
    const val KEY_SLEEP_SCORE = "sleepScore"
    const val KEY_ACTIVITY_SCORE = "activityScore"
    const val KEY_ACTIVE_MINUTES = "activeMinutes"
    const val KEY_STRESS_SCORE = "stressScore"
    const val KEY_LIVE_STEPS = "liveSteps"
    const val KEY_TARGET_STEPS = "targetSteps"
    const val KEY_LIVE_CALORIE = "liveCalorie"
    const val KEY_TARGET_CALORIE = "targetCalorie"
    const val KEY_ELECTRIC = "electric"
    const val KEY_IS_CHARGE = "isCharge"
    const val KEY_IS_CONNECTED = "isConnected"
    const val KEY_REFRESH_TIMESTAMP = "keyRefreshTimestamp"
    const val KEY_LAST_SYNC_SOURCE = "keyLastSyncSource"
    const val KEY_LAST_SYNC_TIME_STR = "keyLastSyncTimeStr"
    const val KEY_MIN_HR = "keyMinHr"
    const val KEY_MAX_HR = "keyMaxHr"
    const val KEY_ZONE1_MINS = "keyZone1Mins"
    const val KEY_ZONE2_MINS = "keyZone2Mins"
    const val KEY_ZONE3_MINS = "keyZone3Mins"
    const val KEY_ZONE4_MINS = "keyZone4Mins"
    const val KEY_ZONE5_MINS = "keyZone5Mins"
    const val KEY_SLEEP_DEEP_MINS = "keySleepDeepMins"
    const val KEY_SLEEP_REM_MINS = "keySleepRemMins"
    const val KEY_SLEEP_LIGHT_MINS = "keySleepLightMins"
    const val KEY_SLEEP_AWAKE_MINS = "keySleepAwakeMins"
    const val KEY_SKIN_TEMP_OFFSET = "keySkinTempOffset"
    const val KEY_SKIN_TEMP_BASELINE = "keySkinTempBaseline"

    val CORE_HEALTH_PERMISSIONS = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
    )

    fun isHealthConnectAvailable(context: Context): Boolean {
        return try {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    suspend fun hasRequiredPermissions(context: Context): Boolean {
        return try {
            if (!isHealthConnectAvailable(context)) return false
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            granted.containsAll(CORE_HEALTH_PERMISSIONS)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Health Connect permissions", e)
            false
        }
    }

    suspend fun readFromHealthConnect(context: Context): RingConnVitals? {
        if (!isHealthConnectAvailable(context)) {
            Log.w(TAG, "Health Connect SDK not available")
            return null
        }

        val client = try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to get HealthConnectClient", e)
            return null
        }

        val granted = try {
            client.permissionController.getGrantedPermissions()
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading granted permissions", e)
            emptySet()
        }

        if (granted.isEmpty()) {
            Log.w(TAG, "No Health Connect permissions granted")
            return null
        }

        val now = Instant.now()
        val queryStart = now.minus(Duration.ofHours(48))
        val todayStart = ZonedDateTime.now(ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()

        // Existing saved values for fallback continuity
        val existing = parseFromPreferences(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

        var hasRingConnOrigin = false
        var lastBpm = 0
        var avgBpm = 0
        var restingBpm = 0
        var minBpm = 0
        var maxBpm = 0
        var z1 = 0
        var z2 = 0
        var z3 = 0
        var z4 = 0
        var z5 = 0

        var latestHrTimestamp = 0L
        var latestSpo2Timestamp = 0L
        var latestHrvTimestamp = 0L

        // 1. Heart Rate
        if (granted.contains(HealthPermission.getReadPermission(HeartRateRecord::class))) {
            try {
                val hrResponse = client.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(queryStart, now)
                    )
                )

                // Strictly prioritize RingConn smart ring origin over Samsung Health / Watch PPG
                val ringConnHr = hrResponse.records.filter {
                    it.metadata.dataOrigin.packageName == RINGCONN_PKG
                }
                val effectiveHrRecords = ringConnHr.ifEmpty { hrResponse.records }
                if (ringConnHr.isNotEmpty()) {
                    hasRingConnOrigin = true
                }

                val allSamples = effectiveHrRecords.flatMap { record ->
                    record.samples.map { sample -> sample to record.metadata.dataOrigin.packageName }
                }

                if (allSamples.isNotEmpty()) {
                    val latestSample = allSamples.maxByOrNull { it.first.time }?.first
                    if (latestSample != null) {
                        lastBpm = latestSample.beatsPerMinute.toInt()
                        latestHrTimestamp = latestSample.time.epochSecond
                    }

                    val todaySamples = allSamples.filter { it.first.time >= todayStart }
                    if (todaySamples.isNotEmpty()) {
                        avgBpm = todaySamples.map { it.first.beatsPerMinute.toInt() }.average().roundToInt()
                        minBpm = todaySamples.minOf { it.first.beatsPerMinute.toInt() }
                        maxBpm = todaySamples.maxOf { it.first.beatsPerMinute.toInt() }
                        val sampleWindowMins = 5 // RingConn PPG samples in Health Connect span ~5 mins each
                        z1 = todaySamples.count { it.first.beatsPerMinute < 100 } * sampleWindowMins
                        z2 = todaySamples.count { it.first.beatsPerMinute in 100..120 } * sampleWindowMins
                        z3 = todaySamples.count { it.first.beatsPerMinute in 121..140 } * sampleWindowMins
                        z4 = todaySamples.count { it.first.beatsPerMinute in 141..160 } * sampleWindowMins
                        z5 = todaySamples.count { it.first.beatsPerMinute > 160 } * sampleWindowMins
                    } else {
                        avgBpm = lastBpm
                        minBpm = allSamples.minOf { it.first.beatsPerMinute.toInt() }
                        maxBpm = allSamples.maxOf { it.first.beatsPerMinute.toInt() }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed querying HeartRateRecord: ${e.message}")
            }
        }

        // Resting Heart Rate (Prioritize RingConn origin)
        if (granted.contains(HealthPermission.getReadPermission(RestingHeartRateRecord::class))) {
            try {
                val rhrResponse = client.readRecords(
                    ReadRecordsRequest(
                        recordType = RestingHeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(queryStart, now)
                    )
                )
                val ringConnRhr = rhrResponse.records.filter {
                    it.metadata.dataOrigin.packageName == RINGCONN_PKG
                }
                val effectiveRhr = ringConnRhr.ifEmpty { rhrResponse.records }
                val latestRhr = effectiveRhr.maxByOrNull { it.time }
                if (latestRhr != null) {
                    restingBpm = latestRhr.beatsPerMinute.toInt()
                    if (avgBpm == 0) avgBpm = restingBpm
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed querying RestingHeartRateRecord: ${e.message}")
            }
        }

        // 2. Oxygen Saturation (SpO2 - Prioritize RingConn origin)
        var lastSpo2 = 0
        var avgSpo2 = 0
        if (granted.contains(HealthPermission.getReadPermission(OxygenSaturationRecord::class))) {
            try {
                val spo2Response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = OxygenSaturationRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(queryStart, now)
                    )
                )
                val ringConnSpo2 = spo2Response.records.filter {
                    it.metadata.dataOrigin.packageName == RINGCONN_PKG
                }
                val effectiveSpo2 = ringConnSpo2.ifEmpty { spo2Response.records }
                val latestSpo2Record = effectiveSpo2.maxByOrNull { it.time }
                if (latestSpo2Record != null) {
                    val rawVal = latestSpo2Record.percentage.value
                    lastSpo2 = if (rawVal <= 1.0) (rawVal * 100.0).roundToInt() else rawVal.roundToInt()
                    latestSpo2Timestamp = latestSpo2Record.time.epochSecond
                }

                val todaySpo2 = effectiveSpo2.filter { it.time >= todayStart }
                if (todaySpo2.isNotEmpty()) {
                    avgSpo2 = todaySpo2.map {
                        val v = it.percentage.value
                        if (v <= 1.0) v * 100.0 else v
                    }.average().roundToInt()
                } else {
                    avgSpo2 = lastSpo2
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed querying OxygenSaturationRecord: ${e.message}")
            }
        }

        // 3. Heart Rate Variability (HRV RMSSD - Prioritize RingConn origin)
        var lastHrv = 0
        var avgHrv = 0
        if (granted.contains(HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class))) {
            try {
                val hrvResponse = client.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateVariabilityRmssdRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(queryStart, now)
                    )
                )
                val ringConnHrv = hrvResponse.records.filter {
                    it.metadata.dataOrigin.packageName == RINGCONN_PKG
                }
                val effectiveHrv = ringConnHrv.ifEmpty { hrvResponse.records }
                val latestHrvRecord = effectiveHrv.maxByOrNull { it.time }
                if (latestHrvRecord != null) {
                    lastHrv = latestHrvRecord.heartRateVariabilityMillis.roundToInt()
                    latestHrvTimestamp = latestHrvRecord.time.epochSecond
                }

                val todayHrv = effectiveHrv.filter { it.time >= todayStart }
                if (todayHrv.isNotEmpty()) {
                    avgHrv = todayHrv.map { it.heartRateVariabilityMillis }.average().roundToInt()
                } else {
                    avgHrv = lastHrv
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed querying HRV: ${e.message}")
            }
        }

        // 4. Steps Today (Prioritize RingConn data origin)
        var todaySteps = 0
        var activeMinutes = 0
        if (granted.contains(HealthPermission.getReadPermission(StepsRecord::class))) {
            try {
                val stepsResponse = client.readRecords(
                    ReadRecordsRequest(
                        recordType = StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(todayStart, now)
                    )
                )
                val ringConnSteps = stepsResponse.records.filter {
                    it.metadata.dataOrigin.packageName == RINGCONN_PKG
                }
                val effectiveSteps = ringConnSteps.ifEmpty { stepsResponse.records }
                if (ringConnSteps.isNotEmpty()) {
                    hasRingConnOrigin = true
                }
                if (effectiveSteps.isNotEmpty()) {
                    todaySteps = effectiveSteps.sumOf { it.count }.toInt()
                    activeMinutes = effectiveSteps.sumOf {
                        val m = Duration.between(it.startTime, it.endTime).toMinutes()
                        if (m > 0) m else 1L
                    }.toInt()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed querying StepsRecord: ${e.message}")
            }
        }

        // 5. Total Calories Burned Today
        var todayCalories = 0
        if (granted.contains(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))) {
            try {
                val calResponse = client.readRecords(
                    ReadRecordsRequest(
                        recordType = TotalCaloriesBurnedRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(todayStart, now)
                    )
                )
                val ringConnCal = calResponse.records.filter {
                    it.metadata.dataOrigin.packageName == RINGCONN_PKG
                }
                todayCalories = if (ringConnCal.isNotEmpty()) {
                    hasRingConnOrigin = true
                    ringConnCal.sumOf { it.energy.inKilocalories }.roundToInt()
                } else {
                    calResponse.records.sumOf { it.energy.inKilocalories }.roundToInt()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed querying Calories: ${e.message}")
            }
        }

        // 6. Sleep Session & Stages (RingConn writes multiple SleepSessionRecord segments per night)
        var sleepScore = 0
        var deepMins = 0L
        var remMins = 0L
        var lightMins = 0L
        var awakeMins = 0L
        if (granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class))) {
            try {
                val sleepResponse = client.readRecords(
                    ReadRecordsRequest(
                        recordType = SleepSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(queryStart, now)
                    )
                )
                val ringConnSleeps = sleepResponse.records.filter {
                    it.metadata.dataOrigin.packageName == RINGCONN_PKG
                }.ifEmpty { sleepResponse.records }

                // Only consider sleep sessions concluding today (endTime >= todayStart)
                val todaySleeps = ringConnSleeps.filter { it.endTime >= todayStart }
                val latestEnd = todaySleeps.maxOfOrNull { it.endTime }
                if (latestEnd != null) {
                    val nightWindowStart = latestEnd.minus(Duration.ofHours(14))
                    val nightSessions = todaySleeps.filter { it.endTime >= nightWindowStart }
                    if (nightSessions.any { it.metadata.dataOrigin.packageName == RINGCONN_PKG }) {
                        hasRingConnOrigin = true
                    }

                    Log.i(TAG, "Health Connect: found ${nightSessions.size} nightSessions (todaySleeps=${todaySleeps.size})")
                    for ((idx, s) in nightSessions.withIndex()) {
                        val dur = Duration.between(s.startTime, s.endTime).toMinutes()
                        Log.i(TAG, "Session #$idx id=${s.metadata.id} mod=${s.metadata.lastModifiedTime} cid=${s.metadata.clientRecordId}: ${s.startTime} -> ${s.endTime} ($dur mins), stages=${s.stages.size}")
                        for (st in s.stages) {
                            val stDur = Duration.between(st.startTime, st.endTime).toMinutes()
                            Log.i(TAG, "   Stage type=${st.stage}: ${st.startTime} -> ${st.endTime} ($stDur mins)")
                        }
                    }

                    var totalDurationMins = 0L
                    deepMins = 0L
                    remMins = 0L
                    lightMins = 0L
                    awakeMins = 0L

                    val discreteStages = mutableListOf<RawStageInterval>()
                    var umbrellaMins = 0L
                    var umbrellaStart: Instant? = null
                    var umbrellaEnd: Instant? = null

                    for (session in nightSessions) {
                        val sessionDur = ((Duration.between(session.startTime, session.endTime).seconds + 30L) / 60L).coerceAtLeast(0L)
                        if (session.stages.isEmpty()) {
                            if (sessionDur > umbrellaMins) {
                                umbrellaMins = sessionDur
                                umbrellaStart = session.startTime
                                umbrellaEnd = session.endTime
                            }
                        } else {
                            for (st in session.stages) {
                                when (st.stage) {
                                    SleepSessionRecord.STAGE_TYPE_DEEP,
                                    SleepSessionRecord.STAGE_TYPE_REM,
                                    SleepSessionRecord.STAGE_TYPE_LIGHT,
                                    SleepSessionRecord.STAGE_TYPE_AWAKE,
                                    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> {
                                        discreteStages.add(RawStageInterval(st.startTime, st.endTime, st.stage, session.metadata.lastModifiedTime))
                                    }
                                    SleepSessionRecord.STAGE_TYPE_SLEEPING -> {
                                        val dur = ((Duration.between(st.startTime, st.endTime).seconds + 30L) / 60L).coerceAtLeast(0L)
                                        if (dur > umbrellaMins) {
                                            umbrellaMins = dur
                                            umbrellaStart = st.startTime
                                            umbrellaEnd = st.endTime
                                        }
                                    }
                                    else -> {
                                        // Ignore unknown stage containers
                                    }
                                }
                            }
                        }
                    }

                    if (discreteStages.isNotEmpty()) {
                        // Exclude pre-bed awakening records that concluded before bed session onset
                        val validStages = if (umbrellaStart != null) {
                            discreteStages.filter { it.end > umbrellaStart }
                        } else {
                            discreteStages
                        }.ifEmpty { discreteStages }

                        val tStart = if (umbrellaStart != null) umbrellaStart else validStages.minOf { it.start }
                        val tEnd = if (umbrellaEnd != null) umbrellaEnd else validStages.maxOf { it.end }
                        val totalSeconds = Duration.between(tStart, tEnd).seconds.coerceIn(30L, 86400L)
                        val epochCount = ((totalSeconds + 15L) / 30L).toInt().coerceAtLeast(1)
                        val epochTimeline = IntArray(epochCount) { 0 }

                        // Sort by lastModified ascending so newer sync batches overwrite obsolete preliminary fragments,
                        // then chronologically by start time, and finally longer intervals first.
                        validStages.toMutableList().apply {
                            sortWith(
                                compareBy<RawStageInterval> { it.lastModified }
                                    .thenBy { it.start }
                                    .thenByDescending { Duration.between(it.start, it.end).seconds }
                            )
                        }.forEach { st ->
                            val sEpoch = (Duration.between(tStart, st.start).seconds / 30L).toInt().coerceIn(0, epochCount - 1)
                            val eEpoch = ((Duration.between(tStart, st.end).seconds + 15L) / 30L).toInt().coerceIn(0, epochCount)
                            for (ep in sEpoch until eEpoch) {
                                epochTimeline[ep] = st.stageType
                            }
                        }

                        deepMins = (epochTimeline.count { it == SleepSessionRecord.STAGE_TYPE_DEEP } * 30.0 / 60.0).roundToInt().toLong()
                        remMins = (epochTimeline.count { it == SleepSessionRecord.STAGE_TYPE_REM } * 30.0 / 60.0).roundToInt().toLong()
                        lightMins = (epochTimeline.count { it == SleepSessionRecord.STAGE_TYPE_LIGHT } * 30.0 / 60.0).roundToInt().toLong()

                        val totalAsleep = deepMins + remMins + lightMins

                        val firstAsleepEpoch = epochTimeline.indexOfFirst {
                            it == SleepSessionRecord.STAGE_TYPE_DEEP ||
                            it == SleepSessionRecord.STAGE_TYPE_REM ||
                            it == SleepSessionRecord.STAGE_TYPE_LIGHT
                        }
                        val lastAsleepEpoch = epochTimeline.indexOfLast {
                            it == SleepSessionRecord.STAGE_TYPE_DEEP ||
                            it == SleepSessionRecord.STAGE_TYPE_REM ||
                            it == SleepSessionRecord.STAGE_TYPE_LIGHT
                        }

                        // WASO: awakenings between sleep onset and final morning awakening
                        val wasoEpochs = if (firstAsleepEpoch in 0..lastAsleepEpoch) {
                            (firstAsleepEpoch..lastAsleepEpoch).count { ep ->
                                epochTimeline[ep] == SleepSessionRecord.STAGE_TYPE_AWAKE ||
                                epochTimeline[ep] == SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
                            }
                        } else 0
                        val wasoMins = (wasoEpochs * 30.0 / 60.0).roundToInt().toLong()

                        val latencyMins = if (firstAsleepEpoch > 0) {
                            (firstAsleepEpoch * 30.0 / 60.0).roundToInt().toLong()
                        } else 0L

                        val inBedAwakeMins = if (umbrellaMins > totalAsleep) {
                            umbrellaMins - totalAsleep + 1L
                        } else 0L

                        awakeMins = if (inBedAwakeMins in 1L..90L) {
                            inBedAwakeMins
                        } else {
                            (wasoMins + latencyMins + 1L).coerceIn(1L, 90L)
                        }
                    }

                    totalDurationMins = if (deepMins + remMins + lightMins > 0) {
                        deepMins + remMins + lightMins
                    } else {
                        umbrellaMins
                    }

                    Log.i(TAG, "Health Connect sleep metrics: totalDur=$totalDurationMins m, deep=$deepMins m, rem=$remMins m, light=$lightMins m, awake=$awakeMins m")

                    if (totalDurationMins > 0) {
                        val durScore = ((totalDurationMins.toDouble() / 450.0).coerceAtMost(1.0) * 55.0)
                        val deepScore = if (deepMins > 0) ((deepMins.toDouble() / 75.0).coerceAtMost(1.0) * 23.0) else 18.0
                        val remScore = if (remMins > 0) ((remMins.toDouble() / 85.0).coerceAtMost(1.0) * 22.0) else 17.0
                        val awakePenalty = (awakeMins.toDouble() / 12.0).coerceAtMost(12.0)
                        sleepScore = (durScore + deepScore + remScore - awakePenalty).roundToInt().coerceIn(45, 98)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed querying SleepSessionRecord: ${e.message}")
            }
        }

        // 7. Skin Temperature from Health Connect (if available and granted)
        var hcSkinTempOffset: Float? = null
        var hcSkinTempBaseline: Float? = null
        try {
            val skinTempClass = Class.forName("androidx.health.connect.client.records.SkinTemperatureRecord").asSubclass(androidx.health.connect.client.records.Record::class.java)
            val perm = HealthPermission.getReadPermission(skinTempClass.kotlin)
            if (granted.contains(perm)) {
                val tempResponse = client.readRecords(
                    ReadRecordsRequest(
                        recordType = skinTempClass.kotlin,
                        timeRangeFilter = TimeRangeFilter.between(queryStart, now)
                    )
                )
                val ringTemps = tempResponse.records.filter {
                    it.metadata.dataOrigin.packageName == RINGCONN_PKG
                }.ifEmpty { tempResponse.records }
                val latest = ringTemps.maxByOrNull {
                    try {
                        val m = it.javaClass.getMethod("getStartTime")
                        m.invoke(it) as Instant
                    } catch (_: Exception) { Instant.EPOCH }
                }
                if (latest != null) {
                    try {
                        val getBaseline = latest.javaClass.getMethod("getBaseline")
                        val baselineObj = getBaseline.invoke(latest)
                        if (baselineObj != null) {
                            val inCelsius = baselineObj.javaClass.getMethod("getInCelsius").invoke(baselineObj) as Double
                            if (inCelsius in 34.0..40.0) hcSkinTempBaseline = inCelsius.toFloat()
                        }
                        val getDeltas = latest.javaClass.getMethod("getDeltas")
                        val deltas = getDeltas.invoke(latest) as? List<*>
                        val latestDelta = deltas?.lastOrNull()
                        if (latestDelta != null) {
                            val getDelta = latestDelta.javaClass.getMethod("getDelta")
                            val deltaObj = getDelta.invoke(latestDelta)
                            if (deltaObj != null) {
                                val inCelsius = deltaObj.javaClass.getMethod("getInCelsius").invoke(deltaObj) as Double
                                if (inCelsius in -5.0..5.0) hcSkinTempOffset = inCelsius.toFloat()
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed extracting SkinTemperatureRecord details: ${e.message}")
                    }
                }
            }
        } catch (_: ClassNotFoundException) {
            // SkinTemperatureRecord class not available on this API / library level
        } catch (e: Exception) {
            Log.w(TAG, "Health Connect skin temp error: ${e.message}")
        }

        // Check if we retrieved any real telemetry from Health Connect
        val hasRealTelemetry = lastBpm > 0 || todaySteps > 0 || lastSpo2 > 0 || lastHrv > 0 || sleepScore > 0 || hcSkinTempOffset != null
        if (!hasRealTelemetry) {
            Log.d(TAG, "Health Connect permissions granted but 0 records found in range")
            return null
        }

        // Fill remaining missing metrics from continuity cache
        val finalHr = if (lastBpm > 0) lastBpm else existing.lastHeartRate
        val finalAvgHr = if (avgBpm > 0) avgBpm else (if (restingBpm > 0) restingBpm else finalHr)
        val finalSpo2 = if (lastSpo2 > 0) lastSpo2 else existing.lastSpo2
        val finalAvgSpo2 = if (avgSpo2 > 0) avgSpo2 else finalSpo2
        val finalHrv = if (lastHrv > 0) lastHrv else existing.lastHrv
        val finalAvgHrv = if (avgHrv > 0) avgHrv else finalHrv
        val finalSleep = if (sleepScore > 0) sleepScore else existing.sleepScore
        val finalSteps = if (todaySteps > 0) todaySteps else existing.liveSteps
        val stepBasedCalories = (finalSteps * 0.045).roundToInt()
        val finalCalories = maxOf(todayCalories, stepBasedCalories)

        // Dynamically compute Activity Score
        val actScore = if (finalSteps > 0 || finalCalories > 0) {
            ((finalSteps.toDouble() / 8000.0 * 55.0) + (finalCalories.toDouble() / 350.0 * 45.0)).roundToInt().coerceIn(0, 100)
        } else {
            existing.activityScore
        }

        // Preserve exact RingConn stress score if already present, otherwise estimate from HRV
        val stressScore = if (existing.stressScore > 0) {
            existing.stressScore
        } else if (finalHrv > 0) {
            (100 - (finalHrv * 1.05).roundToInt()).coerceIn(1, 99)
        } else {
            0
        }

        val ringBattery = existing.ringBattery

        val sourceLabel = if (hasRingConnOrigin) {
            "Health Connect (com.gdjztech.ringconn)"
        } else {
            "Health Connect (Sincronizado)"
        }

        val finalMinHr = if (minBpm > 0) minBpm else existing.minHeartRate
        val finalMaxHr = if (maxBpm > 0) maxBpm else existing.maxHeartRate
        val hasNewZones = (z1 + z2 + z3 + z4 + z5) > 0
        val finalZ1 = if (hasNewZones) z1 else existing.zone1Mins
        val finalZ2 = if (hasNewZones) z2 else existing.zone2Mins
        val finalZ3 = if (hasNewZones) z3 else existing.zone3Mins
        val finalZ4 = if (hasNewZones) z4 else existing.zone4Mins
        val finalZ5 = if (hasNewZones) z5 else existing.zone5Mins

        // A valid sleep breakdown requires effective sleep (deep, rem, or light).
        // Never allow awake-only intervals to overwrite and zero out real nocturnal sleep stages.
        val hasValidStages = (deepMins + remMins + lightMins) > 0
        val finalDeep = if (hasValidStages) deepMins.toInt() else existing.sleepDeepMins
        val finalRem = if (hasValidStages) remMins.toInt() else existing.sleepRemMins
        val finalLight = if (hasValidStages) lightMins.toInt() else existing.sleepLightMins
        val finalAwake = if (hasValidStages) awakeMins.toInt() else existing.sleepAwakeMins

        val vitals = RingConnVitals(
            lastHeartRate = finalHr,
            avgHeartRate = finalAvgHr,
            lastSpo2 = finalSpo2,
            avgSpo2 = finalAvgSpo2,
            lastHrv = finalHrv,
            avgHrv = finalAvgHrv,
            sleepScore = finalSleep,
            activityScore = actScore,
            activeMinutes = if (activeMinutes > 0) activeMinutes else existing.activeMinutes,
            stressScore = stressScore,
            liveSteps = finalSteps,
            targetSteps = 10000,
            liveCalorie = finalCalories,
            targetCalorie = 500,
            ringBattery = ringBattery,
            isCharging = existing.isCharging,
            isConnected = true,
            refreshTimestamp = maxOf(latestHrTimestamp, latestSpo2Timestamp, latestHrvTimestamp).takeIf { it > 0 } ?: now.epochSecond,
            minHeartRate = finalMinHr,
            maxHeartRate = finalMaxHr,
            zone1Mins = finalZ1,
            zone2Mins = finalZ2,
            zone3Mins = finalZ3,
            zone4Mins = finalZ4,
            zone5Mins = finalZ5,
            sleepDeepMins = finalDeep,
            sleepRemMins = finalRem,
            sleepLightMins = finalLight,
            sleepAwakeMins = finalAwake,
            skinTempOffset = hcSkinTempOffset ?: existing.skinTempOffset,
            skinTempBaseline = hcSkinTempBaseline ?: existing.skinTempBaseline
        )

        saveToPreferences(context, vitals, source = sourceLabel)
        Log.i(TAG, "[$sourceLabel] Fresh vitals loaded: HR=$finalHr, Min=$finalMinHr, Max=$finalMaxHr, SpO2=$finalSpo2, Steps=$finalSteps, Sleep=$finalSleep, HRV=$finalHrv")
        return vitals
    }

    suspend fun readBestAvailableVitalsSuspend(context: Context): RingConnVitals {
        // 1. Direct AppWidgetHost extraction (zero credentials, 100% exact from Glance widgets)
        val widgetVitals = try {
            RingConnWidgetHostReader.readRingConnWidgets(context)
        } catch (e: Exception) {
            Log.d(TAG, "WidgetHost read attempt: ${e.message}")
            null
        }
        if (widgetVitals != null && (widgetVitals.lastHeartRate > 0 || widgetVitals.sleepScore > 0 || widgetVitals.liveSteps > 0)) {
            val cached = parseFromPreferences(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
            val hcVitals = try {
                readFromHealthConnect(context)
            } catch (e: Exception) {
                null
            }

            // Smart Timestamp Merge: Compare widget sample time vs Health Connect sample time
            val hcHrIsFresher = hcVitals != null && hcVitals.lastHeartRate > 0 && hcVitals.refreshTimestamp > widgetVitals.refreshTimestamp
            if (hcHrIsFresher) {
                Log.i(TAG, "Smart Merge: Health Connect HR (${hcVitals!!.lastHeartRate} bpm @ ${hcVitals.refreshTimestamp}) is FRESHER than widget (${widgetVitals.lastHeartRate} bpm @ ${widgetVitals.refreshTimestamp}) -> prioritizing Health Connect HR")
            } else {
                Log.i(TAG, "Smart Merge: Widget vitals (${widgetVitals.lastHeartRate} bpm @ ${widgetVitals.refreshTimestamp}) are >= Health Connect (${hcVitals?.refreshTimestamp ?: 0}) -> prioritizing Widget vitals")
            }

            val finalHr = if (hcHrIsFresher) {
                hcVitals!!.lastHeartRate
            } else if (widgetVitals.lastHeartRate > 0) {
                widgetVitals.lastHeartRate
            } else {
                hcVitals?.lastHeartRate?.takeIf { it > 0 } ?: cached.lastHeartRate
            }

            val finalAvgHr = if (hcHrIsFresher && hcVitals!!.avgHeartRate > 0) {
                hcVitals.avgHeartRate
            } else if (widgetVitals.avgHeartRate > 0) {
                widgetVitals.avgHeartRate
            } else {
                hcVitals?.avgHeartRate?.takeIf { it > 0 } ?: cached.avgHeartRate
            }

            val finalSpo2 = if (hcHrIsFresher && hcVitals!!.lastSpo2 > 0) {
                hcVitals.lastSpo2
            } else if (widgetVitals.lastSpo2 > 0) {
                widgetVitals.lastSpo2
            } else {
                hcVitals?.lastSpo2?.takeIf { it > 0 } ?: cached.lastSpo2
            }

            val finalAvgSpo2 = if (hcHrIsFresher && hcVitals!!.avgSpo2 > 0) {
                hcVitals.avgSpo2
            } else if (widgetVitals.avgSpo2 > 0) {
                widgetVitals.avgSpo2
            } else {
                hcVitals?.avgSpo2?.takeIf { it > 0 } ?: cached.avgSpo2
            }

            val finalHrv = if (hcHrIsFresher && hcVitals!!.lastHrv > 0) {
                hcVitals.lastHrv
            } else if (widgetVitals.lastHrv > 0) {
                widgetVitals.lastHrv
            } else {
                hcVitals?.lastHrv?.takeIf { it > 0 } ?: cached.lastHrv
            }

            val finalAvgHrv = if (hcHrIsFresher && hcVitals!!.avgHrv > 0) {
                hcVitals.avgHrv
            } else if (widgetVitals.avgHrv > 0) {
                widgetVitals.avgHrv
            } else {
                hcVitals?.avgHrv?.takeIf { it > 0 } ?: cached.avgHrv
            }

            val minHr = hcVitals?.minHeartRate?.takeIf { it > 0 } ?: cached.minHeartRate
            val maxHr = hcVitals?.maxHeartRate?.takeIf { it > 0 } ?: cached.maxHeartRate
            val z1 = hcVitals?.zone1Mins?.takeIf { it > 0 } ?: cached.zone1Mins
            val z2 = hcVitals?.zone2Mins?.takeIf { it > 0 } ?: cached.zone2Mins
            val z3 = hcVitals?.zone3Mins?.takeIf { it > 0 } ?: cached.zone3Mins
            val z4 = hcVitals?.zone4Mins?.takeIf { it > 0 } ?: cached.zone4Mins
            val z5 = hcVitals?.zone5Mins?.takeIf { it > 0 } ?: cached.zone5Mins

            val finalSteps = maxOf(widgetVitals.liveSteps, hcVitals?.liveSteps ?: 0, cached.liveSteps)
            val finalCalorie = maxOf(widgetVitals.liveCalorie, hcVitals?.liveCalorie ?: 0, cached.liveCalorie)
            val finalBattery = if (widgetVitals.ringBattery > 0) widgetVitals.ringBattery else cached.ringBattery
            val finalSleepScore = if (widgetVitals.sleepScore > 0) widgetVitals.sleepScore else (hcVitals?.sleepScore?.takeIf { it > 0 } ?: cached.sleepScore)
            val finalStressScore = if (widgetVitals.stressScore > 0) widgetVitals.stressScore else (hcVitals?.stressScore?.takeIf { it > 0 } ?: cached.stressScore)

            val merged = widgetVitals.copy(
                lastHeartRate = finalHr,
                avgHeartRate = finalAvgHr,
                lastSpo2 = finalSpo2,
                avgSpo2 = finalAvgSpo2,
                lastHrv = finalHrv,
                avgHrv = finalAvgHrv,
                liveSteps = finalSteps,
                liveCalorie = finalCalorie,
                ringBattery = finalBattery,
                sleepScore = finalSleepScore,
                stressScore = finalStressScore,
                refreshTimestamp = maxOf(widgetVitals.refreshTimestamp, hcVitals?.refreshTimestamp ?: 0L),
                minHeartRate = minHr,
                maxHeartRate = maxHr,
                zone1Mins = z1,
                zone2Mins = z2,
                zone3Mins = z3,
                zone4Mins = z4,
                zone5Mins = z5,
                sleepDeepMins = hcVitals?.sleepDeepMins?.takeIf { it > 0 } ?: (if (widgetVitals.sleepDeepMins > 0) widgetVitals.sleepDeepMins else cached.sleepDeepMins),
                sleepRemMins = hcVitals?.sleepRemMins?.takeIf { it > 0 } ?: (if (widgetVitals.sleepRemMins > 0) widgetVitals.sleepRemMins else cached.sleepRemMins),
                sleepLightMins = hcVitals?.sleepLightMins?.takeIf { it > 0 } ?: (if (widgetVitals.sleepLightMins > 0) widgetVitals.sleepLightMins else cached.sleepLightMins),
                sleepAwakeMins = if (hcVitals != null && (hcVitals.sleepDeepMins + hcVitals.sleepRemMins + hcVitals.sleepLightMins) > 0) hcVitals.sleepAwakeMins else (if (widgetVitals.sleepAwakeMins > 0) widgetVitals.sleepAwakeMins else cached.sleepAwakeMins),
                skinTempOffset = if (widgetVitals.skinTempOffset != 0.0f) widgetVitals.skinTempOffset else (hcVitals?.skinTempOffset?.takeIf { it != 0.0f } ?: cached.skinTempOffset),
                skinTempBaseline = if (widgetVitals.skinTempBaseline > 0f) widgetVitals.skinTempBaseline else (hcVitals?.skinTempBaseline?.takeIf { it > 0f } ?: cached.skinTempBaseline)
            )
            val sourceLabel = if (hcHrIsFresher) "Health Connect (Fresher)" else "Glance Widgets + HC"
            saveToPreferences(context, merged, source = sourceLabel)
            return merged
        }

        // 2. Health Connect API
        val hcVitals = try {
            readFromHealthConnect(context)
        } catch (e: Exception) {
            Log.e(TAG, "Health Connect read exception: ${e.message}")
            null
        }

        if (hcVitals != null && (hcVitals.lastHeartRate > 0 || hcVitals.liveSteps > 0 || hcVitals.lastSpo2 > 0)) {
            val cached = parseFromPreferences(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
            val finalHcVitals = if ((hcVitals.sleepDeepMins + hcVitals.sleepRemMins + hcVitals.sleepLightMins) == 0 && (cached.sleepDeepMins + cached.sleepRemMins + cached.sleepLightMins) > 0) {
                hcVitals.copy(
                    sleepDeepMins = cached.sleepDeepMins,
                    sleepRemMins = cached.sleepRemMins,
                    sleepLightMins = cached.sleepLightMins,
                    sleepAwakeMins = cached.sleepAwakeMins
                )
            } else {
                hcVitals
            }
            return finalHcVitals
        }

        // 3. Local cache or user calibration
        val localPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (localPrefs.contains(KEY_LASTLY_HR)) {
            return parseFromPreferences(localPrefs)
        }

        // Initial default if cache is completely fresh/empty
        return RingConnVitals(
            lastHeartRate = 0,
            avgHeartRate = 0,
            lastSpo2 = 0,
            avgSpo2 = 0,
            lastHrv = 0,
            avgHrv = 0,
            sleepScore = 0,
            activityScore = 0,
            stressScore = 0,
            liveSteps = 0,
            targetSteps = 10000,
            liveCalorie = 0,
            targetCalorie = 500,
            ringBattery = 0,
            isCharging = false,
            isConnected = false,
            refreshTimestamp = System.currentTimeMillis() / 1000L
        )
    }

    fun readBestAvailableVitals(context: Context): RingConnVitals {
        val localPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (localPrefs.contains(KEY_LASTLY_HR)) {
            return parseFromPreferences(localPrefs)
        }
        return RingConnVitals(
            lastHeartRate = 0,
            avgHeartRate = 0,
            lastSpo2 = 0,
            avgSpo2 = 0,
            lastHrv = 0,
            avgHrv = 0,
            sleepScore = 0,
            activityScore = 0,
            stressScore = 0,
            liveSteps = 0,
            targetSteps = 10000,
            liveCalorie = 0,
            targetCalorie = 500,
            ringBattery = 0,
            isCharging = false,
            isConnected = false,
            refreshTimestamp = System.currentTimeMillis() / 1000L
        )
    }

    fun saveToPreferences(context: Context, vitals: RingConnVitals, source: String = "Manual") {
        val timeStr = ZonedDateTime.now(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putInt(KEY_LASTLY_HR, vitals.lastHeartRate)
            putInt(KEY_AVG_HR, vitals.avgHeartRate)
            putInt(KEY_LASTLY_SPO, vitals.lastSpo2)
            putInt(KEY_AVG_SPO, vitals.avgSpo2)
            putInt(KEY_LASTLY_HRV, vitals.lastHrv)
            putInt(KEY_AVG_HRV, vitals.avgHrv)
            putInt(KEY_SLEEP_SCORE, vitals.sleepScore)
            putInt(KEY_ACTIVITY_SCORE, vitals.activityScore)
            putInt(KEY_ACTIVE_MINUTES, vitals.activeMinutes)
            putInt(KEY_STRESS_SCORE, vitals.stressScore)
            putInt(KEY_LIVE_STEPS, vitals.liveSteps)
            putInt(KEY_TARGET_STEPS, vitals.targetSteps)
            putInt(KEY_LIVE_CALORIE, vitals.liveCalorie)
            putInt(KEY_TARGET_CALORIE, vitals.targetCalorie)
            putInt(KEY_ELECTRIC, vitals.ringBattery)
            putBoolean(KEY_IS_CHARGE, vitals.isCharging)
            putBoolean(KEY_IS_CONNECTED, vitals.isConnected)
            putInt(KEY_REFRESH_TIMESTAMP, vitals.refreshTimestamp.toInt())
            putInt(KEY_MIN_HR, vitals.minHeartRate)
            putInt(KEY_MAX_HR, vitals.maxHeartRate)
            putInt(KEY_ZONE1_MINS, vitals.zone1Mins)
            putInt(KEY_ZONE2_MINS, vitals.zone2Mins)
            putInt(KEY_ZONE3_MINS, vitals.zone3Mins)
            putInt(KEY_ZONE4_MINS, vitals.zone4Mins)
            putInt(KEY_ZONE5_MINS, vitals.zone5Mins)
            putInt(KEY_SLEEP_DEEP_MINS, vitals.sleepDeepMins)
            putInt(KEY_SLEEP_REM_MINS, vitals.sleepRemMins)
            putInt(KEY_SLEEP_LIGHT_MINS, vitals.sleepLightMins)
            putInt(KEY_SLEEP_AWAKE_MINS, vitals.sleepAwakeMins)
            putFloat(KEY_SKIN_TEMP_OFFSET, vitals.skinTempOffset)
            putFloat(KEY_SKIN_TEMP_BASELINE, vitals.skinTempBaseline)
            putString(KEY_LAST_SYNC_SOURCE, source)
            putString(KEY_LAST_SYNC_TIME_STR, timeStr)
            apply()
        }
    }

    fun parseFromPreferences(prefs: SharedPreferences): RingConnVitals {
        return RingConnVitals(
            lastHeartRate = prefs.getInt(KEY_LASTLY_HR, 0),
            avgHeartRate = prefs.getInt(KEY_AVG_HR, 0),
            lastSpo2 = prefs.getInt(KEY_LASTLY_SPO, 0),
            avgSpo2 = prefs.getInt(KEY_AVG_SPO, 0),
            lastHrv = prefs.getInt(KEY_LASTLY_HRV, 0),
            avgHrv = prefs.getInt(KEY_AVG_HRV, 0),
            sleepScore = prefs.getInt(KEY_SLEEP_SCORE, 0),
            activityScore = prefs.getInt(KEY_ACTIVITY_SCORE, 0),
            activeMinutes = prefs.getInt(KEY_ACTIVE_MINUTES, 0),
            stressScore = prefs.getInt(KEY_STRESS_SCORE, 0),
            liveSteps = prefs.getInt(KEY_LIVE_STEPS, 0),
            targetSteps = prefs.getInt(KEY_TARGET_STEPS, 10000),
            liveCalorie = prefs.getInt(KEY_LIVE_CALORIE, 0),
            targetCalorie = prefs.getInt(KEY_TARGET_CALORIE, 500),
            ringBattery = prefs.getInt(KEY_ELECTRIC, 80),
            isCharging = prefs.getBoolean(KEY_IS_CHARGE, false),
            isConnected = prefs.getBoolean(KEY_IS_CONNECTED, true),
            refreshTimestamp = prefs.getInt(KEY_REFRESH_TIMESTAMP, (System.currentTimeMillis() / 1000L).toInt()).toLong(),
            minHeartRate = prefs.getInt(KEY_MIN_HR, 0),
            maxHeartRate = prefs.getInt(KEY_MAX_HR, 0),
            zone1Mins = prefs.getInt(KEY_ZONE1_MINS, 0),
            zone2Mins = prefs.getInt(KEY_ZONE2_MINS, 0),
            zone3Mins = prefs.getInt(KEY_ZONE3_MINS, 0),
            zone4Mins = prefs.getInt(KEY_ZONE4_MINS, 0),
            zone5Mins = prefs.getInt(KEY_ZONE5_MINS, 0),
            sleepDeepMins = prefs.getInt(KEY_SLEEP_DEEP_MINS, 0),
            sleepRemMins = prefs.getInt(KEY_SLEEP_REM_MINS, 0),
            sleepLightMins = prefs.getInt(KEY_SLEEP_LIGHT_MINS, 0),
            sleepAwakeMins = prefs.getInt(KEY_SLEEP_AWAKE_MINS, 0),
            skinTempOffset = prefs.getFloat(KEY_SKIN_TEMP_OFFSET, 0.0f),
            skinTempBaseline = prefs.getFloat(KEY_SKIN_TEMP_BASELINE, 36.1f)
        )
    }
}
