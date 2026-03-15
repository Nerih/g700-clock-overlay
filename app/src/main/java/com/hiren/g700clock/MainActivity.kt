package com.hiren.g700clock

import android.app.Presentation
import android.content.Context
import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiren.g700clock.ui.theme.G700ClockOverlayTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val PREFS_NAME = "g700_clock_prefs"
private const val KEY_OFFSET_X = "offset_x"
private const val KEY_OFFSET_Y = "offset_y"
private const val KEY_FONT_SIZE = "font_size"
private const val KEY_USE_24H = "use_24h"
private const val KEY_SHOW_SECONDS = "show_seconds"
private const val KEY_SELECTED_DISPLAY_ID = "selected_display_id"
private const val KEY_BACKGROUND_BLACK = "background_black"

data class ClockConfig(
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val fontSizeSp: Int = 96,
    val use24Hour: Boolean = true,
    val showSeconds: Boolean = true,
    val backgroundBlack: Boolean = true
)

data class DisplayDebugInfo(
    val displayId: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val flags: Int,
    val state: Int,
    val isMain: Boolean
)

object ClockStateStore {
    private val _config = MutableStateFlow(ClockConfig())
    val config: StateFlow<ClockConfig> = _config

    fun update(newConfig: ClockConfig) {
        _config.value = newConfig
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var displayManager: DisplayManager

    private val availableDisplays = MutableStateFlow<List<DisplayDebugInfo>>(emptyList())
    private val selectedDisplayId = MutableStateFlow<Int?>(null)

    private var clockPresentation: ClockPresentation? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            refreshDisplays()
            ensurePresentation()
        }

        override fun onDisplayRemoved(displayId: Int) {
            refreshDisplays()
            ensurePresentation()
        }

