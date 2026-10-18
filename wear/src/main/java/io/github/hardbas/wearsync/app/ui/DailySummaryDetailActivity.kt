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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.*
import io.github.hardbas.wearsync.app.R
import io.github.hardbas.wearsync.app.complication.RingConnComplicationProvider
import io.github.hardbas.wearsync.app.data.RingConnVitals
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val DailyActivityBrush = Brush.verticalGradient(listOf(ActivityGradientStart, ActivityGradientEnd))

class DailySummaryDetailActivity : ComponentActivity() {
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
            DailySummaryDetailScreen(this)
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
fun DailySummaryDetailScreen(context: Context) {
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

    DailySummaryDetailContent(vitals = vitals)
}

@Composable
fun DailySummaryDetailContent(vitals: RingConnVitals) {
    val listState = rememberScalingLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val liveSteps = vitals.liveSteps
    val targetSteps = if (vitals.targetSteps > 0) vitals.targetSteps else 8000
    // Real active minutes from RingConn (cadence fallback when duration collapsed)
    val activeMins = if (vitals.activeMinutes > 0) vitals.activeMinutes else if (vitals.liveSteps > 0) (vitals.liveSteps / 100).coerceAtLeast(1) else 0
    val targetActiveMins = 60
    val calories = vitals.liveCalorie
    val targetCalories = if (vitals.targetCalorie > 0) vitals.targetCalorie else 500
    val activityScore = vitals.activityScore

    val animatedActivityScore by animateIntAsState(
        targetValue = activityScore,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "activityScoreAnim"
    )

    val stepsFormatted = remember(liveSteps) {
        NumberFormat.getIntegerInstance(Locale.getDefault()).format(liveSteps)
    }

    val stepsProgress = (liveSteps.toFloat() / targetSteps).coerceIn(0f, 1.2f)
    val timeProgress = (activeMins.toFloat() / targetActiveMins).coerceIn(0f, 1.2f)
    val calProgress = (calories.toFloat() / targetCalories).coerceIn(0f, 1.2f)

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
            // Header: Clean title with circular clearance
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.daily_activity_title),
                        fontSize = TextBody,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.ring_model_subtitle),
                        fontSize = TextCaption,
                        color = LabelSilver,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 1. Hero: Center Concentric Rings Dial (120dp) with score in center and status pill
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(122.dp)
                    ) {
                        ConcentricActivityRings(
                            stepsProgress = stepsProgress,
                            timeProgress = timeProgress,
                            calProgress = calProgress,
                            modifier = Modifier.fillMaxSize()
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$animatedActivityScore",
                                fontSize = TextHero,
                                style = TabularNums,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.pts_unit).uppercase(),
                                fontSize = TextCaption,
                                color = LabelSilver,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val statusLabel = when {
                        activityScore >= 85 -> stringResource(R.string.excellent)
                        activityScore >= 70 -> stringResource(R.string.status_good_pace)
                        else -> stringResource(R.string.status_in_progress)
                    }
                    val statusColor = when {
                        activityScore >= 85 -> LimeSteps
                        activityScore >= 70 -> CyanActive
                        else -> Color(0xFFFFD600)
                    }
                    Box(
                        modifier = Modifier
                            .clip(ShapePill)
                            .background(statusColor.copy(alpha = 0.26f))
                            .padding(horizontal = 14.dp, vertical = 3.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = statusLabel.uppercase(),
                            fontSize = TextCaption,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 2. Card: Pasos Diarios (Neon Lime)
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
                                        .background(LimeSteps)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.steps),
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
                                    .background(LimeSteps.copy(alpha = 0.26f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${(stepsProgress * 100).toInt()}%",
                                    fontSize = TextCaption,
                                    style = TabularNums,
                                    color = LimeSteps,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = stepsFormatted,
                                fontSize = TextBig,
                                style = TabularNums,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "/ 8,000",
                                fontSize = TextLabel,
                                style = TabularNums,
                                color = LabelSilver,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ActivityProgressBar(progress = stepsProgress, color = LimeSteps)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val distKm = (liveSteps * 0.00075f)
                            Text(
                                text = String.format(Locale.US, "%.1f km", distKm),
                                fontSize = TextCaption,
                                style = TabularNums,
                                color = LabelSilver
                            )
                            val cadence = if (activeMins > 0) (liveSteps / activeMins).coerceIn(0, 180) else 0
                            Text(
                                text = if (cadence > 0) "$cadence spm" else "--",
                                fontSize = TextCaption,
                                style = TabularNums,
                                color = LabelSilver
                            )
                        }
                    }
                }
            }

            // 3. Card: Tiempo Activo (Electric Cyan)
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
                                    text = stringResource(R.string.active_time),
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
                                    .background(CyanActive.copy(alpha = 0.26f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.goal_format_min, targetActiveMins),
                                    fontSize = TextCaption,
                                    style = TabularNums,
                                    color = CyanActive,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$activeMins",
                                fontSize = TextBig,
                                style = TabularNums,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.min_unit),
                                fontSize = TextLabel,
                                color = LabelSilver,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ActivityProgressBar(progress = timeProgress, color = CyanActive)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (activeMins >= targetActiveMins) stringResource(R.string.goal_achieved) else stringResource(R.string.goal_remaining_min, targetActiveMins - activeMins),
                            fontSize = TextCaption,
                            color = LabelSilver
                        )
                    }
                }
            }

            // 4. Card: Calorías Activas (Vivid Violet)
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
                                        .background(VioletCalorie)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.active_calories),
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
                                    .background(VioletCalorie.copy(alpha = 0.26f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.goal_format_cal, targetCalories),
                                    fontSize = TextCaption,
                                    style = TabularNums,
                                    color = VioletCalorie,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$calories",
                                fontSize = TextBig,
                                style = TabularNums,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "kcal",
                                fontSize = TextLabel,
                                color = LabelSilver,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ActivityProgressBar(progress = calProgress, color = VioletCalorie)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.calorie_desc),
                            fontSize = TextCaption,
                            color = LabelSilver
                        )
                    }
                }
            }

            // 5. Perspectiva RingConn (Floating Typography)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ringconn_insights),
                        fontSize = TextLabel,
                        color = CyanActive,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (vitals.activityInsight.isNotBlank()) {
                            vitals.activityInsight
                        } else if (activityScore >= 80) {
                            stringResource(R.string.daily_insight_high)
                        } else {
                            stringResource(R.string.daily_insight_low)
                        },
                        fontSize = TextCaption,
                        color = Color(0xFFCFD8DC),
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * 3 Concentric Activity Rings (One UI Watch 8 clone)
 * Outer: Neon Lime (Steps)
 * Middle: Electric Cyan (Active Time)
 * Inner: Vivid Violet (Calories)
 * Cascades outward with organic spring physics and zero per-frame object allocations.
 */
