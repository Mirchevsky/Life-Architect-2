package com.mirchevsky.lifearchitect2.ui.composables

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.mirchevsky.lifearchitect2.data.db.entity.TaskEntity
import com.mirchevsky.lifearchitect2.ui.theme.BrandAmber
import com.mirchevsky.lifearchitect2.ui.theme.Purple
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 *
 * Displays a single pending task in a card.
 *
 * Interaction model:
 *
 * Tap card (outside the title) → marks task complete.
 *
 * Long-press card OR tap title → inline title editing via [BasicTextField].
 *
 * Pressing Done on the keyboard commits the change via [onUpdate].
 *
 * Calendar icon (tap) → opens DatePicker → TimeInput flow to edit the
 *
 * due date/time. On confirm, [onUpdateDueDate] is called with the old and new millis
 *
 * so the ViewModel can sync the device calendar correctly. Shows a "Calendar Updated"
 *
 * popup after the change is confirmed.
 *
 * Flag icon → toggles [TaskEntity.isUrgent]. Filled red when urgent.
 *
 * Pin icon → toggles [TaskEntity.isPinned]. Filled amber when pinned.
 *
 * Title colour is always the app primary green for this surface.
 *
 * @param task The task entity to display.
 *
 * @param onCompleted Called when the user checks the task off.
 *
 * @param onUpdate Called for pin/urgent toggles and title edits.
 *
 * @param onUpdateDueDate Called when the due date/time changes. Receives the old millis
 *
 *                      (nullable) and the new millis so the ViewModel can decide
 *                      whether to delete+recreate or update the calendar event.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TaskItem(
    task: TaskEntity,
    onCompleted: (TaskEntity) -> Unit,
    onUpdate: (TaskEntity) -> Unit,
    onUpdateDueDate: (oldMillis: Long?, newMillis: Long) -> Unit,
    spotlightStrength: Float = 0f
) {
    val dueDateTime: LocalDateTime? = task.dueDate?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime()
    }
    val dueDate: LocalDate? = dueDateTime?.toLocalDate()
    val isOverdue = dueDate != null && dueDate.isBefore(LocalDate.now())

// ── Inline editing ──────────────────────────────────────────────────────
    var isEditing by remember { mutableStateOf(false) }
    var titleFieldValue by remember(task.id) {
        mutableStateOf(TextFieldValue(task.title, selection = TextRange(task.title.length)))
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isEditing) { if (isEditing) focusRequester.requestFocus() }

    fun commitEdit() {
        val trimmed = titleFieldValue.text.trim()
        if (trimmed.isNotBlank() && trimmed != task.title) onUpdate(task.copy(title = trimmed))
        isEditing = false
    }

// ── Date/time picker state ──────────────────────────────────────────────
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateMillis by remember(task.id) { mutableStateOf(task.dueDate) }

// ── Calendar Updated popup ──────────────────────────────────────────────
    var showCalendarUpdatedPopup by remember { mutableStateOf(false) }

    val todayMillis = remember {
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = task.dueDate ?: todayMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayMillis
        }
    )
    val existingHour   = task.dueDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).hour   } ?: 9
    val existingMinute = task.dueDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).minute } ?: 0
    val timePickerState = rememberTimePickerState(
        initialHour = existingHour,
        initialMinute = existingMinute,
        is24Hour = true
    )

// ── Title colour ────────────────────────────────────────────────────────
    val titleColor: Color = MaterialTheme.colorScheme.primary
    val glowStrength = spotlightStrength.coerceIn(0f, 1f)
    val titleShadow = Shadow(
        color = titleColor.copy(alpha = 1.0f * glowStrength),
        offset = Offset(0f, 0f),
        blurRadius = 56f * glowStrength
    )

