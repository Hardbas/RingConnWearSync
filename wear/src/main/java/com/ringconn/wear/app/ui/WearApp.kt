package com.ringconn.wear.app.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.wear.compose.material.*
import com.google.android.gms.wearable.Wearable
import com.ringconn.wear.app.R
import com.ringconn.wear.app.complication.RingConnComplicationProvider
import com.ringconn.wear.app.data.RingConnVitals
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

// Exact Samsung Health One UI Watch 8 Palette (extracted from shealth_watch_base.apk)
val OneUiBlack = Color(0xFF000000)
val HrGradientStart = Color(0xFFCB6089)
val HrGradientEnd = Color(0xFF662E43)
val HrSubtitlePink = Color(0xFFF99BD6)
val HrMinMaxPink = Color(0xFFFF5D99)

val VitalsGradientStart = Color(0xFF6E5A2F)
val VitalsGradientEnd   = Color(0xFF2B2514)
val VitalsGold          = Color(0xFFEDCB8B)

val ActivityGradientStart = Color(0xFF3B434A)
val ActivityGradientEnd = Color(0xFF181A1D)
val LimeSteps = Color(0xFFA0F116)
val CyanActive = Color(0xFF47CDFF)
val VioletCalorie = Color(0xFFC580FF)

val SleepGradientStart = Color(0xFF3D4468)
val SleepGradientEnd = Color(0xFF181A22)
val SleepPeriwinkle = Color(0xFFA5AEDE)

val StressGradientStart = Color(0xFF543F74)
val StressGradientMid = Color(0xFF382750)
val StressGradientEnd = Color(0xFF1E142E)
val StressLilac = Color(0xFFD8B4FE)

val LabelSilver = Color(0xFFB7B7BB)
val PipeGray = Color(0xFF686D73)
val GlassBlack50 = Color(0x80000000)

private val WearVitalsBrush = Brush.linearGradient(colors = listOf(VitalsGradientStart, VitalsGradientEnd))
private val WearHrBrush = Brush.linearGradient(colors = listOf(HrGradientStart, HrGradientEnd))
private val WearActivityBrush = Brush.horizontalGradient(colors = listOf(ActivityGradientStart, ActivityGradientEnd))
private val WearSleepBrush = Brush.horizontalGradient(colors = listOf(SleepGradientStart, SleepGradientEnd))
private val WearStressBrush = Brush.horizontalGradient(colors = listOf(StressGradientStart, StressGradientMid, StressGradientEnd))

@Composable
fun WearApp(context: Context) {
    val scope = rememberCoroutineScope()
    var vitals by remember { mutableStateOf(RingConnComplicationProvider.getLatestVitals(context)) }
    var isMeasuring by remember { mutableStateOf(false) }

    // Listen to live SharedPreferences updates when phone or ADB pushes new telemetry
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

    WearAppContent(
        vitals = vitals,
        isMeasuring = isMeasuring,
        onMeasureClick = {
            isMeasuring = true
            scope.launch {
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
                delay(5500L)
                isMeasuring = false
            }
        }
    )
}

