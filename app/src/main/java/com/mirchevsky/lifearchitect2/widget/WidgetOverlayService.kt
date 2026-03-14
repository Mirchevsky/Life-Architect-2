package com.mirchevsky.lifearchitect2.widget

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.CalendarContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mirchevsky.lifearchitect2.R
import com.mirchevsky.lifearchitect2.data.db.AppDatabase
import com.mirchevsky.lifearchitect2.data.db.entity.TaskEntity
import com.mirchevsky.lifearchitect2.ui.theme.AppTheme
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetOverlayService : Service() {

    companion object {
        const val ACTION_ADD_TASK = "com.mirchevsky.lifearchitect2.OVERLAY_ADD_TASK"
        const val ACTION_ADD_EVENT = "com.mirchevsky.lifearchitect2.OVERLAY_ADD_EVENT"
        const val ACTION_MIC = "com.mirchevsky.lifearchitect2.OVERLAY_MIC"
        const val ACTION_SHOW_XP = "com.mirchevsky.lifearchitect2.OVERLAY_SHOW_XP"
        const val EXTRA_XP_AMOUNT = "xp_amount"
        const val EXTRA_XP_LABEL = "xp_label"
        private const val CHANNEL_ID = "widget_overlay_channel"
        private const val NOTIF_ID = 9001
        private const val TAG = "WidgetOverlayService"

        fun buildIntent(context: Context, action: String, widgetId: Int): Intent =
            Intent(context, WidgetOverlayService::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private var feedbackView: ComposeView? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val lifecycleOwner = object : LifecycleOwner, SavedStateRegistryOwner {
        val lifecycleRegistry = LifecycleRegistry(this)
        val savedStateRegistryController = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle
            get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateRegistryController.savedStateRegistry
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        lifecycleOwner.savedStateRegistryController.performRestore(null)
        lifecycleOwner.lifecycleRegistry.currentState = Lifecycle.State.CREATED
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForegroundCompat()
        lifecycleOwner.lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        if (!Settings.canDrawOverlays(this)) {
            PendingWidgetActionStore.saveServiceIntent(this, intent)
            startActivity(
                Intent(this, OverlayPermissionDialogActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_NO_HISTORY or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    )
            )
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        when (intent.action) {
            ACTION_ADD_TASK -> showOverlay(OverlayMode.ADD_TASK, widgetId)

            ACTION_MIC -> {
                if (
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    PendingWidgetActionStore.saveServiceIntent(this, intent)
                    startActivity(
                        Intent(this, MicPermissionActivity::class.java)
                            .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                            )
                    )
                    stopSelf(startId)
                    return START_NOT_STICKY
                } else {
                    showOverlay(OverlayMode.MIC, widgetId)
                }
            }

            ACTION_SHOW_XP -> {
                val xp = intent.getIntExtra(EXTRA_XP_AMOUNT, 10)
                val label = intent.getStringExtra(EXTRA_XP_LABEL) ?: "+${xp} XP"
                showFeedbackOverlay(label)
            }

            ACTION_ADD_EVENT -> {
                if (
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) !=
                    PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    PendingWidgetActionStore.saveServiceIntent(this, intent)
                    startActivity(
                        Intent(this, CalendarPermissionActivity::class.java)
                            .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                            )
                    )
                    stopSelf(startId)
                    return START_NOT_STICKY
                } else {
                    showOverlay(OverlayMode.ADD_EVENT, widgetId)
                }
            }

            else -> stopSelf(startId)
        }

        return START_STICKY
    }

    private fun startForegroundCompat() {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Life Architect")
            .setContentText("Adding task…")
            .setSmallIcon(R.drawable.widget_ic_add)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun showOverlay(mode: OverlayMode, widgetId: Int) {
        if (overlayView != null) return

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.BOTTOM }

        overlayView =
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setContent {
                    AppTheme {
                        OverlayPanel(
                            mode = mode,
                            onDismiss = { removeOverlay() },
                            onSaved = {
                                overlayView?.let { view -> windowManager.removeView(view) }
                                overlayView = null
                                TaskWidgetProvider.sendRefreshBroadcast(applicationContext)
                                showFeedbackOverlay(it)
                            }
                        )
                    }
                }
            }
        windowManager.addView(overlayView, params)
    }

    private fun removeOverlay() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        if (feedbackView == null) stopSelf()
    }

    private fun showFeedbackOverlay(message: String) {
        if (message.isBlank() || feedbackView != null) {
            removeOverlay()
            return
        }

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.BOTTOM }

        feedbackView =
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setContent { AppTheme { FeedbackToast(message) } }
            }
        windowManager.addView(feedbackView, params)

        serviceScope.launch {
            delay(3500)
            removeFeedbackOverlay()
            removeOverlay()
        }
    }

    private fun removeFeedbackOverlay() {
        feedbackView?.let { windowManager.removeView(it) }
        feedbackView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        removeOverlay()
        removeFeedbackOverlay()
        lifecycleOwner.lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Widget Overlays",
                    NotificationManager.IMPORTANCE_LOW
                )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    @Composable
    private fun greenTextFieldColors() =
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF10B981),
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            focusedLabelColor = Color(0xFF10B981),
            cursorColor = Color(0xFF10B981),
            focusedContainerColor = overlayInputColor(),
            unfocusedContainerColor = overlayInputColor(),
            disabledContainerColor = overlayInputColor()
        )

    @Composable
    private fun purpleTextFieldColors() =
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF7C3AED),
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            focusedLabelColor = Color(0xFF7C3AED),
            cursorColor = Color(0xFF7C3AED),
            focusedContainerColor = overlayInputColor(),
            unfocusedContainerColor = overlayInputColor(),
            disabledContainerColor = overlayInputColor()
        )

    @Composable
    private fun overlaySheetColor(): Color {
        return if (isSystemInDarkTheme()) {
            MaterialTheme.colorScheme.surface
        } else {
            colorResource(id = R.color.widget_background)
        }
    }

    @Composable
    private fun overlayInputColor(): Color {
        return if (isSystemInDarkTheme()) {
            Color.Transparent
        } else {
            colorResource(id = R.color.widget_item_card)
        }
    }

    @Composable
    private fun overlayInnerContentColor(): Color {
        return if (isSystemInDarkTheme()) {
            Color.Transparent
        } else {
            colorResource(id = R.color.widget_item_card)
        }
    }

    @Composable
    private fun datePickerContainerColor(): Color {
        return if (isSystemInDarkTheme()) {
            MaterialTheme.colorScheme.surface
        } else {
            colorResource(id = R.color.widget_item_card)
        }
    }

    @Composable
    private fun secondaryBackButtonColor(): Color {
        return if (isSystemInDarkTheme()) {
            Color.White
        } else {
            colorResource(id = R.color.widget_item_card)
        }
    }

    @Composable
    private fun minuteFieldContainerColor(): Color {
        return if (isSystemInDarkTheme()) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            colorResource(id = R.color.widget_background)
        }
    }

    @Composable
    private fun PanelSurface(content: @Composable ColumnScope.() -> Unit) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = overlaySheetColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier =
                    Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp, top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content
            )
        }
    }

    @Composable
    private fun DragHandle() {
        Box(
            modifier =
                Modifier.padding(vertical = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), CircleShape)
        )
    }

    @Composable
    private fun OverlayPanel(mode: OverlayMode, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        val dismissInteraction = remember { MutableInteractionSource() }
        val consumeInteraction = remember { MutableInteractionSource() }

        AnimatedVisibility(
            visible = visible,
            enter =
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(260))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.30f))
                            .clickable(
                                interactionSource = dismissInteraction,
                                indication = null,
                                onClick = onDismiss
                            )
                )
                Box(
                    modifier =
                        Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = consumeInteraction,
                                indication = null,
                                onClick = {}
                            )
                ) {
                    when (mode) {
                        OverlayMode.ADD_TASK -> AddTaskPanel(onDismiss, onSaved)
                        OverlayMode.MIC -> MicPanel(onDismiss, onSaved)
                        OverlayMode.ADD_EVENT -> AddEventPanel(onDismiss, onSaved)
                    }
                }
            }
        }
    }

    @Composable
    private fun PanelTopBar(onDismiss: () -> Unit) {
        Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Box(modifier = Modifier.align(Alignment.TopCenter)) { DragHandle() }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd).size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun AddTaskPanel(onDismiss: () -> Unit, onSaved: (String) -> Unit) {
        var title by remember { mutableStateOf("") }
        var isSaving by remember { mutableStateOf(false) }
        val keyboard = LocalSoftwareKeyboardController.current

        fun save() {
            if (title.isBlank()) return
            isSaving = true
            serviceScope.launch(Dispatchers.IO) {
                AppDatabase.getDatabase(applicationContext)
                    .taskDao()
                    .upsertTask(
                        TaskEntity(
                            id = UUID.randomUUID().toString(),
                            userId = "local_user",
                            title = title.trim(),
                            difficulty = "medium"
                        )
                    )
                launch(Dispatchers.Main) {
                    keyboard?.hide()
                    onSaved("")
                }
            }
        }

        PanelSurface {
            PanelTopBar(onDismiss)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                keyboardActions = KeyboardActions(onDone = { save() }),
                colors = greenTextFieldColors()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { save() },
                enabled = title.isNotBlank() && !isSaving,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text(if (isSaving) "Saving…" else "Save Task", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }

    @Composable
    private fun MicPanel(onDismiss: () -> Unit, onSaved: (String) -> Unit) {
        var title by remember { mutableStateOf("") }
        var isListening by remember { mutableStateOf(true) }
        var isSaving by remember { mutableStateOf(false) }
        var recognizerRef by remember { mutableStateOf<SpeechRecognizer?>(null) }
        val keyboard = LocalSoftwareKeyboardController.current

        DisposableEffect(Unit) {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
            recognizerRef = recognizer
            val recognizerIntent =
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
            recognizer.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                    }

                    override fun onBeginningOfSpeech() {
                        isListening = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                    }

                    override fun onResults(results: Bundle?) {
                        results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.let { title = it }
                        isListening = false
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        partialResults
                            ?.getStringArrayList(RecognizerIntent.EXTRA_PARTIAL_RESULTS)
                            ?.firstOrNull()
                            ?.let { title = it }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                }
            )
            recognizer.startListening(recognizerIntent)
            onDispose { recognizer.destroy() }
        }

        fun save() {
            if (title.isBlank()) return
            isSaving = true
            serviceScope.launch(Dispatchers.IO) {
                AppDatabase.getDatabase(applicationContext)
                    .taskDao()
                    .upsertTask(
                        TaskEntity(
                            id = UUID.randomUUID().toString(),
                            userId = "local_user",
                            title = title.trim(),
                            difficulty = "medium"
                        )
                    )
                launch(Dispatchers.Main) {
                    keyboard?.hide()
                    onSaved("")
                }
            }
        }

        fun startListening() {
            isListening = true
            val intent =
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
            recognizerRef?.startListening(intent)
        }

        PanelSurface {
            PanelTopBar(onDismiss)
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                if (isListening) {
                    PulsingMicIndicator()
                } else {
                    IconButton(onClick = { startListening() }, modifier = Modifier.size(64.dp)) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Start Listening",
                            modifier = Modifier.fillMaxSize(),
                            tint = Color(0xFF10B981)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                colors = greenTextFieldColors(),
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                keyboardActions = KeyboardActions(onDone = { save() })
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { save() },
                enabled = title.isNotBlank() && !isSaving,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text(if (isSaving) "Saving…" else "Save Task", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }

    @Composable
    private fun PulsingMicIndicator() {
        val infiniteTransition = rememberInfiniteTransition(label = "")
        val circleCount = 3
        val circleDelay = 300

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            repeat(circleCount) {
                val alpha by
                infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation =
                                tween(
                                    durationMillis = circleDelay * circleCount,
                                    easing = LinearEasing
                                ),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(circleDelay * it)
                        ),
                    label = ""
                )
                Box(
                    modifier =
                        Modifier.padding(horizontal = 4.dp)
                            .size(12.dp)
                            .alpha(alpha)
                            .background(Color(0xFF10B981), CircleShape)
                )
            }
        }
    }

    @Composable
    private fun TimeInputCustom(state: EventState) {
        val customTextSelectionColors =
            TextSelectionColors(handleColor = Color.White, backgroundColor = Color.White.copy(alpha = 0.4f))

        CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = state.hour.toString().padStart(2, '0'),
                    onValueChange = { value ->
                        state.hour = value.filter { it.isDigit() }.take(2).toIntOrNull() ?: 0
                    },
                    textStyle = TextStyle(color = Color.White, fontSize = 48.sp, textAlign = TextAlign.Center),
                    modifier =
                        Modifier.width(110.dp)
                            .height(80.dp)
                            .background(Color(0xFF7C3AED), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    cursorBrush = SolidColor(Color.White)
                )

                Spacer(modifier = Modifier.width(16.dp))

                BasicTextField(
                    value = state.minute.toString().padStart(2, '0'),
                    onValueChange = { value ->
                        state.minute = value.filter { it.isDigit() }.take(2).toIntOrNull() ?: 0
                    },
                    textStyle =
                        TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 48.sp,
                            textAlign = TextAlign.Center
                        ),
                    modifier =
                        Modifier.width(110.dp)
                            .height(80.dp)
                            .background(minuteFieldContainerColor(), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface)
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AddEventPanel(onDismiss: () -> Unit, onSaved: (String) -> Unit) {
        val eventState = remember { EventState() }
        var currentStep by remember { mutableStateOf(EventStep.TITLE) }
        val keyboard = LocalSoftwareKeyboardController.current
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    eventState.selectedDate
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
            )

        LaunchedEffect(datePickerState.selectedDateMillis) {
            datePickerState.selectedDateMillis?.let { millis: Long ->
                eventState.selectedDate =
                    java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
            }
        }

        fun saveEvent() {
            if (eventState.title.isBlank()) return
            eventState.isSaving = true
            serviceScope.launch(Dispatchers.IO) {
                val startMillis =
                    if (eventState.isAllDay) {
                        eventState.selectedDate
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    } else {
                        eventState.selectedDate
                            .atTime(eventState.hour, eventState.minute)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    }

                val values =
                    ContentValues().apply {
                        put(CalendarContract.Events.DTSTART, startMillis)
                        put(CalendarContract.Events.DTEND, startMillis + 3_600_000L)
                        put(CalendarContract.Events.TITLE, eventState.title)
                        put(CalendarContract.Events.CALENDAR_ID, 1)
                        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                        put(CalendarContract.Events.ALL_DAY, if (eventState.isAllDay) 1 else 0)
                    }

                try {
                    contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                } catch (e: SecurityException) {
                    Log.e(TAG, "Failed to insert calendar event", e)
                }

                AppDatabase.getDatabase(applicationContext)
                    .taskDao()
                    .upsertTask(
                        TaskEntity(
                            id = UUID.randomUUID().toString(),
                            userId = "local_user",
                            title = eventState.title.trim(),
                            difficulty = "medium",
                            dueDate = startMillis,
                            category = "Calendar"
                        )
                    )

                withContext(Dispatchers.Main) {
                    keyboard?.hide()
                    onSaved("Added to Calendar")
                }
            }
        }

        PanelSurface {
            PanelTopBar(onDismiss)
            when (currentStep) {
                EventStep.TITLE -> {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = eventState.title,
                        onValueChange = { eventState.title = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = purpleTextFieldColors(),
                        singleLine = true,
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Next
                            ),
                        keyboardActions = KeyboardActions(onNext = { currentStep = EventStep.DATE })
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { currentStep = EventStep.DATE },
                        enabled = eventState.title.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                    ) {
                        Text("Set Date & Time", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                EventStep.DATE -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = overlayInnerContentColor()
                    ) {
                        DatePicker(
                            state = datePickerState,
                            modifier = Modifier.padding(0.dp),
                            title = null,
                            headline = null,
                            showModeToggle = false,
                            colors =
                                DatePickerDefaults.colors(
                                    containerColor = datePickerContainerColor(),
                                    selectedDayContainerColor = Color(0xFF7C3AED),
                                    todayDateBorderColor = MaterialTheme.colorScheme.onSurface,
                                    todayContentColor = MaterialTheme.colorScheme.onSurface
                                )
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { currentStep = EventStep.TITLE },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = secondaryBackButtonColor(),
                                    contentColor = Color(0xFF7C3AED)
                                )
                        ) {
                            Text(
                                "Back",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF7C3AED)
                            )
                        }
                        Button(
                            onClick = { currentStep = EventStep.TIME },
                            modifier = Modifier.weight(2f).height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                        ) {
                            Text("Next: Set Time", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }

                EventStep.TIME -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("All Day", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = eventState.isAllDay,
                            onCheckedChange = { eventState.isAllDay = it },
                            colors =
                                SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF7C3AED),
                                    uncheckedThumbColor =
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }

                    AnimatedVisibility(visible = !eventState.isAllDay) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = overlayInnerContentColor()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 24.dp, horizontal = 12.dp)
                            ) {
                                TimeInputCustom(state = eventState)
                            }
                        }
                    }

                    Spacer(Modifier.height(if (eventState.isAllDay) 24.dp else 16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { currentStep = EventStep.DATE },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = secondaryBackButtonColor(),
                                    contentColor = Color(0xFF7C3AED)
                                )
                        ) {
                            Text(
                                "Back",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF7C3AED)
                            )
                        }
                        Button(
                            onClick = { saveEvent() },
                            enabled = !eventState.isSaving,
                            modifier = Modifier.weight(2f).height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                        ) {
                            Text(
                                if (eventState.isSaving) "Saving..." else "Create Event",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FeedbackToast(message: String) {
        val purple = Color(0xFF7C3AED)
        val offsetY = remember { Animatable(600f) }

        LaunchedEffect(Unit) {
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing)
            )
        }

        val alpha = (1f - offsetY.value / 600f).coerceIn(0f, 1f)

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier =
                    Modifier.padding(bottom = 160.dp)
                        .offset { IntOffset(x = 0, y = offsetY.value.toInt()) }
                        .alpha(alpha)
                        .shadow(
                            elevation = 24.dp,
                            spotColor = purple,
                            ambientColor = purple,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .shadow(
                            elevation = 16.dp,
                            spotColor = purple,
                            ambientColor = purple,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .shadow(
                            elevation = 8.dp,
                            spotColor = Color.White,
                            ambientColor = Color.White,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .background(color = Color.White, shape = RoundedCornerShape(20.dp))
                        .defaultMinSize(minWidth = 60.dp, minHeight = 60.dp)
                        .padding(horizontal = 24.dp, vertical = 0.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = message, color = purple, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

enum class OverlayMode {
    ADD_TASK,
    MIC,
    ADD_EVENT
}

enum class EventStep {
    TITLE,
    DATE,
    TIME
}

class EventState {
    var title by mutableStateOf("")
    var selectedDate by mutableStateOf(LocalDateTime.now().toLocalDate())
    var hour by mutableStateOf(LocalDateTime.now().hour)
    var minute by mutableStateOf(LocalDateTime.now().minute)
    var isAllDay by mutableStateOf(false)
    var isSaving by mutableStateOf(false)
}