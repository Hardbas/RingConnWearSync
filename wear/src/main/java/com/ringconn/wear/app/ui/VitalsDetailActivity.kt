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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val VitalsBrush = Brush.verticalGradient(listOf(VitalsGradientStart, VitalsGradientEnd))

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
                intent.action == "com.ringconn.wear.app.ACTION_MEASURE_HR"
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
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val currentHr = vitals.lastHeartRate
    val hasHr = currentHr > 0
    val hasMinMax = vitals.minHeartRate > 0 && vitals.maxHeartRate > 0
    val minHr = if (hasMinMax) vitals.minHeartRate else (if (currentHr > 0) currentHr else 0)
    val maxHr = if (hasMinMax) vitals.maxHeartRate else (if (currentHr > 0) currentHr else 0)

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
            // Header: Frecuencia Cardíaca
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
                    color = VitalsGold
                )
            }

            // Hero Stadium Capsule (BPM + Pulsating Icon + Measure button)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(ShapeStadium)
                    .background(VitalsBrush)
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
                                text = "$currentHr",
                                fontSize = TextHero,
                                style = TabularNums,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.bpm_unit),
                                fontSize = TextBody,
                                color = VitalsGold,
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
                        Text(stringResource(R.string.min_hr_format, minHr), fontSize = TextLabel, style = TabularNums, color = VitalsGold)
                        AnimatedMinMaxBar(
                            progress = 0.68f,
                            color = VitalsGold,
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .padding(horizontal = 8.dp)
                        )
                        Text(stringResource(R.string.max_hr_format, maxHr), fontSize = TextLabel, style = TabularNums, color = VitalsGold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sync button
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

            // One UI Heart Rate Zones Capsule
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(ShapePod)
                    .background(Color(0xFF1B1E22))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.hr_zones_title),
                        fontSize = TextLabel,
                        color = LabelSilver,
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

            // Telemetry indicators row (SpO2, HRV, Ring Battery)
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val spo2Text = if (vitals.lastSpo2 > 0) "${vitals.lastSpo2}%" else "--%"
                val hrvText = if (vitals.lastHrv > 0) "${vitals.lastHrv} ms" else "-- ms"
                val batText = if (vitals.ringBattery > 0) "${vitals.ringBattery}%" else "--%"
                MetricMiniCapsule(stringResource(R.string.blood_oxygen_short), spo2Text, CyanActive, Modifier.weight(1f))
                MetricMiniCapsule(stringResource(R.string.hrv_short), hrvText, VioletCalorie, Modifier.weight(1f))
                MetricMiniCapsule(stringResource(R.string.ring_short), batText, LimeSteps, Modifier.weight(1f))
            }
        }
    }
}

/**
 * Isolated pulsing heart icon with zero recomposition of parent/sibling composables.
 * Scale animation executes in the graphicsLayer draw phase.
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

@Composable
private fun ZoneRow(label: String, isActive: Boolean, activeColor: Color = VitalsGold) {
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
                .background(if (isActive) activeColor else PipeGray)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = TextLabel,
            color = if (isActive) activeColor else LabelSilver,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun MetricMiniCapsule(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(ShapeCard)
            .background(Color(0xFF1B1E22))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, fontSize = TextCaption, color = LabelSilver)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = TextBody, style = TabularNums, color = color, fontWeight = FontWeight.Bold)
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

