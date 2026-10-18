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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import io.github.hardbas.wearsync.app.R
import io.github.hardbas.wearsync.app.complication.RingConnComplicationProvider
import io.github.hardbas.wearsync.app.data.RingConnVitals
import kotlinx.coroutines.launch

private val TempCool = Color(0xFF38BDF8)          // Electric Sky Cyan
private val TempOptimal = Color(0xFF00E676)       // Clinical Mint
private val TempWarmAlert = Color(0xFFFF7043)     // Warm Amber / Coral
private val TempEmeraldSoft = Color(0xFF34D399)   // Luminous Mint Emerald

class TemperatureDetailActivity : ComponentActivity() {
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
            TemperatureDetailScreen(this)
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
fun TemperatureDetailScreen(context: Context) {
    var vitals by remember { mutableStateOf(RingConnComplicationProvider.getLatestVitals(context)) }

    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("RingConnWearCache", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            vitals = RingConnComplicationProvider.getLatestVitals(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    TemperatureDetailContent(vitals = vitals)
}

@Composable
fun TemperatureDetailContent(vitals: RingConnVitals) {
    val listState = rememberScalingLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val offset = vitals.skinTempOffset
    val baseline = if (vitals.skinTempBaseline > 0f) vitals.skinTempBaseline else 36.1f
    val offsetFormatted = when {
        offset in -0.05f..0.05f -> "±0.0 °C"
        offset > 0.05f -> String.format(java.util.Locale.US, "+%.1f °C", offset)
        else -> String.format(java.util.Locale.US, "%.1f °C", offset)
    }

    val statusLabel = when {
        offset in -0.5f..0.5f -> stringResource(R.string.temp_stable_optimal)
        offset > 0.5f -> stringResource(R.string.temp_warm)
        else -> stringResource(R.string.temp_cool)
    }

    val statusColor = when {
        offset in -0.5f..0.5f -> TempOptimal
        offset > 0.5f -> TempWarmAlert
        else -> TempCool
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
                        text = stringResource(R.string.skin_temp),
                        fontSize = TextBody,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.ring_model_subtitle),
                        fontSize = TextCaption,
                        color = TempEmeraldSoft,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // =========================================================================
            // b) Hero: Centered Temp Dial (122dp), Offset & Status Pill
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
                        TempDialGauge(
                            offset = offset,
                            modifier = Modifier.fillMaxSize()
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = offsetFormatted,
                                fontSize = TextHero,
                                style = TabularNums,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.skin_temp).uppercase(),
                                fontSize = TextCaption,
                                color = TempEmeraldSoft,
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
            // c) Card: Desviación Dérmica y Escala Circadiana
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
                                        .background(TempOptimal)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.baseline_temp),
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
                                    .background(statusColor.copy(alpha = 0.18f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = statusLabel.uppercase(),
                                    fontSize = TextCaption,
                                    style = TabularNums,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = offsetFormatted,
                                fontSize = TextBig,
                                style = TabularNums,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.temp_baseline_format, baseline),
                                fontSize = TextLabel,
                                style = TabularNums,
                                color = LabelSilver,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Real-time Deviation Scale Bar (-1.0°C to +1.0°C)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        ) {
                            val totalW = size.width
                            val h = size.height

                            // Track Background
                            drawRoundRect(
                                color = Color(0x33FFFFFF),
                                topLeft = Offset(0f, 0f),
                                size = Size(totalW, h),
                                cornerRadius = CornerRadius(h / 2f, h / 2f)
                            )

                            // Optimal Middle Zone (-0.5°C to +0.5°C, 25% to 75%)
                            val optStart = totalW * 0.25f
                            val optWidth = totalW * 0.5f
                            drawRoundRect(
                                color = TempOptimal.copy(alpha = 0.45f),
                                topLeft = Offset(optStart, 0f),
                                size = Size(optWidth, h),
                                cornerRadius = CornerRadius(h / 2f, h / 2f)
                            )

                            // Center Reference Mark (0.0°C)
                            drawLine(
                                color = Color.White,
                                start = Offset(totalW * 0.5f, -2.dp.toPx()),
                                end = Offset(totalW * 0.5f, h + 2.dp.toPx()),
                                strokeWidth = 2.dp.toPx()
                            )

                            // Marker Indicator Dot
                            val normOffset = ((offset + 1.0f) / 2.0f).coerceIn(0f, 1f)
                            val markerX = normOffset * totalW
                            drawCircle(
                                color = Color.White,
                                radius = 5.dp.toPx(),
                                center = Offset(markerX.coerceIn(5.dp.toPx(), totalW - 5.dp.toPx()), h / 2f)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("-1.0 °C", fontSize = TextCaption, style = TabularNums, color = LabelSilver)
                            Text("0.0 °C", fontSize = TextCaption, style = TabularNums, color = TempOptimal)
                            Text("+1.0 °C", fontSize = TextCaption, style = TabularNums, color = LabelSilver)
                        }
                    }
                }
            }

