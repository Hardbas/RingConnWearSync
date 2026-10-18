package io.github.hardbas.wearsync.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class DailyHistoryEntry(
    val epochDay: Long = 0L,
    val dayLabel: String = "",
    val sleepScore: Int = 0,
    val activityScore: Int = 0,
    val stressScore: Int = 0,
    val hrv: Int = 0,
    val rhr: Int = 0,
    val spo2: Int = 0,
    val steps: Int = 0,
    val skinTempOffset: Float = 0f,
    val readinessScore: Int = 0
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("epochDay", epochDay)
        json.put("dayLabel", dayLabel)
        json.put("sleepScore", sleepScore)
        json.put("activityScore", activityScore)
        json.put("stressScore", stressScore)
        json.put("hrv", hrv)
        json.put("rhr", rhr)
        json.put("spo2", spo2)
        json.put("steps", steps)
        json.put("skinTempOffset", skinTempOffset.toDouble())
        json.put("readinessScore", readinessScore)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): DailyHistoryEntry {
            return DailyHistoryEntry(
                epochDay = json.optLong("epochDay", 0L),
                dayLabel = json.optString("dayLabel", ""),
                sleepScore = json.optInt("sleepScore", 0),
                activityScore = json.optInt("activityScore", 0),
                stressScore = json.optInt("stressScore", 0),
                hrv = json.optInt("hrv", 0),
                rhr = json.optInt("rhr", 0),
                spo2 = json.optInt("spo2", 0),
                steps = json.optInt("steps", 0),
                skinTempOffset = json.optDouble("skinTempOffset", 0.0).toFloat(),
                readinessScore = json.optInt("readinessScore", 0)
            )
        }
    }
}

object RingConnHistoryStore {
    private const val PREFS_NAME = "RingConnWearCache"
    private const val KEY_HISTORY_JSON = "history_7day_json"

    /**
     * Pure O(1) readiness engine (zero battery cost).
     * Combines sleepScore (40%), HRV recovery (30%), autonomic calm (20%), and thermal stability (10%).
     * Returns 0 if no metrics are available yet, otherwise clamped to 1..100.
     */
    fun computeReadinessScore(vitals: RingConnVitals): Int {
        if (vitals.lastHeartRate == 0 && vitals.sleepScore == 0 && vitals.lastHrv == 0) {
            return 0
        }

        // 1. Sleep score (40% weight)
        val sleepPart = vitals.sleepScore.coerceIn(0, 100).toFloat() * 0.40f

        // 2. HRV recovery (30% weight) vs average HRV or 50ms baseline
        val baselineHrv = if (vitals.avgHrv > 0) vitals.avgHrv.toFloat() else 50f
        val hrvScore = if (vitals.lastHrv > 0) {
            ((vitals.lastHrv.toFloat() / baselineHrv) * 100f).coerceIn(0f, 100f)
        } else {
            0f
        }
        val hrvPart = hrvScore * 0.30f

        // 3. Autonomic calm (20% weight) -> 100 - stressScore
        val calmScore = (100 - vitals.stressScore).coerceIn(0, 100).toFloat()
        val calmPart = calmScore * 0.20f

        // 4. Thermal stability (10% weight) -> 100 - (abs(skinTempOffset) * 25).toInt()
        val thermalScore = (100 - (abs(vitals.skinTempOffset) * 25f).toInt()).coerceIn(0, 100).toFloat()
        val thermalPart = thermalScore * 0.10f

        val rawScore = (sleepPart + hrvPart + calmPart + thermalPart).roundToInt()
        return rawScore.coerceIn(1, 100)
    }

