package io.github.hardbas.wearsync.bridge

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.google.android.gms.wearable.Wearable
import io.github.hardbas.wearsync.bridge.data.RingConnVitals
import io.github.hardbas.wearsync.bridge.service.RingConnAccessibilityService
import io.github.hardbas.wearsync.bridge.service.RingConnForegroundService
import io.github.hardbas.wearsync.bridge.service.RingConnTelemetryReader
import io.github.hardbas.wearsync.bridge.service.RingConnWidgetHostReader
import io.github.hardbas.wearsync.bridge.service.WearDataSyncService
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class PhoneBridgeMainActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // UI Badges & Status
    private lateinit var headerBadge: TextView
    private lateinit var hcStatusText: TextView
    private lateinit var connectHcBtn: Button
    private lateinit var syncStatusText: TextView
    private lateinit var sourceBadgeText: TextView
    private lateinit var accessibilityStatusText: TextView

    // Bento Card Value & Subtitle TextViews
    private lateinit var hrValText: TextView
    private lateinit var hrSubText: TextView
    private lateinit var spo2ValText: TextView
    private lateinit var spo2SubText: TextView
    private lateinit var hrvValText: TextView
    private lateinit var hrvSubText: TextView
    private lateinit var sleepValText: TextView
    private lateinit var sleepSubText: TextView
    private lateinit var actValText: TextView
    private lateinit var actSubText: TextView
    private lateinit var stepsValText: TextView
    private lateinit var stepsSubText: TextView
    private lateinit var stressValText: TextView
    private lateinit var stressSubText: TextView
    private lateinit var batteryValText: TextView
    private lateinit var batterySubText: TextView

    // Manual Calibration Collapsible Inputs
    private lateinit var calibrationContainer: LinearLayout
    private lateinit var hrInput: EditText
    private lateinit var spo2Input: EditText
    private lateinit var hrvInput: EditText
    private lateinit var sleepInput: EditText
    private lateinit var activityInput: EditText
    private lateinit var stressInput: EditText
    private lateinit var stepsInput: EditText
    private lateinit var batteryInput: EditText

    // Health Connect Permission Launcher
    private val requestPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        Log.i(TAG, "Health Connect permissions result: $grantedPermissions")
        checkAndUpdateHealthConnectStatus()
        loadAndSyncVitals(forceHealthConnect = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Iniciar servicio 24/7 en segundo plano de forma protegida
        try {
            RingConnForegroundService.start(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service on launch", e)
        }

        // 2. Solicitar permisos de runtime adicionales si faltan
        requestBridgePermissions()

        // 3. ScrollView configurado para scroll suave sin bloqueos
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
            setBackgroundColor(Color.parseColor("#090A0F"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(dp(16), dp(24), dp(16), dp(48))
        }

        // ==========================================
        // 1. HERO APP HEADER
        // ==========================================
        val logoView = ImageView(this).apply {
            setImageResource(R.drawable.ic_app_logo)
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)).apply {
                bottomMargin = dp(12)
            }
        }
        root.addView(logoView)

        val titleView = TextView(this).apply {
            text = "RingConn Wear Sync"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(titleView)

        val subtitleView = TextView(this).apply {
            text = "Galaxy Watch 8 Classic • One UI 8 Bridge"
            textSize = 13f
            setTextColor(Color.parseColor("#8E95A5"))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(10))
        }
        root.addView(subtitleView)

        headerBadge = TextView(this).apply {
            text = "Iniciando Puente..."
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00E676"))
            background = createCardBackground(
                bgColor = Color.parseColor("#10251C"),
                strokeColor = Color.parseColor("#1B5E20"),
                radiusDp = 16f
            )
            setPadding(dp(14), dp(6), dp(14), dp(6))
            gravity = Gravity.CENTER
        }
        root.addView(headerBadge)

        val heroSpacer = Space(this).apply { minimumHeight = dp(20) }
        root.addView(heroSpacer)

        // ==========================================
        // 2. HEALTH CONNECT STATUS & ACTION CARD
        // ==========================================
        val hcCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground(
                bgColor = Color.parseColor("#141824"),
                strokeColor = Color.parseColor("#222A3F"),
                radiusDp = 20f
            )
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        }

        val hcHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val hcTitle = TextView(this).apply {
            text = "Health Connect • Sincronización Real"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E0E6F0"))
        }
        hcHeaderRow.addView(hcTitle)
        hcCard.addView(hcHeaderRow)

        hcStatusText = TextView(this).apply {
            text = "Comprobando permisos de Health Connect..."
            textSize = 12.5f
            setTextColor(Color.parseColor("#A5B0CC"))
            setPadding(0, dp(6), 0, dp(14))
        }
        hcCard.addView(hcStatusText)

        val hcButtonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        connectHcBtn = Button(this).apply {
            text = "Conectar con Health Connect (Lectura Automática)"
            setTextColor(Color.parseColor("#090A0F"))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            background = createRipplePill(
                bgColor = Color.parseColor("#00E5FF"),
                radiusDp = 24f
            )
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener {
                requestHealthConnectPermissions()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }
        hcButtonsRow.addView(connectHcBtn)

        val openRingConnBtn = Button(this).apply {
            text = "Abrir RingConn Oficial (Forzar Lectura del Anillo)"
            setTextColor(Color.parseColor("#A5B0CC"))
            textSize = 12.5f
            background = createCardBackground(
                bgColor = Color.parseColor("#1A2030"),
                strokeColor = Color.parseColor("#2D3750"),
                radiusDp = 24f
            )
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener {
                openOfficialRingConnApp()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        hcButtonsRow.addView(openRingConnBtn)
        hcCard.addView(hcButtonsRow)
        root.addView(hcCard)

        // ==========================================
        // 2B. DIRECT EXTRACTION CARD (AppWidgetHost + Accessibility)
        // ==========================================
        val directCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground(
                bgColor = Color.parseColor("#121927"),
                strokeColor = Color.parseColor("#1D3354"),
                radiusDp = 20f
            )
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        }

        val directTitle = TextView(this).apply {
            text = "Extracción Nativa Directa (100% Exacto)"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00E5FF"))
        }
        directCard.addView(directTitle)

        accessibilityStatusText = TextView(this).apply {
            text = "• WidgetHost: Activo (BIND_APPWIDGET)\n• Accesibilidad: Comprobando..."
            textSize = 12f
            setTextColor(Color.parseColor("#A5B0CC"))
            setPadding(0, dp(6), 0, dp(12))
        }
        directCard.addView(accessibilityStatusText)

        val directBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val readWidgetsBtn = Button(this).apply {
            text = "🎯 Leer Widgets Directos (AppWidgetHost)"
            setTextColor(Color.parseColor("#090A0F"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = createRipplePill(
                bgColor = Color.parseColor("#00E5FF"),
                radiusDp = 24f
            )
            setPadding(dp(16), dp(11), dp(16), dp(11))
            setOnClickListener {
                directWidgetExtraction()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }
        directBtnRow.addView(readWidgetsBtn)

        val scrapeAppBtn = Button(this).apply {
            text = "⚡ Sincronizar desde RingConn (Accesibilidad)"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            background = createCardBackground(
                bgColor = Color.parseColor("#1B293E"),
                strokeColor = Color.parseColor("#2B4870"),
                radiusDp = 24f
            )
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener {
                scrapeFromRingConnApp()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        directBtnRow.addView(scrapeAppBtn)
        directCard.addView(directBtnRow)
        root.addView(directCard)

        // ==========================================
        // 3. LIVE WATCH SYNC ACTION CARD
        // ==========================================
        val syncCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground(
                bgColor = Color.parseColor("#141824"),
                strokeColor = Color.parseColor("#222A3F"),
                radiusDp = 20f
            )
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16)
            }
        }

        val syncBtn = Button(this).apply {
            text = "⚡ Sincronizar Ahora con Galaxy Watch 8"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#8A2BE2"), Color.parseColor("#E040FB"))
            ).apply {
                cornerRadius = dp(28).toFloat()
            }
            setPadding(dp(20), dp(15), dp(20), dp(15))
            setOnClickListener {
                loadAndSyncVitals(forceHealthConnect = true)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        syncCard.addView(syncBtn)

        syncStatusText = TextView(this).apply {
            text = "Última sincronización: Pendiente..."
            textSize = 12.5f
            setTextColor(Color.parseColor("#8E95A5"))
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(4))
        }
        syncCard.addView(syncStatusText)

        sourceBadgeText = TextView(this).apply {
            text = "Fuente: Consultando..."
            textSize = 11.5f
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
        }
        syncCard.addView(sourceBadgeText)
        root.addView(syncCard)

        // ==========================================
        // 4. 2x4 BENTO GRID / METRIC CARDS
        // ==========================================
        val gridTitle = TextView(this).apply {
            text = "TELEMETRÍA EN VIVO"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#6C7387"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
                leftMargin = dp(4)
            }
        }
        root.addView(gridTitle)

        // Row 1: FC & SpO2
        val row1 = createBentoRow()
        val cardHr = createMetricCard("FRECUENCIA CARDÍACA", "#FF3B30")
        hrValText = cardHr.first
        hrSubText = cardHr.second
        val cardSpo2 = createMetricCard("OXÍGENO SpO2", "#00E5FF")
        spo2ValText = cardSpo2.first
        spo2SubText = cardSpo2.second
        row1.addView(cardHr.third)
        row1.addView(cardSpo2.third)
        root.addView(row1)

        // Row 2: VFC / HRV & Sueño
        val row2 = createBentoRow()
        val cardHrv = createMetricCard("VFC / HRV RMSSD", "#AF52DE")
        hrvValText = cardHrv.first
        hrvSubText = cardHrv.second
        val cardSleep = createMetricCard("PUNTAJE DE SUEÑO", "#5E5CE6")
        sleepValText = cardSleep.first
        sleepSubText = cardSleep.second
        row2.addView(cardHrv.third)
        row2.addView(cardSleep.third)
        root.addView(row2)

        // Row 3: Actividad & Pasos
        val row3 = createBentoRow()
        val cardAct = createMetricCard("PUNTAJE ACTIVIDAD", "#FF9F0A")
        actValText = cardAct.first
        actSubText = cardAct.second
        val cardSteps = createMetricCard("PASOS HOY", "#30E565")
        stepsValText = cardSteps.first
        stepsSubText = cardSteps.second
        row3.addView(cardAct.third)
        row3.addView(cardSteps.third)
        root.addView(row3)

        // Row 4: Estrés & Batería
        val row4 = createBentoRow()
        val cardStress = createMetricCard("ÍNDICE DE ESTRÉS", "#FFD60A")
        stressValText = cardStress.first
        stressSubText = cardStress.second
        val cardBat = createMetricCard("BATERÍA DEL ANILLO", "#34C759")
        batteryValText = cardBat.first
        batterySubText = cardBat.second
        row4.addView(cardStress.third)
        row4.addView(cardBat.third)
        root.addView(row4)

        // ==========================================
        // 5. COLLAPSIBLE MANUAL CALIBRATION SECTION
        // ==========================================
        val toggleCalibrationBtn = Button(this).apply {
            text = "⚙️ Calibración / Modo Manual (Avanzado) ▼"
            setTextColor(Color.parseColor("#8E95A5"))
            textSize = 12f
            background = createCardBackground(
                bgColor = Color.parseColor("#11141D"),
                strokeColor = Color.parseColor("#1C2230"),
                radiusDp = 18f
            )
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
                bottomMargin = dp(8)
            }
            setOnClickListener {
                val isVisible = calibrationContainer.visibility == View.VISIBLE
                calibrationContainer.visibility = if (isVisible) View.GONE else View.VISIBLE
                text = if (isVisible) {
                    "⚙️ Calibración / Modo Manual (Avanzado) ▼"
                } else {
                    "⚙️ Ocultar Calibración Manual ▲"
                }
            }
        }
        root.addView(toggleCalibrationBtn)

        calibrationContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE // Hidden initially so keyboard never pops up or blocks touch scrolling!
            background = createCardBackground(
                bgColor = Color.parseColor("#11141D"),
                strokeColor = Color.parseColor("#1C2230"),
                radiusDp = 18f
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        }

        val initialVitals = RingConnTelemetryReader.readBestAvailableVitals(this)
        hrInput = createInputField("Frecuencia Cardíaca (BPM):", "${initialVitals.lastHeartRate}", calibrationContainer)
        spo2Input = createInputField("Oxígeno en Sangre SpO2 (%):", "${initialVitals.lastSpo2}", calibrationContainer)
        hrvInput = createInputField("VFC / HRV (ms):", "${initialVitals.lastHrv}", calibrationContainer)
        sleepInput = createInputField("Puntaje de Sueño (pts):", "${initialVitals.sleepScore}", calibrationContainer)
        activityInput = createInputField("Puntaje de Actividad (pts):", "${initialVitals.activityScore}", calibrationContainer)
        stressInput = createInputField("Índice de Estrés (pts):", "${initialVitals.stressScore}", calibrationContainer)
        stepsInput = createInputField("Pasos del Día:", "${initialVitals.liveSteps}", calibrationContainer)
        batteryInput = createInputField("Batería del Anillo (%):", "${initialVitals.ringBattery}", calibrationContainer)

        val saveManualBtn = Button(this).apply {
            text = "Guardar y Forzar Sincronización Manual"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            background = createRipplePill(Color.parseColor("#4A148C"), 24f)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener {
                syncManualCalibration()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        }
        calibrationContainer.addView(saveManualBtn)
        root.addView(calibrationContainer)

        // ==========================================
        // 6. 24/7 BATTERY OPTIMIZATION EXEMPTION
        // ==========================================
        val batteryOptBtn = Button(this).apply {
            text = "🛡️ Garantizar 24/7 (Sin Restricciones Samsung)"
            setTextColor(Color.parseColor("#8E95A5"))
            textSize = 12f
            background = createCardBackground(
                bgColor = Color.parseColor("#11141D"),
                strokeColor = Color.parseColor("#1C2230"),
                radiusDp = 20f
            )
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener {
                requestIgnoreBatteryOptimizations()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16)
            }
        }
        root.addView(batteryOptBtn)

        val footerText = TextView(this).apply {
            text = "RingConn Wear Sync • v1.0.0 (Health Connect Engine)"
            textSize = 11f
            setTextColor(Color.parseColor("#505666"))
            gravity = Gravity.CENTER
        }
        root.addView(footerText)

        scroll.addView(root)
        setContentView(scroll)

        // Inicializar estado de permisos y sincronizar
        checkAndUpdateHealthConnectStatus()
        checkAndUpdateAccessibilityStatus()
        loadAndSyncVitals(forceHealthConnect = true)
    }

    override fun onResume() {
        super.onResume()
        checkAndUpdateHealthConnectStatus()
        checkAndUpdateAccessibilityStatus()
        loadAndSyncVitals(forceHealthConnect = false)
    }

    private fun checkAndUpdateHealthConnectStatus() {
        scope.launch {
            if (!RingConnTelemetryReader.isHealthConnectAvailable(this@PhoneBridgeMainActivity)) {
                hcStatusText.text = "⚠️ Health Connect no está instalado o requiere actualización."
                hcStatusText.setTextColor(Color.parseColor("#FF5252"))
                connectHcBtn.text = "Abrir Configuración de Salud"
                return@launch
            }

            val hasAll = RingConnTelemetryReader.hasRequiredPermissions(this@PhoneBridgeMainActivity)
            if (hasAll) {
                hcStatusText.text = "✅ Sincronización Automática Health Connect Activa"
                hcStatusText.setTextColor(Color.parseColor("#00E676"))
                connectHcBtn.visibility = View.GONE
                headerBadge.text = "Health Connect Activo"
                headerBadge.setTextColor(Color.parseColor("#00E676"))
            } else {
                hcStatusText.text = "⚠️ Permiso de Health Connect Requerido para leer datos reales"
                hcStatusText.setTextColor(Color.parseColor("#FFD60A"))
                connectHcBtn.visibility = View.VISIBLE
                connectHcBtn.text = "Conectar con Health Connect (Lectura Automática)"
                headerBadge.text = "Permiso Requerido"
                headerBadge.setTextColor(Color.parseColor("#FFD60A"))
            }
        }
    }

    private fun requestBridgePermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(permissionsToRequest.toTypedArray(), 105)
        }
    }

    private fun requestHealthConnectPermissions() {
        scope.launch {
            if (!RingConnTelemetryReader.isHealthConnectAvailable(this@PhoneBridgeMainActivity)) {
                Toast.makeText(this@PhoneBridgeMainActivity, "Health Connect no disponible en este dispositivo.", Toast.LENGTH_LONG).show()
                return@launch
            }
            try {
                requestPermissionLauncher.launch(RingConnTelemetryReader.CORE_HEALTH_PERMISSIONS)
            } catch (e: Exception) {
                Log.e(TAG, "Error launching permission request", e)
                try {
                    val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
                    startActivity(intent)
                } catch (ex: Exception) {
                    Toast.makeText(this@PhoneBridgeMainActivity, "Abra Ajustes > Health Connect para conceder permisos.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabledServices.contains(packageName) || RingConnAccessibilityService.isRunning()
    }

    private fun checkAndUpdateAccessibilityStatus() {
        val isEnabled = isAccessibilityServiceEnabled()
        if (isEnabled) {
            accessibilityStatusText.text = "• WidgetHost: Activo (BIND_APPWIDGET)\n• Accesibilidad: Activa (100% Exacto) ✅"
            accessibilityStatusText.setTextColor(Color.parseColor("#00E676"))
        } else {
            accessibilityStatusText.text = "• WidgetHost: Activo (BIND_APPWIDGET)\n• Accesibilidad: Requiere habilitar vía ADB / Ajustes"
            accessibilityStatusText.setTextColor(Color.parseColor("#FFD60A"))
        }
    }

    private fun directWidgetExtraction() {
        scope.launch {
            syncStatusText.text = "Leyendo widgets de RingConn (AppWidgetHost)..."
            val vitals = withContext(Dispatchers.IO) {
                RingConnWidgetHostReader.readRingConnWidgets(this@PhoneBridgeMainActivity)
            }
            if (vitals != null) {
                updateMetricCards(vitals)
                sourceBadgeText.text = "Fuente: RingConn WidgetHost (100% Exacto)"
                syncStatusText.text = "✅ Sincronizado directo desde widgets"
                syncStatusText.setTextColor(Color.parseColor("#30E565"))
                Toast.makeText(this@PhoneBridgeMainActivity, "¡Datos exactos leídos de widgets!", Toast.LENGTH_SHORT).show()
            } else {
                syncStatusText.text = "⚠️ No se detectaron valores en widgets"
                syncStatusText.setTextColor(Color.parseColor("#FF9F0A"))
                Toast.makeText(this@PhoneBridgeMainActivity, "Verifica que el widget de RingConn esté configurado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scrapeFromRingConnApp() {
        scope.launch {
            val scraped = RingConnAccessibilityService.triggerImmediateScrape()
            if (scraped != null) {
                updateMetricCards(scraped)
                sourceBadgeText.text = "Fuente: RingConn Directo (100% Exacto)"
                syncStatusText.text = "✅ Telemetría exacta capturada"
                syncStatusText.setTextColor(Color.parseColor("#30E565"))
                Toast.makeText(this@PhoneBridgeMainActivity, "¡Telemetría 100% exacta capturada!", Toast.LENGTH_SHORT).show()
            } else {
                openOfficialRingConnApp()
                Toast.makeText(this@PhoneBridgeMainActivity, "Abriendo RingConn... El servicio extraerá los datos al instante", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openOfficialRingConnApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(RingConnTelemetryReader.RINGCONN_PKG)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "La app de RingConn no está instalada en este dispositivo.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadAndSyncVitals(forceHealthConnect: Boolean) {
        scope.launch {
            syncStatusText.text = "Consultando telemetría de Health Connect..."

            val vitals = withContext(Dispatchers.IO) {
                // Priority 1: Direct AppWidgetHost extraction (zero credentials, 100% exact)
                val widgetVitals = try {
                    RingConnWidgetHostReader.readRingConnWidgets(this@PhoneBridgeMainActivity)
                } catch (e: Exception) {
                    null
                }

                if (widgetVitals != null && (widgetVitals.lastHeartRate > 0 || widgetVitals.sleepScore > 0 || widgetVitals.liveSteps > 0)) {
                    widgetVitals
                } else if (forceHealthConnect) {
                    RingConnTelemetryReader.readFromHealthConnect(this@PhoneBridgeMainActivity)
                        ?: RingConnTelemetryReader.readBestAvailableVitalsSuspend(this@PhoneBridgeMainActivity)
                } else {
                    RingConnTelemetryReader.readBestAvailableVitalsSuspend(this@PhoneBridgeMainActivity)
                }
            }

            updateMetricCards(vitals)

            val prefs = getSharedPreferences(RingConnTelemetryReader.PREFS_NAME, Context.MODE_PRIVATE)
            val source = prefs.getString(RingConnTelemetryReader.KEY_LAST_SYNC_SOURCE, "Health Connect") ?: "Health Connect"
            val timeStr = prefs.getString(RingConnTelemetryReader.KEY_LAST_SYNC_TIME_STR, "--:--") ?: "--:--"

            sourceBadgeText.text = "Fuente: $source"

            syncStatusText.text = "Enviando al Galaxy Watch 8..."
            val result = withContext(Dispatchers.IO) {
                try {
                    val nodes = Wearable.getNodeClient(this@PhoneBridgeMainActivity).connectedNodes.await()
                    val targetName = if (nodes.isEmpty()) "Galaxy Watch 8" else nodes.joinToString { it.displayName }
                    val ok = WearDataSyncService(this@PhoneBridgeMainActivity).syncVitalsToWatch(vitals, force = true)
                    ok to targetName
                } catch (e: Exception) {
                    false to (e.message ?: "Error desconocido")
                }
            }

            if (result.first) {
                syncStatusText.text = "✅ Sincronizado ($timeStr) • ${result.second}"
                syncStatusText.setTextColor(Color.parseColor("#30E565"))
                headerBadge.text = "Conectado en Vivo"
                headerBadge.setTextColor(Color.parseColor("#00E676"))
            } else {
                syncStatusText.text = "⚠️ En espera de conexión con Watch (${result.second})"
                syncStatusText.setTextColor(Color.parseColor("#FF9F0A"))
            }
        }
    }

    private fun syncManualCalibration() {
        scope.launch {
            syncStatusText.text = "Aplicando calibración manual..."
            val hr = hrInput.text.toString().toIntOrNull() ?: 72
            val spo2 = spo2Input.text.toString().toIntOrNull() ?: 98
            val hrv = hrvInput.text.toString().toIntOrNull() ?: 50
            val sleep = sleepInput.text.toString().toIntOrNull() ?: 80
            val act = activityInput.text.toString().toIntOrNull() ?: 70
            val stress = stressInput.text.toString().toIntOrNull() ?: 40
            val steps = stepsInput.text.toString().toIntOrNull() ?: 5000
            val battery = batteryInput.text.toString().toIntOrNull() ?: 85

            val vitals = RingConnVitals(
                lastHeartRate = hr,
                avgHeartRate = hr,
                lastSpo2 = spo2,
                avgSpo2 = spo2,
                lastHrv = hrv,
                avgHrv = hrv,
                sleepScore = sleep,
                activityScore = act,
                stressScore = stress,
                liveSteps = steps,
                targetSteps = 10000,
                liveCalorie = (steps * 0.04).toInt(),
                targetCalorie = 500,
                ringBattery = battery,
                isCharging = false,
                isConnected = true,
                refreshTimestamp = System.currentTimeMillis() / 1000L
            )

            RingConnTelemetryReader.saveToPreferences(this@PhoneBridgeMainActivity, vitals, source = "Calibración Manual")
            updateMetricCards(vitals)

            val ok = withContext(Dispatchers.IO) {
                WearDataSyncService(this@PhoneBridgeMainActivity).syncVitalsToWatch(vitals)
            }

            sourceBadgeText.text = "Fuente: Calibración Manual"
            syncStatusText.text = if (ok) {
                "✅ Calibración enviada con éxito al Watch"
            } else {
                "⚠️ Guardado en teléfono (en espera de Watch)"
            }
            syncStatusText.setTextColor(if (ok) Color.parseColor("#30E565") else Color.parseColor("#FF9F0A"))
            Toast.makeText(this@PhoneBridgeMainActivity, "¡Calibración guardada!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateMetricCards(vitals: RingConnVitals) {
        // Heart Rate
        hrValText.text = if (vitals.lastHeartRate > 0) "${vitals.lastHeartRate}" else "--"
        hrSubText.text = if (vitals.avgHeartRate > 0) "BPM • Prom: ${vitals.avgHeartRate}" else "BPM"

        // SpO2
        spo2ValText.text = if (vitals.lastSpo2 > 0) "${vitals.lastSpo2}%" else "--"
        spo2SubText.text = if (vitals.avgSpo2 > 0) "Oxígeno • Prom: ${vitals.avgSpo2}%" else "Oxígeno"

        // HRV
        hrvValText.text = if (vitals.lastHrv > 0) "${vitals.lastHrv}" else "--"
        hrvSubText.text = if (vitals.avgHrv > 0) "ms • Prom: ${vitals.avgHrv}" else "ms RMSSD"

        // Sleep
        sleepValText.text = if (vitals.sleepScore > 0) "${vitals.sleepScore}" else "--"
        sleepSubText.text = when {
            vitals.sleepScore >= 85 -> "Pts • Óptimo"
            vitals.sleepScore >= 70 -> "Pts • Bueno"
            vitals.sleepScore > 0 -> "Pts • Regular"
            else -> "Pts"
        }

        // Activity
        actValText.text = if (vitals.activityScore > 0) "${vitals.activityScore}" else "--"
        actSubText.text = "Puntos • Meta: 100"

        // Steps
        stepsValText.text = if (vitals.liveSteps > 0) String.format("%,d", vitals.liveSteps) else "0"
        stepsSubText.text = "Pasos • ${vitals.liveCalorie} kcal"

        // Stress
        stressValText.text = if (vitals.stressScore > 0) "${vitals.stressScore}" else "--"
        stressSubText.text = when {
            vitals.stressScore < 30 -> "Pts • Relajado"
            vitals.stressScore < 60 -> "Pts • Normal"
            vitals.stressScore > 0 -> "Pts • Elevado"
            else -> "Pts"
        }

        // Battery
        batteryValText.text = if (vitals.ringBattery > 0) "${vitals.ringBattery}%" else "--"
        batterySubText.text = if (vitals.isCharging) "Cargando..." else "En uso"

        // Inputs
        if (hrInput.text.isEmpty() || hrInput.text.toString() == "0") {
            hrInput.setText("${vitals.lastHeartRate}")
            spo2Input.setText("${vitals.lastSpo2}")
            hrvInput.setText("${vitals.lastHrv}")
            sleepInput.setText("${vitals.sleepScore}")
            activityInput.setText("${vitals.activityScore}")
            stressInput.setText("${vitals.stressScore}")
            stepsInput.setText("${vitals.liveSteps}")
            batteryInput.setText("${vitals.ringBattery}")
        }
    }

    private fun createBentoRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        }
    }

    private fun createMetricCard(label: String, accentHex: String): Triple<TextView, TextView, View> {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground(
                bgColor = Color.parseColor("#141824"),
                strokeColor = Color.parseColor("#222A3F"),
                radiusDp = 18f
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
            }
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(accentHex))
            }
            layoutParams = LinearLayout.LayoutParams(dp(7), dp(7)).apply {
                rightMargin = dp(6)
            }
        }
        topRow.addView(dot)

        val lbl = TextView(this).apply {
            text = label
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#8E95A5"))
        }
        topRow.addView(lbl)
        card.addView(topRow)

        val valueView = TextView(this).apply {
            text = "--"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(6), 0, dp(2))
        }
        card.addView(valueView)

        val subView = TextView(this).apply {
            text = ""
            textSize = 11.5f
            setTextColor(Color.parseColor("#A5B0CC"))
        }
        card.addView(subView)

        return Triple(valueView, subView, card)
    }

    private fun createInputField(label: String, defaultValue: String, parent: LinearLayout): EditText {
        val lbl = TextView(this).apply {
            text = label
            textSize = 11.5f
            setTextColor(Color.parseColor("#8E95A5"))
            setPadding(dp(4), dp(8), dp(4), dp(3))
        }
        val edit = EditText(this).apply {
            setText(defaultValue)
            setTextColor(Color.WHITE)
            textSize = 15f
            background = createCardBackground(
                bgColor = Color.parseColor("#1B2130"),
                strokeColor = Color.parseColor("#2C364E"),
                radiusDp = 12f
            )
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        parent.addView(lbl)
        parent.addView(edit)
        return edit
    }

    private fun createCardBackground(bgColor: Int, strokeColor: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(bgColor)
            setStroke(dp(1), strokeColor)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }
    }

    private fun createRipplePill(bgColor: Int, radiusDp: Float): RippleDrawable {
        val content = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }
        return RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), content, mask)
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "¡Ya cuenta con permiso de batería sin restricciones!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun dp(dpVal: Int): Int {
        return (dpVal * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PhoneBridgeMain"
    }
}
