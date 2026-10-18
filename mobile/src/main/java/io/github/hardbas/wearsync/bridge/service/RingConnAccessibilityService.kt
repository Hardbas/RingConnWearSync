package io.github.hardbas.wearsync.bridge.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import io.github.hardbas.wearsync.bridge.data.RingConnVitals
import kotlinx.coroutines.*

class RingConnAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scrapeJob: Job? = null
    private var lastScrapeTimestamp = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "RingConnAccessibilityService connected and active!")

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 200
            packageNames = arrayOf("com.gdjztech.ringconn")
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == "com.gdjztech.ringconn") {
            // Debounce rapid window changes
            val now = System.currentTimeMillis()
            if (now - lastScrapeTimestamp < 400L) return

            scrapeJob?.cancel()
            scrapeJob = serviceScope.launch {
                delay(300L) // Wait for Flutter semantics tree to settle
                scrapeWindowContent()
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "RingConnAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        scrapeJob?.cancel()
        serviceScope.cancel()
        Log.i(TAG, "RingConnAccessibilityService destroyed")
    }

    /**
     * Traverses the active Flutter semantics tree, extracts all metrics,
     * updates SharedPreferences and pushes directly to Galaxy Watch 8.
     */
    fun scrapeWindowContent(): RingConnVitals? {
        val collectedTokens = mutableListOf<String>()

        try {
            // 1. Traverse root in active window (only if RingConn)
            rootInActiveWindow?.let { root ->
                if (root.packageName?.toString() == "com.gdjztech.ringconn") {
                    traverseNode(root, collectedTokens)
                }
                root.recycle()
            }

            // 2. Traverse interactive windows (only if RingConn)
            try {
                for (w in windows) {
                    val winRoot = w.root ?: continue
                    if (winRoot.packageName?.toString() == "com.gdjztech.ringconn") {
                        traverseNode(winRoot, collectedTokens)
                    }
                    winRoot.recycle()
                }
            } catch (e: Exception) {
                // Ignore window enumeration errors
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error traversing accessibility tree", e)
        }

        if (collectedTokens.isEmpty()) return null

        lastScrapeTimestamp = System.currentTimeMillis()
        val currentVitals = RingConnTelemetryReader.readBestAvailableVitals(this)
        val parsedVitals = RingConnTelemetryParser.parseTokens(collectedTokens, currentVitals)

        val hasMeaningfulData = parsedVitals.lastHeartRate > 0 ||
                parsedVitals.sleepScore > 0 ||
                parsedVitals.activityScore > 0 ||
                parsedVitals.liveSteps > 0 ||
                parsedVitals.lastSpo2 > 0

        if (hasMeaningfulData) {
            RingConnTelemetryReader.saveToPreferences(
                context = this,
                vitals = parsedVitals,
                source = "RingConn Directo (100% Exacto)"
            )

            serviceScope.launch {
                WearDataSyncService(this@RingConnAccessibilityService).syncVitalsToWatch(parsedVitals)
            }

            Log.i(TAG, "100% Exact RingConn telemetry scraped & synced: HR=${parsedVitals.lastHeartRate}, Sleep=${parsedVitals.sleepScore}, Act=${parsedVitals.activityScore}, Steps=${parsedVitals.liveSteps}, Bat=${parsedVitals.ringBattery}%")
            return parsedVitals
        }

        return null
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null) return
        val nodePkg = node.packageName?.toString()
        if (nodePkg != null && nodePkg != "com.gdjztech.ringconn") return
        try {
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverseNode(child, out)
                child.recycle()
            }
        } catch (e: Exception) {
            // Node could have become invalid during tree walk
        }
    }

    companion object {
        private const val TAG = "RingConnAccessibility"
        var instance: RingConnAccessibilityService? = null

        fun isRunning(): Boolean = instance != null

        fun triggerImmediateScrape(): RingConnVitals? {
            return instance?.scrapeWindowContent()
        }
    }
}
