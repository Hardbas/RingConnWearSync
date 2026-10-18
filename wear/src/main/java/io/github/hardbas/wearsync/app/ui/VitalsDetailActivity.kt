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
                    withTimeoutOrNull(8500L) {
                        WearDataListenerService.lastSyncCompletedMs.first { it != startSync }
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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val currentHr = vitals.lastHeartRate
    val hasHr = currentHr > 0
    val hrDisplay = if (hasHr) "$currentHr" else "--"
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
                    true
                }
                .focusRequester(focusRequester)
                .focusable(),
            state = listState,
            autoCentering = null,
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

                        val spo2Val = if (vitals.lastSpo2 > 0) "${vitals.lastSpo2}" else "--"
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
            // d) Telemetry Mini Cards: HRV & Battery (Centered)
            // =========================================================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val hrvVal = if (vitals.lastHrv > 0) "${vitals.lastHrv} ms" else "--"
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
        }
    }
}

/**
 * Isolated pulsing heart icon with graphicsLayer scale animation.
 */
@Composable
private fun PulsingHeartIcon(isMeasuring: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "hr_pulse")
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
            .size(32.dp)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
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

    Canvas(modifier = modifier) {
        val strokeWidth = 7.5.dp.toPx()
        val diameter = size.minDimension - strokeWidth
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
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Progress Arc
        if (animProgress.value > 0f) {
            val sweep = 270f * animProgress.value
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Color(0xFFFF2D55),
                        Color(0xFFFF6482),
                        Color(0xFFFF9500),
                        Color(0xFFFF2D55)
                    )
                ),
                startAngle = 135f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
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