            // =========================================================================
            // c) Nocturnal Circadian Thermal Curve Chart
            // =========================================================================
            item {
                CircadianThermalCurveCard(offset = offset)
            }

            // =========================================================================
            // d) Card: Perspectiva Fisiológica
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
                                    .background(TempEmeraldSoft)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.temp_delta_explanation_title),
                                color = Color.White,
                                fontSize = TextBody,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.temp_delta_explanation_desc),
                            color = Color(0xFFCFD8DC),
                            fontSize = TextCaption,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // =========================================================================
            // e) Card: Rangos de Termorregulación Circadiana
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
                                    .background(TempOptimal)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.temp_thermoregulation_title),
                                color = Color.White,
                                fontSize = TextBody,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        TempBandRow(
                            label = stringResource(R.string.optimal),
                            range = "±0.5 °C",
                            desc = stringResource(R.string.euthermia_desc),
                            color = TempOptimal,
                            isActive = offset in -0.5f..0.5f
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        TempBandRow(
                            label = stringResource(R.string.elevated),
                            range = "> +0.5 °C",
                            desc = stringResource(R.string.elevated_temp_desc),
                            color = TempWarmAlert,
                            isActive = offset > 0.5f
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        TempBandRow(
                            label = stringResource(R.string.temp_cool),
                            range = "< -0.5 °C",
                            desc = stringResource(R.string.cool_temp_desc),
                            color = TempCool,
                            isActive = offset < -0.5f
                        )
                    }
                }
            }

            // =========================================================================
            // f) Card: Alerta Inmune Temprana
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
                                    .background(TempWarmAlert)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.temp_recovery_title),
                                color = Color.White,
                                fontSize = TextBody,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.temp_recovery_desc),
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
private fun CircadianThermalCurveCard(offset: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCard)
            .background(CardBg)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.temp_circadian_curve_title),
                    color = Color.White,
                    fontSize = TextBody,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.sleep_stat),
                    color = TempEmeraldSoft,
                    fontSize = TextCaption,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Nocturnal Circadian Spline Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
            ) {
                val totalW = size.width
                val totalH = size.height - 12.dp.toPx()
                val midY = totalH / 2f
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)

                // Shaded Optimal Corridor (Band from -0.5°C to +0.5°C)
                val bandTopY = midY - (totalH * 0.35f)
                val bandBottomY = midY + (totalH * 0.35f)
                val bandHeight = bandBottomY - bandTopY

                drawRoundRect(
                    color = TempOptimal.copy(alpha = 0.08f),
                    topLeft = Offset(0f, bandTopY),
                    size = Size(totalW, bandHeight),
                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                )

                drawLine(
                    color = TempEmeraldSoft.copy(alpha = 0.25f),
                    start = Offset(0f, bandTopY),
                    end = Offset(totalW, bandTopY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )
                drawLine(
                    color = TempCool.copy(alpha = 0.25f),
                    start = Offset(0f, bandBottomY),
                    end = Offset(totalW, bandBottomY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )

                // Central Reference 0.0°C Baseline Line
                drawLine(
                    color = Color.White.copy(alpha = 0.45f),
                    start = Offset(0f, midY),
                    end = Offset(totalW, midY),
                    strokeWidth = 1.dp.toPx()
                )

                // Smooth Cubic Spline Nocturnal Curve
                val p0 = Offset(0f, midY - (offset * 10.dp.toPx()))
                val p1 = Offset(totalW * 0.25f, midY + 7.dp.toPx())
                val p2 = Offset(totalW * 0.50f, midY + 18.dp.toPx()) // Nadir (~ -0.4°C cooling)
                val p3 = Offset(totalW * 0.75f, midY + 3.dp.toPx())
                val p4 = Offset(totalW, midY - (offset * 12.dp.toPx()).coerceIn(-12.dp.toPx(), 12.dp.toPx()))

                val splinePath = Path().apply {
                    moveTo(p0.x, p0.y)
                    cubicTo(totalW * 0.12f, p0.y, totalW * 0.18f, p1.y, p1.x, p1.y)
                    cubicTo(totalW * 0.35f, p1.y + 8.dp.toPx(), totalW * 0.42f, p2.y, p2.x, p2.y)
                    cubicTo(totalW * 0.58f, p2.y, totalW * 0.65f, p3.y, p3.x, p3.y)
                    cubicTo(totalW * 0.85f, p3.y - 6.dp.toPx(), totalW * 0.92f, p4.y, p4.x, p4.y)
                }

                // Fill area under/above curve
                val fillPath = Path().apply {
                    addPath(splinePath)
                    lineTo(totalW, midY)
                    lineTo(0f, midY)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        listOf(TempEmeraldSoft.copy(alpha = 0.25f), TempCool.copy(alpha = 0.25f)),
                        startY = bandTopY,
                        endY = bandBottomY
                    )
                )

                // Luminous curve stroke
                drawPath(
                    path = splinePath,
                    brush = Brush.horizontalGradient(
                        listOf(TempCool, Color(0xFF00E676), TempEmeraldSoft, Color(0xFF00E5FF))
                    ),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Point Markers
                drawCircle(color = TempCool, radius = 3.5.dp.toPx(), center = p2)
                drawCircle(color = Color.White, radius = 1.8.dp.toPx(), center = p2)

                drawCircle(color = TempOptimal, radius = 3.5.dp.toPx(), center = p4)
                drawCircle(color = Color.White, radius = 1.8.dp.toPx(), center = p4)
            }

            // X-Axis Sleep Time Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "23h", color = Color(0xFF94A3B8), fontSize = TextCaption, style = TabularNums)
                Text(text = "02h", color = Color(0xFF94A3B8), fontSize = TextCaption, style = TabularNums)
                Text(text = "05h", color = Color(0xFF94A3B8), fontSize = TextCaption, style = TabularNums)
                Text(text = "08h", color = Color(0xFF94A3B8), fontSize = TextCaption, style = TabularNums)
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
                        text = "-0.4 °C",
                        color = TempCool,
                        fontSize = TextBody,
                        style = TabularNums,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.min_stat),
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
                        text = "98%",
                        color = TempOptimal,
                        fontSize = TextBody,
                        style = TabularNums,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.in_range_stat),
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
                        text = "0.0 °C",
                        color = Color.White,
                        fontSize = TextBody,
                        style = TabularNums,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.baseline_stat),
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
private fun TempBandRow(
    label: String,
    range: String,
    desc: String,
    color: Color,
    isActive: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeChip)
            .background(if (isActive) color.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
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
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = label,
                    color = if (isActive) Color.White else Color(0xFFB0BEC5),
                    fontSize = TextLabel,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = desc,
            color = Color(0xFF90A4AE),
            fontSize = TextCaption,
            lineHeight = TextBody,
            modifier = Modifier.padding(start = 14.dp)
        )
    }
}

/**
 * 270-degree animated circular gauge for Skin Temperature in One UI Watch style.
 */
@Composable
private fun TempDialGauge(
    offset: Float,
    modifier: Modifier = Modifier
) {
    val animProgress = remember { Animatable(0.5f) }
    // Map -1.0..+1.0°C to 0..1f (0.0°C baseline = 0.5f)
    val targetProgress = ((offset + 1.0f) / 2.0f).coerceIn(0.05f, 0.95f)

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
            color = Color(0x2210B981),
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
                        TempCool,
                        TempOptimal,
                        TempWarmAlert,
                        TempOptimal
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
fun TemperatureDetailPreview() {
    TemperatureDetailContent(
        vitals = RingConnVitals(
            skinTempOffset = 0.3f,
            skinTempBaseline = 36.4f,
            refreshTimestamp = System.currentTimeMillis() / 1000L,
            isConnected = true
        )
    )
}
