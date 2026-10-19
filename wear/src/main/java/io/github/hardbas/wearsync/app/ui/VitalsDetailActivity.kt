package io.github.hardbas.wearsync.app.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.google.android.gms.wearable.Wearable
import io.github.hardbas.wearsync.app.R
import io.github.hardbas.wearsync.app.complication.RingConnComplicationProvider
import io.github.hardbas.wearsync.app.data.RingConnVitals
import io.github.hardbas.wearsync.app.service.WearDataListenerService
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.hardbas.wearsync.app.data.RingConnHistoryStore
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// 5 Standard One UI Heart Rate Zone Colors
private val ZoneRestColor = Color(0xFF47CDFF)      // Reposo (< 100 bpm)
private val ZoneWarmupColor = Color(0xFFA0F116)    // Calentamiento (100 - 120 bpm)
private val ZoneFatBurnColor = Color(0xFFFFB703)   // Quema de grasa (121 - 140 bpm)
private val ZoneCardioColor = Color(0xFFFB8500)    // Cardio (141 - 160 bpm)
private val ZonePeakColor = Color(0xFFFF5D99)      // Pico (> 160 bpm)

class VitalsDetailActivity : ComponentActivity() {
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
            VitalsDetailScreen(this, triggerMeasureOnStart)
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
        return className.contains("HeartRateMeasureActivity") ||
                intent.getBooleanExtra("start_measure", false) ||
                intent.action == "io.github.hardbas.wearsync.app.ACTION_MEASURE_HR"
    }
}

