package com.ringconn.wear.app.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.wearable.Wearable
import com.ringconn.wear.app.R
import com.ringconn.wear.app.complication.RingConnComplicationProvider
import com.ringconn.wear.app.data.RingConnVitals
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// One UI Watch 8 Sleep Stage Colors (decompiled from shealth_watch_base.apk)
private val StageAwake = Color(0xFFFF6B6B)     // Despierto
private val StageRem = Color(0xFF47CDFF)       // REM
private val StageLight = Color(0xFFA5AEDE)     // Ligero
private val StageDeep = Color(0xFF5E72E4)      // Profundo
private val SleepTrackBg = Color(0x33000000)

private val SleepHeroBrush = Brush.verticalGradient(listOf(SleepGradientStart, SleepGradientEnd))
private val NocturnalGradientBrush = Brush.verticalGradient(listOf(Color(0xFF252B42), Color(0xFF141724)))
private val SleepBarBrush = Brush.horizontalGradient(listOf(StageDeep, SleepPeriwinkle))

class SleepDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        setContent {
            SleepDetailScreen(this)
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
    }
}

@Composable
fun SleepDetailScreen(context: Context) {
    var vitals by remember { mutableStateOf(RingConnComplicationProvider.getLatestVitals(context)) }

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

    SleepDetailContent(
        vitals = vitals,
        onSyncClick = {
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
        }
    )
}

