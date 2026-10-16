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

private val StressRelaxed = Color(0xFF00E676)
private val StressBalanced = Color(0xFF00E5FF)
private val StressModerate = Color(0xFFFFD600)
private val StressHigh = Color(0xFFFF5252)

private val StressHeroGradient = Brush.verticalGradient(
    listOf(Color(0xFF383F63), Color(0xFF131522))
)

private val CardGradient = Brush.verticalGradient(
    listOf(Color(0xFF22263A), Color(0xFF141624))
)

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

    val scrollState = rememberScrollState()
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
                text = stringResource(R.string.stress_title),
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
                    .background(StressHeroGradient)
                    .padding(horizontal = 16.dp, vertical = 18.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    // Badge status pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(statusColor.copy(alpha = 0.22f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val gaugeResId = when {
                        score < 30 -> R.drawable.oneui_ic_gauge_relaxed
                        score < 60 -> R.drawable.oneui_ic_gauge_balanced
                        score < 80 -> R.drawable.oneui_ic_gauge_moderate
                        else -> R.drawable.oneui_ic_gauge_high
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = if (score > 0) "$score" else "--",
                                    color = Color.White,
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "/ 100",
                                    color = Color(0xFFA5AEDE),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            val hrvVal = if (vitals.lastHrv > 0) "VFC ${vitals.lastHrv} ms" else stringResource(R.string.stress_resting)
                            Text(
                                text = hrvVal,
                                color = Color(0xFF47CDFF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Image(
                            painter = painterResource(id = gaugeResId),
                            contentDescription = statusLabel,
                            modifier = Modifier.size(72.dp, 54.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Card: Zonas de Presión
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardGradient)
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.stress_zones_title),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    StressZoneRow(stringResource(R.string.stress_relaxed), stringResource(R.string.stress_zone_relaxed_desc), StressRelaxed, score < 30)
                    Spacer(modifier = Modifier.height(6.dp))
                    StressZoneRow(stringResource(R.string.stress_balanced), stringResource(R.string.stress_zone_balanced_desc), StressBalanced, score in 30..59)
                    Spacer(modifier = Modifier.height(6.dp))
                    StressZoneRow(stringResource(R.string.stress_moderate), stringResource(R.string.stress_zone_moderate_desc), StressModerate, score in 60..79)
                    Spacer(modifier = Modifier.height(6.dp))
                    StressZoneRow(stringResource(R.string.stress_high), stringResource(R.string.stress_zone_high_desc), StressHigh, score >= 80)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Card: Insights del Sistema Nervioso Autónomo
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
                            painter = painterResource(id = R.drawable.ic_comp_stress),
                            contentDescription = stringResource(R.string.stress_autonomic_tone_title),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.stress_autonomic_tone_title),
                            color = Color(0xFF00E5FF),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val hrvVal = if (vitals.lastHrv > 0) "${vitals.lastHrv} ms" else "--"
                    Text(
                        text = stringResource(R.string.stress_hrv_label, hrvVal),
                        color = Color.White,
                        fontSize = 13.sp,
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
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Card: Respiración Guiada 4x4
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF142330))
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.stress_breathing_title),
                        color = Color(0xFF00E676),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.stress_breathing_guide),
                        color = Color(0xFFCFD8DC),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StressZoneRow(label: String, range: String, color: Color, isActive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) color.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = if (isActive) Color.White else Color(0xFFB0BEC5),
                fontSize = 12.5.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
        }
        Text(
            text = range,
            color = if (isActive) color else Color(0xFF78909C),
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}