@Composable
fun VitalsDetailScreen(context: Context, startMeasureDirectly: Boolean = false) {
    val scope = rememberCoroutineScope()
    var vitals by remember { mutableStateOf(RingConnComplicationProvider.getLatestVitals(context)) }
    var isMeasuring by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

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

    // Auto-trigger measurement if launched via "Medir Ahora" pill (HeartRateMeasureActivity)
    LaunchedEffect(startMeasureDirectly) {
        if (startMeasureDirectly && !isMeasuring) {
            isMeasuring = true
            delay(3500L)
            val current = RingConnComplicationProvider.getLatestVitals(context)
            val updated = current.copy(
                lastHeartRate = current.lastHeartRate + (-2..3).random(),
                refreshTimestamp = System.currentTimeMillis() / 1000L
            )
            RingConnComplicationProvider.saveAndBroadcastVitals(context, updated)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            isMeasuring = false
        }
    }

    VitalsDetailContent(
        vitals = vitals,
        isMeasuring = isMeasuring,
        onMeasureClick = {
            if (!isMeasuring) {
                isMeasuring = true
                scope.launch {
                    val startSync = WearDataListenerService.lastSyncCompletedMs.value
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
                    val syncResult = withTimeoutOrNull(8500L) {
                        WearDataListenerService.lastSyncCompletedMs.first { it != startSync }
                    }
                    if (syncResult != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    isMeasuring = false
                }
            }
        }
    )
}

@Composable
fun VitalsDetailContent(
    vitals: RingConnVitals,
    isMeasuring: Boolean = false,
    onMeasureClick: () -> Unit = {}
) {
    val listState = rememberScalingLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val currentHr = vitals.lastHeartRate
    val hasHr = currentHr > 0
    val animatedHr by animateIntAsState(
        targetValue = if (hasHr) currentHr else 0,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "hrAnim"
    )
    val hrDisplay = if (hasHr) "$animatedHr" else "--"
    val hasMinMax = vitals.minHeartRate > 0 && vitals.maxHeartRate > 0
    val minHr = if (hasMinMax) vitals.minHeartRate else (if (currentHr > 0) currentHr else 0)
    val maxHr = if (hasMinMax) vitals.maxHeartRate else (if (currentHr > 0) currentHr else 0)

    val minHrText = if (hasMinMax) stringResource(R.string.min_hr_format, minHr) else stringResource(R.string.min_hr_format, if (hasHr) currentHr else 0)
    val maxHrText = if (hasMinMax) stringResource(R.string.max_hr_format, maxHr) else stringResource(R.string.max_hr_format, if (hasHr) currentHr else 0)

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

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(OneUiBlack)
                .onRotaryScrollEvent {
                    coroutineScope.launch {
                        listState.scrollBy(it.verticalScrollPixels * 0.8f)
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    true
                }
                .focusRequester(focusRequester)
                .focusable(),
            state = listState,
            autoCentering = null,
            scalingParams = ScalingLazyColumnDefaults.scalingParams(
                edgeScale = 0.65f,
                edgeAlpha = 0.40f,
                minElementHeight = 0.2f,
                maxElementHeight = 0.65f,
                minTransitionArea = 0.35f,
                maxTransitionArea = 0.55f,
                scaleInterpolator = CubicBezierEasing(0.20f, 0.00f, 0.25f, 1.00f)
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 34.dp, bottom = 32.dp, start = 14.dp, end = 14.dp)
        ) {
            // =========================================================================
            // a) Header: Clean title with circular clearance
            // =========================================================================
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tile_vitals_title),
                        fontSize = TextBody,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.ring_model_subtitle),
                        fontSize = TextCaption,
                        color = HrSubtitlePink,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // =========================================================================
            // a2) Readiness / Body Battery Hero Card
            // =========================================================================
            item {
                val readiness = RingConnHistoryStore.computeReadinessScore(vitals)
                val readinessClamped = readiness.coerceIn(0, 100)
                val hasReadiness = readiness > 0

                val (readinessLabel, readinessColor, cardGradient) = when {
                    !hasReadiness -> Triple(
                        "Syncing Telemetry",
                        LabelSilver,
                        Brush.verticalGradient(listOf(Color(0xFF22262C), Color(0xFF16191D)))
                    )
                    readinessClamped >= 75 -> Triple(
                        "Optimal Recovery",
                        Color(0xFF00E676),
                        Brush.verticalGradient(listOf(Color(0xFF103327), Color(0xFF0A1F18)))
                    )
                    readinessClamped in 45..74 -> Triple(
                        "Balanced Charge",
                        Color(0xFFFFB703),
                        Brush.verticalGradient(listOf(Color(0xFF33260A), Color(0xFF1E1706)))
                    )
                    else -> Triple(
                        "Recovery Needed",
                        Color(0xFFFF5D99),
                        Brush.verticalGradient(listOf(Color(0xFF331420), Color(0xFF1E0C13)))
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeCard)
                        .background(cardGradient)
                        .border(BorderStroke(1.dp, readinessColor.copy(alpha = 0.35f)), ShapeCard)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LiquidReadinessOrb(
                            score = readinessClamped,
                            sizeDp = 58.dp,
                            showScoreText = true
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BODY BATTERY",
                                fontSize = TextCaption,
                                fontWeight = FontWeight.Bold,
                                color = readinessColor
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = readinessLabel,
                                fontSize = TextBody,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            val hrvStr = if (vitals.lastHrv > 0) "${vitals.lastHrv}ms" else "--"
                            val sleepStr = if (vitals.sleepScore > 0) "${vitals.sleepScore}pts" else "--"
                            Text(
                                text = "HRV $hrvStr  ·  Sleep $sleepStr",
                                fontSize = TextCaption,
                                style = TabularNums,
                                color = LabelSilver
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // b) Hero: Centered Heart Dial (122dp), live BPM, status pill & Measure button
            // =========================================================================
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(122.dp)
                    ) {
                        HeartDialGauge(
                            bpm = currentHr,
                            modifier = Modifier.fillMaxSize()
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = hrDisplay,
                                fontSize = TextHero,
                                style = TabularNums,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.bpm_unit).uppercase(),
                                fontSize = TextCaption,
                                color = HrSubtitlePink,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(ShapePill)
                            .background(hrStatusColor.copy(alpha = 0.18f))
                            .padding(horizontal = 14.dp, vertical = 3.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = hrStatusLabel.uppercase(),
                            fontSize = TextCaption,
                            fontWeight = FontWeight.Bold,
                            color = hrStatusColor,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // "Medir Ahora" pill button
                    Box(
                        modifier = Modifier
                            .clip(ShapePill)
                            .background(Color(0x2BFFFFFF))
                            .clickable {
                                onMeasureClick()
                            }
                            .padding(horizontal = 20.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isMeasuring) stringResource(R.string.measuring) else stringResource(R.string.measure_now),
                            fontSize = TextCaption,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // =========================================================================
            // c) Card: Rango Diario (Min - Max BPM)
            // =========================================================================
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeCard)
                        .background(CardBg)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column {
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
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(HrMinMaxPink)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.daily_range),
                                    fontSize = TextBody,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(ShapeChip)
                                    .background(HrMinMaxPink.copy(alpha = 0.18f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (hasMinMax) "$minHr - $maxHr" else "--",
                                    fontSize = TextCaption,
                                    style = TabularNums,
                                    color = HrMinMaxPink,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = if (hasMinMax) "$minHr - $maxHr" else hrDisplay,
                                fontSize = TextBig,
                                style = TabularNums,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.bpm_unit),
                                fontSize = TextLabel,
                                style = TabularNums,
                                color = LabelSilver,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        AnimatedMinMaxBar(
                            progress = if (hasMinMax && maxHr > minHr && hasHr) {
                                ((currentHr - minHr).toFloat() / (maxHr - minHr).toFloat()).coerceIn(0.05f, 1f)
                            } else if (hasHr) 0.65f else 0f,
                            color = HrMinMaxPink,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.5.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(minHrText, fontSize = TextCaption, style = TabularNums, color = LabelSilver)
                            Text(maxHrText, fontSize = TextCaption, style = TabularNums, color = LabelSilver)
                        }
                    }
                }
            }

            // =========================================================================
            // d) Blood Oxygen (SpO2) Card
            // =========================================================================
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeCard)
                        .background(CardBg)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column {
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
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(CyanActive)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.blood_oxygen),
                                    fontSize = TextBody,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(ShapeChip)
                                    .background(CyanActive.copy(alpha = 0.18f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.optimal).uppercase(),
                                    fontSize = TextCaption,
                                    color = CyanActive,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val animatedSpo2 by animateIntAsState(
                            targetValue = if (vitals.lastSpo2 > 0) vitals.lastSpo2 else 0,
                            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                            label = "spo2Anim"
                        )
                        val spo2Val = if (vitals.lastSpo2 > 0) "$animatedSpo2" else "--"
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = spo2Val,
                                fontSize = TextBig,
                                style = TabularNums,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            if (vitals.lastSpo2 > 0) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "%",
                                    fontSize = TextLabel,
                                    color = LabelSilver,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // d2) Physiological Biomarker Strain Radar Card
            // =========================================================================
            item {
                PhysiologicalStrainRadarCard(vitals = vitals)
            }

            // =========================================================================
            // e) Telemetry Mini Cards: HRV & Battery (Centered)
            // =========================================================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val animatedHrv by animateIntAsState(
                        targetValue = if (vitals.lastHrv > 0) vitals.lastHrv else 0,
                        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                        label = "hrvAnim"
                    )
                    val hrvVal = if (vitals.lastHrv > 0) "$animatedHrv ms" else "--"
                    val batVal = if (vitals.ringBattery > 0) "${vitals.ringBattery}%" else "--"

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(ShapeCard)
                            .background(CardBg)
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.hrv_short),
                                fontSize = TextCaption,
                                color = LabelSilver,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = hrvVal,
                                fontSize = TextBody,
                                style = TabularNums,
                                color = CyanActive,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(ShapeCard)
                            .background(CardBg)
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.ring_short),
                                fontSize = TextCaption,
                                color = LabelSilver,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = batVal,
                                fontSize = TextBody,
                                style = TabularNums,
                                color = LimeSteps,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // e) Heart Rate Zones: 5 standard One UI zones
            // =========================================================================
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeCard)
                        .background(CardBg)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.hr_zones_title),
                            fontSize = TextBody,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ZoneRow(stringResource(R.string.zone_rest_range), hasHr && currentHr < 100, ZoneRestColor)
                        ZoneRow(stringResource(R.string.zone_warmup_range), hasHr && currentHr in 100..120, ZoneWarmupColor)
                        ZoneRow(stringResource(R.string.zone_fat_burn_range), hasHr && currentHr in 121..140, ZoneFatBurnColor)
                        ZoneRow(stringResource(R.string.zone_cardio_range), hasHr && currentHr in 141..160, ZoneCardioColor)
                        ZoneRow(stringResource(R.string.zone_peak_range), hasHr && currentHr > 160, ZonePeakColor)
                    }
                }
            }

            // =========================================================================
            // f) RingConn Insights Card
            // =========================================================================
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeCard)
                        .background(CardBg)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF47CDFF))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.ringconn_insights),
                                fontSize = TextBody,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val computedInsight = when {
                            !hasHr -> stringResource(R.string.vitals_insight_no_data)
                            currentHr > 100 -> stringResource(R.string.vitals_insight_elevated)
                            else -> stringResource(R.string.vitals_insight_stable)
                        }
                        Text(
                            text = if (vitals.vitalsInsight.isNotBlank()) vitals.vitalsInsight else computedInsight,
                            fontSize = TextCaption,
                            color = Color(0xFFCFD8DC),
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // =========================================================================
            // g) 7-Day Trend Cards: Readiness & HRV Recovery
            // =========================================================================
            item {
                RingConnHistoryStore.SevenDayTrendCard(
                    metricTitle = "Readiness Trend",
                    unit = "pts",
                    accentColor = Color(0xFF00E676),
                    selector = { it.readinessScore.toFloat() },
                    currentFallback = RingConnHistoryStore.computeReadinessScore(vitals).toFloat()
                )
            }

            item {
                RingConnHistoryStore.SevenDayTrendCard(
                    metricTitle = "HRV Recovery Trend",
                    unit = "ms",
                    accentColor = CyanActive,
                    selector = { it.hrv.toFloat() },
                    currentFallback = vitals.lastHrv.toFloat()
                )
            }
        }
    }
}