@Composable
private fun ConcentricActivityRings(
    stepsProgress: Float,
    timeProgress: Float,
    calProgress: Float,
    modifier: Modifier = Modifier
) {
    val outerAnim = remember { Animatable(0f) }
    val midAnim = remember { Animatable(0f) }
    val innerAnim = remember { Animatable(0f) }

    LaunchedEffect(stepsProgress, timeProgress, calProgress) {
        launch {
            delay(0L)
            outerAnim.animateTo(
                targetValue = stepsProgress,
                animationSpec = spring(
                    dampingRatio = 0.75f,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            delay(90L)
            midAnim.animateTo(
                targetValue = timeProgress,
                animationSpec = spring(
                    dampingRatio = 0.75f,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            delay(180L)
            innerAnim.animateTo(
                targetValue = calProgress,
                animationSpec = spring(
                    dampingRatio = 0.75f,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    val density = LocalDensity.current
    val strokeWidthPx = remember(density) { with(density) { 7.dp.toPx() } }
    val spacingPx = remember(density, strokeWidthPx) { with(density) { strokeWidthPx + 2.dp.toPx() } }
    val ringStroke = remember(strokeWidthPx) {
        Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    }

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)

        // Ring 1: Steps (Outer)
        val outerRadius = (size.minDimension / 2f) - (strokeWidthPx / 2f)
        val outerTopLeft = Offset(center.x - outerRadius, center.y - outerRadius)
        val outerSize = Size(outerRadius * 2f, outerRadius * 2f)
        drawArc(
            color = LimeSteps.copy(alpha = 0.22f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = outerTopLeft,
            size = outerSize,
            style = ringStroke
        )
        drawArc(
            color = LimeSteps,
            startAngle = -90f,
            sweepAngle = (outerAnim.value * 360f).coerceIn(0f, 360f),
            useCenter = false,
            topLeft = outerTopLeft,
            size = outerSize,
            style = ringStroke
        )

        // Ring 2: Active Time (Middle)
        val middleRadius = outerRadius - spacingPx
        val middleTopLeft = Offset(center.x - middleRadius, center.y - middleRadius)
        val middleSize = Size(middleRadius * 2f, middleRadius * 2f)
        drawArc(
            color = CyanActive.copy(alpha = 0.22f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = middleTopLeft,
            size = middleSize,
            style = ringStroke
        )
        drawArc(
            color = CyanActive,
            startAngle = -90f,
            sweepAngle = (midAnim.value * 360f).coerceIn(0f, 360f),
            useCenter = false,
            topLeft = middleTopLeft,
            size = middleSize,
            style = ringStroke
        )

        // Ring 3: Calories (Inner)
        val innerRadius = middleRadius - spacingPx
        val innerTopLeft = Offset(center.x - innerRadius, center.y - innerRadius)
        val innerSize = Size(innerRadius * 2f, innerRadius * 2f)
        drawArc(
            color = VioletCalorie.copy(alpha = 0.22f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = innerTopLeft,
            size = innerSize,
            style = ringStroke
        )
        drawArc(
            color = VioletCalorie,
            startAngle = -90f,
            sweepAngle = (innerAnim.value * 360f).coerceIn(0f, 360f),
            useCenter = false,
            topLeft = innerTopLeft,
            size = innerSize,
            style = ringStroke
        )
    }
}

/**
 * Clean animated horizontal progress bar with rounded ends.
 */
@Composable
private fun ActivityProgressBar(progress: Float, color: Color) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        anim.animateTo(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(
                durationMillis = 400,
                easing = CubicBezierEasing(0.22f, 0.25f, 0.0f, 1.0f)
            )
        )
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.5.dp)
    ) {
        val corner = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(
            color = Color(0x33FFFFFF),
            size = size,
            cornerRadius = corner
        )
        val barW = size.width * anim.value
        if (barW > 0f) {
            drawRoundRect(
                color = color,
                size = Size(barW, size.height),
                cornerRadius = corner
            )
        }
    }
}

@Preview(
    device = "id:wearos_small_round",
    showSystemUi = true
)
@Composable
fun DailySummaryDetailPreview() {
    DailySummaryDetailContent(
        vitals = RingConnVitals(
            liveSteps = 6420,
            targetSteps = 10000,
            activeMinutes = 42,
            liveCalorie = 340,
            targetCalorie = 500,
            activityScore = 78,
            isConnected = true
        )
    )
}
