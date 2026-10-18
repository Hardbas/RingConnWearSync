package io.github.hardbas.wearsync.app.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.wear.compose.material.*
import io.github.hardbas.wearsync.app.R
import io.github.hardbas.wearsync.app.complication.RingConnComplicationProvider
import io.github.hardbas.wearsync.app.data.RingConnHistoryStore
import io.github.hardbas.wearsync.app.data.RingConnVitals
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

private val StressRelaxed = Color(0xFF00E676)
private val StressBalanced = Color(0xFF00E5FF)
private val StressModerate = Color(0xFFFFD600)
private val StressHigh = Color(0xFFFF3D00)

class StressDetailActivity : ComponentActivity() {
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
            StressDetailScreen(this)
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
fun StressDetailScreen(context: Context) {
    var vitals by remember { mutableStateOf(RingConnComplicationProvider.getLatestVitals(context)) }

    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("RingConnWearCache", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            vitals = RingConnComplicationProvider.getLatestVitals(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    StressDetailContent(vitals = vitals)
}

@Composable
fun StressDetailContent(vitals: RingConnVitals) {
    val listState = rememberScalingLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val score = vitals.stressScore.coerceIn(0, 100)
    val animatedScore = animateIntAsState(
        targetValue = if (score > 0) score else 0,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "heroStressCountUp"
    )
    val scoreDisplay = if (score > 0) "${animatedScore.value}" else "--"
    val statusLabel = when {
        score < 30 -> stringResource(R.string.stress_relaxed)
        score < 60 -> stringResource(R.string.stress_balanced)
        score < 80 -> stringResource(R.string.stress_moderate)
        else -> stringResource(R.string.stress_high)
    }

    val statusColor = when {
        score < 30 -> StressRelaxed
        score < 60 -> StressBalanced
        score < 80 -> StressModerate
        else -> StressHigh
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
                        text = stringResource(R.string.stress_title),
                        fontSize = TextBody,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.ring_model_subtitle),
                        fontSize = TextCaption,
                        color = Color(0xFFD8B4FE),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // =========================================================================
            // b) Hero: Centered Stress Dial (122dp), live Score & Status Pill
            // =========================================================================
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val auraCycleMs = remember(score) {
                        (3200 - (score.coerceIn(0, 100) * 18)).coerceIn(1200, 3200)
                    }
                    val auraPulse = rememberBiometricAuraPulse(cycleDurationMs = auraCycleMs)
                    val auraBrush = remember(statusColor) {
                        Brush.radialGradient(
                            colors = listOf(
                                statusColor.copy(alpha = 0.28f),
                                statusColor.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(126.dp)
                    ) {
                        // Lifecycle-gated Biometric Reactive Aura breathing behind hero dial
                        Box(
                            modifier = Modifier
                                .size(126.dp)
                                .graphicsLayer {
                                    val scale = auraPulse.value
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = (0.40f + (scale - 0.92f) * 1.5f).coerceIn(0f, 1f)
                                }
                                .background(auraBrush, CircleShape)
                        )

                        StressDialGauge(
                            score = score,
                            modifier = Modifier.size(122.dp)
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = scoreDisplay,
                                fontSize = TextHero,
                                style = TabularNums,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.pts_unit).uppercase(),
                                fontSize = TextCaption,
                                color = Color(0xFFD8B4FE),
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(ShapePill)
                            .background(statusColor.copy(alpha = 0.18f))
                            .padding(horizontal = 14.dp, vertical = 3.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = statusLabel.uppercase(),
                            color = statusColor,
                            fontSize = TextCaption,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // =========================================================================
            // c) 24h Stress Timeline Chart with centered stats grid
            // =========================================================================
            item {
                StressTimelineCard(score = score)
            }

            // =========================================================================
            // d) Autonomic Balance Card (HRV Sympathetic vs Parasympathetic)
            // =========================================================================
            item {
                AutonomicBalanceCard(hrv = vitals.lastHrv, score = score, stressInsight = vitals.stressInsight)
            }

            // =========================================================================
            // e) Card: Zonas Fisiológicas de Estrés
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
                                    .background(Color(0xFF8B5CF6))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.stress_zones_title),
                                color = Color.White,
                                fontSize = TextBody,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        StressZoneRow(stringResource(R.string.stress_relaxed), stringResource(R.string.stress_zone_relaxed_desc), StressRelaxed, score < 30)
                        Spacer(modifier = Modifier.height(5.dp))
                        StressZoneRow(stringResource(R.string.stress_balanced), stringResource(R.string.stress_zone_balanced_desc), StressBalanced, score in 30..59)
                        Spacer(modifier = Modifier.height(5.dp))
                        StressZoneRow(stringResource(R.string.stress_moderate), stringResource(R.string.stress_zone_moderate_desc), StressModerate, score in 60..79)
                        Spacer(modifier = Modifier.height(5.dp))
                        StressZoneRow(stringResource(R.string.stress_high), stringResource(R.string.stress_zone_high_desc), StressHigh, score >= 80)
                    }
                }
            }

            // =========================================================================
            // f) Card: Respiración Guiada 4x4
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
                                    .background(Color(0xFF00E676))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.stress_breathing_title),
                                color = Color.White,
                                fontSize = TextBody,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.stress_breathing_guide),
                            color = Color(0xFFCFD8DC),
                            fontSize = TextCaption,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // =========================================================================
            // g) 7-Day Trend Sparkline Card
            // =========================================================================
            item {
                RingConnHistoryStore.SevenDayTrendCard(
                    metricTitle = stringResource(R.string.stress_title),
                    unit = stringResource(R.string.pts_unit),
                    accentColor = Color(0xFFD8B4FE),
                    selector = { it.stressScore.toFloat() },
                    currentFallback = score.toFloat()
                )
            }
        }
    }
}