@Composable
fun SleepDetailContent(
    vitals: RingConnVitals,
    onSyncClick: () -> Unit = {}
) {
    val listState = rememberScalingLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val hasSleepData = vitals.sleepScore > 0
    val sleepScore = vitals.sleepScore
    val sleepScoreText = if (hasSleepData) "$sleepScore" else "--"
    val qualityBadge = when {
        !hasSleepData -> stringResource(R.string.no_data)
        sleepScore >= 85 -> stringResource(R.string.excellent)
        sleepScore >= 70 -> stringResource(R.string.good)
        else -> stringResource(R.string.fair)
    }

    val totalStagesMins = vitals.sleepDeepMins + vitals.sleepRemMins + vitals.sleepLightMins + vitals.sleepAwakeMins
    val hasStages = totalStagesMins > 0
    val effectiveSleepMins = vitals.sleepDeepMins + vitals.sleepRemMins + vitals.sleepLightMins

    val durationText = if (hasStages) {
        val h = effectiveSleepMins / 60
        val m = effectiveSleepMins % 60
        if (h > 0 && m > 0) "${h}h ${m}m" else if (h > 0) "${h}h" else "${m}m"
    } else if (hasSleepData) {
        "--"
    } else {
        stringResource(R.string.no_sleep_records)
    }

    val bpmUnit = stringResource(R.string.bpm_unit)
    val spo2Val = if (vitals.lastSpo2 > 0) "${vitals.lastSpo2}%" else "--"
    val hrvVal = if (vitals.lastHrv > 0) "${vitals.lastHrv} ms" else "--"
    val minHr = if (vitals.minHeartRate > 0) "${vitals.minHeartRate} $bpmUnit" else if (vitals.lastHeartRate > 0) "${vitals.lastHeartRate} $bpmUnit" else "--"
    val stressVal = if (vitals.stressScore > 0) "${vitals.stressScore}" else "--"

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
                        listState.scrollBy(it.verticalScrollPixels)
                    }
                    true
                }
                .focusRequester(focusRequester)
                .focusable(),
            state = listState,
            autoCentering = AutoCenteringParams(itemIndex = 0),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 28.dp, bottom = 44.dp, start = 8.dp, end = 8.dp)
        ) {
            // Header: Análisis de Sueño
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                    text = stringResource(R.string.sleep_analysis_title),
                    fontSize = TextBody,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.ring_model_subtitle),
                    fontSize = TextLabel,
                    color = SleepPeriwinkle
                )
            }
        }

        // Hero Sleep Score Capsule
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeStadium)
                    .background(SleepHeroBrush)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0x33000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.oneui_sleep_icon),
                                contentDescription = "Sleep Icon",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = sleepScoreText,
                                    fontSize = TextHero,
                                    style = TabularNums,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                if (hasSleepData) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.pts_unit),
                                        fontSize = TextBody,
                                        color = SleepPeriwinkle,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(bottom = 3.dp)
                                    )
                                }
                            }
                            Text(
                                text = qualityBadge,
                                fontSize = TextLabel,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Animated Canvas score progress bar (Draw-phase only)
                    AnimatedSleepScoreBar(
                        scorePct = sleepScore / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.total_sleep_time),
                            fontSize = TextLabel,
                            color = SleepPeriwinkle
                        )
                        Text(
                            text = durationText,
                            fontSize = TextLabel,
                            style = TabularNums,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Capsule 2: Fases del Sueño (Sleep Stages)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeStadium)
                    .background(SleepHeroBrush)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.sleep_stages_title),
                            fontSize = TextBody,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        if (!hasStages) {
                            Text(
                                text = stringResource(R.string.no_data),
                                fontSize = TextCaption,
                                style = TabularNums,
                                color = LabelSilver
                            )
                        }
                    }

                    if (hasStages) {
                        Spacer(modifier = Modifier.height(8.dp))

                        val awakePct = vitals.sleepAwakeMins.toFloat() / totalStagesMins.toFloat()
                        val remPct = vitals.sleepRemMins.toFloat() / totalStagesMins.toFloat()
                        val lightPct = vitals.sleepLightMins.toFloat() / totalStagesMins.toFloat()
                        val deepPct = vitals.sleepDeepMins.toFloat() / totalStagesMins.toFloat()

                        // Stacked multi-color stage bar (Canvas draw animation, zero relayout)
                        SleepStagesBar(
                            awakePct = awakePct,
                            remPct = remPct,
                            lightPct = lightPct,
                            deepPct = deepPct,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(ShapeBar)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Detailed stage breakdowns with real duration and percentages
                        SleepStageRow(stringResource(R.string.stage_awake), formatMins(vitals.sleepAwakeMins), "${(awakePct * 100).roundToInt()}%", StageAwake)
                        SleepStageRow(stringResource(R.string.stage_rem), formatMins(vitals.sleepRemMins), "${(remPct * 100).roundToInt()}%", StageRem)
                        SleepStageRow(stringResource(R.string.stage_light), formatMins(vitals.sleepLightMins), "${(lightPct * 100).roundToInt()}%", StageLight)
                        SleepStageRow(stringResource(R.string.stage_deep), formatMins(vitals.sleepDeepMins), "${(deepPct * 100).roundToInt()}%", StageDeep)
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.no_stages_detected),
                            fontSize = TextLabel,
                            color = LabelSilver,
                            lineHeight = TextBody
                        )
                    }
                }
            }
        }

        // Capsule 3: Biometría Nocturna (Recuperación)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeStadium)
                    .background(NocturnalGradientBrush)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.nocturnal_biometrics),
                        fontSize = TextBody,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        NocturnalMetricBox(stringResource(R.string.blood_oxygen), spo2Val, StageRem)
                        NocturnalMetricBox(stringResource(R.string.hrv_title), hrvVal, StageLight)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        NocturnalMetricBox(stringResource(R.string.resting_hr), minHr, Color(0xFFFF85A1))
                        NocturnalMetricBox(stringResource(R.string.stress), stressVal, Color(0xFF70E000))
                    }
                }
            }
        }

        // Capsule 4: Sincronizar Anillo via Mobile RPC
        item {
            var isSyncing by remember { mutableStateOf(false) }
            val syncScope = rememberCoroutineScope()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeStadium)
                    .background(Color(0xFF1E2638))
                    .clickable {
                        if (!isSyncing) {
                            isSyncing = true
                            syncScope.launch {
                                onSyncClick()
                                delay(4000L)
                                isSyncing = false
                            }
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSyncing) stringResource(R.string.syncing_ring) else stringResource(R.string.sync_ring),
                    fontSize = TextLabel,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSyncing) Color(0xFF70E000) else Color(0xFF8E99F3)
                )
            }
        }
    }
}
}

