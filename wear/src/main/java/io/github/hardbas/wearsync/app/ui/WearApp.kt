package io.github.hardbas.wearsync.app.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.google.android.gms.wearable.Wearable
import io.github.hardbas.wearsync.app.R
import io.github.hardbas.wearsync.app.complication.RingConnComplicationProvider
import io.github.hardbas.wearsync.app.data.RingConnVitals
import io.github.hardbas.wearsync.app.service.WearDataListenerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.sp
import io.github.hardbas.wearsync.app.data.RingConnHistoryStore
import java.text.NumberFormat
import java.util.Locale

// Dark Obsidian Watch UI Color Palette
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
private val WearActivityBrush = Brush.horizontalGradient(colors = listOf(ActivityGradientStart, ActivityGradientEnd))
private val WearSleepBrush = Brush.horizontalGradient(colors = listOf(SleepGradientStart, SleepGradientEnd))
private val WearStressBrush = Brush.horizontalGradient(colors = listOf(StressGradientStart, StressGradientMid, StressGradientEnd))

@Composable
fun WearApp(context: Context) {
    val scope = rememberCoroutineScope()
    var vitals by remember { mutableStateOf(RingConnComplicationProvider.getLatestVitals(context)) }
    var isMeasuring by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

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
                val startSync = WearDataListenerService.lastSyncCompletedMs.value
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
                val syncResult = withTimeoutOrNull(8500L) {
                    WearDataListenerService.lastSyncCompletedMs.first { it != startSync }
                }
                if (syncResult != null) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
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
    val listState = rememberScalingLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val stepsFormatted = remember(vitals.liveSteps) {
        NumberFormat.getIntegerInstance(Locale.getDefault()).format(vitals.liveSteps)
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
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    true
                }
                .focusRequester(focusRequester)
                .focusable(),
            state = listState,
            autoCentering = null,
            scalingParams = ScalingLazyColumnDefaults.scalingParams(
                edgeScale = 0.65f,
                edgeAlpha = 0.40f,
                minElementHeight = 0.2f,
                maxElementHeight = 0.65f,
                minTransitionArea = 0.35f,
                maxTransitionArea = 0.55f,
                scaleInterpolator = CubicBezierEasing(0.20f, 0.00f, 0.25f, 1.00f)
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 34.dp, bottom = 32.dp, start = 14.dp, end = 14.dp)
        ) {
            // =========================================================================
            // 1. One UI Watch 8 Top Header ("RingConn Health" + Ring Battery Pill)
            // =========================================================================
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontSize = TextBody,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(ShapePill)
                            .background(Color(0xFF1F2328))
                            .border(BorderStroke(1.dp, Color(0x22FFFFFF)), ShapePill)
                            .padding(horizontal = 10.dp, vertical = 2.5.dp)
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
                                fontSize = TextCaption,
                                style = TabularNums,
                                color = LabelSilver,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 1b. PRIME CAPSULE: Liquid Readiness / Body Battery Orb
            // =========================================================================
            item {
                val readiness = RingConnHistoryStore.computeReadinessScore(vitals)
                val readinessClamped = readiness.coerceIn(0, 100)
                val hasReadiness = readiness > 0

                val isSpanish = remember { Locale.getDefault().language.startsWith("es") }
                val (statusLabel, statusColor, cardBgBrush) = when {
                    !hasReadiness -> Triple(
                        if (isSpanish) "Sincronizando..." else "Syncing...",
                        LabelSilver,
                        Brush.horizontalGradient(listOf(Color(0xFF22262C), Color(0xFF181A1D)))
                    )
                    readinessClamped >= 75 -> Triple(
                        if (isSpanish) "Energía Óptima" else "Optimal Energy",
                        Color(0xFF00E676),
                        Brush.horizontalGradient(listOf(Color(0xFF0F3025), Color(0xFF071C15)))
                    )
                    readinessClamped in 45..74 -> Triple(
                        if (isSpanish) "Carga Equilibrada" else "Balanced Charge",
                        Color(0xFFFFB703),
                        Brush.horizontalGradient(listOf(Color(0xFF33260A), Color(0xFF1C1505)))
                    )
                    else -> Triple(
                        if (isSpanish) "Baja · Descanso" else "Low · Rest",
                        Color(0xFFFF5D99),
                        Brush.horizontalGradient(listOf(Color(0xFF331420), Color(0xFF1C0A12)))
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .clip(ShapePill)
                        .background(cardBgBrush)
                        .border(BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)), ShapePill)
                        .clickable {
                            context.startActivity(Intent(context, VitalsDetailActivity::class.java))
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LiquidReadinessOrb(
                            score = readinessClamped,
                            sizeDp = 48.dp,
                            showScoreText = true
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isSpanish) "ENERGÍA CORPORAL" else "BODY BATTERY",
                                fontSize = TextCaption,
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = statusLabel,
                                fontSize = TextBody,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val hrvText = if (vitals.lastHrv > 0) "HRV ${vitals.lastHrv}ms" else "HRV --"
                            val sleepWord = if (isSpanish) "Sueño" else "Sleep"
                            val sleepText = if (vitals.sleepScore > 0) "$sleepWord ${vitals.sleepScore}" else "$sleepWord --"
                            Text(
                                text = "$hrvText  ·  $sleepText",
                                fontSize = TextCaption,
                                style = TabularNums,
                                color = LabelSilver,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 2. CAPSULE 1: Heart Rate & SpO2 (One UI Watch 8 Vitals Capsule)
            // =========================================================================
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .clip(ShapePill)
                        .background(WearVitalsBrush)
                        .border(BorderStroke(1.dp, Color(0x33EDCB8B)), ShapePill)
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
                                val spo2Text = if (hasSpo2) "${stringResource(R.string.blood_oxygen_short)} ${vitals.lastSpo2}%" else "${stringResource(R.string.blood_oxygen_short)} --"
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
            }

            // =========================================================================
            // 3. CAPSULE 2: Daily Activity (Concentric Rings & Steps / Active / Calories)
            // =========================================================================
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .clip(ShapePill)
                        .background(WearActivityBrush)
                        .border(BorderStroke(1.dp, Color(0x33A0F116)), ShapePill)
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
                                        val activeMins = if (vitals.activeMinutes > 0) vitals.activeMinutes else if (vitals.liveSteps > 0) (vitals.liveSteps / 100).coerceAtLeast(1) else 0
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
            }

            // =========================================================================
            // 4. CAPSULE 3: Sleep Score & HRV
            // =========================================================================
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .clip(ShapePill)
                        .background(WearSleepBrush)
                        .border(BorderStroke(1.dp, Color(0x335E72E4)), ShapePill)
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
                                painter = painterResource(id = R.drawable.ic_vitals_sleep),
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
                                val hrvText = if (hasHrv) "  ·  ${stringResource(R.string.hrv_short)} ${vitals.lastHrv} ms" else "  ·  ${stringResource(R.string.hrv_short)} --"
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
            }

            // =========================================================================
            // 5. CAPSULE 4: Stress Index
            // =========================================================================
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .clip(ShapePill)
                        .background(WearStressBrush)
                        .border(BorderStroke(1.dp, Color(0x33A855F7)), ShapePill)
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
            }

            // =========================================================================
            // 6. CAPSULE 5: Skin Temperature (Emerald Mint Gradient - One UI Watch 8 match)
            // =========================================================================
            item {
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
                        .fillMaxWidth()
                        .height(74.dp)
                        .clip(ShapePill)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF144238), Color(0xFF0B2822))))
                        .border(BorderStroke(1.dp, Color(0x3300E676)), ShapePill)
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
 * Uses physiological lub-dub double-beat keyframes gated on lifecycle.
 */