    /**
     * Updates or appends today's entry into the 7-day rolling history cache in SharedPreferences.
     * Trims to the most recent 7 days sorted ascending by epochDay.
     */
    fun recordDailySnapshot(context: Context, vitals: RingConnVitals) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_HISTORY_JSON, null)
            val entries = mutableListOf<DailyHistoryEntry>()

            if (!jsonStr.isNullOrBlank()) {
                try {
                    val jsonArray = JSONArray(jsonStr)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.optJSONObject(i)
                        if (obj != null) {
                            entries.add(DailyHistoryEntry.fromJson(obj))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RingConnHistoryStore", "Error parsing history JSON in recordDailySnapshot", e)
                }
            }

            val today = LocalDate.now()
            val todayEpoch = today.toEpochDay()
            val dayLabel = today.dayOfWeek
                .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                .replace(".", "")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

            val rhr = if (vitals.minHeartRate > 0) vitals.minHeartRate
                else if (vitals.avgHeartRate > 0) vitals.avgHeartRate
                else vitals.lastHeartRate
            val hrv = if (vitals.lastHrv > 0) vitals.lastHrv else vitals.avgHrv
            val spo2 = if (vitals.lastSpo2 > 0) vitals.lastSpo2 else vitals.avgSpo2
            val readiness = computeReadinessScore(vitals)

            val existingIndex = entries.indexOfFirst { it.epochDay == todayEpoch }
            val existing = if (existingIndex >= 0) entries[existingIndex] else null

            val updatedEntry = DailyHistoryEntry(
                epochDay = todayEpoch,
                dayLabel = dayLabel,
                sleepScore = if (vitals.sleepScore > 0) vitals.sleepScore else (existing?.sleepScore ?: 0),
                activityScore = if (vitals.activityScore > 0) vitals.activityScore else (existing?.activityScore ?: 0),
                stressScore = if (vitals.stressScore > 0) vitals.stressScore else (existing?.stressScore ?: 0),
                hrv = if (hrv > 0) hrv else (existing?.hrv ?: 0),
                rhr = if (rhr > 0) rhr else (existing?.rhr ?: 0),
                spo2 = if (spo2 > 0) spo2 else (existing?.spo2 ?: 0),
                steps = if (vitals.liveSteps > 0) vitals.liveSteps else (existing?.steps ?: 0),
                skinTempOffset = if (vitals.skinTempOffset != 0f) vitals.skinTempOffset else (existing?.skinTempOffset ?: 0f),
                readinessScore = if (readiness > 0) readiness else (existing?.readinessScore ?: 0)
            )

            if (existingIndex >= 0) {
                entries[existingIndex] = updatedEntry
            } else {
                entries.add(updatedEntry)
            }

            val sorted = entries.sortedBy { it.epochDay }
            val trimmed = if (sorted.size > 7) sorted.takeLast(7) else sorted

            val outputArray = JSONArray()
            for (item in trimmed) {
                outputArray.put(item.toJson())
            }
            prefs.edit().putString(KEY_HISTORY_JSON, outputArray.toString()).apply()
        } catch (e: Exception) {
            Log.e("RingConnHistoryStore", "Failed to record daily snapshot", e)
        }
    }

    /**
     * Reads up to 7 stored daily snapshots from SharedPreferences.
     * If the cache is empty and currentVitals is provided, synthesizes a 1-day entry for today.
     */
    fun getHistory(context: Context, currentVitals: RingConnVitals? = null): List<DailyHistoryEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_HISTORY_JSON, null)
        val entries = mutableListOf<DailyHistoryEntry>()

        if (!jsonStr.isNullOrBlank()) {
            try {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i)
                    if (obj != null) {
                        entries.add(DailyHistoryEntry.fromJson(obj))
                    }
                }
            } catch (e: Exception) {
                Log.e("RingConnHistoryStore", "Error parsing history JSON in getHistory", e)
            }
        }

        val sorted = entries.sortedBy { it.epochDay }
        if (sorted.isEmpty() && currentVitals != null) {
            val today = LocalDate.now()
            val dayLabel = today.dayOfWeek
                .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                .replace(".", "")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

            val rhr = if (currentVitals.minHeartRate > 0) currentVitals.minHeartRate
                else if (currentVitals.avgHeartRate > 0) currentVitals.avgHeartRate
                else currentVitals.lastHeartRate
            val hrv = if (currentVitals.lastHrv > 0) currentVitals.lastHrv else currentVitals.avgHrv
            val spo2 = if (currentVitals.lastSpo2 > 0) currentVitals.lastSpo2 else currentVitals.avgSpo2

            val single = DailyHistoryEntry(
                epochDay = today.toEpochDay(),
                dayLabel = dayLabel,
                sleepScore = currentVitals.sleepScore,
                activityScore = currentVitals.activityScore,
                stressScore = currentVitals.stressScore,
                hrv = hrv,
                rhr = rhr,
                spo2 = spo2,
                steps = currentVitals.liveSteps,
                skinTempOffset = currentVitals.skinTempOffset,
                readinessScore = computeReadinessScore(currentVitals)
            )
            return listOf(single)
        }
        return sorted
    }

    /**
     * Sleek dark obsidian card displaying a 7-day rolling trend.
     * Features:
     * - Dark obsidian card container (Color(0xFF1B1E22), RoundedCornerShape(22.dp))
     * - Header with metricTitle and 7-day average badge in accentColor
     * - 7-slot bar + connected trend-line sparkline in Canvas
     * - Stroke, Brush, and Path hoisted with remember outside Canvas for zero per-frame allocations
     * - Day labels below each slot
     * - Interactive tap selection with haptic feedback to inspect any day's value
     */
    @Composable
    fun SevenDayTrendCard(
        metricTitle: String,
        unit: String,
        accentColor: Color,
        selector: (DailyHistoryEntry) -> Float,
        currentFallback: Float,
        modifier: Modifier = Modifier
    ) {
        val context = LocalContext.current
        var history by remember { mutableStateOf(getHistory(context)) }

        DisposableEffect(context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_HISTORY_JSON) {
                    history = getHistory(context)
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }

        val today = remember { LocalDate.now() }
        val slots = remember(history, currentFallback, today) {
            (6 downTo 0).map { daysAgo ->
                val day = today.minusDays(daysAgo.toLong())
                val epoch = day.toEpochDay()
                val entry = history.find { it.epochDay == epoch }
                val label = day.dayOfWeek
                    .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    .replace(".", "")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    .take(2)
                    .uppercase()

                val value: Float
                val hasData: Boolean
                if (entry != null) {
                    value = selector(entry)
                    hasData = true
                } else if (daysAgo == 0 && (currentFallback > 0f || (unit.contains("°") && currentFallback != 0f))) {
                    value = currentFallback
                    hasData = true
                } else {
                    value = 0f
                    hasData = false
                }
                TrendSlot(epoch = epoch, label = label, value = value, hasData = hasData)
            }
        }

        val validSlots = slots.filter { it.hasData && (it.value > 0f || unit.contains("°")) }
        val avgValue = remember(slots) {
            if (validSlots.isNotEmpty()) {
                validSlots.map { it.value }.average().toFloat()
            } else if (currentFallback > 0f || unit.contains("°")) {
                currentFallback
            } else {
                0f
            }
        }

        val (rangeMin, rangeMax) = remember(slots, unit) {
            if (unit.contains("°")) {
                -2.0f to 2.0f
            } else {
                val vals = slots.filter { it.hasData && it.value > 0f }.map { it.value }
                if (vals.isEmpty()) {
                    0f to 100f
                } else {
                    val min = vals.minOrNull() ?: 0f
                    val max = vals.maxOrNull() ?: 100f
                    if (max <= min) {
                        val base = if (max > 0f) max else 100f
                        (base * 0.7f).coerceAtLeast(0f) to (base * 1.3f).coerceAtLeast(10f)
                    } else {
                        val pad = (max - min) * 0.2f
                        (min - pad).coerceAtLeast(0f) to (max + pad)
                    }
                }
            }
        }

        var selectedIndex by remember { mutableStateOf<Int?>(null) }
        val haptic = LocalHapticFeedback.current

        // Hoisting allocations outside Canvas for 0 per-frame cost
        val density = LocalDensity.current
        val strokeWidthPx = remember(density) { with(density) { 2.dp.toPx() } }
        val lineStroke = remember(strokeWidthPx) {
            Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        }
        val lineBrush = remember(accentColor) {
            Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.55f), accentColor))
        }
        val barBrush = remember(accentColor) {
            Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.45f), accentColor.copy(alpha = 0.12f)))
        }
        val selectedBarBrush = remember(accentColor) {
            Brush.verticalGradient(listOf(accentColor, accentColor.copy(alpha = 0.35f)))
        }
        val trackColor = remember { Color(0x14FFFFFF) }
        val haloColor = remember(accentColor) { accentColor.copy(alpha = 0.30f) }
        val trendPath = remember { Path() }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF1B1E22))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                // Header: Metric title + 7-day average / inspected badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(3.5.dp))
                                .background(accentColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = metricTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val inspectedSlot = selectedIndex?.let { slots.getOrNull(it) }
                    val badgeText = if (inspectedSlot != null && inspectedSlot.hasData) {
                        "${inspectedSlot.label}: ${formatMetricValue(inspectedSlot.value, unit)}"
                    } else if (avgValue > 0f || (unit.contains("°") && avgValue != 0f)) {
                        "Avg ${formatMetricValue(avgValue, unit)}"
                    } else {
                        "-- $unit".trim()
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(accentColor.copy(alpha = 0.20f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Canvas: 7-slot bar + connected trend-line sparkline
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val slotW = size.width / 7f
                                val idx = (offset.x / slotW).toInt().coerceIn(0, 6)
                                selectedIndex = if (selectedIndex == idx) null else idx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                ) {
                    val slotCount = 7
                    val slotWidth = size.width / slotCount.toFloat()
                    val barWidth = slotWidth * 0.40f
                    val padTop = 6.dp.toPx()
                    val padBottom = 4.dp.toPx()
                    val usableHeight = size.height - padTop - padBottom
                    val rangeSpan = if (rangeMax > rangeMin) (rangeMax - rangeMin) else 1f

                    // 1. Draw background tracks and active bars
                    for (i in 0 until slotCount) {
                        val slot = slots[i]
                        val cx = (i + 0.5f) * slotWidth
                        val barLeft = cx - (barWidth / 2f)

                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset(barLeft, padTop),
                            size = Size(barWidth, usableHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )

                        if (slot.hasData) {
                            val norm = ((slot.value - rangeMin) / rangeSpan).coerceIn(0.12f, 1.0f)
                            val barH = norm * usableHeight
                            val barTop = padTop + usableHeight - barH
                            val brush = if (i == selectedIndex) selectedBarBrush else barBrush
                            drawRoundRect(
                                brush = brush,
                                topLeft = Offset(barLeft, barTop),
                                size = Size(barWidth, barH),
                                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                            )
                        }
                    }

                    // 2. Draw connected sparkline path
                    trendPath.reset()
                    var hasMoved = false
                    for (i in 0 until slotCount) {
                        val slot = slots[i]
                        if (slot.hasData) {
                            val cx = (i + 0.5f) * slotWidth
                            val norm = ((slot.value - rangeMin) / rangeSpan).coerceIn(0.08f, 0.95f)
                            val cy = padTop + usableHeight - (norm * usableHeight)
                            if (!hasMoved) {
                                trendPath.moveTo(cx, cy)
                                hasMoved = true
                            } else {
                                trendPath.lineTo(cx, cy)
                            }
                        }
                    }
                    if (hasMoved) {
                        drawPath(path = trendPath, brush = lineBrush, style = lineStroke)
                    }

                    // 3. Draw dots and selection halo
                    for (i in 0 until slotCount) {
                        val slot = slots[i]
                        if (slot.hasData) {
                            val cx = (i + 0.5f) * slotWidth
                            val norm = ((slot.value - rangeMin) / rangeSpan).coerceIn(0.08f, 0.95f)
                            val cy = padTop + usableHeight - (norm * usableHeight)
                            val isSelected = (i == selectedIndex)

                            if (isSelected) {
                                drawCircle(
                                    color = haloColor,
                                    radius = 6.dp.toPx(),
                                    center = Offset(cx, cy)
                                )
                                drawCircle(
                                    color = accentColor,
                                    radius = 3.5.dp.toPx(),
                                    center = Offset(cx, cy)
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 1.5.dp.toPx(),
                                    center = Offset(cx, cy)
                                )
                            } else {
                                drawCircle(
                                    color = accentColor,
                                    radius = 2.dp.toPx(),
                                    center = Offset(cx, cy)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Day labels row below Canvas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (i in 0 until 7) {
                        val slot = slots[i]
                        val isSelected = (i == selectedIndex)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedIndex = if (selectedIndex == i) null else i
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = slot.label,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) accentColor else Color(0xFF8E959E),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Top-level convenience forwarder for SevenDayTrendCard.
 */
@Composable
fun SevenDayTrendCard(
    metricTitle: String,
    unit: String,
    accentColor: Color,
    selector: (DailyHistoryEntry) -> Float,
    currentFallback: Float,
    modifier: Modifier = Modifier
) {
    RingConnHistoryStore.SevenDayTrendCard(
        metricTitle = metricTitle,
        unit = unit,
        accentColor = accentColor,
        selector = selector,
        currentFallback = currentFallback,
        modifier = modifier
    )
}

private data class TrendSlot(
    val epoch: Long,
    val label: String,
    val value: Float,
    val hasData: Boolean
)

private fun formatMetricValue(value: Float, unit: String): String {
    return when {
        unit.contains("°") -> String.format(Locale.US, "%+.1f%s", value, unit)
        unit.isEmpty() -> "${value.roundToInt()}"
        unit == "%" -> "${value.roundToInt()}%"
        unit.equals("steps", ignoreCase = true) || unit.equals("pasos", ignoreCase = true) -> {
            if (value >= 10000) String.format(Locale.US, "%.1fk", value / 1000.0)
            else "${value.roundToInt()}"
        }
        else -> "${value.roundToInt()} $unit"
    }
}
