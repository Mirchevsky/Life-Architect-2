package com.mirchevsky.lifearchitect2.ui.composables

import android.Manifest
import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mirchevsky.lifearchitect2.R
import com.mirchevsky.lifearchitect2.permissions.PermissionGateState
import com.mirchevsky.lifearchitect2.permissions.PermissionPrefs
import com.mirchevsky.lifearchitect2.permissions.openAppPermissionSettings
import com.mirchevsky.lifearchitect2.permissions.resolvePermissionGateState
import com.mirchevsky.lifearchitect2.ui.theme.BrandGreen
import com.mirchevsky.lifearchitect2.ui.theme.Purple
import com.mirchevsky.lifearchitect2.utils.DateIntentParser
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce

private const val TAG = "AddTaskItem"

@Composable
private fun inAppSheetColor(): Color {
    return if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surface
    } else {
        colorResource(id = R.color.widget_background)
    }
}

@Composable
private fun inAppInnerContentColor(): Color {
    return if (isSystemInDarkTheme()) {
        Color.Transparent
    } else {
        colorResource(id = R.color.widget_item_card)
    }
}

@Composable
private fun inAppDatePickerContainerColor(): Color {
    return if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surface
    } else {
        colorResource(id = R.color.widget_item_card)
    }
}

@Composable
private fun inAppDateInnerContainerColor(): Color {
    return if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        colorResource(id = R.color.widget_item_card)
    }
}

@Composable
private fun inAppTimeFieldBackgroundColor(): Color {
    return if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        colorResource(id = R.color.white)
    }
}

@Composable
private fun inAppTimeFieldContentColor(): Color {
    return if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.onSurface
    } else {
        colorResource(id = R.color.widget_text_primary)
    }
}

@Composable
private fun inAppBackButtonContainerColor(): Color {
    return if (isSystemInDarkTheme()) {
        Color.White
    } else {
        colorResource(id = R.color.widget_item_card)
    }
}