        override fun onDisplayChanged(displayId: Int) {
            refreshDisplays()
            ensurePresentation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

        ClockStateStore.update(loadConfig())
        selectedDisplayId.value = loadSelectedDisplayId()

        displayManager.registerDisplayListener(displayListener, null)
        refreshDisplays()
        ensurePresentation()

        setContent {
            G700ClockOverlayTheme {
                MainControlScreen(
                    configFlow = ClockStateStore.config,
                    displaysFlow = availableDisplays,
                    selectedDisplayIdFlow = selectedDisplayId,
                    onConfigChanged = { newConfig ->
                        ClockStateStore.update(newConfig)
                        saveConfig(newConfig)
                    },
                    onNudge = { dx, dy ->
                        val current = ClockStateStore.config.value
                        val updated = current.copy(
                            offsetX = current.offsetX + dx,
                            offsetY = current.offsetY + dy
                        )
                        ClockStateStore.update(updated)
                        saveConfig(updated)
                    },
                    onPreset = { x, y ->
                        val current = ClockStateStore.config.value
                        val updated = current.copy(offsetX = x, offsetY = y)
                        ClockStateStore.update(updated)
                        saveConfig(updated)
                    },
                    onSelectDisplay = { displayId ->
                        selectedDisplayId.value = displayId
                        saveSelectedDisplayId(displayId)
                        ensurePresentation(forceRecreate = true)
                    },
                    onRefreshDisplays = {
                        refreshDisplays()
                        ensurePresentation(forceRecreate = true)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        displayManager.unregisterDisplayListener(displayListener)
        dismissPresentation()
    }

    private fun loadConfig(): ClockConfig {
        return ClockConfig(
            offsetX = prefs.getInt(KEY_OFFSET_X, 0),
            offsetY = prefs.getInt(KEY_OFFSET_Y, 0),
            fontSizeSp = prefs.getInt(KEY_FONT_SIZE, 96),
            use24Hour = prefs.getBoolean(KEY_USE_24H, true),
            showSeconds = prefs.getBoolean(KEY_SHOW_SECONDS, true),
            backgroundBlack = prefs.getBoolean(KEY_BACKGROUND_BLACK, true)
        )
    }

    private fun saveConfig(config: ClockConfig) {
        prefs.edit()
            .putInt(KEY_OFFSET_X, config.offsetX)
            .putInt(KEY_OFFSET_Y, config.offsetY)
            .putInt(KEY_FONT_SIZE, config.fontSizeSp)
            .putBoolean(KEY_USE_24H, config.use24Hour)
            .putBoolean(KEY_SHOW_SECONDS, config.showSeconds)
            .putBoolean(KEY_BACKGROUND_BLACK, config.backgroundBlack)
            .apply()
    }

    private fun loadSelectedDisplayId(): Int? {
        return if (prefs.contains(KEY_SELECTED_DISPLAY_ID)) {
            prefs.getInt(KEY_SELECTED_DISPLAY_ID, -1).takeIf { it != -1 }
        } else {
            null
        }
    }

    private fun saveSelectedDisplayId(displayId: Int) {
        prefs.edit().putInt(KEY_SELECTED_DISPLAY_ID, displayId).apply()
    }

    private fun refreshDisplays() {
        val mainId = Display.DEFAULT_DISPLAY
        val infos = displayManager.displays.map { d ->
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            d.getMetrics(metrics)

            DisplayDebugInfo(
                displayId = d.displayId,
                name = d.name ?: "Display ${d.displayId}",
                width = metrics.widthPixels,
                height = metrics.heightPixels,
                rotation = d.rotation,
                flags = d.flags,
                state = d.state,
                isMain = d.displayId == mainId
            )
        }

        availableDisplays.value = infos

        val selected = selectedDisplayId.value
        if (selected == null || infos.none { it.displayId == selected }) {
            val fallback = infos.firstOrNull { !it.isMain }?.displayId
            if (fallback != null) {
                selectedDisplayId.value = fallback
                saveSelectedDisplayId(fallback)
            }
        }
    }

    private fun ensurePresentation(forceRecreate: Boolean = false) {
        val targetId = selectedDisplayId.value ?: run {
            dismissPresentation()
            return
        }

        val targetDisplay = displayManager.displays.firstOrNull { it.displayId == targetId } ?: run {
            dismissPresentation()
            return
        }

        if (targetDisplay.displayId == Display.DEFAULT_DISPLAY) {
            dismissPresentation()
            return
        }

        val existingDisplayId = clockPresentation?.display?.displayId

        if (!forceRecreate &&
            clockPresentation?.isShowing == true &&
            existingDisplayId == targetDisplay.displayId
        ) {
            return
        }

        dismissPresentation()

        clockPresentation = ClockPresentation(this, targetDisplay)
        try {
            clockPresentation?.show()
        } catch (_: Exception) {
            dismissPresentation()
        }
    }

    private fun dismissPresentation() {
        try {
            clockPresentation?.dismiss()
        } catch (_: Exception) {
        }
        clockPresentation = null
    }
}

class ClockPresentation(
    context: Context,
    display: Display
) : Presentation(context, display) {

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var clockText: android.widget.TextView

    private val tick = object : Runnable {
        override fun run() {
            val config = ClockStateStore.config.value

            val pattern = when {
                config.use24Hour && config.showSeconds -> "HH:mm:ss"
                config.use24Hour && !config.showSeconds -> "HH:mm"
                !config.use24Hour && config.showSeconds -> "hh:mm:ss a"
                else -> "hh:mm a"
            }

            val formatter = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
            clockText.text = formatter.format(java.util.Date())
            clockText.textSize = config.fontSizeSp.toFloat()

            val bgColor = if (config.backgroundBlack) android.graphics.Color.BLACK
            else android.graphics.Color.TRANSPARENT
            window?.decorView?.setBackgroundColor(bgColor)

            val params = clockText.layoutParams as android.widget.FrameLayout.LayoutParams
            params.leftMargin = dpToPx(config.offsetX)
            params.topMargin = dpToPx(config.offsetY)
            clockText.layoutParams = params

            handler.postDelayed(this, 200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        val root = android.widget.FrameLayout(context)
        // ensures the root draws properly even when transparent
        root.setWillNotDraw(false)

        clockText = android.widget.TextView(context).apply {
            text = "00:00:00"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 42f
            //remote DEFAULT_BOLD,  go with DEFAULT
            typeface = android.graphics.Typeface.DEFAULT
            // remove any background
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        val params = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.CENTER
        }

        root.addView(clockText, params)
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        handler.post(tick)
    }

    override fun onStop() {
        handler.removeCallbacks(tick)
        super.onStop()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}

@Composable
fun MainControlScreen(
    configFlow: StateFlow<ClockConfig>,
    displaysFlow: StateFlow<List<DisplayDebugInfo>>,
    selectedDisplayIdFlow: StateFlow<Int?>,
    onConfigChanged: (ClockConfig) -> Unit,
    onNudge: (Int, Int) -> Unit,
    onPreset: (Int, Int) -> Unit,
    onSelectDisplay: (Int) -> Unit,
    onRefreshDisplays: () -> Unit
) {
    val config by configFlow.collectAsState()
    val displays by displaysFlow.collectAsState()
    val selectedDisplayId by selectedDisplayIdFlow.collectAsState()

    var showPositionDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101114))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "G700 Clock Control",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Main display = control panel. Secondary display = live clock output.",
                color = Color(0xFFB7BDC9),
                fontSize = 14.sp
            )

            CardBlock(title = "Position") {
                TextValueRow("X Offset", "${config.offsetX} dp")
                TextValueRow("Y Offset", "${config.offsetY} dp")

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ElevatedButton(onClick = { showPositionDialog = true }) {
                        Text("Open Position Pad")
                    }
                    OutlinedButton(onClick = { onPreset(0, 0) }) {
                        Text("Reset 0,0")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetButton("Top Left") { onPreset(-250, -120) }
                    PresetButton("Top Right") { onPreset(250, -120) }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetButton("Bottom Left") { onPreset(-250, 120) }
                    PresetButton("Bottom Right") { onPreset(250, 120) }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetButton("Center") { onPreset(0, 0) }
                }
            }

            CardBlock(title = "Clock Style") {
                TextValueRow("Font Size", "${config.fontSizeSp} sp")
                Slider(
                    value = config.fontSizeSp.toFloat(),
                    onValueChange = { onConfigChanged(config.copy(fontSizeSp = it.roundToInt())) },
                    valueRange = 48f..220f
                )

                SettingSwitchRow(
                    title = "24 Hour Format",
                    checked = config.use24Hour,
                    onCheckedChange = { onConfigChanged(config.copy(use24Hour = it)) }
                )

                SettingSwitchRow(
                    title = "Show Seconds",
                    checked = config.showSeconds,
                    onCheckedChange = { onConfigChanged(config.copy(showSeconds = it)) }
                )

                SettingSwitchRow(
                    title = "Black Background",
                    checked = config.backgroundBlack,
                    onCheckedChange = { onConfigChanged(config.copy(backgroundBlack = it)) }
                )
            }

            CardBlock(title = "Outputs") {
                Text(
                    text = "Pick which detected display should receive the clock. This is where we test what LANSO maps to HDMI:2.",
                    color = Color(0xFFB7BDC9),
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                displays.forEach { info ->
                    DisplaySelectButton(
                        info = info,
                        selected = selectedDisplayId == info.displayId,
                        onClick = { onSelectDisplay(info.displayId) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                ElevatedButton(onClick = onRefreshDisplays) {
                    Text("Refresh Displays")
                }
            }

            CardBlock(title = "Debug") {
                displays.forEachIndexed { index, info ->
                    DebugDisplayCard(
                        index = index,
                        info = info,
                        selected = selectedDisplayId == info.displayId
                    )
                    if (index != displays.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        if (showPositionDialog) {
            PositionDialog(
                config = config,
                onDismiss = { showPositionDialog = false },
                onNudge = onNudge
            )
        }
    }
}

@Composable
fun ExternalClockScreen() {
    val config by ClockStateStore.config.collectAsState()

    val timePattern = remember(config.use24Hour, config.showSeconds) {
        when {
            config.use24Hour && config.showSeconds -> "HH:mm:ss"
            config.use24Hour && !config.showSeconds -> "HH:mm"
            !config.use24Hour && config.showSeconds -> "hh:mm:ss a"
            else -> "hh:mm a"
        }
    }

    val formatter = remember(timePattern) {
        SimpleDateFormat(timePattern, Locale.getDefault())
    }

    var currentTime by remember { mutableStateOf(formatter.format(Date())) }

    LaunchedEffect(timePattern) {
        while (true) {
            currentTime = formatter.format(Date())
            delay(200)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (config.backgroundBlack) Color.Black else Color.Transparent)
    ) {
        Text(
            text = currentTime,
            color = Color.White,
            fontSize = config.fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = config.offsetX.dp, y = config.offsetY.dp)
        )
    }
}

@Composable
fun PositionDialog(
    config: ClockConfig,
    onDismiss: () -> Unit,
    onNudge: (Int, Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Position Pad") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Live moves update the secondary-display clock instantly.")
                Text("X: ${config.offsetX} dp")
                Text("Y: ${config.offsetY} dp")

                Row {
                    Spacer(modifier = Modifier.width(70.dp))
                    Button(onClick = { onNudge(0, -10) }) {
                        Text("Up")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onNudge(-10, 0) }) {
                        Text("Left")
                    }
                    Button(onClick = { onNudge(0, 10) }) {
                        Text("Down")
                    }
                    Button(onClick = { onNudge(10, 0) }) {
                        Text("Right")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                Text("Fine Adjust")

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onNudge(-1, 0) }) { Text("X-1") }
                    OutlinedButton(onClick = { onNudge(1, 0) }) { Text("X+1") }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onNudge(0, -1) }) { Text("Y-1") }
                    OutlinedButton(onClick = { onNudge(0, 1) }) { Text("Y+1") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun CardBlock(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF191C22)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun TextValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFFB7BDC9))
        Text(value, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun PresetButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Text(label)
    }
}

@Composable
fun DisplaySelectButton(
    info: DisplayDebugInfo,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) Color(0xFF58A6FF) else Color(0xFF323844)
    val bg = if (selected) Color(0xFF162131) else Color(0xFF11151B)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, MaterialTheme.shapes.medium)
            .background(bg, MaterialTheme.shapes.medium)
            .padding(12.dp)
    ) {
        Text(
            text = if (info.isMain) "${info.name} (Main Display)" else info.name,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "ID ${info.displayId} • ${info.width}x${info.height} • flags ${info.flags}",
            color = Color(0xFFB7BDC9),
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onClick,
            enabled = !info.isMain
        ) {
            Text(if (selected) "Selected" else "Send Clock Here")
        }
    }
}

@Composable
fun DebugDisplayCard(
    index: Int,
    info: DisplayDebugInfo,
    selected: Boolean
) {
    val bg = if (selected) Color(0xFF1A2636) else Color(0xFF11151B)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, MaterialTheme.shapes.medium)
            .padding(12.dp)
    ) {
        Text(
            text = "Display ${index + 1}",
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text("Name: ${info.name}", color = Color(0xFFB7BDC9), fontSize = 13.sp)
        Text("ID: ${info.displayId}", color = Color(0xFFB7BDC9), fontSize = 13.sp)
        Text("Resolution: ${info.width}x${info.height}", color = Color(0xFFB7BDC9), fontSize = 13.sp)
        Text("Rotation: ${info.rotation}", color = Color(0xFFB7BDC9), fontSize = 13.sp)
        Text("Flags: ${info.flags}", color = Color(0xFFB7BDC9), fontSize = 13.sp)
        Text("State: ${info.state}", color = Color(0xFFB7BDC9), fontSize = 13.sp)
        Text("Main: ${info.isMain}", color = Color(0xFFB7BDC9), fontSize = 13.sp)
        Text("Selected Output: $selected", color = Color(0xFFB7BDC9), fontSize = 13.sp)
    }
}