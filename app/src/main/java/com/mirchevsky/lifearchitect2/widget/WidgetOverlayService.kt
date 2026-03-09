package com.mirchevsky.lifearchitect2.widget

import android.accounts.Account
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentResolver
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
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import com.mirchevsky.lifearchitect2.ui.theme.AppTheme
import com.mirchevsky.lifearchitect2.data.db.AppDatabase
import com.mirchevsky.lifearchitect2.data.db.entity.TaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import java.util.UUID

/**
 * WidgetOverlayService
 * ─────────────────────────────────────────────────────────────────────────────
 * A ForegroundService that draws a slide-up Jetpack Compose panel directly
 * onto the screen using WindowManager + TYPE_APPLICATION_OVERLAY.
 *
 * ## AddEventPanel — no Dialog wrappers
 *
 * Compose's Dialog() and DatePickerDialog() call android.app.Dialog.show()
 * internally, which requires an Activity window token. Inside a WindowManager
 * overlay service there is no Activity, so the token is null and the app
 * crashes with BadTokenException. The fix is to render the DatePicker and
 * TimeInput as *inline* Compose content inside the PanelSurface column,
 * controlled by a simple EventStep enum state — no Dialog wrapper at all.
 *
 * UI flow:
 *   TITLE step  → user types event title, taps "Set Date & Time"
 *   DATE  step  → inline DatePicker, taps "Next: Set Time"
 *   TIME  step  → inline TimeInput, taps "Create Event" → inserts & dismisses
 *
 * Place at:
 *   app/src/main/java/com/mirchevsky/lifearchitect2/widget/WidgetOverlayService.kt
 */
class WidgetOverlayService : Service() {