// ── Card ────────────────────────────────────────────────────────────
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick    = { if (!isEditing) onCompleted(task) },
                    onLongClick = {
                        titleFieldValue = TextFieldValue(task.title, selection = TextRange(task.title.length))
                        isEditing = true
                    }
                )
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            Checkbox(
                checked = false,
                onCheckedChange = null,
                modifier = Modifier.size(36.dp),
                colors = CheckboxDefaults.colors(
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    checkedColor   = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.width(4.dp))

            // Title + due-date label
            Column(modifier = Modifier.weight(1f)) {
                if (isEditing) {
                    BasicTextField(
                        value = titleFieldValue,
                        onValueChange = { titleFieldValue = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = titleColor,
                            shadow = titleShadow
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commitEdit() })
                    )
                } else {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge.copy(shadow = titleShadow),
                        color = titleColor,
                        modifier = Modifier.combinedClickable(
                            onClick     = {
                                titleFieldValue = TextFieldValue(task.title, selection = TextRange(task.title.length))
                                isEditing = true
                            },
                            onLongClick = {
                                titleFieldValue = TextFieldValue(task.title, selection = TextRange(task.title.length))
                                isEditing = true
                            }
                        )
                    )
                }
                if (dueDate != null) {
                    val dateLabel = dueDate.format(DateTimeFormatter.ofPattern("MMM d"))
                    val timeLabel = dueDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                    val label = "$dateLabel, $timeLabel"
                    Text(
                        text  = if (isOverdue) "Overdue — $label" else label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOverdue) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Action icons (compact, right-aligned) ───────────────────────

            // Calendar icon — only shown when a due date is set.
            // Tap → opens DatePicker → TimeInput flow to edit the date & time.
            // The updated date/time is synced to the device calendar via onUpdateDueDate.
            if (dueDate != null) {
                IconButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Edit date & time",
                        tint = if (isOverdue) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Flag icon — urgent toggle
            IconButton(
                onClick = { onUpdate(task.copy(isUrgent = !task.isUrgent)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (task.isUrgent) Icons.Filled.Flag else Icons.Outlined.Flag,
                    contentDescription = if (task.isUrgent) "Remove urgent" else "Mark urgent",
                    tint = if (task.isUrgent) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Pin icon — pin toggle
            IconButton(
                onClick = { onUpdate(task.copy(isPinned = !task.isPinned)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (task.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (task.isPinned) "Unpin task" else "Pin task to top",
                    tint = if (task.isPinned) BrandAmber
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

// ── Calendar Updated popup — anchored to bottom of screen ────────────
    if (showCalendarUpdatedPopup) {
        Popup(
            alignment = Alignment.BottomCenter,
            properties = PopupProperties(focusable = false)
        ) {
            CalendarUpdatedPopup(onDismiss = { showCalendarUpdatedPopup = false })
        }
    }

// ── Date picker dialog ──────────────────────────────────────────────────
    if (showDatePicker) {
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

// ── Time picker dialog ──────────────────────────────────────────────────
    if (showTimePicker) {
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Select time",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    TimeInput(state = timePickerState)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                        TextButton(onClick = {
                            showTimePicker = false
                            val selectedDate = pendingDateMillis ?: return@TextButton
                            val localDate = Instant.ofEpochMilli(selectedDate)
                                .atZone(ZoneId.systemDefault()).toLocalDate()
                            val newMillis = LocalDateTime
                                .of(localDate, LocalTime.of(timePickerState.hour, timePickerState.minute))
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                            onUpdateDueDate(task.dueDate, newMillis)
                            showCalendarUpdatedPopup = true
                        }) { Text("Confirm") }
                    }
                }
            }
        }
    }
}

/**
 *
 * A floating "Calendar Updated" confirmation popup that animates upward and fades out,
 *
 * matching the style of the "Added to Calendar" popup in [AddTaskItem].
 *
 * The outer [Box] is 200 dp tall so the text (anchored at the bottom) can travel
 *
 * 120 dp upward without leaving the Popup window bounds and being clipped.
 *
 * @param onDismiss Called when the animation completes.
 */
@Composable
private fun CalendarUpdatedPopup(onDismiss: () -> Unit) {
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

// Tall outer box: text starts at the bottom and travels upward within this window.
// Without this height the Popup window clips the text as soon as it moves above y=0.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            text = "Calendar Updated",
            color = Purple,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .offset(y = yOffset.value.dp)
                .alpha(alpha.value)
                .padding(16.dp)
        )
    }
}