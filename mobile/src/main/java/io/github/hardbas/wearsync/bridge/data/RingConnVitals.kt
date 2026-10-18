package io.github.hardbas.wearsync.bridge.data

data class RingConnVitals(
    val lastHeartRate: Int = 0,
    val avgHeartRate: Int = 0,
    val lastSpo2: Int = 0,
    val avgSpo2: Int = 0,
    val lastHrv: Int = 0,
    val avgHrv: Int = 0,
    val sleepScore: Int = 0,
    val activityScore: Int = 0,
    val activeMinutes: Int = 0,
    val stressScore: Int = 0,
    val liveSteps: Int = 0,
    val targetSteps: Int = 10000,
    val liveCalorie: Int = 0,
    val targetCalorie: Int = 500,
    val ringBattery: Int = 0,
    val isCharging: Boolean = false,
    val isConnected: Boolean = false,
    val refreshTimestamp: Long = 0L,
    val minHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val zone1Mins: Int = 0,
    val zone2Mins: Int = 0,
    val zone3Mins: Int = 0,
    val zone4Mins: Int = 0,
    val zone5Mins: Int = 0,
    val sleepDeepMins: Int = 0,
    val sleepRemMins: Int = 0,
    val sleepLightMins: Int = 0,
    val sleepAwakeMins: Int = 0,
    val skinTempOffset: Float = 0.0f,
    val skinTempBaseline: Float = 36.1f
) {
    val isStale: Boolean
        get() = (System.currentTimeMillis() / 1000L) - refreshTimestamp > 1800L // > 30 mins
}