    // ── Constants ─────────────────────────────────────────────────────────────
    companion object {
        const val ACTION_ADD_TASK  = "com.mirchevsky.lifearchitect2.OVERLAY_ADD_TASK"
        const val ACTION_ADD_EVENT = "com.mirchevsky.lifearchitect2.OVERLAY_ADD_EVENT"
        const val ACTION_MIC       = "com.mirchevsky.lifearchitect2.OVERLAY_MIC"

        private const val CHANNEL_ID = "widget_overlay_channel"
        private const val NOTIF_ID   = 9001
        private const val TAG        = "WidgetOverlayService"

        fun buildIntent(context: Context, action: String, widgetId: Int): Intent =
            Intent(context, WidgetOverlayService::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── Lifecycle owner shim (required for ComposeView outside an Activity) ───
    private val lifecycleOwner = object : LifecycleOwner, SavedStateRegistryOwner {
        val lifecycleRegistry = LifecycleRegistry(this)
        val savedStateRegistryController = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateRegistryController.savedStateRegistry
    }

    // ── Service lifecycle ─────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        lifecycleOwner.savedStateRegistryController.performRestore(null)
        lifecycleOwner.lifecycleRegistry.currentState = Lifecycle.State.CREATED
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        lifecycleOwner.lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleOwner.lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(this, OverlayPermissionDialogActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            stopSelf()
            return START_NOT_STICKY
        }

        val widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        when (intent?.action) {
            ACTION_ADD_TASK  -> showOverlay(OverlayMode.ADD_TASK, widgetId)
            ACTION_MIC       -> showOverlay(OverlayMode.MIC, widgetId)
            ACTION_ADD_EVENT -> showOverlay(OverlayMode.ADD_EVENT, widgetId)
            else             -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeOverlay()
        lifecycleOwner.lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── Foreground notification ───────────────────────────────────────────────
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Life Architect Widget",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Used while the task input panel is open"
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun startForegroundCompat() {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.widget_ic_add)
            .setContentTitle("Life Architect")
            .setContentText("Adding task…")
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    // ── Overlay management ────────────────────────────────────────────────────
    private fun showOverlay(mode: OverlayMode, widgetId: Int) {
        removeOverlay()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                AppTheme {
                    OverlayPanel(
                        mode = mode,
                        onDismiss = { dismissOverlay(widgetId, taskSaved = false) },
                        onSaved   = { dismissOverlay(widgetId, taskSaved = true) }
                    )
                }
            }
        }

        overlayView = composeView
        windowManager.addView(composeView, params)
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
    }

    /**
     * Dismisses the overlay and, when something was saved, triggers a full
     * widget rebuild via [TaskWidgetProvider.sendRefreshBroadcast].
     *
     * Replaces the deprecated AppWidgetManager.notifyAppWidgetViewDataChanged().
     */
    private fun dismissOverlay(widgetId: Int, taskSaved: Boolean) {
        removeOverlay()
        if (taskSaved) {
            TaskWidgetProvider.sendRefreshBroadcast(this@WidgetOverlayService)
        }
        stopSelf()
    }

    // ── Compose UI ────────────────────────────────────────────────────────────
    @Composable
    private fun OverlayPanel(
        mode: OverlayMode,
        onDismiss: () -> Unit,
        onSaved: () -> Unit
    ) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (visible) 0.55f else 0f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(320)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(260)
                )
            ) {
                Box(modifier = Modifier.clickable(enabled = false, onClick = {})) {
                    when (mode) {
                        OverlayMode.ADD_TASK  -> AddTaskPanel(onDismiss, onSaved)
                        OverlayMode.MIC       -> MicPanel(onDismiss, onSaved)
                        OverlayMode.ADD_EVENT -> AddEventPanel(onDismiss, onSaved)
                    }
                }
            }
        }
    }

    // ── Add Task panel ────────────────────────────────────────────────────────
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AddTaskPanel(onDismiss: () -> Unit, onSaved: () -> Unit) {
        var title      by remember { mutableStateOf("") }
        var difficulty by remember { mutableStateOf("medium") }
        var isSaving   by remember { mutableStateOf(false) }
        val keyboard   = LocalSoftwareKeyboardController.current

        fun save() {
            if (title.isBlank()) return
            isSaving = true
            serviceScope.launch(Dispatchers.IO) {
                AppDatabase.getDatabase(applicationContext).taskDao().upsertTask(
                    TaskEntity(
                        id          = UUID.randomUUID().toString(),
                        userId      = "local_user",
                        title       = title.trim(),
                        difficulty  = difficulty,
                        status      = "pending",
                        isCompleted = false
                    )
                )
                launch(Dispatchers.Main) {
                    keyboard?.hide()
                    onSaved()
                }
            }
        }

        PanelSurface {
            DragHandle()
            PanelTitle("New Task", onDismiss)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Task title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
                colors = outlinedTextFieldColors()
            )
            Spacer(Modifier.height(16.dp))
            Text("Difficulty", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DifficultyChip("Easy",   "easy",   Color(0xFF22C55E), difficulty) { difficulty = it }
                DifficultyChip("Medium", "medium", Color(0xFFF59E0B), difficulty) { difficulty = it }
                DifficultyChip("Hard",   "hard",   Color(0xFFEF4444), difficulty) { difficulty = it }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { save() },
                enabled = title.isNotBlank() && !isSaving,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
            ) {
                Text(if (isSaving) "Saving…" else "Save Task",
                    fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }

    // ── Mic / Voice panel ─────────────────────────────────────────────────────
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MicPanel(onDismiss: () -> Unit, onSaved: () -> Unit) {
        val hasAudio = ContextCompat.checkSelfPermission(
            applicationContext, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasAudio) {
            startActivity(
                Intent(applicationContext, MicPermissionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            onDismiss()
            return
        }

        var title       by remember { mutableStateOf("") }
        var difficulty  by remember { mutableStateOf("medium") }
        var isListening by remember { mutableStateOf(true) }
        var statusText  by remember { mutableStateOf("Listening…") }
        var isSaving    by remember { mutableStateOf(false) }
        val keyboard    = LocalSoftwareKeyboardController.current

        DisposableEffect(Unit) {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) { statusText = "Listening…" }
                override fun onBeginningOfSpeech() { statusText = "Hearing you…" }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { statusText = "Processing…" }
                override fun onError(error: Int) { isListening = false; statusText = "Tap the field to type instead" }
                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) title = matches[0]
                    isListening = false; statusText = "Edit or save"
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!partial.isNullOrEmpty()) title = partial[0]
                }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            recognizer.startListening(recognizerIntent)
            onDispose { recognizer.destroy() }
        }

        fun save() {
            if (title.isBlank()) return
            isSaving = true
            serviceScope.launch(Dispatchers.IO) {
                AppDatabase.getDatabase(applicationContext).taskDao().upsertTask(
                    TaskEntity(
                        id          = UUID.randomUUID().toString(),
                        userId      = "local_user",
                        title       = title.trim(),
                        difficulty  = difficulty,
                        status      = "pending",
                        isCompleted = false
                    )
                )
                launch(Dispatchers.Main) { keyboard?.hide(); onSaved() }
            }
        }

        PanelSurface {
            DragHandle()
            PanelTitle("Voice Input", onDismiss)
            Spacer(Modifier.height(12.dp))
            Text(statusText, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; isListening = false },
                label = { Text("Transcribed text") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
                colors = outlinedTextFieldColors()
            )
            Spacer(Modifier.height(16.dp))
            Text("Difficulty", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DifficultyChip("Easy",   "easy",   Color(0xFF22C55E), difficulty) { difficulty = it }
                DifficultyChip("Medium", "medium", Color(0xFFF59E0B), difficulty) { difficulty = it }
                DifficultyChip("Hard",   "hard",   Color(0xFFEF4444), difficulty) { difficulty = it }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { save() },
                enabled = title.isNotBlank() && !isSaving,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
            ) {
                Text(if (isSaving) "Saving…" else "Save Task",
                    fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }

    // ── Add Calendar Event panel ──────────────────────────────────────────────
    /**
     * Three-step inline panel — NO Dialog wrappers.
     *
     * Compose's Dialog() calls android.app.Dialog.show() which requires an
     * Activity window token. Inside a WindowManager overlay service the token
     * is null → BadTokenException crash. The fix is to render DatePicker and
     * TimeInput as plain inline Compose content, controlled by EventStep state.
     *
     * TITLE → user types title, taps "Set Date & Time"
     * DATE  → inline DatePicker, taps "Next: Set Time"
     * TIME  → inline TimeInput, taps "Create Event" → inserts & dismisses
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AddEventPanel(onDismiss: () -> Unit, onSaved: () -> Unit) {

        var eventTitle by remember { mutableStateOf("") }
        var isSaving   by remember { mutableStateOf(false) }
        var step       by remember { mutableStateOf(EventStep.TITLE) }
        val keyboard   = LocalSoftwareKeyboardController.current

        // These are hoisted outside the `when` so their state survives step transitions.
        val datePickerState = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val todayUtc = java.time.LocalDate.now()
                        .atStartOfDay(ZoneId.of("UTC"))
                        .toInstant()
                        .toEpochMilli()
                    return utcTimeMillis >= todayUtc
                }
            }
        )
        val timePickerState = rememberTimePickerState(
            initialHour = 9, initialMinute = 0, is24Hour = true
        )

        fun createEvent() {
            if (eventTitle.isBlank()) return
            val dateMillis = datePickerState.selectedDateMillis ?: return
            val dueDateTime = Instant.ofEpochMilli(dateMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .withHour(timePickerState.hour)
                .withMinute(timePickerState.minute)
                .withSecond(0).withNano(0)

            isSaving = true
            serviceScope.launch {
                val epochMillis = dueDateTime
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                val projection = arrayOf(CalendarContract.Calendars._ID)
                val cr = contentResolver

                val calendarId: Long? = withContext(Dispatchers.IO) {
                    try {
                        cr.query(
                            CalendarContract.Calendars.CONTENT_URI, projection,
                            "${CalendarContract.Calendars.IS_PRIMARY} = 1", null, null
                        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
                    } catch (e: Exception) { Log.e(TAG, "Primary calendar query failed", e); null }
                        ?: try {
                            cr.query(
                                CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
                            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
                        } catch (e: Exception) { Log.e(TAG, "Any-calendar query failed", e); null }
                }

                if (calendarId != null) {
                    val values = ContentValues().apply {
                        put(CalendarContract.Events.CALENDAR_ID, calendarId)
                        put(CalendarContract.Events.TITLE, eventTitle.trim())
                        put(CalendarContract.Events.DTSTART, epochMillis)
                        put(CalendarContract.Events.DTEND, epochMillis + 3_600_000L)
                        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                    }
                    val uri = withContext(Dispatchers.IO) {
                        try { cr.insert(CalendarContract.Events.CONTENT_URI, values) }
                        catch (e: Exception) { Log.e(TAG, "Calendar insert failed", e); null }
                    }
                    if (uri != null) {
                        withContext(Dispatchers.IO) {
                            try {
                                cr.query(
                                    CalendarContract.Calendars.CONTENT_URI,
                                    arrayOf(
                                        CalendarContract.Calendars.ACCOUNT_NAME,
                                        CalendarContract.Calendars.ACCOUNT_TYPE
                                    ),
                                    "${CalendarContract.Calendars._ID} = ?",
                                    arrayOf(calendarId.toString()), null
                                )?.use { c ->
                                    if (c.moveToFirst()) {
                                        val account = Account(c.getString(0), c.getString(1))
                                        ContentResolver.requestSync(
                                            account, CalendarContract.AUTHORITY,
                                            Bundle().apply {
                                                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                                                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                                            }
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Sync request failed (non-critical)", e)
                            }
                        }
                    }
                } else {
                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        data = CalendarContract.Events.CONTENT_URI
                        putExtra(CalendarContract.Events.TITLE, eventTitle.trim())
                        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, epochMillis)
                        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, epochMillis + 3_600_000L)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }

                keyboard?.hide()
                onSaved()
            }
        }

        PanelSurface {
            DragHandle()

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = Color(0xFF7C3AED),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        "New Calendar Event",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(onClick = {
                    if (step != EventStep.TITLE) step = EventStep.TITLE else onDismiss()
                }) {
                    Text(
                        if (step != EventStep.TITLE) "Back" else "✕",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Step content ──────────────────────────────────────────────────
            when (step) {

                // Step 1: title entry
                EventStep.TITLE -> {
                    OutlinedTextField(
                        value = eventTitle,
                        onValueChange = { eventTitle = it },
                        label = { Text("Event title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = {
                            if (eventTitle.isNotBlank()) {
                                keyboard?.hide()
                                step = EventStep.DATE
                            }
                        }),
                        colors = outlinedTextFieldColors()
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            keyboard?.hide()
                            step = EventStep.DATE
                        },
                        enabled = eventTitle.isNotBlank() && !isSaving,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EditCalendar,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Set Date & Time",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                // Step 2: inline date picker (no Dialog)
                EventStep.DATE -> {
                    DatePicker(
                        state = datePickerState,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { step = EventStep.TIME },
                        enabled = datePickerState.selectedDateMillis != null,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                    ) {
                        Text("Next: Set Time", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                // Step 3: inline time input (no Dialog) → creates event on confirm
                EventStep.TIME -> {
                    Text(
                        "Select time",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    TimeInput(
                        state = timePickerState,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { createEvent() },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                    ) {
                        Text(
                            if (isSaving) "Creating…" else "Create Event",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }

    // ── Shared Compose components ─────────────────────────────────────────────
    @Composable
    private fun PanelSurface(content: @Composable ColumnScope.() -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .imePadding(),
            content = content
        )
    }

    @Composable
    private fun DragHandle() {
        Box(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    }

    @Composable
    private fun PanelTitle(title: String, onDismiss: () -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = onDismiss) {
                Text("✕", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable
    private fun DifficultyChip(
        label: String,
        value: String,
        activeColor: Color,
        selected: String,
        onSelect: (String) -> Unit
    ) {
        val isSelected = selected == value
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (isSelected) activeColor else MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onSelect(value) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor    = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor  = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor          = MaterialTheme.colorScheme.primary,
        focusedTextColor     = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor   = MaterialTheme.colorScheme.onSurface
    )
}

// ── Enums ─────────────────────────────────────────────────────────────────────
enum class OverlayMode { ADD_TASK, MIC, ADD_EVENT }

/**
 * Controls which inline step is shown inside [WidgetOverlayService.AddEventPanel].
 * Using an enum instead of boolean flags avoids impossible state combinations.
 */
enum class EventStep { TITLE, DATE, TIME }
