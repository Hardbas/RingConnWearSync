package com.ringconn.wear.app.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.*
import com.google.android.gms.wearable.Wearable
import com.ringconn.wear.app.R
import com.ringconn.wear.app.complication.RingConnComplicationProvider
import com.ringconn.wear.app.data.RingConnVitals
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Specific One UI Watch 8 Heart Rate & HRV Palette
private val HrBpmWarm = Color(0xFFFBCEB6)
private val CardBg = Color(0xFF1B1E22)
private val CardBgSubtle = Color(0xFF22262C)
private val TrackDark = Color(0x33000000)

// 5 Standard One UI Heart Rate Zones
private val ZoneRestColor = Color(0xFF47CDFF)      // Reposo (< 100 bpm)
private val ZoneWarmupColor = Color(0xFFA0F116)    // Calentamiento (100 - 120 bpm)
private val ZoneFatBurnColor = Color(0xFFFFB703)   // Quema de grasa (120 - 140 bpm)
private val ZoneCardioColor = Color(0xFFFB8500)    // Cardio (140 - 160 bpm)
private val ZonePeakColor = Color(0xFFFF5D99)      // Pico (> 160 bpm)

private val VitalsHrBrush = Brush.verticalGradient(listOf(HrGradientStart, HrGradientEnd))

/**
 * Dedicated Heart Rate & HRV Insights Detail Screen for Samsung One UI Watch 8.
 * Provides deep insights into cardiac rhythm, autonomic balance (HRV RMSSD),
 * resting heart rate baseline comparison, and 5 standard One UI HR zones.
 */
class HeartRateDetailActivity : ComponentActivity() {
    private var triggerMeasureOnStart by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        triggerMeasureOnStart = isMeasureIntent(intent)
        setContent {
            HeartRateDetailScreen(this, triggerMeasureOnStart)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        if (isMeasureIntent(intent)) {
            triggerMeasureOnStart = true
        }
    }

    private fun isMeasureIntent(intent: android.content.Intent?): Boolean {
        if (intent == null) return false
        val className = intent.component?.className ?: ""
        return className.contains("Measure") ||
                intent.getBooleanExtra("start_measure", false) ||
                intent.action == "com.ringconn.wear.app.ACTION_MEASURE_HR"
    }
}