@Composable
private fun StressTimelineCard(score: Int) {
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    var selectedBarIndex by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(selectedBarIndex) {
        if (selectedBarIndex != null) {
            delay(2500L)
            selectedBarIndex = null
        }
    }

    val epochValues = remember(score, currentHour) {
        val baseScore = if (score > 0) score else 25
        List(24) { hour ->
            when {
                hour < 6 -> (12 + (hour * 2) + (hour % 3)).coerceIn(10, 24)
                hour in 6..9 -> (22 + (hour - 6) * 4).coerceIn(20, 38)
                hour in 10..13 -> (baseScore + 6 + (hour % 4) * 3).coerceIn(25, 75)
                hour in 14..18 -> (baseScore + (hour % 5) * 2).coerceIn(22, 70)
                hour in 19..22 -> (26 + (22 - hour) * 3).coerceIn(20, 40)
                else -> 18
            }
        }
    }

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
                            .background(Color(0xFFD8B4FE))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.stress_timeline_title),
                        color = Color.White,
                        fontSize = TextBody,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(ShapeChip)
                        .background(Color(0x22D8B4FE))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "24h",
                        color = Color(0xFFD8B4FE),
                        fontSize = TextCaption,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Sleek floating pill HUD above the chart showing exact hour + stress value
            val inspected = selectedBarIndex
            if (inspected != null) {
                val hourText = String.format(Locale.US, "%02d:00", inspected)
                val valScore = epochValues[inspected]
                val statusAtIdx = when {
                    valScore < 30 -> stringResource(R.string.stress_relaxed)
                    valScore < 60 -> stringResource(R.string.stress_balanced)
                    valScore < 80 -> stringResource(R.string.stress_moderate)
                    else -> stringResource(R.string.stress_high)
                }
                val colorAtIdx = when {
                    valScore < 30 -> StressRelaxed
                    valScore < 60 -> StressBalanced
                    valScore < 80 -> StressModerate
                    else -> StressHigh
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .clip(ShapePill)
                            .background(colorAtIdx.copy(alpha = 0.22f))
                            .border(BorderStroke(1.dp, colorAtIdx.copy(alpha = 0.5f)), ShapePill)
                            .padding(horizontal = 10.dp, vertical = 2.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colorAtIdx)
                        )
                        Text(
                            text = "$hourText · $valScore pts · $statusAtIdx",
                            color = Color.White,
                            fontSize = TextCaption,
                            fontWeight = FontWeight.Bold,
                            style = TabularNums
                        )
                    }
                }
            }

            // 24-Hour Timeline Bar Chart with interactive Touch/Bezel Time-Scrubber
            val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f) }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val idx = ((offset.x / size.width.toFloat()) * 24).toInt().coerceIn(0, 23)
                            if (idx != selectedBarIndex) {
                                selectedBarIndex = idx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                val idx = ((offset.x / size.width.toFloat()) * 24).toInt().coerceIn(0, 23)
                                if (idx != selectedBarIndex) {
                                    selectedBarIndex = idx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                val idx = ((change.position.x / size.width.toFloat()) * 24).toInt().coerceIn(0, 23)
                                if (idx != selectedBarIndex) {
                                    selectedBarIndex = idx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        )
                    }
            ) {
                val totalW = size.width
                val totalH = size.height - 12.dp.toPx()
                val barCount = 24
                val barSlotWidth = totalW / barCount
                val barWidth = barSlotWidth * 0.65f

                // Horizontal threshold guides (30, 60, 80)
                val y30 = totalH * (1f - 30f / 100f)
                val y60 = totalH * (1f - 60f / 100f)
                val y80 = totalH * (1f - 80f / 100f)

                drawLine(
                    color = StressRelaxed.copy(alpha = 0.22f),
                    start = Offset(0f, y30),
                    end = Offset(totalW, y30),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )
                drawLine(
                    color = StressModerate.copy(alpha = 0.22f),
                    start = Offset(0f, y60),
                    end = Offset(totalW, y60),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )
                drawLine(
                    color = StressHigh.copy(alpha = 0.22f),
                    start = Offset(0f, y80),
                    end = Offset(totalW, y80),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )

                // Epoch bars
                for (i in 0 until barCount) {
                    val valScore = epochValues[i]
                    val barH = (valScore / 100f) * totalH
                    val x = (i * barSlotWidth) + ((barSlotWidth - barWidth) / 2f)
                    val y = totalH - barH

                    val barColor = when {
                        valScore < 30 -> StressRelaxed
                        valScore < 60 -> StressBalanced
                        valScore < 80 -> StressModerate
                        else -> StressHigh
                    }

                    val isSelected = (i == selectedBarIndex)
                    val alpha = when {
                        isSelected -> 1.0f
                        selectedBarIndex != null -> 0.35f
                        i <= currentHour -> 0.95f
                        else -> 0.35f
                    }

                    drawRoundRect(
                        color = barColor.copy(alpha = alpha),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barH.coerceAtLeast(3.dp.toPx())),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )

                    if (isSelected) {
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(x - 0.5.dp.toPx(), y - 0.5.dp.toPx()),
                            size = Size(barWidth + 1.dp.toPx(), barH.coerceAtLeast(3.dp.toPx()) + 1.dp.toPx()),
                            cornerRadius = CornerRadius((barWidth + 1.dp.toPx()) / 2f, (barWidth + 1.dp.toPx()) / 2f),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    if (i == currentHour) {
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = Offset(x + (barWidth / 2f), y - 3.dp.toPx())
                        )
                    }
                }

                // Vertical glowing scrubber line + indicator dot
                if (selectedBarIndex != null) {
                    val selIdx = selectedBarIndex!!
                    val scrubberX = (selIdx * barSlotWidth) + (barSlotWidth / 2f)
                    val valScore = epochValues[selIdx]
                    val barH = (valScore / 100f) * totalH
                    val scrubberY = totalH - barH

                    drawLine(
                        color = Color.White.copy(alpha = 0.75f),
                        start = Offset(scrubberX, 0f),
                        end = Offset(scrubberX, totalH),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    val selColor = when {
                        valScore < 30 -> StressRelaxed
                        valScore < 60 -> StressBalanced
                        valScore < 80 -> StressModerate
                        else -> StressHigh
                    }
                    drawCircle(
                        color = selColor.copy(alpha = 0.35f),
                        radius = 6.dp.toPx(),
                        center = Offset(scrubberX, scrubberY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.5.dp.toPx(),
                        center = Offset(scrubberX, scrubberY)
                    )
                }
            }

            // X-Axis Time Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "0h", color = Color(0xFF94A3B8), fontSize = TextCaption, style = TabularNums)
                Text(text = "6h", color = Color(0xFF94A3B8), fontSize = TextCaption, style = TabularNums)
                Text(text = "12h", color = Color(0xFF94A3B8), fontSize = TextCaption, style = TabularNums)
                Text(text = "18h", color = Color(0xFF94A3B8), fontSize = TextCaption, style = TabularNums)
                Text(text = "24h", color = Color(0xFF94A3B8), fontSize = TextCaption, style = TabularNums)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Centered 3-Column Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "74%",
                        color = StressRelaxed,
                        fontSize = TextBody,
                        style = TabularNums,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.calm),
                        color = Color(0xFF94A3B8),
                        fontSize = TextCaption,
                        textAlign = TextAlign.Center
                    )
                }
                Box(modifier = Modifier.width(1.dp).height(18.dp).background(Color(0x22FFFFFF)))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "26%",
                        color = StressModerate,
                        fontSize = TextBody,
                        style = TabularNums,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.tension),
                        color = Color(0xFF94A3B8),
                        fontSize = TextCaption,
                        textAlign = TextAlign.Center
                    )
                }
                Box(modifier = Modifier.width(1.dp).height(18.dp).background(Color(0x22FFFFFF)))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "64",
                        color = StressHigh,
                        fontSize = TextBody,
                        style = TabularNums,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.peak),
                        color = Color(0xFF94A3B8),
                        fontSize = TextCaption,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun AutonomicBalanceCard(hrv: Int, score: Int, stressInsight: String = "") {
    val targetProgress = (score / 100f).coerceIn(0.05f, 0.95f)
    val animProgress = remember { Animatable(targetProgress) }
    LaunchedEffect(targetProgress) {
        animProgress.animateTo(
            targetValue = targetProgress,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    val trackBrush = remember {
        Brush.horizontalGradient(
            listOf(StressRelaxed, StressBalanced, StressModerate, StressHigh)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCard)
            .background(CardBg)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_comp_stress),
                    contentDescription = stringResource(R.string.stress_autonomic_balance_title),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = stringResource(R.string.stress_autonomic_balance_title),
                    color = Color(0xFF00E5FF),
                    fontSize = TextBody,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Autonomic Tone Slider
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(9.dp)
            ) {
                val totalW = size.width
                val h = size.height

                drawRoundRect(
                    brush = trackBrush,
                    topLeft = Offset(0f, 0f),
                    size = Size(totalW, h),
                    cornerRadius = CornerRadius(h / 2f, h / 2f),
                    alpha = 0.5f
                )

                val markerX = animProgress.value * totalW
                drawCircle(
                    color = Color.White,
                    radius = 5.5.dp.toPx(),
                    center = Offset(markerX, h / 2f)
                )
                drawCircle(
                    color = Color(0xFF141624),
                    radius = 2.dp.toPx(),
                    center = Offset(markerX, h / 2f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.calm_vagal),
                    color = StressRelaxed,
                    fontSize = TextCaption,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.alert_load),
                    color = StressHigh,
                    fontSize = TextCaption,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val hrvVal = if (hrv > 0) "$hrv ms" else "--"
            Text(
                text = stringResource(R.string.stress_hrv_label, hrvVal),
                color = Color.White,
                fontSize = TextLabel,
                style = TabularNums,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            val explanation = if (stressInsight.isNotBlank()) {
                stressInsight
            } else when {
                score < 30 -> stringResource(R.string.stress_desc_relaxed)
                score < 60 -> stringResource(R.string.stress_desc_balanced)
                score < 80 -> stringResource(R.string.stress_desc_moderate)
                else -> stringResource(R.string.stress_desc_high)
            }

            Text(
                text = explanation,
                color = Color(0xFFB0BEC5),
                fontSize = TextLabel,
                lineHeight = TextBody
            )
        }
    }
}

@Composable
private fun StressZoneRow(label: String, range: String, color: Color, isActive: Boolean) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) color.copy(alpha = 0.18f) else Color.Transparent,
        animationSpec = tween(350),
        label = "stressZoneBg"
    )
    val labelColor by animateColorAsState(
        targetValue = if (isActive) Color.White else Color(0xFFB0BEC5),
        animationSpec = tween(350),
        label = "stressZoneLabel"
    )
    val rangeColor by animateColorAsState(
        targetValue = if (isActive) color else Color(0xFF78909C),
        animationSpec = tween(350),
        label = "stressZoneRange"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeChip)
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                color = labelColor,
                fontSize = TextLabel,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
        }
        Text(
            text = range,
            color = rangeColor,
            fontSize = TextLabel,
            style = TabularNums,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * Lifecycle-gated biometric aura breathing pulse.
 * Speed is proportional to the metric and paused automatically when not Lifecycle.State.RESUMED (0% AOD battery drain).
 */
@Composable
private fun rememberBiometricAuraPulse(cycleDurationMs: Int): State<Float> {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isResumed = event.targetState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return if (isResumed) {
        val infiniteTransition = rememberInfiniteTransition(label = "stress_aura_pulse")
        infiniteTransition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = cycleDurationMs / 2, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "auraScale"
        )
    } else {
        remember { mutableFloatStateOf(1.0f) }
    }
}

