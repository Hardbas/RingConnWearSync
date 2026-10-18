package io.github.hardbas.wearsync.app.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
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
import io.github.hardbas.wearsync.app.data.RingConnHistoryStore
import io.github.hardbas.wearsync.app.data.RingConnVitals
import io.github.hardbas.wearsync.app.service.WearDataListenerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    val animatedSleepScore by animateIntAsState(
        targetValue = if (hasSleepData) sleepScore else 0,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "sleepScoreAnim"
    )
    val sleepScoreText = if (hasSleepData) "$animatedSleepScore" else "--"
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

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var selectedStageIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(selectedStageIndex) {
        if (selectedStageIndex != null) {
            delay(4000L)
            selectedStageIndex = null
        }
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
            scalingParams = ScalingLazyColumnDefaults.scalingParams(
                edgeScale = 0.65f,
                edgeAlpha = 0.40f,
                minTransitionArea = 0.20f,
                maxTransitionArea = 0.80f
            ),
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
            // b) Hero Dial: Concentric Sleep Score Gauge with Biometric Reactive Aura and 3D depth
            // =========================================================================
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(134.dp)
                            .graphicsLayer {
                                cameraDistance = 12f * density.density
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        BiometricHeroAura(
                            color = StageDeep,
                            modifier = Modifier.size(134.dp)
                        )
                        SleepDialGauge(
                            score = if (hasSleepData) sleepScore else 0,
                            modifier = Modifier.size(122.dp)
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

                            val awakeName = stringResource(R.string.stage_awake)
                            val remName = stringResource(R.string.stage_rem)
                            val lightName = stringResource(R.string.stage_light)
                            val deepName = stringResource(R.string.stage_deep)

                            val stages = remember(vitals, totalStagesMins, awakeName, remName, lightName, deepName) {
                                listOf(
                                    SleepStageSegment(0, awakeName, vitals.sleepAwakeMins, awakePct, StageAwake),
                                    SleepStageSegment(1, remName, vitals.sleepRemMins, remPct, StageRem),
                                    SleepStageSegment(2, lightName, vitals.sleepLightMins, lightPct, StageLight),
                                    SleepStageSegment(3, deepName, vitals.sleepDeepMins, deepPct, StageDeep)
                                )
                            }

                            val resolveStageIndex: (Float, Float) -> Int? = { touchX, width ->
                                if (width <= 0f) null
                                else {
                                    val fraction = (touchX / width).coerceIn(0f, 0.999f)
                                    var cumulative = 0f
                                    var resolved: Int? = null
                                    for (stage in stages) {
                                        if (stage.pct <= 0f) continue
                                        if (fraction < cumulative + stage.pct) {
                                            resolved = stage.index
                                            break
                                        }
                                        cumulative += stage.pct
                                    }
                                    resolved ?: stages.lastOrNull { it.pct > 0f }?.index
                                }
                            }

                            val selectedStage = stages.find { it.index == selectedStageIndex }

                            // Crisp inline pill badge showing selected stage name, duration, and percentage
                            if (selectedStage != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(ShapePill)
                                        .background(selectedStage.color.copy(alpha = 0.22f))
                                        .border(BorderStroke(1.dp, selectedStage.color.copy(alpha = 0.65f)), ShapePill)
                                        .padding(horizontal = 10.dp, vertical = 3.5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(selectedStage.color)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = selectedStage.name.uppercase(),
                                            fontSize = TextCaption,
                                            fontWeight = FontWeight.Bold,
                                            color = selectedStage.color
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "·  ${formatMins(selectedStage.durationMins)} (${(selectedStage.pct * 100).roundToInt()}%)",
                                            fontSize = TextCaption,
                                            style = TabularNums,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            // Interactive multi-stage scrubber bar with haptics
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(stages) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            val hit = resolveStageIndex(down.position.x, size.width.toFloat())
                                            if (hit != null && hit != selectedStageIndex) {
                                                selectedStageIndex = hit
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            val pointerId = down.id
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                                if (!change.pressed) break
                                                val dragHit = resolveStageIndex(change.position.x, size.width.toFloat())
                                                if (dragHit != null && dragHit != selectedStageIndex) {
                                                    selectedStageIndex = dragHit
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                                change.consume()
                                            }
                                        }
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                SleepStagesBar(
                                    awakePct = awakePct,
                                    remPct = remPct,
                                    lightPct = lightPct,
                                    deepPct = deepPct,
                                    selectedStageIndex = selectedStageIndex,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(ShapeBar)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Detailed stage breakdowns with interactive row tap selection
                            SleepStageRow(
                                name = awakeName,
                                duration = formatMins(vitals.sleepAwakeMins),
                                percentage = "${(awakePct * 100).roundToInt()}%",
                                dotColor = StageAwake,
                                isSelected = (selectedStageIndex == 0),
                                onClick = {
                                    selectedStageIndex = if (selectedStageIndex == 0) null else 0
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            )
                            SleepStageRow(
                                name = remName,
                                duration = formatMins(vitals.sleepRemMins),
                                percentage = "${(remPct * 100).roundToInt()}%",
                                dotColor = StageRem,
                                isSelected = (selectedStageIndex == 1),
                                onClick = {
                                    selectedStageIndex = if (selectedStageIndex == 1) null else 1
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            )
                            SleepStageRow(
                                name = lightName,
                                duration = formatMins(vitals.sleepLightMins),
                                percentage = "${(lightPct * 100).roundToInt()}%",
                                dotColor = StageLight,
                                isSelected = (selectedStageIndex == 2),
                                onClick = {
                                    selectedStageIndex = if (selectedStageIndex == 2) null else 2
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            )
                            SleepStageRow(
                                name = deepName,
                                duration = formatMins(vitals.sleepDeepMins),
                                percentage = "${(deepPct * 100).roundToInt()}%",
                                dotColor = StageDeep,
                                isSelected = (selectedStageIndex == 3),
                                onClick = {
                                    selectedStageIndex = if (selectedStageIndex == 3) null else 3
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            )
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
            // e) Card: RingConn Coaching & Insights
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
                                    .background(SleepPeriwinkle)
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
                            !hasSleepData -> stringResource(R.string.sleep_insight_no_data)
                            sleepScore >= 85 -> stringResource(R.string.sleep_insight_optimal)
                            sleepScore >= 70 -> stringResource(R.string.sleep_insight_good)
                            else -> stringResource(R.string.sleep_insight_fair)
                        }
                        Text(
                            text = if (vitals.sleepInsight.isNotBlank()) vitals.sleepInsight else computedInsight,
                            fontSize = TextCaption,
                            color = Color(0xFFCFD8DC),
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // =========================================================================
            // f) Card: 7-Day Trend Sparkline for Sleep Score
            // =========================================================================
            item {
                RingConnHistoryStore.SevenDayTrendCard(
                    metricTitle = stringResource(R.string.sleep_score),
                    unit = stringResource(R.string.pts_unit),
                    accentColor = StageDeep,
                    selector = { it.sleepScore.toFloat() },
                    currentFallback = sleepScore.toFloat()
                )
            }

            // =========================================================================
            // g) Card: Sincronizar Anillo via Mobile RPC
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
                                    val startSync = WearDataListenerService.lastSyncCompletedMs.value
                                    onSyncClick()
                                    withTimeoutOrNull(8500L) {
                                        WearDataListenerService.lastSyncCompletedMs.first { it != startSync }
                                    }
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
 * Hoists Brush and Stroke outside Canvas to prevent per-frame allocations during animation.
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

    val density = LocalDensity.current
    val strokeWidthPx = remember(density) { with(density) { 7.5.dp.toPx() } }
    val arcStroke = remember(strokeWidthPx) {
        Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    }
    val gradientBrush = remember {
        Brush.sweepGradient(
            listOf(
                Color(0xFF5E72E4),
                Color(0xFF8E99F3),
                Color(0xFF47CDFF),
                Color(0xFF5E72E4)
            )
        )
    }

    Canvas(modifier = modifier) {
        val diameter = size.minDimension - strokeWidthPx
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
 * Multi-stage sleep bar drawn in Canvas with zero weight layout recomputations.
 * Features sequential reveal animation with subtle glowing divider ticks and
 * segment highlight when inspected.
 */
@Composable
private fun SleepStagesBar(
    awakePct: Float,
    remPct: Float,
    lightPct: Float,
    deepPct: Float,
    selectedStageIndex: Int? = null,
    modifier: Modifier = Modifier
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(deepPct, remPct, lightPct, awakePct) {
        anim.snapTo(0f)
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 650,
                easing = CubicBezierEasing(0.22f, 0.25f, 0.0f, 1.0f)
            )
        )
    }

    val density = LocalDensity.current
    val tickWidthPx = remember(density) { with(density) { 1.5.dp.toPx() } }
    val dividerColor = remember { Color(0x80FFFFFF) }

    Canvas(modifier = modifier) {
        val corner = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = SleepTrackBg, size = size, cornerRadius = corner)

        val totalAnimW = size.width * anim.value
        var curX = 0f

        val stagesWithIndex = listOf(
            Triple(0, StageAwake, awakePct),
            Triple(1, StageRem, remPct),
            Triple(2, StageLight, lightPct),
            Triple(3, StageDeep, deepPct)
        )

        val isAnySelected = selectedStageIndex != null

        for (i in stagesWithIndex.indices) {
            val (stageIdx, color, pct) = stagesWithIndex[i]
            val w = totalAnimW * pct
            if (w > 0f) {
                val isSelected = (selectedStageIndex == stageIdx)
                val alpha = if (!isAnySelected || isSelected) 1.0f else 0.35f

                drawRect(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset(curX, 0f),
                    size = Size(w, size.height)
                )

                if (isSelected) {
                    drawRect(
                        color = Color.White.copy(alpha = 0.32f),
                        topLeft = Offset(curX, 0f),
                        size = Size(w, size.height)
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(curX, 0f),
                        end = Offset(curX + w, 0f),
                        strokeWidth = 2.dp.toPx()
                    )
                }

                curX += w

                // Subtle glowing divider tick between stages if reached by animation sweep
                if (i < stagesWithIndex.size - 1 && curX < totalAnimW && curX > 0f) {
                    drawLine(
                        color = dividerColor,
                        start = Offset(curX, 0f),
                        end = Offset(curX, size.height),
                        strokeWidth = tickWidthPx
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepStageRow(
    name: String,
    duration: String,
    percentage: String,
    dotColor: Color,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeChip)
            .then(
                if (isSelected) Modifier.background(dotColor.copy(alpha = 0.20f))
                else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = if (isSelected) 8.dp else 2.dp, vertical = 2.5.dp),
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
                color = if (isSelected) dotColor else Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
        Row {
            Text(
                text = duration,
                fontSize = TextLabel,
                style = TabularNums,
                color = if (isSelected) Color.White else SleepPeriwinkle
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = percentage,
                fontSize = TextLabel,
                style = TabularNums,
                color = if (isSelected) dotColor else Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Isolated breathing radial aura rendered behind the hero sleep dial.
 * Evaluated in Modifier.graphicsLayer draw phase strictly gated on Lifecycle.State.RESUMED
 * for 0 recompositions and 0 AOD battery drain.
 */
@Composable
private fun BiometricHeroAura(
    color: Color,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val isResumed = lifecycleState == Lifecycle.State.RESUMED

    val infiniteTransition = rememberInfiniteTransition(label = "sleepHeroAura")
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sleepAuraScale"
    )
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sleepAuraAlpha"
    )

    val auraBrush = remember(color) {
        Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.50f),
                color.copy(alpha = 0.16f),
                Color.Transparent
            )
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                val scale = if (isResumed) auraScale else 1.0f
                val alpha = if (isResumed) auraAlpha else 0.12f
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .background(auraBrush, CircleShape)
    )
}

private data class SleepStageSegment(
    val index: Int,
    val name: String,
    val durationMins: Int,
    val pct: Float,
    val color: Color
)

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
