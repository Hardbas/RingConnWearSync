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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.scrollBy
import androidx.wear.compose.material.Text
import com.ringconn.wear.app.R
import com.ringconn.wear.app.complication.RingConnComplicationProvider
import kotlinx.coroutines.launch

private val TempOptimal = Color(0xFFA0F116)
private val TempWarm = Color(0xFFFF9800)
private val TempCool = Color(0xFF47CDFF)

private val TempHeroGradient = Brush.verticalGradient(
    listOf(Color(0xFF2E3E48), Color(0xFF10161B))
)

private val CardGradient = Brush.verticalGradient(
    listOf(Color(0xFF1E282E), Color(0xFF12181C))
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
        offset > 0.5f -> TempWarm
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
                .padding(horizontal = 14.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Clean title (Wear OS standard swipe-to-dismiss)
            Text(
                text = stringResource(R.string.skin_temp),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 1. Hero Stadium Capsule
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(TempHeroGradient)
                    .padding(horizontal = 16.dp, vertical = 18.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    // Badge status pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(statusColor.copy(alpha = 0.22f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontSize = 12.sp,
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

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.temp_baseline_format, baseline),
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Normal Range Scale Bar (-1.0°C to +1.0°C)
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
                            cornerRadius = CornerRadius(h / 2, h / 2)
                        )

                        // Optimal Middle Zone (-0.4°C to +0.4°C)
                        val optStart = totalW * 0.3f
                        val optWidth = totalW * 0.4f
                        drawRoundRect(
                            color = TempOptimal.copy(alpha = 0.5f),
                            topLeft = Offset(optStart, 0f),
                            size = Size(optWidth, h),
                            cornerRadius = CornerRadius(h / 2, h / 2)
                        )

                        // Center Reference Mark (0.0°C)
                        drawLine(
                            color = Color(0x88FFFFFF),
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
                            center = Offset(markerX.coerceIn(5.dp.toPx(), totalW - 5.dp.toPx()), h / 2)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Card: ¿Qué significa ±0.0 °C?
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardGradient)
                    .padding(14.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_comp_temp),
                            contentDescription = stringResource(R.string.temp_delta_explanation_title),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.temp_delta_explanation_title),
                            color = Color(0xFF00E5FF),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.temp_delta_explanation_desc),
                        color = Color(0xFFCFD8DC),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Card: Bandas de Termorregulación
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardGradient)
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.temp_thermoregulation_title),
                        color = Color(0xFFFFB74D),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = stringResource(R.string.temp_thermoregulation_desc1),
                        color = Color(0xFFCFD8DC),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = stringResource(R.string.temp_thermoregulation_desc2),
                        color = Color(0xFF90A4AE),
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Card: Alerta Inmune Temprana
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardGradient)
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.temp_recovery_title),
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.temp_recovery_desc),
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
