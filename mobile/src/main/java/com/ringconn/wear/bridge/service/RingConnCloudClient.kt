package com.ringconn.wear.bridge.service

import android.content.Context
import android.util.Log
import com.ringconn.wear.bridge.data.RingConnVitals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object RingConnCloudClient {
    private const val TAG = "RingConnCloudClient"
    private const val PREFS_CLOUD = "RingConnCloudPreferences"
    private const val KEY_AUTH_TOKEN = "cloudAuthToken"
    private const val KEY_USER_EMAIL = "cloudUserEmail"
    private const val KEY_LAST_SYNC_TS = "cloudLastSyncTimestamp"

    // Discovered base endpoints & appKey from libapp.so inspection
    const val DEFAULT_BASE_URL = "https://gb.gdjztech.com"
    const val ALT_BASE_URL = "https://api.ringconn.com/v1"
    const val APP_KEY = "043b4c382ce37aa192a4019e763036f4f5dd4d7ebb938cf935318fdced6bc28286531733c3f03c4fee"

    data class AuthResponse(
        val success: Boolean,
        val token: String? = null,
        val uid: String? = null,
        val message: String? = null
    )

    fun getSavedToken(context: Context): String? {
        return context.getSharedPreferences(PREFS_CLOUD, Context.MODE_PRIVATE)
            .getString(KEY_AUTH_TOKEN, null)
    }

    fun saveToken(context: Context, token: String, email: String? = null) {
        context.getSharedPreferences(PREFS_CLOUD, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_AUTH_TOKEN, token)
            email?.let { putString(KEY_USER_EMAIL, it) }
            putLong(KEY_LAST_SYNC_TS, System.currentTimeMillis())
            apply()
        }
    }

    fun clearSavedCredentials(context: Context) {
        context.getSharedPreferences(PREFS_CLOUD, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /**
     * Authenticate with RingConn Cloud (gb.gdjztech.com) using Email & Password
     */
    suspend fun loginWithEmail(email: String, pass: String): AuthResponse = withContext(Dispatchers.IO) {
        val loginEndpoints = listOf(
            "$DEFAULT_BASE_URL/user/login",
            "$DEFAULT_BASE_URL/api/v1/auth/login",
            "$ALT_BASE_URL/user/login"
        )

        for (endpoint in loginEndpoints) {
            try {
                Log.d(TAG, "Attempting login at $endpoint for $email...")
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12000
                    readTimeout = 12000
                    doInput = true
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("User-Agent", "RingConn/Android (SM-S938B; OneUI 8)")
                    setRequestProperty("appKey", APP_KEY)
                }

                val payload = JSONObject().apply {
                    put("email", email)
                    put("password", pass)
                    put("appKey", APP_KEY)
                    put("client", "android")
                    put("system", "android")
                }

                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { os ->
                    os.write(payload.toString())
                    os.flush()
                }

                val code = conn.responseCode
                Log.d(TAG, "Login HTTP status: $code from $endpoint")

                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseStr = stream?.let {
                    BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { br -> br.readText() }
                } ?: ""

                if (code in 200..299 && responseStr.isNotEmpty()) {
                    val json = JSONObject(responseStr)
                    var token: String? = null
                    var uid: String? = null

                    if (json.has("token")) token = json.optString("token")
                    if (json.has("accessToken")) token = json.optString("accessToken")
                    if (json.has("data")) {
                        val dataObj = json.optJSONObject("data")
                        if (dataObj != null) {
                            if (dataObj.has("token")) token = dataObj.optString("token")
                            if (dataObj.has("accessToken")) token = dataObj.optString("accessToken")
                            if (dataObj.has("uid")) uid = dataObj.optString("uid")
                        }
                    }

                    if (!token.isNullOrEmpty()) {
                        Log.i(TAG, "Successfully authenticated with RingConn Cloud. Token: ${token.take(8)}...")
                        return@withContext AuthResponse(success = true, token = token, uid = uid)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Login failed on $endpoint: ${e.message}")
            }
        }
        return@withContext AuthResponse(success = false, message = "Could not authenticate with RingConn Cloud.")
    }

    /**
     * Fetches daily telemetry summary from RingConn Cloud matching DailyModel fields.
     */
    suspend fun fetchDailySummary(
        token: String,
        dateUtc: Long = System.currentTimeMillis() / 1000L
    ): RingConnVitals? = withContext(Dispatchers.IO) {
        val summaryEndpoints = listOf(
            "$DEFAULT_BASE_URL/health/summary?utc=$dateUtc",
            "$DEFAULT_BASE_URL/api/v1/health/summary",
            "$DEFAULT_BASE_URL/data/daily/summary"
        )

        for (endpoint in summaryEndpoints) {
            try {
                Log.d(TAG, "Fetching daily summary from $endpoint...")
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 12000
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("token", token)
                    setRequestProperty("appKey", APP_KEY)
                    setRequestProperty("User-Agent", "RingConn/Android")
                }

                val code = conn.responseCode
                if (code in 200..299) {
                    val responseStr = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
                    val json = JSONObject(responseStr)
                    val dataObj = if (json.has("data")) json.optJSONObject("data") ?: json else json

                    // Extract DailyModel fields
                    val sleepScore = dataObj.optInt("sleepScore", 0)
                    val activityScore = dataObj.optInt("activityScore", 0)
                    val stressWholeDay = dataObj.optInt("stressWholeDay", dataObj.optInt("stressScore", 0))
                    val consume = dataObj.optInt("consume", dataObj.optInt("liveCalorie", dataObj.optInt("sumConsume", 0)))
                    val activityStep = dataObj.optInt("activityStep", dataObj.optInt("liveSteps", 0))
                    val hrAvg = dataObj.optInt("hrAvg", dataObj.optInt("lastlyHr", 0))
                    val hrvAvg = dataObj.optInt("hrvAvg", dataObj.optInt("lastlyHrv", 0))
                    val spo2Avg = dataObj.optInt("spo2Avg", dataObj.optInt("lastlySpo", 0))
                    val electric = dataObj.optInt("electric", dataObj.optInt("battery", 80))

                    if (sleepScore > 0 || activityStep > 0 || hrAvg > 0) {
                        return@withContext RingConnVitals(
                            lastHeartRate = hrAvg,
                            avgHeartRate = hrAvg,
                            lastSpo2 = spo2Avg,
                            avgSpo2 = spo2Avg,
                            lastHrv = hrvAvg,
                            avgHrv = hrvAvg,
                            sleepScore = sleepScore,
                            activityScore = activityScore,
                            stressScore = stressWholeDay,
                            liveSteps = activityStep,
                            liveCalorie = consume,
                            ringBattery = electric,
                            isConnected = true,
                            refreshTimestamp = dateUtc
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fetch summary failed on $endpoint: ${e.message}")
            }
        }
        return@withContext null
    }

    /**
     * Executes full cloud sync and updates local preferences & Wear OS watch.
     */
    suspend fun syncCloudTelemetry(context: Context): Boolean = withContext(Dispatchers.IO) {
        val token = getSavedToken(context)
        if (token.isNullOrEmpty()) {
            Log.w(TAG, "No saved RingConn Cloud token available.")
            return@withContext false
        }

        val vitals = fetchDailySummary(token)
        if (vitals != null) {
            RingConnTelemetryReader.saveToPreferences(context, vitals, source = "RingConn Cloud API")
            WearDataSyncService(context).syncVitalsToWatch(vitals)
            Log.i(TAG, "Cloud sync complete and pushed to Watch: $vitals")
            return@withContext true
        }
        return@withContext false
    }
}