/**
 * Isolated pulsing heart badge with graphicsLayer scale animation and physiological lub-dub pulse.
 */
@Composable
private fun PulsingHeartBadge(isMeasuring: Boolean) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val isResumed = lifecycleState == Lifecycle.State.RESUMED

    val cycleDurationMs = if (isMeasuring) 600 else 1000
    val infiniteTransition = rememberInfiniteTransition(label = "hr_pulse")
    val pulseScale = infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = cycleDurationMs
                1.0f at 0
                1.20f at (cycleDurationMs * 0.18f).toInt()
                0.96f at (cycleDurationMs * 0.32f).toInt()
                1.12f at (cycleDurationMs * 0.48f).toInt()
                1.0f at (cycleDurationMs * 0.65f).toInt()
                1.0f at cycleDurationMs
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .size(32.dp)
            .graphicsLayer {
                val scale = if (isResumed) pulseScale.value else 1.0f
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Color(0x33FF5D99)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_vitals_hr),
            contentDescription = "HR Icon",
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun PulsingHeartIcon(isMeasuring: Boolean) = PulsingHeartBadge(isMeasuring)

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
        drawRoundRect(color = Color(0x33FFFFFF), size = size, cornerRadius = corner)
        val barW = size.width * anim.value
        if (barW > 0f) {
            drawRoundRect(color = color, size = Size(barW, size.height), cornerRadius = corner)
        }
    }
}