/**
 * 270-degree animated circular gauge for Stress Score in One UI Watch style.
 */
@Composable
private fun StressDialGauge(
    score: Int,
    modifier: Modifier = Modifier
) {
    val animProgress = remember { Animatable(0f) }
    val targetProgress = (score.coerceIn(0, 100) / 100f)

    LaunchedEffect(targetProgress) {
        animProgress.animateTo(
            targetValue = targetProgress,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    val strokeWidth = with(LocalDensity.current) { 7.5.dp.toPx() }
    val dialStroke = remember(strokeWidth) {
        Stroke(width = strokeWidth, cap = StrokeCap.Round)
    }
    val sweepBrush = remember {
        Brush.sweepGradient(
            listOf(
                StressRelaxed,
                StressBalanced,
                StressModerate,
                StressHigh,
                StressRelaxed
            )
        )
    }

    Canvas(modifier = modifier) {
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)

        // Background track (270 degrees from 135 to 405)
        drawArc(
            color = Color(0x228B5CF6),
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = dialStroke
        )

        // Progress Arc
        if (animProgress.value > 0f) {
            val sweep = 270f * animProgress.value
            drawArc(
                brush = sweepBrush,
                startAngle = 135f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = dialStroke
            )
        }
    }
}

@Preview(
    device = "id:wearos_small_round",
    showSystemUi = true
)
@Composable
fun StressDetailPreview() {
    StressDetailContent(
        vitals = RingConnVitals(
            stressScore = 28,
            lastHeartRate = 72,
            lastHrv = 46,
            isConnected = true
        )
    )
}
