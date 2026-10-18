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
            contentPadding = PaddingValues(top = 28.dp, bottom = 24.dp, start = 8.dp, end = 8.dp)
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
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.ring_model_subtitle),
                        fontSize = TextCaption,
                        color = LabelSilver
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
                                text = "$activityScore",
                                fontSize = TextHero,
                                style = TabularNums,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.pts_unit).uppercase(),
                                fontSize = TextCaption,
                                color = LabelSilver,
                                fontWeight = FontWeight.SemiBold
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
                            .background(statusColor.copy(alpha = 0.18f))
                            .border(BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)), ShapePill)
                            .padding(horizontal = 14.dp, vertical = 3.5.dp)
                    ) {
                        Text(
                            text = statusLabel.uppercase(),
                            fontSize = TextCaption,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
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
                        .background(Brush.verticalGradient(listOf(Color(0xFF142410), Color(0xFF0C170A))))
                        .border(BorderStroke(1.dp, Color(0x33A0F116)), ShapeCard)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column {
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
                                        .background(LimeSteps)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.steps),
                                    fontSize = TextBody,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(ShapeChip)
                                    .background(LimeSteps.copy(alpha = 0.18f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${(stepsProgress * 100).toInt()}%",
                                    fontSize = TextCaption,
                                    style = TabularNums,
                                    color = LimeSteps,
                                    fontWeight = FontWeight.Bold
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
                        .background(Brush.verticalGradient(listOf(Color(0xFF0B212D), Color(0xFF07141C))))
                        .border(BorderStroke(1.dp, Color(0x3347CDFF)), ShapeCard)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column {
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
                                        .background(CyanActive)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.active_time),
                                    fontSize = TextBody,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(ShapeChip)
                                    .background(CyanActive.copy(alpha = 0.18f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.goal_format_min, targetActiveMins),
                                    fontSize = TextCaption,
                                    style = TabularNums,
                                    color = CyanActive,
                                    fontWeight = FontWeight.Bold
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
                        .background(Brush.verticalGradient(listOf(Color(0xFF221128), Color(0xFF130917))))
                        .border(BorderStroke(1.dp, Color(0x33C580FF)), ShapeCard)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column {
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
                                        .background(VioletCalorie)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.active_calories),
                                    fontSize = TextBody,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(ShapeChip)
                                    .background(VioletCalorie.copy(alpha = 0.18f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.goal_format_cal, targetCalories),
                                    fontSize = TextCaption,
                                    style = TabularNums,
                                    color = VioletCalorie,
                                    fontWeight = FontWeight.Bold
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

            // 5. Card: Perspectiva RingConn
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeCard)
                        .background(Brush.verticalGradient(listOf(Color(0xFF161F24), Color(0xFF0E1317))))
                        .border(BorderStroke(1.dp, Color(0x22FFFFFF)), ShapeCard)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.ringconn_insights),
                            fontSize = TextLabel,
                            color = CyanActive,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (activityScore >= 80) {
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
        val strokeWidth = 7.dp.toPx()
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