/**
 * Animated sleep score bar executed solely in the Canvas draw phase.
 */
@Composable
private fun AnimatedSleepScoreBar(scorePct: Float, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(scorePct) {
        anim.animateTo(
            targetValue = scorePct.coerceIn(0f, 1f),
            animationSpec = tween(
                durationMillis = 350,
                easing = CubicBezierEasing(0.22f, 0.25f, 0.0f, 1.0f)
            )
        )
    }
    Canvas(modifier = modifier) {
        val corner = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = SleepTrackBg, size = size, cornerRadius = corner)
        val w = size.width * anim.value
        if (w > 0f) {
            drawRoundRect(
                brush = SleepBarBrush,
                size = Size(w, size.height),
                cornerRadius = corner
            )
        }
    }
}

/**
 * Multi-stage sleep bar drawn in Canvas with zero weight layout recomputations.
 */
@Composable
private fun SleepStagesBar(
    awakePct: Float,
    remPct: Float,
    lightPct: Float,
    deepPct: Float,
    modifier: Modifier = Modifier
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 350,
                easing = CubicBezierEasing(0.22f, 0.25f, 0.0f, 1.0f)
            )
        )
    }
    Canvas(modifier = modifier) {
        val corner = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = SleepTrackBg, size = size, cornerRadius = corner)

        val totalAnimW = size.width * anim.value
        var curX = 0f

        val wAwake = totalAnimW * awakePct
        if (wAwake > 0f) {
            drawRect(color = StageAwake, topLeft = Offset(curX, 0f), size = Size(wAwake, size.height))
            curX += wAwake
        }
        val wRem = totalAnimW * remPct
        if (wRem > 0f) {
            drawRect(color = StageRem, topLeft = Offset(curX, 0f), size = Size(wRem, size.height))
            curX += wRem
        }
        val wLight = totalAnimW * lightPct
        if (wLight > 0f) {
            drawRect(color = StageLight, topLeft = Offset(curX, 0f), size = Size(wLight, size.height))
            curX += wLight
        }
        val wDeep = totalAnimW * deepPct
        if (wDeep > 0f) {
            drawRect(color = StageDeep, topLeft = Offset(curX, 0f), size = Size(wDeep, size.height))
        }
    }
}

@Composable
private fun SleepStageRow(
    name: String,
    duration: String,
    percentage: String,
    dotColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = name,
                fontSize = TextLabel,
                color = Color.White
            )
        }
        Row {
            Text(
                text = duration,
                fontSize = TextLabel,
                style = TabularNums,
                color = SleepPeriwinkle
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = percentage,
                fontSize = TextLabel,
                style = TabularNums,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun NocturnalMetricBox(
    label: String,
    value: String,
    valueColor: Color
) {
    Box(
        modifier = Modifier
            .width(76.dp)
            .clip(ShapeChip)
            .background(GlassBlack50)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Column {
            Text(
                text = label,
                fontSize = TextCaption,
                color = SleepPeriwinkle,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = TextLabel,
                style = TabularNums,
                color = valueColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

private fun formatMins(mins: Int): String {
    if (mins <= 0) return "0m"
    val h = mins / 60
    val m = mins % 60
    return if (h > 0 && m > 0) "${h}h ${m}m" else if (h > 0) "${h}h" else "${m}m"
}

@Preview(
    device = "id:wearos_small_round",
    showSystemUi = true
)
@Composable
fun SleepDetailPreview() {
    SleepDetailContent(
        vitals = RingConnVitals(
            sleepScore = 86,
            sleepDeepMins = 85,
            sleepRemMins = 110,
            sleepLightMins = 230,
            sleepAwakeMins = 25,
            lastHeartRate = 56,
            lastSpo2 = 98,
            isConnected = true
        )
    )
}