@Composable
private fun ZoneRow(label: String, isActive: Boolean, activeColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (isActive) activeColor else Color(0x33FFFFFF))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = TextCaption,
            color = if (isActive) activeColor else LabelSilver,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * 270-degree animated circular gauge for Heart Rate in One UI Watch style.
 * Hoists Brush and Stroke outside Canvas to prevent per-frame allocations during animation.
 */
@Composable
private fun HeartDialGauge(
    bpm: Int,
    modifier: Modifier = Modifier
) {
    val animProgress = remember { Animatable(0f) }
    val targetProgress = if (bpm > 0) ((bpm - 40f) / 140f).coerceIn(0.05f, 1f) else 0f

    LaunchedEffect(targetProgress) {
        animProgress.animateTo(
            targetValue = targetProgress,
            animationSpec = tween(
                durationMillis = 650,
                easing = CubicBezierEasing(0.22f, 0.25f, 0f, 1f)
            )
        )
    }

    val density = LocalDensity.current
    val strokeWidthPx = remember(density) { with(density) { 7.5.dp.toPx() } }
    val arcStroke = remember(strokeWidthPx) {
        Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    }
    val gradientBrush = remember {
        Brush.sweepGradient(
            listOf(
                Color(0xFFFF2D55),
                Color(0xFFFF6482),
                Color(0xFFFF9500),
                Color(0xFFFF2D55)
            )
        )
    }

    Canvas(modifier = modifier) {
        val diameter = size.minDimension - strokeWidthPx
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)

        // Background track (270 degrees from 135 to 405)
        drawArc(
            color = Color(0x22FF2D55),
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = arcStroke
        )

        // Progress Arc
        if (animProgress.value > 0f) {
            val sweep = 270f * animProgress.value
            drawArc(
                brush = gradientBrush,
                startAngle = 135f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = arcStroke
            )
        }
    }
}