/**
 * The inline add-task panel shown at the bottom of the Tasks screen.
 *
 * Design
 * A [Card] panel (matching [TaskItem]'s surfaceVariant style) containing:
 *
 * A borderless [BasicTextField] for the task title.
 *
 * An internal divider separating the text area from the action row.
 *
 * Three static side-by-side action buttons (right-aligned):
 *
 * Mic button: uses Android's [SpeechRecognizer] API directly — no system
 * overlay, no privacy notice dialog. Recognizes in the device's default language.
 * The mic button pulses while listening; partial results fill the field in real-time.
 *
 * Calendar button (purple): opens a two-step date + time picker pre-filled by
 * [DateIntentParser]. On confirm, writes the event silently to the system calendar
 * via [CalendarContract] content provider and requests an immediate sync. Falls
 * back to [Intent.ACTION_INSERT] if no calendar account is configured.
 *
 * Add button (green): adds the task to the app only, no calendar event.
 *
 * Keyboard behaviour (WhatsApp-style)
 * The composable uses [Modifier.imePadding] so the entire card floats above the
 * software keyboard whenever the text field is focused — the action buttons are always
 * visible. [ImeAction.Done] on the keyboard simply clears focus (lowers the keyboard)
 * without submitting, so the user can still tap calendar or add.
 *
 * All three buttons dim to 25 % alpha when no text is present.
 *
 * @param onAddTask Called with the task title, difficulty string, and optional due
 * date when the user confirms via either button.
 *
 * @param requestFocus When true, the text field requests focus on first composition.
 *
 * @param onFocusConsumed Called once after the focus request fires so the caller
 * can reset the flag.
 */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun AddTaskItem(
    onAddTask: (title: String, difficulty: String, dueDate: LocalDateTime?) -> Unit,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false,
    onFocusConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val permissionPrefs = remember(context) { PermissionPrefs(context) }

    // Permission gate states — re-resolved on each composition so they update
    // immediately after the user returns from the system dialog.
    var micPermState by remember {
        mutableStateOf(
            resolvePermissionGateState(
                context,
                Manifest.permission.RECORD_AUDIO,
                permissionPrefs
            )
        )
    }
    var calPermState by remember {
        mutableStateOf(
            resolvePermissionGateState(
                context,
                Manifest.permission.WRITE_CALENDAR,
                permissionPrefs
            )
        )
    }

    // In-app rationale / blocked dialogs
    var showMicRationale by remember { mutableStateOf(false) }
    var showMicBlocked by remember { mutableStateOf(false) }
    var showCalRationale by remember { mutableStateOf(false) }
    var showCalBlocked by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf("") }
    var parseResult by remember { mutableStateOf<DateIntentParser.ParseResult?>(null) }

    // Two-step picker state
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    // Calendar confirmation popup
    var showCalendarPopup by remember { mutableStateOf(false) }

    // Mic active state — true while SpeechRecognizer is listening
    var isMicActive by remember { mutableStateOf(false) }

    val titleFlow = remember { MutableStateFlow("") }

    // ── SpeechRecognizer (direct API — no overlay, no privacy notice) ──────
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer.destroy()
        }
    }

    val recognitionListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isMicActive = true
                Log.d(TAG, "SpeechRecognizer: ready")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "SpeechRecognizer: speech started")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "SpeechRecognizer: speech ended")
            }

            override fun onError(error: Int) {
                isMicActive = false
                Log.e(TAG, "SpeechRecognizer error: $error")
            }

            override fun onResults(results: Bundle?) {
                isMicActive = false
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()

                if (!text.isNullOrBlank()) {
                    title = text
                    titleFlow.value = text
                }
                Log.d(TAG, "SpeechRecognizer result: $text")
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Fill field in real-time as the user speaks
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()

                if (!partial.isNullOrBlank()) {
                    title = partial
                    titleFlow.value = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    // ── Permission launchers (gate-aware) ────────────────────────────────────
    var pendingCalendarAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        calPermState = resolvePermissionGateState(
            context,
            Manifest.permission.WRITE_CALENDAR,
            permissionPrefs
        )

        if (results[Manifest.permission.WRITE_CALENDAR] == true) {
            pendingCalendarAction?.invoke()
            pendingCalendarAction = null
        } else {
            pendingCalendarAction = null
            when (calPermState) {
                PermissionGateState.RequestableWithRationale -> showCalRationale = true
                PermissionGateState.PermanentlyDenied -> showCalBlocked = true
                else -> Unit
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micPermState = resolvePermissionGateState(
            context,
            Manifest.permission.RECORD_AUDIO,
            permissionPrefs
        )

        if (granted) {
            speechRecognizer.setRecognitionListener(recognitionListener)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer.startListening(intent)
        } else {
            Log.w(TAG, "RECORD_AUDIO denied — state=$micPermState")
            when (micPermState) {
                PermissionGateState.RequestableWithRationale -> showMicRationale = true
                PermissionGateState.PermanentlyDenied -> showMicBlocked = true
                else -> Unit
            }
        }
    }

    // ── Focus request ──────────────────────────────────────────────────────
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            onFocusConsumed()
        }
    }

    // ── Date parser (debounced) ────────────────────────────────────────────
    LaunchedEffect(Unit) {
        titleFlow
            .debounce(300)
            .collect { text ->
                parseResult = if (text.isNotBlank()) DateIntentParser.parse(text) else null
            }
    }

    val hasText = title.isNotBlank()

    // ── Submit helpers ─────────────────────────────────────────────────────
    fun resetState() {
        title = ""
        titleFlow.value = ""
        parseResult = null
        pendingDateMillis = null
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    fun submitTaskOnly() {
        if (!hasText) return
        onAddTask(title.trim(), "MEDIUM", parseResult?.bestGuess)
        resetState()
    }

    /**
     * Silently inserts a calendar event via [CalendarContract] content provider
     * (no app switch) and simultaneously adds the task to the app.
     *
     * Strategy:
     * 1. Try to find the primary calendar (IS_PRIMARY = 1).
     * 2. If not found, fall back to any available calendar.
     * 3. If no calendar account exists at all, fall back to [Intent.ACTION_INSERT].
     */
    fun submitWithCalendar(dueDate: LocalDateTime) {
        if (!hasText) return

        val epochMillis = dueDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val projection = arrayOf(CalendarContract.Calendars._ID)

        // Step 1: primary calendar
        val calendarId: Long? = try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.IS_PRIMARY} = 1",
                null,
                null
            )?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Primary calendar query failed", e)
            null
        }
        // Step 2: any calendar
            ?: try {
                context.contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection,
                    null,
                    null,
                    null
                )?.use { c ->
                    if (c.moveToFirst()) c.getLong(0) else null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Any-calendar query failed", e)
                null
            }

        Log.d(TAG, "Calendar insert — calendarId=$calendarId, dueDate=$dueDate")

        if (calendarId != null) {
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title.trim())
                put(CalendarContract.Events.DTSTART, epochMillis)
                put(CalendarContract.Events.DTEND, epochMillis + 3_600_000L)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }

            val uri: Uri? = try {
                context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            } catch (e: Exception) {
                Log.e(TAG, "Calendar event insert failed", e)
                null
            }

            Log.d(TAG, "Calendar event inserted: uri=$uri")
            if (uri != null) showCalendarPopup = true

            // Request an immediate sync so the event appears in Google Calendar without delay
            if (uri != null) {
                try {
                    context.contentResolver.query(
                        CalendarContract.Calendars.CONTENT_URI,
                        arrayOf(
                            CalendarContract.Calendars.ACCOUNT_NAME,
                            CalendarContract.Calendars.ACCOUNT_TYPE
                        ),
                        "${CalendarContract.Calendars._ID} = ?",
                        arrayOf(calendarId.toString()),
                        null
                    )?.use { c ->
                        if (c.moveToFirst()) {
                            val account = Account(c.getString(0), c.getString(1))
                            ContentResolver.requestSync(
                                account,
                                CalendarContract.AUTHORITY,
                                Bundle().apply {
                                    putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                                    putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                                }
                            )
                            Log.d(TAG, "Sync requested for account: ${account.name}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Sync request failed (non-critical)", e)
                }
            }
        } else {
            // Step 3: no calendar account — open external calendar app as fallback
            Log.w(TAG, "No calendar found, falling back to Intent.ACTION_INSERT")
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title.trim())
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, epochMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, epochMillis + 3_600_000L)
            }
            context.startActivity(intent)
        }

        onAddTask(title.trim(), "MEDIUM", dueDate)
        resetState()
    }

    /**
     * Starts the [SpeechRecognizer] directly — no system overlay, no privacy notice.
     * Uses the device's default language automatically (no EXTRA_LANGUAGE needed).
     * Requests [Manifest.permission.RECORD_AUDIO] on first use if not already granted.
     */
    fun startListening() {
        if (isMicActive) {
            speechRecognizer.stopListening()
            isMicActive = false
            return
        }

        // Re-resolve state on every tap so it reflects any changes since last tap
        micPermState = resolvePermissionGateState(
            context,
            Manifest.permission.RECORD_AUDIO,
            permissionPrefs
        )

        when (micPermState) {
            PermissionGateState.Granted -> {
                speechRecognizer.setRecognitionListener(recognitionListener)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                speechRecognizer.startListening(intent)
            }

            PermissionGateState.RequestableFirstTime,
            PermissionGateState.RequestableWithRationale -> {
                permissionPrefs.markRequested(Manifest.permission.RECORD_AUDIO)
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }

            PermissionGateState.PermanentlyDenied -> showMicBlocked = true
        }
    }

    /** Opens the date picker, requesting calendar permissions if needed. */
    fun openCalendarPicker() {
        if (!hasText) return

        // Re-resolve state on every tap
        calPermState = resolvePermissionGateState(
            context,
            Manifest.permission.WRITE_CALENDAR,
            permissionPrefs
        )

        when (calPermState) {
            PermissionGateState.Granted -> showDatePicker = true
            PermissionGateState.RequestableFirstTime,
            PermissionGateState.RequestableWithRationale -> {
                permissionPrefs.markRequested(Manifest.permission.WRITE_CALENDAR)
                pendingCalendarAction = { showDatePicker = true }
                calendarPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.WRITE_CALENDAR,
                        Manifest.permission.READ_CALENDAR
                    )
                )
            }

            PermissionGateState.PermanentlyDenied -> showCalBlocked = true
        }
    }

    // ── Card panel + popup overlay ────────────────────────────────────────
    Box {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ── Text input area ────────────────────────────────────────────
                BasicTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleFlow.value = it
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp, bottom = 12.dp)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    // ImeAction.Done shows the "✓" key; handler only clears focus
                    // (lowers keyboard) — does NOT submit the task.
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    decorationBox = { innerTextField ->
                        if (title.isEmpty()) {
                            Text(
                                text = "Task",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                )

                // ── Internal divider ───────────────────────────────────────────
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )

                // ── Action row ─────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    // Mic button — shows animated dots while listening, mic icon otherwise
                    FilledIconButton(
                        onClick = { startListening() },
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(
                                alpha = when {
                                    isMicActive -> 1f
                                    hasText -> 0.6f
                                    else -> 0.25f
                                }
                            ),
                            contentColor = Color.White
                        )
                    ) {
                        if (isMicActive) {
                            RecordingDotsIndicator()
                        } else {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice input",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Calendar button
                    FilledIconButton(
                        onClick = { openCalendarPicker() },
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Purple.copy(alpha = if (hasText) 1f else 0.25f),
                            contentColor = Color.White
                        ),
                        enabled = hasText
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Add to Calendar",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Add button
                    FilledIconButton(
                        onClick = { submitTaskOnly() },
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = BrandGreen.copy(alpha = if (hasText) 1f else 0.25f),
                            contentColor = Color.White
                        ),
                        enabled = hasText
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Task",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // ── Calendar confirmation popup ────────────────────────────────────────
        if (showCalendarPopup) {
            CalendarConfirmPopup(onDismiss = { showCalendarPopup = false })
        }
    }

    // ── Permission rationale dialogs ──────────────────────────────────────────
    // Shown after the first denial to explain why the permission is needed.

    if (showMicRationale) {
        AlertDialog(
            onDismissRequest = { showMicRationale = false },
            title = { Text("Microphone access needed") },
            text = {
                Text("Life Architect needs the microphone to transcribe your voice into a task title. Tap \"Allow\" to grant access.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMicRationale = false
                        permissionPrefs.markRequested(Manifest.permission.RECORD_AUDIO)
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                ) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMicRationale = false }) {
                    Text("Not now")
                }
            }
        )
    }

    if (showMicBlocked) {
        AlertDialog(
            onDismissRequest = { showMicBlocked = false },
            title = { Text("Microphone access blocked") },
            text = {
                Text("You have permanently denied microphone access. To use voice input, open Settings → Permissions → Microphone and enable it.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMicBlocked = false
                        openAppPermissionSettings(context)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMicBlocked = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCalRationale) {
        AlertDialog(
            onDismissRequest = { showCalRationale = false },
            title = { Text("Calendar access needed") },
            text = {
                Text("Life Architect needs calendar access to add your task as a calendar event. Tap \"Allow\" to grant access.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCalRationale = false
                        permissionPrefs.markRequested(Manifest.permission.WRITE_CALENDAR)
                        pendingCalendarAction = { showDatePicker = true }
                        calendarPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.WRITE_CALENDAR,
                                Manifest.permission.READ_CALENDAR
                            )
                        )
                    }
                ) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalRationale = false }) {
                    Text("Not now")
                }
            }
        )
    }

    if (showCalBlocked) {
        AlertDialog(
            onDismissRequest = { showCalBlocked = false },
            title = { Text("Calendar access blocked") },
            text = {
                Text("You have permanently denied calendar access. To add events, open Settings → Permissions → Calendar and enable it.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCalBlocked = false
                        openAppPermissionSettings(context)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalBlocked = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Date picker dialog (step 1 of 2) ──────────────────────────────────
    if (showDatePicker) {
        val initialMillis = parseResult?.bestGuess
            ?.atZone(ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli()

        val todayStartMillis = java.time.LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis >= todayStartMillis
                }
            }
        )

        Dialog(
            onDismissRequest = { showDatePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = inAppSheetColor()
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = inAppDateInnerContainerColor()
                    ) {
                        DatePicker(
                            state = datePickerState,
                            modifier = Modifier.padding(0.dp),
                            title = null,
                            headline = null,
                            showModeToggle = false,
                            colors = DatePickerDefaults.colors(
                                containerColor = inAppDatePickerContainerColor(),
                                dayContentColor = MaterialTheme.colorScheme.onSurface,
                                selectedDayContainerColor = Purple,
                                selectedDayContentColor = Color.White,
                                todayContentColor = MaterialTheme.colorScheme.onSurface,
                                todayDateBorderColor = MaterialTheme.colorScheme.onSurface,
                                weekdayContentColor = MaterialTheme.colorScheme.onSurface,
                                navigationContentColor = MaterialTheme.colorScheme.onSurface,
                                subheadContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { showDatePicker = false },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = inAppBackButtonContainerColor(),
                                contentColor = Purple
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                        ) {
                            Text(
                                text = "Back",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = {
                                pendingDateMillis = datePickerState.selectedDateMillis ?: todayStartMillis
                                showDatePicker = false
                                showTimePicker = true
                            },
                            modifier = Modifier
                                .weight(2f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Purple,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "Set Time",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Time picker dialog (step 2 of 2) ──────────────────────────────────
    if (showTimePicker) {
        val parsedTime = parseResult?.bestGuess
        var isAllDay by remember { mutableStateOf(false) }
        var hour by remember { mutableStateOf(parsedTime?.hour ?: 9) }
        var minute by remember { mutableStateOf(parsedTime?.minute ?: 0) }

        Dialog(
            onDismissRequest = { showTimePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = inAppSheetColor()
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // All-day toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "All Day",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = isAllDay,
                            onCheckedChange = { isAllDay = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Purple,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                uncheckedTrackColor = inAppInnerContentColor()
                            )
                        )
                    }

                    // Time input — hidden when all-day
                    AnimatedVisibility(visible = !isAllDay) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = inAppInnerContentColor()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(
                                    vertical = 24.dp,
                                    horizontal = 12.dp
                                )
                            ) {
                                val timeFieldBackground = inAppTimeFieldBackgroundColor()
                                val timeFieldContent = inAppTimeFieldContentColor()
                                val customTextSelectionColors = TextSelectionColors(
                                    handleColor = timeFieldContent,
                                    backgroundColor = timeFieldContent.copy(alpha = 0.28f)
                                )

                                CompositionLocalProvider(
                                    LocalTextSelectionColors provides customTextSelectionColors
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        BasicTextField(
                                            value = hour.toString().padStart(2, '0'),
                                            onValueChange = { v ->
                                                hour = v.filter { it.isDigit() }
                                                    .take(2)
                                                    .toIntOrNull()
                                                    ?.coerceIn(0, 23) ?: 0
                                            },
                                            textStyle = TextStyle(
                                                color = timeFieldContent,
                                                fontSize = 48.sp,
                                                textAlign = TextAlign.Center
                                            ),
                                            modifier = Modifier
                                                .width(110.dp)
                                                .height(80.dp)
                                                .background(timeFieldBackground, RoundedCornerShape(12.dp))
                                                .padding(8.dp),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            cursorBrush = SolidColor(timeFieldContent)
                                        )

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Text(
                                            text = ":",
                                            color = timeFieldContent,
                                            fontSize = 36.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )

                                        Spacer(modifier = Modifier.width(10.dp))

                                        BasicTextField(
                                            value = minute.toString().padStart(2, '0'),
                                            onValueChange = { v ->
                                                minute = v.filter { it.isDigit() }
                                                    .take(2)
                                                    .toIntOrNull()
                                                    ?.coerceIn(0, 59) ?: 0
                                            },
                                            textStyle = TextStyle(
                                                color = timeFieldContent,
                                                fontSize = 48.sp,
                                                textAlign = TextAlign.Center
                                            ),
                                            modifier = Modifier
                                                .width(110.dp)
                                                .height(80.dp)
                                                .background(timeFieldBackground, RoundedCornerShape(12.dp))
                                                .padding(8.dp),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            cursorBrush = SolidColor(timeFieldContent)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(if (isAllDay) 24.dp else 16.dp))

                    // Bottom buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { showTimePicker = false },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = inAppBackButtonContainerColor(),
                                contentColor = Purple
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                        ) {
                            Text(
                                text = "Back",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = {
                                showTimePicker = false
                                val dateMillis = pendingDateMillis ?: return@Button
                                val dueDate = if (isAllDay) {
                                    Instant.ofEpochMilli(dateMillis)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDateTime()
                                        .withHour(0)
                                        .withMinute(0)
                                        .withSecond(0)
                                } else {
                                    Instant.ofEpochMilli(dateMillis)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDateTime()
                                        .withHour(hour)
                                        .withMinute(minute)
                                        .withSecond(0)
                                }
                                submitWithCalendar(dueDate)
                            },
                            modifier = Modifier
                                .weight(2f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Purple,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "Create Event",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Three small circles that bounce up and down in sequence, indicating that the
 * [SpeechRecognizer] is actively listening. Each dot is 4 dp — small enough to
 * sit comfortably inside the 44 dp mic button alongside the existing icon style.
 *
 * Dots are staggered by 150 ms to create a rolling wave effect.
 */
@Composable
private fun RecordingDotsIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "recordingDots")

    // Three dots with staggered animation delays
    val offsets = listOf(0, 150, 300).map { delay ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -4f, // 4 dp bounce — subtle, fits inside button
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 450,
                    delayMillis = delay,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_$delay"
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp), // tight spacing
        verticalAlignment = Alignment.CenterVertically
    ) {
        offsets.forEach { offset ->
            Box(
                modifier = Modifier
                    .size(4.dp) // 4 dp dot — matches icon weight
                    .offset(y = offset.value.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.9f),
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * A floating "Added to Calendar" confirmation popup that animates upward and fades out,
 * matching the style of [XpPopup].
 *
 * @param onDismiss Called when the animation completes.
 */
@Composable
private fun CalendarConfirmPopup(onDismiss: () -> Unit) {
    val yOffset = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        yOffset.animateTo(
            targetValue = -120f,
            animationSpec = tween(durationMillis = 1500)
        )
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 400)
        )
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = yOffset.value.dp)
            .alpha(alpha.value)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Added to Calendar",
            color = Purple, // calendar purple — matches CalendarUpdatedPopup in TaskItem
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall
        )
    }
}