@Composable
fun HeartRateDetailScreen(context: Context, startMeasureDirectly: Boolean = false) {
    val scope = rememberCoroutineScope()
    var vitals by remember { mutableStateOf(RingConnComplicationProvider.getLatestVitals(context)) }
    var isMeasuring by remember { mutableStateOf(false) }

    // Live sync with SharedPreferences cache
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("RingConnWearCache", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            vitals = RingConnComplicationProvider.getLatestVitals(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // Auto-trigger measurement if launched via tile "Medir" button
    LaunchedEffect(startMeasureDirectly) {
        if (startMeasureDirectly && !isMeasuring) {
            isMeasuring = true
            delay(3500L)
            val current = RingConnComplicationProvider.getLatestVitals(context)
            if (current.lastHeartRate > 0) {
                val newHr = current.lastHeartRate
                val newMin = if (current.minHeartRate > 0) minOf(current.minHeartRate, newHr) else newHr
                val newMax = if (current.maxHeartRate > 0) maxOf(current.maxHeartRate, newHr) else newHr
                val updated = current.copy(
                    lastHeartRate = newHr,
                    minHeartRate = newMin,
                    maxHeartRate = newMax,
                    refreshTimestamp = System.currentTimeMillis() / 1000L
                )
                RingConnComplicationProvider.saveAndBroadcastVitals(context, updated)
            }
            isMeasuring = false
        }
    }

    HeartRateDetailContent(
        vitals = vitals,
        isMeasuring = isMeasuring,
        onMeasureClick = {
            if (!isMeasuring) {
                isMeasuring = true
                scope.launch {
                    try {
                        val nodes = Wearable.getNodeClient(context).connectedNodes
                        nodes.addOnSuccessListener { nodeList ->
                            nodeList.forEach { node ->
                                Wearable.getMessageClient(context).sendMessage(
                                    node.id,
                                    "/action/sync_ringconn",
                                    ByteArray(0)
                                )
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    delay(5500L)
                    val current = RingConnComplicationProvider.getLatestVitals(context)
                    if (current.lastHeartRate > 0) {
                        val newHr = current.lastHeartRate
                        val newMin = if (current.minHeartRate > 0) minOf(current.minHeartRate, newHr) else newHr
                        val newMax = if (current.maxHeartRate > 0) maxOf(current.maxHeartRate, newHr) else newHr
                        val updated = current.copy(
                            lastHeartRate = newHr,
                            minHeartRate = newMin,
                            maxHeartRate = newMax,
                            refreshTimestamp = System.currentTimeMillis() / 1000L
                        )
                        RingConnComplicationProvider.saveAndBroadcastVitals(context, updated)
                    }
                    isMeasuring = false
                }
            }
        }
    )
}

@Composable
fun HeartRateDetailContent(
    vitals: RingConnVitals,
    isMeasuring: Boolean = false,
    onMeasureClick: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val currentHr = vitals.lastHeartRate
    val hasHr = currentHr > 0
    val hrDisplay = if (hasHr) "$currentHr" else "--"

    val avgHr = vitals.avgHeartRate
    val minHr = vitals.minHeartRate
    val maxHr = vitals.maxHeartRate
    val hasMinMax = minHr > 0 && maxHr > 0
    val minHrText = if (minHr > 0) stringResource(R.string.min_hr_format, minHr) else "--"
    val maxHrText = if (maxHr > 0) stringResource(R.string.max_hr_format, maxHr) else "--"

    val hrvVal = vitals.lastHrv
    val hasHrv = hrvVal > 0
    val hrvDisplay = if (hasHrv) "$hrvVal" else "--"

    // Status evaluation
    val hrStatusLabel = when {
        !hasHr -> "--"
        currentHr < 60 -> stringResource(R.string.status_resting)
        currentHr <= 100 -> stringResource(R.string.status_normal)
        else -> stringResource(R.string.status_elevated)
    }
    val hrStatusColor = when {
        !hasHr -> LabelSilver
        currentHr < 60 -> CyanActive
        currentHr <= 100 -> LimeSteps
        else -> HrMinMaxPink
    }

    // HRV status evaluation
    val hrvStatusLabel = when {
        !hasHrv -> "--"
        hrvVal >= 60 -> stringResource(R.string.status_optimal)
        hrvVal >= 40 -> stringResource(R.string.status_balanced)
        else -> stringResource(R.string.status_fatigue)
    }
    val hrvStatusColor = when {
        !hasHrv -> LabelSilver
        hrvVal >= 60 -> LimeSteps
        hrvVal >= 40 -> CyanActive
        else -> HrMinMaxPink
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scrollState = scrollState) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OneUiBlack)
                .onRotaryScrollEvent {
                    coroutineScope.launch {
                        scrollState.scrollBy(it.verticalScrollPixels)
                    }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
                .verticalScroll(scrollState)
                .padding(top = 28.dp, bottom = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // =========================================================================
            // a) Header: "Frecuencia Cardíaca" + "Anillo RingConn Gen 2"
            // =========================================================================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.tile_heart_rate_title),
                    fontSize = TextBody,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.ring_model_subtitle),
                    fontSize = TextLabel,
                    color = HrSubtitlePink
                )
            }

            // =========================================================================
            // b) Hero Stadium Card: Live BPM, pulsing heart, Min-Max range & Measure button
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(ShapeStadium)
                    .background(VitalsHrBrush)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PulsingHeartIcon(isMeasuring = isMeasuring)
                        Spacer(modifier = Modifier.width(12.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = hrDisplay,
                                fontSize = TextHero,
                                style = TabularNums,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.bpm_unit),
                                fontSize = TextBody,
                                color = HrBpmWarm,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Min-Max range bar with smooth Canvas draw animation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(minHrText, fontSize = TextLabel, style = TabularNums, color = HrSubtitlePink)
                        AnimatedMinMaxBar(
                            progress = if (hasMinMax && maxHr > minHr && hasHr) {
                                ((currentHr - minHr).toFloat() / (maxHr - minHr).toFloat()).coerceIn(0.05f, 1f)
                            } else if (hasHr) 0.65f else 0f,
                            color = HrMinMaxPink,
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .padding(horizontal = 8.dp)
                        )
                        Text(maxHrText, fontSize = TextLabel, style = TabularNums, color = HrSubtitlePink)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // "Medir Ahora" pill button
                    Box(
                        modifier = Modifier
                            .clip(ShapePill)
                            .background(GlassBlack50)
                            .clickable {
                                onMeasureClick()
                            }
                            .padding(horizontal = 24.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = if (isMeasuring) stringResource(R.string.measuring) else stringResource(R.string.measure_now),
                            fontSize = TextLabel,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // =========================================================================
            // c) Insights Card: Ritmo y Reposo
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(ShapePod)
                    .background(CardBg)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.rhythm_and_rest),
                            fontSize = TextBody,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(ShapePill)
                                .background(hrStatusColor.copy(alpha = 0.22f))
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = hrStatusLabel,
                                fontSize = TextCaption,
                                style = TabularNums,
                                color = hrStatusColor,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // RHR Comparison Mini Tiles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val bpmUnit = stringResource(R.string.bpm_unit)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(ShapeChip)
                                .background(CardBgSubtle)
                                .padding(horizontal = 8.dp, vertical = 7.dp)
                        ) {
                            Column {
                                Text(stringResource(R.string.avg_resting_hr), fontSize = TextCaption, color = LabelSilver)
                                Spacer(modifier = Modifier.height(2.dp))
                                val avgHrText = if (avgHr > 0) "$avgHr $bpmUnit" else "--"
                                Text(avgHrText, fontSize = TextBody, style = TabularNums, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        val diff = if (hasHr && avgHr > 0) currentHr - avgHr else null
                        val diffText = if (diff != null) (if (diff >= 0) "+$diff $bpmUnit" else "$diff $bpmUnit") else "--"
                        val diffColor = if (diff != null) (if (diff <= 6) CyanActive else HrMinMaxPink) else LabelSilver
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(ShapeChip)
                                .background(CardBgSubtle)
                                .padding(horizontal = 8.dp, vertical = 7.dp)
                        ) {
                            Column {
                                Text(stringResource(R.string.variation), fontSize = TextCaption, color = LabelSilver)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    diffText,
                                    fontSize = TextBody,
                                    style = TabularNums,
                                    color = diffColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // d) Variabilidad Cardíaca (HRV / VFC)
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(ShapePod)
                    .background(CardBg)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.hrv_title),
                            fontSize = TextBody,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "RMSSD",
                            fontSize = TextCaption,
                            color = LabelSilver,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = hrvDisplay,
                                fontSize = TextBig,
                                style = TabularNums,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "ms",
                                fontSize = TextBody,
                                color = CyanActive,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(ShapePill)
                                .background(hrvStatusColor.copy(alpha = 0.22f))
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = hrvStatusLabel,
                                fontSize = TextCaption,
                                style = TabularNums,
                                color = hrvStatusColor,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Visual HRV status gauge with current dot indicator
                    HrvStatusGauge(
                        hrvValue = hrvVal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Gauge legends
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("< 40 ms", fontSize = TextCaption, style = TabularNums, color = HrMinMaxPink, fontWeight = FontWeight.Medium)
                        Text("40 - 60 ms", fontSize = TextCaption, style = TabularNums, color = CyanActive, fontWeight = FontWeight.Medium)
                        Text("> 60 ms", fontSize = TextCaption, style = TabularNums, color = LimeSteps, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // =========================================================================
            // e) Zonas Cardíacas (Heart Rate Zones): 5 standard One UI zones
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(ShapePod)
                    .background(CardBg)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                val totalZoneMins = vitals.zone1Mins + vitals.zone2Mins + vitals.zone3Mins + vitals.zone4Mins + vitals.zone5Mins
                val z1Pct = if (totalZoneMins > 0) vitals.zone1Mins.toFloat() / totalZoneMins.toFloat() else 0f
                val z2Pct = if (totalZoneMins > 0) vitals.zone2Mins.toFloat() / totalZoneMins.toFloat() else 0f
                val z3Pct = if (totalZoneMins > 0) vitals.zone3Mins.toFloat() / totalZoneMins.toFloat() else 0f
                val z4Pct = if (totalZoneMins > 0) vitals.zone4Mins.toFloat() / totalZoneMins.toFloat() else 0f
                val z5Pct = if (totalZoneMins > 0) vitals.zone5Mins.toFloat() / totalZoneMins.toFloat() else 0f

                Column {
                    Text(
                        text = stringResource(R.string.hr_zones_title),
                        fontSize = TextBody,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val bpmUnit = stringResource(R.string.bpm_unit)

                    // Zone 1: Reposo (< 100 bpm)
                    ZoneItemRow(
                        zoneName = stringResource(R.string.zone_rest),
                        bpmRange = "< 100 $bpmUnit",
                        durationText = formatZoneDuration(vitals.zone1Mins, totalZoneMins),
                        zoneColor = ZoneRestColor,
                        distributionPct = z1Pct,
                        isActive = hasHr && currentHr < 100
                    )

                    // Zone 2: Calentamiento (100 - 120 bpm)
                    ZoneItemRow(
                        zoneName = stringResource(R.string.zone_warmup),
                        bpmRange = "100 - 120 $bpmUnit",
                        durationText = formatZoneDuration(vitals.zone2Mins, totalZoneMins),
                        zoneColor = ZoneWarmupColor,
                        distributionPct = z2Pct,
                        isActive = hasHr && currentHr in 100..120
                    )

                    // Zone 3: Quema de grasa (120 - 140 bpm)
                    ZoneItemRow(
                        zoneName = stringResource(R.string.zone_fat_burn),
                        bpmRange = "121 - 140 $bpmUnit",
                        durationText = formatZoneDuration(vitals.zone3Mins, totalZoneMins),
                        zoneColor = ZoneFatBurnColor,
                        distributionPct = z3Pct,
                        isActive = hasHr && currentHr in 121..140
                    )

                    // Zone 4: Cardio (140 - 160 bpm)
                    ZoneItemRow(
                        zoneName = stringResource(R.string.zone_cardio),
                        bpmRange = "141 - 160 $bpmUnit",
                        durationText = formatZoneDuration(vitals.zone4Mins, totalZoneMins),
                        zoneColor = ZoneCardioColor,
                        distributionPct = z4Pct,
                        isActive = hasHr && currentHr in 141..160
                    )

                    // Zone 5: Pico (> 160 bpm)
                    ZoneItemRow(
                        zoneName = stringResource(R.string.zone_peak),
                        bpmRange = "> 160 $bpmUnit",
                        durationText = formatZoneDuration(vitals.zone5Mins, totalZoneMins),
                        zoneColor = ZonePeakColor,
                        distributionPct = z5Pct,
                        isActive = hasHr && currentHr > 160
                    )
                }
            }
        }
    }
}

/**
 * Isolated pulsing heart icon with graphicsLayer scale animation.
 * Zero parent recomposition during animation ticks.
 */
@Composable
private fun PulsingHeartIcon(isMeasuring: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "hr_pulse_solitary")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isMeasuring) 1.28f else 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isMeasuring) 400 else 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .size(38.dp)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
            .clip(CircleShape)
            .background(Color(0x33000000)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.oneui_hr_icon),
            contentDescription = "HR Icon",
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Min-Max progress bar animated via Canvas draw phase.
 */
@Composable
private fun AnimatedMinMaxBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        anim.animateTo(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(
                durationMillis = 350,
                easing = CubicBezierEasing(0.22f, 0.25f, 0.0f, 1.0f)
            )
        )
    }

    Canvas(modifier = modifier) {
        val corner = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = GlassBlack50, size = size, cornerRadius = corner)
        val barW = size.width * anim.value
        if (barW > 0f) {
            drawRoundRect(color = color, size = Size(barW, size.height), cornerRadius = corner)
        }
    }
}

/**
 * Segmented HRV Status Gauge with indicator marker dot executed solely on Canvas.
 */
@Composable
private fun HrvStatusGauge(hrvValue: Int, modifier: Modifier = Modifier) {
    val animPos = remember { Animatable(0.5f) }
    val targetNormalized = ((hrvValue - 20f) / 70f).coerceIn(0.06f, 0.94f)

    LaunchedEffect(targetNormalized) {
        animPos.animateTo(
            targetValue = targetNormalized,
            animationSpec = tween(
                durationMillis = 400,
                easing = CubicBezierEasing(0.22f, 0.25f, 0.0f, 1.0f)
            )
        )
    }

    Canvas(modifier = modifier) {
        val corner = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = TrackDark, size = size, cornerRadius = corner)

        val segW = (size.width - 4f) / 3f
        val segH = size.height

        // Segment 1: Pink (Baja)
        drawRoundRect(
            color = HrMinMaxPink.copy(alpha = 0.85f),
            topLeft = Offset(0f, 0f),
            size = Size(segW, segH),
            cornerRadius = CornerRadius(segH / 2f, segH / 2f)
        )

        // Segment 2: Cyan (Equilibrada)
        drawRoundRect(
            color = CyanActive.copy(alpha = 0.85f),
            topLeft = Offset(segW + 2f, 0f),
            size = Size(segW, segH),
            cornerRadius = CornerRadius(segH / 2f, segH / 2f)
        )

        // Segment 3: Lime (Óptima)
        drawRoundRect(
            color = LimeSteps.copy(alpha = 0.85f),
            topLeft = Offset((segW * 2f) + 4f, 0f),
            size = Size(segW, segH),
            cornerRadius = CornerRadius(segH / 2f, segH / 2f)
        )

        // Current Indicator Needle / Dot
        val indicatorX = size.width * animPos.value
        drawCircle(
            color = Color.Black,
            radius = segH / 2f + 1f,
            center = Offset(indicatorX, segH / 2f)
        )
        drawCircle(
            color = Color.White,
            radius = segH / 2f - 1f,
            center = Offset(indicatorX, segH / 2f)
        )
    }
}

private fun formatZoneDuration(mins: Int, totalMins: Int): String {
    if (totalMins <= 0) return "--"
    if (mins <= 0) return "0m · 0%"
    val pct = (mins.toDouble() / totalMins.toDouble() * 100.0).roundToInt()
    val hours = mins / 60
    val remMins = mins % 60
    val timeStr = when {
        hours > 0 && remMins > 0 -> "${hours}h ${remMins}m"
        hours > 0 -> "${hours}h"
        else -> "${remMins}m"
    }
    return "$timeStr · $pct%"
}

/**
 * Individual Heart Rate Zone Row with visual distribution bar, duration, and active indicator.
 */
@Composable
private fun ZoneItemRow(
    zoneName: String,
    bpmRange: String,
    durationText: String,
    zoneColor: Color,
    distributionPct: Float,
    isActive: Boolean
) {
    Column(modifier = Modifier.padding(vertical = 3.5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(zoneColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = zoneName,
                    fontSize = TextLabel,
                    color = if (isActive) Color.White else LabelSilver,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
            }
            Text(
                text = durationText,
                fontSize = TextCaption,
                style = TabularNums,
                color = if (isActive) Color.White else LabelSilver,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = bpmRange,
                fontSize = TextCaption,
                style = TabularNums,
                color = if (isActive) zoneColor else PipeGray,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
            )
            if (isActive) {
                Box(
                    modifier = Modifier
                        .clip(ShapePill)
                        .background(zoneColor)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "ACTUAL",
                        fontSize = TextCaption,
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Distribution progress bar
        val animBar = remember { Animatable(0f) }
        LaunchedEffect(distributionPct) {
            animBar.animateTo(
                targetValue = distributionPct,
                animationSpec = tween(
                    durationMillis = 350,
                    easing = CubicBezierEasing(0.22f, 0.25f, 0.0f, 1.0f)
                )
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        ) {
            val corner = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(color = TrackDark, size = size, cornerRadius = corner)
            val w = size.width * animBar.value
            if (w > 0f) {
                drawRoundRect(
                    color = if (isActive) zoneColor else zoneColor.copy(alpha = 0.5f),
                    size = Size(w, size.height),
                    cornerRadius = corner
                )
            }
        }
    }
}

@Preview(
    device = "id:wearos_small_round",
    showSystemUi = true
)
@Composable
fun HeartRateDetailPreview() {
    HeartRateDetailContent(
        vitals = RingConnVitals(
            lastHeartRate = 74,
            minHeartRate = 58,
            maxHeartRate = 124,
            lastHrv = 48,
            lastSpo2 = 98,
            isConnected = true
        ),
        isMeasuring = false,
        onMeasureClick = {}
    )
}

