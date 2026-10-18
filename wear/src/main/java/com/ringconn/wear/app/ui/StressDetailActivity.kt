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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.PathEffect
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
import com.ringconn.wear.app.R
import com.ringconn.wear.app.complication.RingConnComplicationProvider
import com.ringconn.wear.app.data.RingConnVitals
import kotlinx.coroutines.launch
import java.util.Calendar

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
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(122.dp)
                    ) {
                        StressDialGauge(
                            score = score,
                            modifier = Modifier.fillMaxSize()
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (score > 0) "$score" else "--",
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
                AutonomicBalanceCard(hrv = vitals.lastHrv, score = score)
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
        }
    }
}

@Composable
private fun StressTimelineCard(score: Int) {
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }

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

            Spacer(modifier = Modifier.height(8.dp))

            // 24-Hour Timeline Bar Chart
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            ) {
                val totalW = size.width
                val totalH = size.height - 12.dp.toPx()
                val barCount = 24
                val barSlotWidth = totalW / barCount
                val barWidth = barSlotWidth * 0.65f
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)

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

                    drawRoundRect(
                        color = barColor.copy(alpha = if (i <= currentHour) 0.95f else 0.35f),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barH.coerceAtLeast(3.dp.toPx())),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )

                    if (i == currentHour) {
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = Offset(x + (barWidth / 2f), y - 3.dp.toPx())
                        )
                    }
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
private fun AutonomicBalanceCard(hrv: Int, score: Int) {
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

                val trackBrush = Brush.horizontalGradient(
                    listOf(StressRelaxed, StressBalanced, StressModerate, StressHigh)
                )
                drawRoundRect(
                    brush = trackBrush,
                    topLeft = Offset(0f, 0f),
                    size = Size(totalW, h),
                    cornerRadius = CornerRadius(h / 2f, h / 2f),
                    alpha = 0.5f
                )

                val normPos = (score / 100f).coerceIn(0.05f, 0.95f)
                val markerX = normPos * totalW
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

            val explanation = when {
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeChip)
            .background(if (isActive) color.copy(alpha = 0.18f) else Color.Transparent)
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
                color = if (isActive) Color.White else Color(0xFFB0BEC5),
                fontSize = TextLabel,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
        }
        Text(
            text = range,
            color = if (isActive) color else Color(0xFF78909C),
            fontSize = TextLabel,
            style = TabularNums,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
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
            color = Color(0x228B5CF6),
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
                        StressRelaxed,
                        StressBalanced,
                        StressModerate,
                        StressHigh,
                        StressRelaxed
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
