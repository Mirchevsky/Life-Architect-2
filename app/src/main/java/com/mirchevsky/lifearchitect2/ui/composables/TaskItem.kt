package com.mirchevsky.lifearchitect2.ui.composables

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.mirchevsky.lifearchitect2.R
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

Displays a single pending task in a card.

Interaction model:

Tap card (outside the title) → marks task complete.

Long-press card OR tap title → inline title editing via [BasicTextField].

Pressing Done on the keyboard commits the change via [onUpdate].

Calendar icon (tap) → opens DatePicker → TimeInput flow to edit the

due date/time. On confirm, [onUpdateDueDate] is called with the old and new millis

so the ViewModel can sync the device calendar correctly. Shows a "Calendar Updated"

popup after the change is confirmed.

Flag icon → toggles [TaskEntity.isUrgent]. Outlined icon tinted red when urgent.

Pin icon → toggles [TaskEntity.isPinned]. Outlined icon tinted amber when pinned.

Title colour is always the app primary green for this surface.

@param task The task entity to display.

@param onCompleted Called when the user checks the task off.

@param onUpdate Called for pin/urgent toggles and title edits.

@param onUpdateDueDate Called when the due date/time changes. Receives the old millis

(nullable) and the new millis so the ViewModel can decide

whether to delete+recreate or update the calendar event.
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
    var isEditing by remember(task.id) { mutableStateOf(false) }
    var titleFieldValue by remember(task.id) {
        mutableStateOf(TextFieldValue(task.title, selection = TextRange(task.title.length)))
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isEditing) {
        if (isEditing) focusRequester.requestFocus()
    }

    fun commitEdit() {
        val trimmed = titleFieldValue.text.trim()
        if (trimmed.isNotBlank() && trimmed != task.title) onUpdate(task.copy(title = trimmed))
        isEditing = false
    }

    // ── Date/time picker state ──────────────────────────────────────────────
    var showDatePicker by remember(task.id) { mutableStateOf(false) }
    var showTimePicker by remember(task.id) { mutableStateOf(false) }
    var pendingDateMillis by remember(task.id) { mutableStateOf(task.dueDate) }

    // ── Calendar Updated popup ──────────────────────────────────────────────
    var showCalendarUpdatedPopup by remember(task.id) { mutableStateOf(false) }

    val todayMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = task.dueDate ?: todayMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayMillis
        }
    )
    LaunchedEffect(task.id, task.dueDate, todayMillis) {
        datePickerState.selectedDateMillis = task.dueDate ?: todayMillis
    }
    var isAllDay by remember(task.id) { mutableStateOf(false) }
    var hour by remember(task.id) {
        mutableStateOf(
            task.dueDate?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).hour
            } ?: 9
        )
    }
    var minute by remember(task.id) {
        mutableStateOf(
            task.dueDate?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).minute
            } ?: 0
        )
    }

    // ── Title colour ────────────────────────────────────────────────────────
    val titleColor: Color = if (isSystemInDarkTheme()) {
        colorResource(id = R.color.white)
    } else {
        colorResource(id = R.color.black)
    }
    val inactiveActionTint = if (isSystemInDarkTheme()) {
        colorResource(id = R.color.white)
    } else {
        colorResource(id = R.color.black)
    }
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
                    onClick = { if (!isEditing) onCompleted(task) },
                    onLongClick = {
                        titleFieldValue = TextFieldValue(
                            task.title,
                            selection = TextRange(task.title.length)
                        )
                        isEditing = true
                    }
                )
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val completionAccent = when {
                task.isUrgent -> MaterialTheme.colorScheme.error
                task.isPinned -> BrandAmber
                else -> MaterialTheme.colorScheme.primary
            }
            AnimatedCompletionGlowBox(
                accentColor = completionAccent,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))

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
                            onClick = {
                                titleFieldValue = TextFieldValue(
                                    task.title,
                                    selection = TextRange(task.title.length)
                                )
                                isEditing = true
                            },
                            onLongClick = {
                                titleFieldValue = TextFieldValue(
                                    task.title,
                                    selection = TextRange(task.title.length)
                                )
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
                        text = if (isOverdue) "Overdue — $label" else label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOverdue) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (dueDate != null) {
                IconButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Edit date & time",
                        tint = Purple,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            IconButton(
                onClick = { onUpdate(task.copy(isUrgent = !task.isUrgent)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Flag,
                    contentDescription = if (task.isUrgent) "Remove urgent" else "Mark urgent",
                    tint = inactiveActionTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            val pinAnimationProgress = remember(task.id) {
                Animatable(if (task.isPinned) 1f else 0f)
            }
            LaunchedEffect(task.isPinned) {
                if (task.isPinned) {
                    if (pinAnimationProgress.value < 1f) {
                        pinAnimationProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 375,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                } else {
                    pinAnimationProgress.snapTo(0f)
                }
            }
            val pinIconSize = 20.dp
            val pinIconSizePx = with(LocalDensity.current) { pinIconSize.toPx() }

            IconButton(
                onClick = { onUpdate(task.copy(isPinned = !task.isPinned)) },
                modifier = Modifier.size(36.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_task_pin_unpinned),
                    contentDescription = if (task.isPinned) "Unpin task" else "Pin task to top",
                    modifier = Modifier
                        .size(pinIconSize)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 0.95f)
                            rotationZ = -20f * pinAnimationProgress.value
                            translationY = (pinIconSizePx * 0.05f) * pinAnimationProgress.value
                        }
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
                    containerColor = taskEditSheetColor()
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
                        color = taskEditInnerContainerColor()
                    ) {
                        DatePicker(
                            state = datePickerState,
                            modifier = Modifier.padding(0.dp),
                            title = null,
                            headline = null,
                            showModeToggle = false,
                            colors = DatePickerDefaults.colors(
                                containerColor = taskEditDatePickerContainerColor(),
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
                                containerColor = taskEditBackButtonContainerColor(),
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
                                pendingDateMillis = datePickerState.selectedDateMillis ?: todayMillis
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

    // ── Time picker dialog ────────────────────────────────────────────────
    if (showTimePicker) {
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
                    containerColor = taskEditSheetColor()
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
                                uncheckedTrackColor = taskEditInnerContainerColor()
                            )
                        )
                    }

                    if (!isAllDay) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = taskEditInnerContainerColor()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(
                                    vertical = 24.dp,
                                    horizontal = 12.dp
                                )
                            ) {
                                TimeInputFields(
                                    hour = hour,
                                    minute = minute,
                                    onHourChange = { hour = it },
                                    onMinuteChange = { minute = it },
                                    fieldBackground = taskEditTimeFieldBackgroundColor(),
                                    fieldContent = taskEditTimeFieldContentColor(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(if (isAllDay) 24.dp else 16.dp))

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
                                containerColor = taskEditBackButtonContainerColor(),
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
                                val selectedDate = pendingDateMillis ?: return@Button
                                val localDate = Instant.ofEpochMilli(selectedDate)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                val localDateTime = if (isAllDay) {
                                    LocalDateTime.of(localDate, LocalTime.MIDNIGHT)
                                } else {
                                    LocalDateTime.of(localDate, LocalTime.of(hour, minute))
                                }
                                val newMillis = localDateTime
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli()
                                onUpdateDueDate(task.dueDate, newMillis)
                                showCalendarUpdatedPopup = true
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
                                text = "Update Event",
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

@Composable
private fun AnimatedCompletionGlowBox(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "taskCompletionGlow")
    val pulse by infinite.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "taskCompletionGlowPulse"
    )
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .padding(2.dp)
                .drawBehind {
                    drawCircle(
                        color = accentColor.copy(alpha = 0.18f * pulse),
                        radius = size.minDimension * 0.64f
                    )
                }
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(shape)
                .background(accentColor.copy(alpha = 0.22f + (0.08f * pulse)))
                .border(width = 1.5.dp, color = accentColor, shape = shape)
        )
    }
}

@Composable
private fun taskEditSheetColor(): Color {
    return if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surface
    } else {
        colorResource(id = R.color.widget_background)
    }
}

@Composable
private fun taskEditInnerContainerColor(): Color {
    return if (isSystemInDarkTheme()) {
        Color.Transparent
    } else {
        colorResource(id = R.color.widget_item_card)
    }
}

@Composable
private fun taskEditDatePickerContainerColor(): Color {
    return if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surface
    } else {
        colorResource(id = R.color.widget_item_card)
    }
}

@Composable
private fun taskEditBackButtonContainerColor(): Color {
    return if (isSystemInDarkTheme()) {
        Color.White
    } else {
        colorResource(id = R.color.widget_item_card)
    }
}

@Composable
private fun taskEditTimeFieldBackgroundColor(): Color {
    return if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        colorResource(id = R.color.white)
    }
}

@Composable
private fun taskEditTimeFieldContentColor(): Color {
    return if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.onSurface
    } else {
        colorResource(id = R.color.widget_text_primary)
    }
}

/**
 *

A floating "Calendar Updated" confirmation popup that animates upward and fades out,

matching the style of the "Added to Calendar" popup in [AddTaskItem].

The outer [Box] is 200 dp tall so the text (anchored at the bottom) can travel

120 dp upward without leaving the Popup window bounds and being clipped.

@param onDismiss Called when the animation completes.
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