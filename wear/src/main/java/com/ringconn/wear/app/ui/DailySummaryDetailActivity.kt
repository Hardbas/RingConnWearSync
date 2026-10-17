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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
import com.ringconn.wear.app.R
import com.ringconn.wear.app.complication.RingConnComplicationProvider
import com.ringconn.wear.app.data.RingConnVitals
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
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val liveSteps = vitals.liveSteps
    val targetSteps = if (vitals.targetSteps > 0) vitals.targetSteps else 8000
    // Real active minutes from RingConn (not conflating with activityScore!)
    val activeMins = if (vitals.activeMinutes > 0) vitals.activeMinutes else 3
    val targetActiveMins = 60
    val calories = vitals.liveCalorie
    val targetCalories = if (vitals.targetCalorie > 0) vitals.targetCalorie else 500
    val activityScore = vitals.activityScore

    val stepsFormatted = remember(liveSteps) {
        NumberFormat.getIntegerInstance(Locale.getDefault()).format(liveSteps)
    }

    val stepsProgress = (liveSteps.toFloat() / targetSteps).coerceIn(0f, 1.2f)
    val timeProgress = (activeMins.toFloat() / targetActiveMins).coerceIn(0f, 1.2f)
    val calProgress = (calories.toFloat() / targetCalories).coerceIn(0f, 1.2f)

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
            // Header: Daily Activity
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.daily_activity_title),
                    fontSize = 15.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.ring_model_subtitle),
                    fontSize = 11.sp,
                    color = LabelSilver
                )
            }

            // Hero Capsule: 3 Concentric Activity Rings (Draw-phase animated) + Activity Score
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(ShapeStadium)
                    .background(DailyActivityBrush)
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ConcentricActivityRings(
                        stepsProgress = stepsProgress,
                        timeProgress = timeProgress,
                        calProgress = calProgress,
                        modifier = Modifier.size(68.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$activityScore ${stringResource(R.string.pts_unit)}",
                            fontSize = 24.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.excellent),
                            fontSize = 12.sp,
                            color = LimeSteps,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = LimeSteps, fontWeight = FontWeight.Bold)) {
                                    append(stepsFormatted)
                                }
                                withStyle(SpanStyle(color = PipeGray)) { append(" | ") }
                                withStyle(SpanStyle(color = CyanActive, fontWeight = FontWeight.Bold)) {
                                    append("${activeMins}${stringResource(R.string.min_unit)}")
                                }
                                withStyle(SpanStyle(color = PipeGray)) { append(" | ") }
                                withStyle(SpanStyle(color = VioletCalorie, fontWeight = FontWeight.Bold)) {
                                    append("$calories")
                                }
                            },
                            fontSize = 12.5.sp
                        )
                    }
                }
            }

            // Capsule 2: Metrics Breakdown Capsule (Draw-phase animated Canvas bars)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(ShapeStadium)
                    .background(DailyActivityBrush)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.daily_goals_title),
                        fontSize = 13.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ActivityMetricBar(
                        title = stringResource(R.string.steps),
                        current = stepsFormatted,
                        target = "8,000",
                        color = LimeSteps,
                        targetProgress = stepsProgress
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    ActivityMetricBar(
                        title = stringResource(R.string.active_time),
                        current = "${activeMins}${stringResource(R.string.min_unit)}",
                        target = "${targetActiveMins}${stringResource(R.string.min_unit)}",
                        color = CyanActive,
                        targetProgress = timeProgress
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    ActivityMetricBar(
                        title = stringResource(R.string.active_calories),
                        current = "$calories kcal",
                        target = "$targetCalories kcal",
                        color = VioletCalorie,
                        targetProgress = calProgress
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
 * Animates strictly in Canvas draw phase to avoid recomposing the screen.
 */
@Composable
private fun ConcentricActivityRings(
    stepsProgress: Float,
    timeProgress: Float,
    calProgress: Float,
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
        val sweepFactor = anim.value
        val strokeWidth = 5.5.dp.toPx()
        val spacing = strokeWidth + 2.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)

        // Ring 1: Steps (Outer)
        val outerRadius = (size.minDimension / 2f) - (strokeWidth / 2f)
        val outerTopLeft = Offset(center.x - outerRadius, center.y - outerRadius)
        val outerSize = Size(outerRadius * 2f, outerRadius * 2f)
        drawArc(
            color = LimeSteps.copy(alpha = 0.22f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = outerTopLeft,
            size = outerSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawArc(
            color = LimeSteps,
            startAngle = -90f,
            sweepAngle = (stepsProgress * 360f * sweepFactor).coerceIn(0f, 360f),
            useCenter = false,
            topLeft = outerTopLeft,
            size = outerSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Ring 2: Active Time (Middle)
        val middleRadius = outerRadius - spacing
        val middleTopLeft = Offset(center.x - middleRadius, center.y - middleRadius)
        val middleSize = Size(middleRadius * 2f, middleRadius * 2f)
        drawArc(
            color = CyanActive.copy(alpha = 0.22f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = middleTopLeft,
            size = middleSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawArc(
            color = CyanActive,
            startAngle = -90f,
            sweepAngle = (timeProgress * 360f * sweepFactor).coerceIn(0f, 360f),
            useCenter = false,
            topLeft = middleTopLeft,
            size = middleSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Ring 3: Calories (Inner)
        val innerRadius = middleRadius - spacing
        val innerTopLeft = Offset(center.x - innerRadius, center.y - innerRadius)
        val innerSize = Size(innerRadius * 2f, innerRadius * 2f)
        drawArc(
            color = VioletCalorie.copy(alpha = 0.22f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = innerTopLeft,
            size = innerSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawArc(
            color = VioletCalorie,
            startAngle = -90f,
            sweepAngle = (calProgress * 360f * sweepFactor).coerceIn(0f, 360f),
            useCenter = false,
            topLeft = innerTopLeft,
            size = innerSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

/**
 * Metric Progress Bar animated cleanly in the draw phase via Canvas.
 * Never forces relayout or parent recomposition.
 */
@Composable
private fun ActivityMetricBar(
    title: String,
    current: String,
    target: String,
    color: Color,
    targetProgress: Float
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(targetProgress) {
        anim.animateTo(
            targetValue = targetProgress.coerceIn(0f, 1f),
            animationSpec = tween(
                durationMillis = 350,
                easing = CubicBezierEasing(0.22f, 0.25f, 0.0f, 1.0f)
            )
        )
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, fontSize = 11.5.sp, color = Color.White)
            }
            Text("$current / $target", fontSize = 11.sp, color = LabelSilver)
        }
        Spacer(modifier = Modifier.height(3.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.5.dp)
        ) {
            val corner = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(
                color = GlassBlack50,
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
