package io.github.hardbas.wearsync.bridge.service

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.hardbas.wearsync.bridge.data.RingConnVitals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object RingConnWidgetHostReader {
    private const val TAG = "RingConnWidgetHost"
    const val HOST_ID = 4488
    private const val PREFS_NAME = "ringconn_widget_bindings"

    private val RINGCONN_PROVIDERS = listOf(
        ComponentName("com.gdjztech.ringconn", "com.gdjztech.remotewidget.MediumHomeAppWidgetReceiver"),
        ComponentName("com.gdjztech.ringconn", "com.gdjztech.remotewidget.DataIndicatorsWidgetReceiver"),
        ComponentName("com.gdjztech.ringconn", "com.gdjztech.remotewidget.DataIndicatorsLargeWidgetReceiver"),
        ComponentName("com.gdjztech.ringconn", "com.gdjztech.remotewidget.SportIndicatorsWidgetReceiver"),
        ComponentName("com.gdjztech.ringconn", "com.gdjztech.remotewidget.HomeAppWidgetReceiver"),
        ComponentName("com.gdjztech.ringconn", "com.gdjztech.remotewidget.SportIndicatorsLargeWidgetReceiver")
    )

    private class CleanableAppWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
        public override fun clearViews() {
            super.clearViews()
        }
    }

    private var appWidgetHost: CleanableAppWidgetHost? = null
    private var isListening = false
    private val boundWidgetMap = mutableMapOf<ComponentName, Int>()
    private var pendingBindWidgetId: Int = -1
    private var pendingBindProvider: ComponentName? = null

    fun hasAnyBoundWidget(context: Context): Boolean {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        for (comp in RINGCONN_PROVIDERS) {
            val widgetId = boundWidgetMap[comp] ?: prefs.getInt("widget_id_${comp.className}", -1)
            if (widgetId != -1) {
                val info = appWidgetManager.getAppWidgetInfo(widgetId)
                if (info != null && info.provider == comp) {
                    boundWidgetMap[comp] = widgetId
                    return true
                }
            }
        }
        return false
    }

    /**
     * Tries to bind all installed RingConn widget providers silently via bindAppWidgetIdIfAllowed.
     * If Android requires explicit user authorization (e.g. after installing APK without ADB grantbind),
     * returns an ACTION_APPWIDGET_BIND Intent for the Activity to launch the native Android permission dialog.
     */
    fun prepareNextBindIntent(context: Context): Intent? {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val host = getHost(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        for (comp in RINGCONN_PROVIDERS) {
            val providerInfo = appWidgetManager.installedProviders.firstOrNull { it.provider == comp } ?: continue
            var widgetId = boundWidgetMap[comp] ?: prefs.getInt("widget_id_${comp.className}", -1)
            if (widgetId != -1) {
                val existingInfo = appWidgetManager.getAppWidgetInfo(widgetId)
                if (existingInfo != null && existingInfo.provider == comp) {
                    boundWidgetMap[comp] = widgetId
                    continue
                } else {
                    try { host.deleteAppWidgetId(widgetId) } catch (_: Exception) {}
                    prefs.edit().remove("widget_id_${comp.className}").apply()
                    boundWidgetMap.remove(comp)
                    widgetId = -1
                }
            }

            widgetId = host.allocateAppWidgetId()
            val bound = appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, providerInfo.provider)
            if (bound) {
                boundWidgetMap[comp] = widgetId
                prefs.edit().putInt("widget_id_${comp.className}", widgetId).apply()
                Log.i(TAG, "Silently bound widget ID $widgetId to $comp")
            } else {
                pendingBindWidgetId = widgetId
                pendingBindProvider = comp
                prefs.edit().putInt("widget_id_${comp.className}", widgetId).apply()
                Log.i(TAG, "Requesting native user bind dialog for widget ID $widgetId ($comp)")
                return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, comp)
                }
            }
        }
        return null
    }

    fun onWidgetBindResult(context: Context, resultOk: Boolean) {
        val widgetId = pendingBindWidgetId
        val comp = pendingBindProvider
        pendingBindWidgetId = -1
        pendingBindProvider = null
        if (widgetId == -1 || comp == null) return

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingInfo = appWidgetManager.getAppWidgetInfo(widgetId)
        if (existingInfo != null && existingInfo.provider == comp) {
            boundWidgetMap[comp] = widgetId
            prefs.edit().putInt("widget_id_${comp.className}", widgetId).apply()
            Log.i(TAG, "User authorized widget bind for ID $widgetId ($comp)")
        } else if (!resultOk) {
            prefs.edit().remove("widget_id_${comp.className}").apply()
            boundWidgetMap.remove(comp)
            try { getHost(context).deleteAppWidgetId(widgetId) } catch (_: Exception) {}
        }
    }

    private fun getHost(context: Context): CleanableAppWidgetHost {
        var h = appWidgetHost
        if (h == null) {
            h = CleanableAppWidgetHost(context.applicationContext, HOST_ID)
            appWidgetHost = h
        }
        return h
    }

    fun hasValidTelemetry(vitals: RingConnVitals): Boolean {
        return vitals.lastHeartRate > 0 ||
                vitals.sleepScore > 0 ||
                (vitals.sleepDeepMins + vitals.sleepRemMins + vitals.sleepLightMins) > 0 ||
                vitals.activityScore > 0 ||
                vitals.liveSteps > 0 ||
                vitals.ringBattery > 0 ||
                vitals.lastSpo2 > 0 ||
                vitals.avgSpo2 > 0 ||
                vitals.lastHrv > 0 ||
                vitals.avgHrv > 0 ||
                vitals.stressScore > 0 ||
                vitals.skinTempOffset != 0f ||
                vitals.skinTempBaseline > 0f
    }

    /**
     * Executes persistent widget host extraction on R5CYA1MLMTV.
     * Binds once, caches widget IDs in memory & SharedPreferences, triggers Jetpack Glance
     * via updateAppWidgetOptions only when fresh compose is needed, inflates views,
     * and extracts 100% exact live numbers from RingConn's RemoteViews.
     */
    suspend fun readRingConnWidgets(context: Context): RingConnVitals? = withContext(Dispatchers.Main) {
        if (pendingBindWidgetId != -1) {
            Log.d(TAG, "Skipping background widget read while user bind dialog is open")
            return@withContext null
        }
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val host = getHost(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        try {
            host.startListening()
            isListening = true
            Log.d(TAG, "AppWidgetHost ($HOST_ID) started listening.")
        } catch (e: Exception) {
            Log.w(TAG, "AppWidgetHost startListening error: ${e.message}")
        }

        try {
            Log.i(TAG, "Starting RingConn WidgetHost extraction...")

            // Trigger RingConn Flutter background worker to write fresh vitals before reading RemoteViews
            try {
                val bgIntent = Intent("es.antonborri.home_widget.action.BACKGROUND").apply {
                    setPackage("com.gdjztech.ringconn")
                }
                context.sendBroadcast(bgIntent)
            } catch (e: Exception) {
                Log.d(TAG, "HomeWidget background broadcast error: ${e.message}")
            }

            val activeBindings = mutableListOf<Pair<ComponentName, Int>>()
            var hadNewAllocation = false

            // Phase 1: Ensure each provider is persistently allocated and bound
            for (comp in RINGCONN_PROVIDERS) {
                try {
                    val providerInfo = appWidgetManager.installedProviders.firstOrNull { it.provider == comp }
                    if (providerInfo == null) {
                        Log.w(TAG, "Provider not installed on device: $comp")
                        continue
                    }

                    var widgetId = boundWidgetMap[comp] ?: prefs.getInt("widget_id_${comp.className}", -1)

                    // Validate existing widget ID with system_server
                    if (widgetId != -1) {
                        val existingInfo = appWidgetManager.getAppWidgetInfo(widgetId)
                        if (existingInfo == null || existingInfo.provider != comp) {
                            Log.d(TAG, "Cached widget ID $widgetId invalid or mismatched for $comp; reallocating.")
                            try { host.deleteAppWidgetId(widgetId) } catch (_: Exception) {}
                            prefs.edit().remove("widget_id_${comp.className}").apply()
                            boundWidgetMap.remove(comp)
                            widgetId = -1
                        }
                    }

                    if (widgetId == -1) {
                        widgetId = host.allocateAppWidgetId()
                        val bound = appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, comp)
                        if (bound) {
                            boundWidgetMap[comp] = widgetId
                            prefs.edit().putInt("widget_id_${comp.className}", widgetId).apply()
                            hadNewAllocation = true
                            Log.i(TAG, "Persistently bound new widget ID $widgetId to $comp")
                        } else {
                            Log.w(TAG, "Failed to bind widget ID $widgetId to $comp (grantbind needed?)")
                            try { host.deleteAppWidgetId(widgetId) } catch (_: Exception) {}
                            continue
                        }
                    } else {
                        boundWidgetMap[comp] = widgetId
                    }

                    activeBindings.add(comp to widgetId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error configuring widget for provider $comp", e)
                }
            }

            if (activeBindings.isEmpty()) {
                Log.w(TAG, "No active RingConn widget bindings available.")
                return@withContext null
            }

            fun scrapeActiveWidgets(): List<String> {
                val tokens = mutableListOf<String>()
                for ((comp, widgetId) in activeBindings) {
                    try {
                        val providerInfo = appWidgetManager.getAppWidgetInfo(widgetId) ?: continue
                        val hostView = host.createView(context, widgetId, providerInfo)
                        if (hostView != null) {
                            hostView.measure(
                                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
                            )
                            hostView.layout(0, 0, 800, 800)

                            val viewTexts = mutableListOf<String>()
                            extractTextsFromView(hostView, viewTexts)
                            Log.d(TAG, "Extracted ${viewTexts.size} strings from widget ID $widgetId ($comp): $viewTexts")
                            tokens.addAll(viewTexts)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed inflating widget ID $widgetId ($comp): ${e.message}")
                    }
                }
                return tokens
            }

            val baseVitals = RingConnTelemetryReader.readBestAvailableVitals(context)

            // Phase 2: First pass - scrape directly without sending 6 widget options updates
            var collectedTokens = scrapeActiveWidgets()
            var parsedVitals = if (collectedTokens.isNotEmpty()) {
                RingConnTelemetryParser.parseTokens(collectedTokens, baseVitals)
            } else null

            // Phase 3: If first pass yielded no valid telemetry or was freshly allocated, trigger Glance updateAppWidgetOptions and retry
            if (parsedVitals == null || !hasValidTelemetry(parsedVitals)) {
                Log.d(TAG, "First pass produced no valid telemetry; toggling updateAppWidgetOptions to trigger Glance compose...")
                for ((comp, widgetId) in activeBindings) {
                    try {
                        val toggleKey = "toggle_h_${comp.className}"
                        val currentToggle = prefs.getBoolean(toggleKey, false)
                        val nextToggle = !currentToggle
                        prefs.edit().putBoolean(toggleKey, nextToggle).apply()
                        val height = if (nextToggle) 201 else 200

                        val options = Bundle().apply {
                            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 276)
                            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 276)
                            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
                            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
                        }
                        appWidgetManager.updateAppWidgetOptions(widgetId, options)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating options for widget $comp", e)
                    }
                }

                // Wait for Jetpack Glance inside com.gdjztech.ringconn to compose & publish RemoteViews
                val retryWait = if (hadNewAllocation) 1200L else 450L
                delay(retryWait)

                collectedTokens = scrapeActiveWidgets()
                if (collectedTokens.isNotEmpty()) {
                    parsedVitals = RingConnTelemetryParser.parseTokens(collectedTokens, baseVitals)
                }
            }

            if (parsedVitals != null && hasValidTelemetry(parsedVitals)) {
                RingConnTelemetryReader.saveToPreferences(
                    context = context,
                    vitals = parsedVitals,
                    source = "RingConn WidgetHost (100% Exacto)"
                )
                Log.i(TAG, "Successfully extracted RingConn vitals via WidgetHost: $parsedVitals")
                return@withContext parsedVitals
            }

            return@withContext null
        } finally {
            runCatching {
                host.clearViews()
            }.onFailure { Log.w(TAG, "Error clearing AppWidgetHost views: ${it.message}") }
            runCatching {
                host.stopListening()
            }.onFailure { Log.w(TAG, "Error stopping AppWidgetHost listening: ${it.message}") }
            isListening = false
            Log.d(TAG, "AppWidgetHost ($HOST_ID) cleared views and stopped listening.")
        }
    }

    private fun extractTextsFromView(view: View, out: MutableList<String>) {
        if (view is TextView && !view.text.isNullOrBlank()) {
            out.add(view.text.toString().trim())
        }
        val cd = view.contentDescription
        if (!cd.isNullOrBlank()) {
            out.add(cd.toString().trim())
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                extractTextsFromView(view.getChildAt(i), out)
            }
        }
    }
}