@Composable
private fun PulsingHeartBadge(isMeasuring: Boolean) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val isResumed = lifecycleState == Lifecycle.State.RESUMED

    val cycleDurationMs = if (isMeasuring) 600 else 1000
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale = infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = cycleDurationMs
                1.0f at 0
                1.20f at (cycleDurationMs * 0.18f).toInt()
                0.96f at (cycleDurationMs * 0.32f).toInt()
                1.12f at (cycleDurationMs * 0.48f).toInt()
                1.0f at (cycleDurationMs * 0.65f).toInt()
                1.0f at cycleDurationMs
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .size(38.dp)
            .graphicsLayer {
                val scale = if (isResumed) pulseScale.value else 1.0f
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(GlassBlack50),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_vitals_hr),
            contentDescription = stringResource(R.string.heart_rate),
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Liquid Body Battery / Readiness Orb.
 * Renders a circular orb with a dual sine-wave liquid fill gently undulating at the fill level
 * corresponding to readiness / 100f.
 *
 * ZERO IDLE BATTERY DRAIN:
 * - Wave phase animation is strictly gated on Lifecycle.State.RESUMED, pausing the wave phase
 *   when in Ambient Mode or background.
 * - Path, Brush, and Stroke objects are hoisted with remember outside Canvas for zero per-frame heap allocations.
 * - Color shifts dynamically: emerald-cyan (>= 75), amber-gold (45..74), coral-rose (< 45).
 */