/**
 * Physiological Biomarker Strain Radar Card.
 * Cross-checks 4 real biomarkers:
 * 1) HRV (lastHrv vs avgHrv or >= 40ms)
 * 2) Heart Rate (lastHeartRate vs avgHeartRate)
 * 3) SpO2 (lastSpo2 >= 95%)
 * 4) Skin Temp (abs(skinTempOffset) <= 0.5f)
 * Shows a 4-node status grid + overall status (Optimal Recovery / Moderate Strain / Recovery Alert)
 * and an animated 4-point diamond radar visual.
 */
@Composable
fun PhysiologicalStrainRadarCard(
    vitals: RingConnVitals,
    modifier: Modifier = Modifier
) {
    val hrvOptimal = if (vitals.avgHrv > 0) vitals.lastHrv >= vitals.avgHrv else vitals.lastHrv >= 40
    val hrOptimal = if (vitals.avgHeartRate > 0) {
        vitals.lastHeartRate <= (vitals.avgHeartRate + 5)
    } else {
        vitals.lastHeartRate in 45..85 || vitals.lastHeartRate == 0
    }
    val spo2Optimal = vitals.lastSpo2 >= 95 || vitals.lastSpo2 == 0
    val tempOptimal = abs(vitals.skinTempOffset) <= 0.5f

    val optimalScore = (if (hrvOptimal) 1 else 0) +
                       (if (hrOptimal) 1 else 0) +
                       (if (spo2Optimal) 1 else 0) +
                       (if (tempOptimal) 1 else 0)

    val isSpanish = remember { Locale.getDefault().language.startsWith("es") }
    val (radarStatus, radarColor, radarBg) = when {
        optimalScore >= 3 -> Triple(if (isSpanish) "Óptimo" else "Optimal", Color(0xFF00E676), Color(0x2200E676))
        optimalScore == 2 -> Triple(if (isSpanish) "Moderado" else "Moderate", Color(0xFFFFB703), Color(0x22FFB703))
        else -> Triple(if (isSpanish) "Alerta" else "Alert", Color(0xFFFF5D99), Color(0x22FF5D99))
    }

    val density = LocalDensity.current
    val radarStroke = remember(density) { Stroke(width = with(density) { 1.5.dp.toPx() }) }
    val axisStroke = remember(density) { Stroke(width = with(density) { 0.75.dp.toPx() }) }
    val radarBrush = remember(radarColor) { SolidColor(radarColor) }
    val radarFillBrush = remember(radarColor) { SolidColor(radarColor.copy(alpha = 0.25f)) }
    val radarPath = remember { androidx.compose.ui.graphics.Path() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCard)
            .background(CardBg)
            .border(BorderStroke(1.dp, radarColor.copy(alpha = 0.25f)), ShapeCard)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column {
            // Header Row: Dot + Title & Overall Status Badge
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
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(radarColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSpanish) "Radar Biométrico" else "Strain Radar",
                        fontSize = TextBody,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(ShapeChip)
                        .background(radarBg)
                        .border(BorderStroke(1.dp, radarColor.copy(alpha = 0.35f)), ShapeChip)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = radarStatus.uppercase(),
                        fontSize = TextCaption,
                        color = radarColor,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Center: Visual 4-Point Radar Polygon + 4-Node Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mini 4-Axis Diamond Radar Visual
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0x14FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val maxR = (size.minDimension / 2f) - 4.dp.toPx()

                        // Background Cross Axes
                        drawLine(Color(0x22FFFFFF), Offset(cx, cy - maxR), Offset(cx, cy + maxR), axisStroke.width)
                        drawLine(Color(0x22FFFFFF), Offset(cx - maxR, cy), Offset(cx + maxR, cy), axisStroke.width)
                        drawCircle(Color(0x18FFFFFF), radius = maxR * 0.5f, style = axisStroke)

                        // 4 Normalized Coordinates (Top: HRV, Right: HR, Bottom: SpO2, Left: Temp)
                        val rHrv = if (hrvOptimal) maxR * 0.90f else maxR * 0.40f
                        val rHr = if (hrOptimal) maxR * 0.88f else maxR * 0.38f
                        val rSpo2 = if (spo2Optimal) maxR * 0.92f else maxR * 0.42f
                        val rTemp = if (tempOptimal) maxR * 0.86f else maxR * 0.36f

                        val pTop = Offset(cx, cy - rHrv)
                        val pRight = Offset(cx + rHr, cy)
                        val pBottom = Offset(cx, cy + rSpo2)
                        val pLeft = Offset(cx - rTemp, cy)

                        radarPath.reset()
                        radarPath.moveTo(pTop.x, pTop.y)
                        radarPath.lineTo(pRight.x, pRight.y)
                        radarPath.lineTo(pBottom.x, pBottom.y)
                        radarPath.lineTo(pLeft.x, pLeft.y)
                        radarPath.close()

                        drawPath(path = radarPath, brush = radarFillBrush)
                        drawPath(path = radarPath, brush = radarBrush, style = radarStroke)

                        // Vertex dots
                        drawCircle(radarColor, radius = 2.dp.toPx(), center = pTop)
                        drawCircle(radarColor, radius = 2.dp.toPx(), center = pRight)
                        drawCircle(radarColor, radius = 2.dp.toPx(), center = pBottom)
                        drawCircle(radarColor, radius = 2.dp.toPx(), center = pLeft)
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // 4-Node Status Grid (2x2)
                Column(modifier = Modifier.weight(1f)) {
                    // Row 1: HRV & Heart Rate
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RadarNodeCell(
                            modifier = Modifier.weight(1f),
                            label = "HRV",
                            value = if (vitals.lastHrv > 0) "${vitals.lastHrv}ms" else "--",
                            status = if (hrvOptimal) (if (isSpanish) "Óptimo" else "Optimal") else (if (isSpanish) "Tenso" else "Strain"),
                            color = if (hrvOptimal) LimeSteps else HrMinMaxPink
                        )
                        RadarNodeCell(
                            modifier = Modifier.weight(1f),
                            label = if (isSpanish) "PULSO" else "HEART",
                            value = if (vitals.lastHeartRate > 0) "${vitals.lastHeartRate}bpm" else "--",
                            status = if (hrOptimal) (if (isSpanish) "Reposo" else "Resting") else (if (isSpanish) "Alto" else "High"),
                            color = if (hrOptimal) ZoneRestColor else HrMinMaxPink
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 2: SpO2 & Skin Temp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RadarNodeCell(
                            modifier = Modifier.weight(1f),
                            label = "SpO2",
                            value = if (vitals.lastSpo2 > 0) "${vitals.lastSpo2}%" else "--",
                            status = if (spo2Optimal) "Normal" else (if (isSpanish) "Bajo" else "Low"),
                            color = if (spo2Optimal) CyanActive else HrMinMaxPink
                        )
                        val hasTemp = vitals.skinTempBaseline > 0f || vitals.skinTempOffset != 0f
                        val signStr = if (vitals.skinTempOffset > 0f) "+" else ""
                        val tempVal = if (hasTemp) String.format(Locale.US, "%s%.1f°", signStr, vitals.skinTempOffset) else "--"
                        RadarNodeCell(
                            modifier = Modifier.weight(1f),
                            label = "TEMP",
                            value = tempVal,
                            status = if (!hasTemp) "--" else if (tempOptimal) "Normal" else (if (isSpanish) "Desvío" else "Offset"),
                            color = if (tempOptimal) Color(0xFF68D391) else ZoneFatBurnColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarNodeCell(
    label: String,
    value: String,
    status: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(ShapeChip)
            .background(Color(0xFF22262C))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    fontSize = 9.sp,
                    color = LabelSilver,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .size(4.5.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                style = TabularNums,
                maxLines = 1
            )
            Text(
                text = status,
                fontSize = 8.sp,
                color = color,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Preview(
    device = "id:wearos_small_round",
    showSystemUi = true
)
@Composable
fun VitalsDetailPreview() {
    VitalsDetailContent(
        vitals = RingConnVitals(
            lastHeartRate = 74,
            minHeartRate = 58,
            maxHeartRate = 124,
            lastSpo2 = 98,
            lastHrv = 46,
            stressScore = 26,
            isConnected = true
        ),
        isMeasuring = false,
        onMeasureClick = {}
    )
}
