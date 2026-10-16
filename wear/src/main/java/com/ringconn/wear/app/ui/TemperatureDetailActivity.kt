package com.ringconn.wear.app.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.ringconn.wear.app.R
import com.ringconn.wear.app.complication.RingConnComplicationProvider
import kotlinx.coroutines.launch

private val TempCool = Color(0xFF38BDF8)       // Electric Sky Cyan
private val TempOptimal = Color(0xFF00E676)    // Clinical Mint
private val TempRuby = Color(0xFFFF4D6D)       // Gentle AI Electric Ruby Crimson
private val TempRoseSoft = Color(0xFFFF758F)   // Gentle AI Soft Rose

private val TempHeroGradient = Brush.verticalGradient(
    listOf(Color(0xFF5E1E26), Color(0xFF3A1117), Color(0xFF1D080B))
)

private val CardGradient = Brush.verticalGradient(
    listOf(Color(0xFF2C1014), Color(0xFF150608))
)

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

    val scrollState = rememberScrollState()
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
        offset > 0.5f -> TempRuby
        else -> TempCool
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onRotaryScrollEvent {
                coroutineScope.launch {
                    scrollState.scrollBy(it.verticalScrollPixels * 1.5f)
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 14.dp, end = 14.dp, top = 34.dp, bottom = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Clean title with circular clearance
            Text(
                text = stringResource(R.string.skin_temp),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 1. Hero Stadium Capsule Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(TempHeroGradient)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    // Badge status pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(statusColor.copy(alpha = 0.22f))
                            .padding(horizontal = 12.dp, vertical = 3.5.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Big Offset Value
                    Text(
                        text = offsetFormatted,
                        color = Color.White,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = stringResource(R.string.temp_baseline_format, baseline),
                        color = Color(0xFFFFCCD5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

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
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Nocturnal Circadian Thermal Curve Chart
            CircadianThermalCurveCard(offset = offset)

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Card: Perspectiva Fisiológica Samsung Health
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(CardGradient)
                    .padding(14.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_comp_temp),
                            contentDescription = stringResource(R.string.temp_delta_explanation_title),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = stringResource(R.string.temp_delta_explanation_title),
                            color = TempRuby,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = stringResource(R.string.temp_delta_explanation_desc),
                        color = Color(0xFFCFD8DC),
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Card: Rangos de Termorregulación Circadiana
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(CardGradient)
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.temp_thermoregulation_title),
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    TempBandRow(
                        label = stringResource(R.string.optimal),
                        range = "±0.5 °C",
                        desc = stringResource(R.string.euthermia_desc),
                        color = TempOptimal,
                        isActive = offset in -0.5f..0.5f
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    TempBandRow(
                        label = stringResource(R.string.elevated),
                        range = "> +0.5 °C",
                        desc = stringResource(R.string.elevated_temp_desc),
                        color = TempRuby,
                        isActive = offset > 0.5f
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    TempBandRow(
                        label = stringResource(R.string.temp_cool),
                        range = "< -0.5 °C",
                        desc = stringResource(R.string.cool_temp_desc),
                        color = TempCool,
                        isActive = offset < -0.5f
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5. Card: Alerta Inmune Temprana
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF2E1015))
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.temp_recovery_title),
                        color = TempRoseSoft,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.temp_recovery_desc),
                        color = Color(0xFFCFD8DC),
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )
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
            .clip(RoundedCornerShape(22.dp))
            .background(CardGradient)
            .padding(14.dp)
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
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.sleep_stat),
                    color = TempRoseSoft,
                    fontSize = 11.sp,
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
                    color = TempRuby.copy(alpha = 0.25f),
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
                        listOf(TempRuby.copy(alpha = 0.25f), TempCool.copy(alpha = 0.25f)),
                        startY = bandTopY,
                        endY = bandBottomY
                    )
                )

                // Luminous curve stroke
                drawPath(
                    path = splinePath,
                    brush = Brush.horizontalGradient(
                        listOf(TempCool, Color(0xFF00E676), TempRoseSoft, TempRuby)
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
                Text(text = "23h", color = Color(0xFF94A3B8), fontSize = 9.sp)
                Text(text = "02h", color = Color(0xFF94A3B8), fontSize = 9.sp)
                Text(text = "05h", color = Color(0xFF94A3B8), fontSize = 9.sp)
                Text(text = "08h", color = Color(0xFF94A3B8), fontSize = 9.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Centered 3-Column Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "-0.4 °C", color = TempCool, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                    Text(text = stringResource(R.string.min_stat), color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                }
                Box(modifier = Modifier.width(1.dp).height(18.dp).background(Color(0x22FFFFFF)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "98%", color = TempOptimal, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                    Text(text = stringResource(R.string.in_range_stat), color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                }
                Box(modifier = Modifier.width(1.dp).height(18.dp).background(Color(0x22FFFFFF)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "0.0 °C", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                    Text(text = stringResource(R.string.baseline_stat), color = Color(0xFF94A3B8), fontSize = 10.5.sp)
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
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) color.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
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
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = label,
                    color = if (isActive) Color.White else Color(0xFFB0BEC5),
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
            }
            Text(
                text = range,
                color = if (isActive) color else Color(0xFF78909C),
                fontSize = 11.5.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = desc,
            color = Color(0xFF90A4AE),
            fontSize = 10.5.sp,
            lineHeight = 13.5.sp,
            modifier = Modifier.padding(start = 14.dp)
        )
    }
}