@Composable
fun LiquidReadinessOrb(
    score: Int,
    modifier: Modifier = Modifier,
    sizeDp: androidx.compose.ui.unit.Dp = 52.dp,
    showScoreText: Boolean = true
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val isResumed = lifecycleState == Lifecycle.State.RESUMED

    val clampedScore = score.coerceIn(0, 100)
    val hasData = score > 0
    val fillLevel = if (hasData) (clampedScore / 100f).coerceIn(0.08f, 0.94f) else 0.06f

    val animatedFill = remember { Animatable(fillLevel) }
    LaunchedEffect(fillLevel) {
        animatedFill.animateTo(
            targetValue = fillLevel,
            animationSpec = tween(
                durationMillis = 800,
                easing = CubicBezierEasing(0.22f, 0.25f, 0.0f, 1.0f)
            )
        )
    }

    val phaseAnim = remember { Animatable(0f) }
    LaunchedEffect(isResumed) {
        if (isResumed) {
            val twoPi = (2.0 * Math.PI).toFloat()
            while (true) {
                phaseAnim.animateTo(
                    targetValue = phaseAnim.value + twoPi,
                    animationSpec = tween(durationMillis = 2800, easing = LinearEasing)
                )
                phaseAnim.snapTo(phaseAnim.value % twoPi)
            }
        }
    }

    val (colorWaveBack, colorWaveFront, accentColor, bgTint) = remember(clampedScore >= 75, clampedScore in 45..74, hasData) {
        when {
            !hasData -> listOf(
                Color(0x664A5568),
                Color(0x99718096),
                Color(0xFF718096),
                Color(0x1F718096)
            )
            clampedScore >= 75 -> listOf(
                Color(0x9900B0FF), // wave back (cyan)
                Color(0xDD00E676), // wave front (emerald)
                Color(0xFF00E676), // accent / border
                Color(0x1F00E676)  // bg tint
            )
            clampedScore in 45..74 -> listOf(
                Color(0x99FFD166), // wave back (gold)
                Color(0xDDFFB703), // wave front (amber)
                Color(0xFFFFB703), // accent / border
                Color(0x1FFFB703)  // bg tint
            )
            else -> listOf(
                Color(0x99FF6B6B), // wave back (coral)
                Color(0xDDFF5D99), // wave front (rose)
                Color(0xFFFF5D99), // accent / border
                Color(0x1FFF5D99)  // bg tint
            )
        }
    }

    val wavePath1 = remember { androidx.compose.ui.graphics.Path() }
    val wavePath2 = remember { androidx.compose.ui.graphics.Path() }
    val circleClipPath = remember { androidx.compose.ui.graphics.Path() }

    val density = LocalDensity.current
    val strokeWidthPx = remember(density) { with(density) { 2.dp.toPx() } }
    val orbBorderStroke = remember(strokeWidthPx) {
        Stroke(width = strokeWidthPx)
    }

    val wave1Brush = remember(colorWaveFront) { SolidColor(colorWaveFront) }
    val wave2Brush = remember(colorWaveBack) { SolidColor(colorWaveBack) }
    val borderBrush = remember(accentColor) { SolidColor(accentColor) }

    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val currentPhase = phaseAnim.value
            val currentFill = animatedFill.value
            val baseWaterY = h * (1f - currentFill)
            val amplitude = h * 0.055f
            val waveFreq = (2f * Math.PI.toFloat()) / w

            circleClipPath.reset()
            circleClipPath.addOval(Rect(0f, 0f, w, h))

            // Wave 1: sin(x * freq + phase)
            wavePath1.reset()
            wavePath1.moveTo(0f, h)
            wavePath1.lineTo(0f, baseWaterY)
            var x1 = 0f
            val stepPx = 4f
            while (x1 <= w) {
                val y1 = baseWaterY + amplitude * kotlin.math.sin(x1 * waveFreq + currentPhase)
                wavePath1.lineTo(x1, y1)
                x1 += stepPx
            }
            wavePath1.lineTo(w, h)
            wavePath1.close()

            // Wave 2: cos(x * freq - phase * 1.3f)
            wavePath2.reset()
            wavePath2.moveTo(0f, h)
            wavePath2.lineTo(0f, baseWaterY)
            var x2 = 0f
            while (x2 <= w) {
                val y2 = baseWaterY + (amplitude * 0.85f) * kotlin.math.cos(x2 * waveFreq - currentPhase * 1.3f)
                wavePath2.lineTo(x2, y2)
                x2 += stepPx
            }
            wavePath2.lineTo(w, h)
            wavePath2.close()

            clipPath(circleClipPath) {
                drawCircle(color = bgTint)
                drawPath(path = wavePath2, brush = wave2Brush)
                drawPath(path = wavePath1, brush = wave1Brush)
            }

            val radius = (w - strokeWidthPx) / 2f
            val centerOffset = Offset(w / 2f, h / 2f)
            drawCircle(
                brush = borderBrush,
                radius = radius,
                center = centerOffset,
                style = orbBorderStroke
            )
        }

        if (showScoreText) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (hasData) "$clampedScore" else "--",
                    fontSize = if (sizeDp > 70.dp) TextHero else if (sizeDp > 46.dp) TextBig else TextBody,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    style = TabularNums
                )
                if (sizeDp > 70.dp) {
                    Text(
                        text = "READY",
                        fontSize = TextCaption,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }
        }
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
