package io.lifephysics.architect2.ui.composables

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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import io.lifephysics.architect2.ui.theme.BrandGreen
import io.lifephysics.architect2.ui.theme.Purple
import io.lifephysics.architect2.utils.DateIntentParser
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

private const val TAG = "AddTaskItem"

/**
 * The inline add-task panel shown at the bottom of the Tasks screen.
 *
 * ## Design
 * A [Card] panel (matching [TaskItem]'s `surfaceVariant` style) containing:
 * - A borderless [BasicTextField] for the task title.
 * - An internal divider separating the text area from the action row.
 * - Three static side-by-side action buttons (right-aligned):
 *   - **Mic button**: uses Android's [SpeechRecognizer] API directly — no system
 *     overlay, no privacy notice dialog. Recognizes in the device's default language.
 *     The mic button pulses while listening; partial results fill the field in real-time.
 *   - **Calendar button** (purple): opens a two-step date + time picker pre-filled by
 *     [DateIntentParser]. On confirm, writes the event silently to the system calendar
 *     via [CalendarContract] content provider and requests an immediate sync. Falls
 *     back to [Intent.ACTION_INSERT] if no calendar account is configured.
 *   - **Add button** (green): adds the task to the app only, no calendar event.
 *
 * ## Keyboard behaviour (WhatsApp-style)
 * The composable uses [Modifier.imePadding] so the entire card floats above the
 * software keyboard whenever the text field is focused — the action buttons are always
 * visible. [ImeAction.Done] on the keyboard simply clears focus (lowers the keyboard)
 * without submitting, so the user can still tap calendar or add.
 *
 * All three buttons dim to 25 % alpha when no text is present.
 *
 * @param onAddTask Called with the task title, difficulty string, and optional due
 *   date when the user confirms via either button.
 * @param requestFocus When true, the text field requests focus on first composition.
 * @param onFocusConsumed Called once after the focus request fires so the caller
 *   can reset the flag.
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

    var title by remember { mutableStateOf("") }
    var parseResult by remember { mutableStateOf<DateIntentParser.ParseResult?>(null) }

    // Two-step picker state
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

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
            override fun onRmsChanged(rmsdB: Float) { /* no-op */ }
            override fun onBufferReceived(buffer: ByteArray?) { /* no-op */ }
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
            override fun onEvent(eventType: Int, params: Bundle?) { /* no-op */ }
        }
    }

    // ── Permission launchers ───────────────────────────────────────────────

    var pendingCalendarAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.WRITE_CALENDAR] == true) {
            pendingCalendarAction?.invoke()
        }
        pendingCalendarAction = null
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            speechRecognizer.setRecognitionListener(recognitionListener)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                // No EXTRA_LANGUAGE — uses device default language automatically
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer.startListening(intent)
        } else {
            Log.w(TAG, "RECORD_AUDIO permission denied")
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
                null, null
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        } catch (e: Exception) {
            Log.e(TAG, "Primary calendar query failed", e); null
        }
        // Step 2: any calendar
            ?: try {
                context.contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection, null, null, null
                )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
            } catch (e: Exception) {
                Log.e(TAG, "Any-calendar query failed", e); null
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
                Log.e(TAG, "Calendar event insert failed", e); null
            }
            Log.d(TAG, "Calendar event inserted: uri=$uri")

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
            // Second tap cancels listening
            speechRecognizer.stopListening()
            isMicActive = false
            return
        }
        val hasAudio = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasAudio) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        speechRecognizer.setRecognitionListener(recognitionListener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // No EXTRA_LANGUAGE — device default language is used automatically
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer.startListening(intent)
    }

    /** Opens the date picker, requesting calendar permissions if needed. */
    fun openCalendarPicker() {
        if (!hasText) return
        val hasWrite = context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasRead = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasWrite && hasRead) {
            showDatePicker = true
        } else {
            pendingCalendarAction = { showDatePicker = true }
            calendarPermissionLauncher.launch(
                arrayOf(Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR)
            )
        }
    }

    // ── Card panel ─────────────────────────────────────────────────────────
    //
    // imePadding() is the key to WhatsApp-style behaviour: when the software
    // keyboard opens, this modifier adds bottom padding equal to the keyboard
    // height, pushing the card up so it always sits above the keyboard.
    // The parent screen must use WindowCompat.setDecorFitsSystemWindows(window, false)
    // and Modifier.fillMaxSize() so that insets are propagated correctly.

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
                    onDone = { focusManager.clearFocus() }   // dismiss keyboard, keep text
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

    // ── Date picker dialog (step 1 of 2) ──────────────────────────────────

    if (showDatePicker) {
        val initialMillis = parseResult?.bestGuess
            ?.atZone(ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pendingDateMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ── Time picker dialog (step 2 of 2) ──────────────────────────────────

    if (showTimePicker) {
        val parsedTime = parseResult?.bestGuess
        val timePickerState = rememberTimePickerState(
            initialHour = parsedTime?.hour ?: 9,
            initialMinute = parsedTime?.minute ?: 0,
            is24Hour = false
        )

        Dialog(onDismissRequest = { showTimePicker = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Select time",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(bottom = 16.dp)
                    )

                    TimePicker(state = timePickerState)

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text("Cancel")
                        }
                        TextButton(onClick = {
                            showTimePicker = false
                            val dateMillis = pendingDateMillis ?: return@TextButton
                            val dueDate = Instant.ofEpochMilli(dateMillis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime()
                                .withHour(timePickerState.hour)
                                .withMinute(timePickerState.minute)
                                .withSecond(0)
                            submitWithCalendar(dueDate)
                        }) {
                            Text("Confirm")
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
 * Dots are staggered by 150 ms to create a rolling wave effect.
 */
@Composable
private fun RecordingDotsIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "recordingDots")

    // Three dots with staggered animation delays
    val offsets = listOf(0, 150, 300).map { delay ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -4f,          // 4 dp bounce — subtle, fits inside button
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),  // tight spacing
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        offsets.forEach { offset ->
            Box(
                modifier = Modifier
                    .size(4.dp)         // 4 dp dot — matches icon weight
                    .offset(y = offset.value.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.9f),
                        shape = CircleShape
                    )
            )
        }
    }
}