@Composable
fun WearAppContent(
    vitals: RingConnVitals,
    isMeasuring: Boolean,
    onMeasureClick: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val stepsFormatted = remember(vitals.liveSteps) {
        NumberFormat.getIntegerInstance(Locale.getDefault()).format(vitals.liveSteps)
    }

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
                .padding(top = 20.dp, bottom = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. One UI Watch 8 Top Header ("RingConn Health" + Ring Battery Pill)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
            ) {
                Text(
                    text = "RingConn Health",
                    fontSize = TextBody,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(ShapePill)
                        .background(Color(0xFF1F2328))
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (vitals.ringBattery < 20) HrMinMaxPink else LimeSteps)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.ring_battery, vitals.ringBattery),
                            fontSize = TextLabel,
                            style = TabularNums,
                            color = LabelSilver,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 2. CAPSULE 1: Heart Rate & SpO2 (One UI Watch 8 Vitals Capsule)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .height(78.dp)
                    .clip(ShapePill)
                    .background(WearVitalsBrush)
                    .clickable {
                        context.startActivity(Intent(context, VitalsDetailActivity::class.java))
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.clickable { onMeasureClick() }) {
                        PulsingHeartBadge(isMeasuring = isMeasuring)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isMeasuring) stringResource(R.string.measuring) else stringResource(R.string.heart_rate),
                            fontSize = TextLabel,
                            color = VitalsGold,
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            val hasHr = vitals.lastHeartRate > 0
                            val hrText = if (hasHr) "${vitals.lastHeartRate}" else "--"
                            Text(
                                text = hrText,
                                fontSize = TextBig,
                                style = TabularNums,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = " ${stringResource(R.string.bpm_unit)}  ",
                                fontSize = TextLabel,
                                color = VitalsGold,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            val hasSpo2 = vitals.lastSpo2 > 0
                            val spo2Text = if (hasSpo2) "SpO2 ${vitals.lastSpo2}%" else "SpO2 --"
                            Text(
                                text = spo2Text,
                                fontSize = TextLabel,
                                style = TabularNums,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        val hrProgress = if (vitals.lastHeartRate > 0) ((vitals.lastHeartRate - 40f) / 110f).coerceIn(0.1f, 1f) else 0.5f
                        AnimatedCapsuleBar(
                            progress = hrProgress,
                            color = VitalsGold,
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .height(3.5.dp)
                        )
                    }
                }
            }

            // 3. CAPSULE 2: Daily Activity (Exact match to watch_oneui_active.png)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .height(76.dp)
                    .clip(ShapePill)
                    .background(WearActivityBrush)
                    .clickable {
                        context.startActivity(Intent(context, DailySummaryDetailActivity::class.java))
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.oneui_ic_activity_rings),
                        contentDescription = stringResource(R.string.tile_summary_title),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.tile_summary_title),
                            fontSize = TextLabel,
                            color = LabelSilver,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = LimeSteps, fontWeight = FontWeight.Bold, fontSize = TextBody)) {
                                    append(stepsFormatted)
                                }
                                withStyle(SpanStyle(color = PipeGray, fontSize = TextBody)) {
                                    append(" | ")
                                }
                                withStyle(SpanStyle(color = CyanActive, fontWeight = FontWeight.Bold, fontSize = TextBody)) {
                                    val activeMins = if (vitals.activeMinutes > 0) vitals.activeMinutes else if (vitals.liveSteps > 0) 3 else 0
                                    append(if (activeMins > 0) "${activeMins}m" else "--")
                                }
                                withStyle(SpanStyle(color = PipeGray, fontSize = TextBody)) {
                                    append(" | ")
                                }
                                withStyle(SpanStyle(color = VioletCalorie, fontWeight = FontWeight.Bold, fontSize = TextBody)) {
                                    append("${vitals.liveCalorie}")
                                }
                            },
                            style = TabularNums
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        val stepsProgress = (vitals.liveSteps.toFloat() / 8000f).coerceIn(0.08f, 1f)
                        AnimatedCapsuleBar(
                            progress = stepsProgress,
                            color = LimeSteps,
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .height(3.5.dp)
                        )
                    }
                }
            }

            // 4. CAPSULE 3: Sleep Score & HRV (Exact match to watch_oneui_scrolled.png)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .height(76.dp)
                    .clip(ShapePill)
                    .background(WearSleepBrush)
                    .clickable {
                        context.startActivity(Intent(context, SleepDetailActivity::class.java))
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GlassBlack50),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.oneui_sleep_icon),
                            contentDescription = stringResource(R.string.sleep_score),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.sleep_score),
                            fontSize = TextLabel,
                            color = SleepPeriwinkle,
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            val hasSleep = vitals.sleepScore > 0
                            val sleepText = if (hasSleep) "${vitals.sleepScore} ${stringResource(R.string.pts_unit)}" else "--"
                            Text(
                                text = sleepText,
                                fontSize = TextBody,
                                style = TabularNums,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            val hasHrv = vitals.lastHrv > 0
                            val hrvText = if (hasHrv) "  ·  HRV ${vitals.lastHrv} ms" else "  ·  HRV --"
                            Text(
                                text = hrvText,
                                fontSize = TextLabel,
                                style = TabularNums,
                                color = CyanActive,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        val sleepProgress = (vitals.sleepScore.toFloat() / 100f).coerceIn(0.08f, 1f)
                        AnimatedCapsuleBar(
                            progress = sleepProgress,
                            color = SleepPeriwinkle,
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .height(3.5.dp)
                        )
                    }
                }
            }

            // 5. CAPSULE 4: Stress Index (One UI Watch 8 Stress Gradient)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .height(74.dp)
                    .clip(ShapePill)
                    .background(WearStressBrush)
                    .clickable {
                        context.startActivity(Intent(context, StressDetailActivity::class.java))
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GlassBlack50),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_comp_stress),
                            contentDescription = stringResource(R.string.stress),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.stress_title),
                            fontSize = TextLabel,
                            color = StressLilac,
                            fontWeight = FontWeight.Medium
                        )
                        val hasStress = vitals.stressScore > 0
                        val stressStatus = when {
                            !hasStress -> "--"
                            vitals.stressScore < 30 -> stringResource(R.string.stress_relaxed)
                            vitals.stressScore < 60 -> stringResource(R.string.stress_balanced)
                            vitals.stressScore < 80 -> stringResource(R.string.stress_moderate)
                            else -> stringResource(R.string.stress_high)
                        }
                        Text(
                            text = if (hasStress) "${vitals.stressScore} / 100  ·  $stressStatus" else "--",
                            fontSize = TextBody,
                            style = TabularNums,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        val stressProgress = (vitals.stressScore.toFloat() / 100f).coerceIn(0.08f, 1f)
                        AnimatedCapsuleBar(
                            progress = stressProgress,
                            color = StressLilac,
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .height(3.5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 6. CAPSULE 5: Skin Temperature (Emerald Mint Gradient - One UI Watch 8 match)
            val hasTemp = vitals.skinTempBaseline > 0f || vitals.skinTempOffset != 0f
            val tempOffset = vitals.skinTempOffset
            val signStr = if (tempOffset > 0f) "+" else ""
            val tempFormatted = if (hasTemp) String.format(java.util.Locale.US, "%s%.1f °C", signStr, tempOffset) else "--"
            val tempStatus = when {
                !hasTemp -> "--"
                tempOffset in -0.5f..0.5f -> stringResource(R.string.temp_in_range)
                tempOffset > 0.5f -> stringResource(R.string.temp_warm)
                else -> stringResource(R.string.temp_cool)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .height(74.dp)
                    .clip(ShapePill)
                    .background(Brush.horizontalGradient(listOf(Color(0xFF144238), Color(0xFF0B2822))))
                    .clickable {
                        context.startActivity(Intent(context, TemperatureDetailActivity::class.java))
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GlassBlack50),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_comp_temp),
                            contentDescription = stringResource(R.string.skin_temp),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.skin_temp),
                            fontSize = TextLabel,
                            color = LabelSilver,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (hasTemp) "$tempFormatted  ·  $tempStatus" else "--",
                            fontSize = TextBody,
                            style = TabularNums,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        val tempProgress = ((tempOffset + 1.0f) / 2.0f).coerceIn(0.1f, 1f)
                        AnimatedCapsuleBar(
                            progress = tempProgress,
                            color = Color(0xFF68D391),
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .height(3.5.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * One UI Watch 8 600ms cubic-bezier animated mini progress bar for capsules.
 */
@Composable
private fun AnimatedCapsuleBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        anim.animateTo(
            targetValue = progress.coerceIn(0.05f, 1f),
            animationSpec = tween(
                durationMillis = 600,
                easing = CubicBezierEasing(0.22f, 0.25f, 0.0f, 1.0f)
            )
        )
    }

    Canvas(modifier = modifier) {
        val corner = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = GlassBlack50, size = size, cornerRadius = corner)
        val barW = size.width * anim.value
        if (barW > 0f) {
            drawRoundRect(color = color, size = Size(barW, size.height), cornerRadius = corner)
        }
    }
}

/**
 * Heart icon badge animated with graphicsLayer draw phase to avoid parent recomposition.
 */
@Composable
private fun PulsingHeartBadge(isMeasuring: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isMeasuring) 1.24f else 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isMeasuring) 420 else 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartScale"
    )

    Box(
        modifier = Modifier
            .size(38.dp)
            .graphicsLayer {
                scaleX = heartScale
                scaleY = heartScale
            }
            .clip(CircleShape)
            .background(GlassBlack50),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.oneui_hr_icon),
            contentDescription = stringResource(R.string.heart_rate),
            modifier = Modifier.size(22.dp)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    device = "id:wearos_small_round",
    showSystemUi = true
)
@Composable
fun WearAppPreview() {
    WearAppContent(
        vitals = RingConnVitals(
            lastHeartRate = 74,
            lastSpo2 = 98,
            lastHrv = 46,
            sleepScore = 86,
            activityScore = 78,
            stressScore = 24,
            liveSteps = 6420,
            targetSteps = 10000,
            liveCalorie = 340,
            ringBattery = 84,
            isConnected = true
        ),
        isMeasuring = false,
        onMeasureClick = {}
    )
}
