package io.github.hardbas.wearsync.app.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Dark Obsidian Sleep Stage Accent Palette
private val StageAwake = Color(0xFFFF6B6B)     // Despierto
private val StageRem = Color(0xFF47CDFF)       // REM
private val StageLight = Color(0xFFA5AEDE)     // Ligero
private val StageDeep = Color(0xFF5E72E4)      // Profundo
private val SleepTrackBg = Color(0x33000000)

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
    val qualityColor = when {
        !hasSleepData -> LabelSilver
        sleepScore >= 85 -> LimeSteps
        sleepScore >= 70 -> StageRem
        else -> Color(0xFFFFD600)
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
                        text = stringResource(R.string.sleep_analysis_title),
                        fontSize = TextBody,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.ring_model_subtitle),
                        fontSize = TextCaption,
                        color = SleepPeriwinkle,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // =========================================================================
            // b) Hero Dial: 122dp Concentric Sleep Score Gauge & Duration Badge
            // =========================================================================
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier.size(122.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SleepDialGauge(
                            score = if (hasSleepData) sleepScore else 0,
                            modifier = Modifier.fillMaxSize()
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = sleepScoreText,
                                fontSize = TextHero,
                                style = TabularNums,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.pts_unit).uppercase(),
                                fontSize = TextCaption,
                                color = SleepPeriwinkle,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quality Badge Chip
                    Box(
                        modifier = Modifier
                            .clip(ShapePill)
                            .background(qualityColor.copy(alpha = 0.18f))
                            .padding(horizontal = 14.dp, vertical = 3.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = qualityBadge.uppercase(),
                            fontSize = TextCaption,
                            fontWeight = FontWeight.Bold,
                            color = qualityColor,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Total sleep duration pill
                    Box(
                        modifier = Modifier
                            .clip(ShapePill)
                            .background(Color(0x221E2638))
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_vitals_sleep),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.total_sleep_time) + ": ",
                                fontSize = TextCaption,
                                color = SleepPeriwinkle
                            )
                            Text(
                                text = durationText,
                                fontSize = TextCaption,
                                style = TabularNums,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // c) Card 1: Fases del Sueño (Sleep Stages)
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
                                        .background(StageDeep)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.sleep_stages_title),
                                    fontSize = TextBody,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
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

            // =========================================================================
            // d) Card 2: Biometría Nocturna (Nocturnal Biometrics)
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
                                    .background(StageRem)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.nocturnal_biometrics),
                                fontSize = TextBody,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NocturnalMetricBox(
                                label = stringResource(R.string.blood_oxygen_short),
                                value = spo2Val,
                                valueColor = StageRem,
                                modifier = Modifier.weight(1f)
                            )
                            NocturnalMetricBox(
                                label = stringResource(R.string.hrv_short),
                                value = hrvVal,
                                valueColor = StageLight,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NocturnalMetricBox(
                                label = stringResource(R.string.resting_hr),
                                value = minHr,
                                valueColor = Color(0xFFFF85A1),
                                modifier = Modifier.weight(1f)
                            )
                            NocturnalMetricBox(
                                label = stringResource(R.string.stress),
                                value = stressVal,
                                valueColor = Color(0xFF70E000),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // e) Card 3: Sincronizar Anillo via Mobile RPC
            // =========================================================================
            item {
                var isSyncing by remember { mutableStateOf(false) }
                val syncScope = rememberCoroutineScope()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapePill)
                        .background(Color(0x2BFFFFFF))
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
                        color = if (isSyncing) LimeSteps else Color.White
                    )
                }
            }
        }
    }
}

/**
 * 270-degree animated circular gauge for Sleep Score in One UI Watch style.
 */
@Composable
private fun SleepDialGauge(
    score: Int,
    modifier: Modifier = Modifier
) {
    val animProgress = remember { Animatable(0f) }
    val targetProgress = (score.coerceIn(0, 100) / 100f)

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
            color = Color(0x225E72E4),
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
                        Color(0xFF5E72E4),
                        Color(0xFF8E99F3),
                        Color(0xFF47CDFF),
                        Color(0xFF5E72E4)
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
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(ShapeChip)
            .background(CardBgSubtle)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                fontSize = TextCaption,
                color = LabelSilver,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = TextBody,
                style = TabularNums,
                color = valueColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center
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
