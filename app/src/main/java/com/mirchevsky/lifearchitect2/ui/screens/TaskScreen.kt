package com.mirchevsky.lifearchitect2.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirchevsky.lifearchitect2.ui.composables.AddTaskItem
import com.mirchevsky.lifearchitect2.ui.composables.TaskItem
import com.mirchevsky.lifearchitect2.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

/**
 *
 * Tasks screen — WhatsApp-style layout.
 *
 * Uses a structural [Column] with [Modifier.weight] instead of a [Box] overlay.
 *
 * This means the [LazyColumn] physically stops where [AddTaskItem] begins — no
 *
 * items can ever be hidden behind the card, no manual height math needed.
 *
 * [statusBarsPadding] on the root Column pushes content below the Android status bar.
 *
 * [imePadding] handles the keyboard inset smoothly when the user is typing.
 *
 * A [delay] of 100 ms before [animateScrollToItem] gives the layout engine time
 *
 * to begin its resize animation so the scroll targets the correct final offset.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksScreen(
    viewModel: MainViewModel,
    focusAddTask: Boolean = false,
    onFocusHandled: () -> Unit = {},
    enableOpenGlow: Boolean = false,
    onOpenGlowConsumed: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val isImeVisible = WindowInsets.isImeVisible

// Pinned tasks float to the top; within each group order is preserved.
// Must be computed here (composable scope) — remember() cannot be called
// inside a LazyListScope lambda (not a @Composable context).
    val sortedTasks = remember(uiState.pendingTasks) {
        uiState.pendingTasks.sortedByDescending { it.isPinned }
    }

    val totalItems = 1 + uiState.pendingTasks.size
    val glowAmount = remember { Animatable(0f) }

    LaunchedEffect(enableOpenGlow) {
        if (enableOpenGlow) {
            onOpenGlowConsumed()
            glowAmount.snapTo(1f)
            glowAmount.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 1000)
            )
        } else {
            glowAmount.snapTo(0f)
        }
    }

// When keyboard opens, wait 100 ms for layout to settle then scroll to last item
    LaunchedEffect(isImeVisible) {
        if (isImeVisible && totalItems > 0) {
            delay(100)
            listState.animateScrollToItem(index = totalItems - 1)
        }
    }

// Also scroll when a new task is added (so it is always visible)
    LaunchedEffect(uiState.pendingTasks.size) {
        val idx = (1 + uiState.pendingTasks.size) - 1
        if (idx >= 0) {
            delay(50)
            listState.animateScrollToItem(index = idx)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
// Push content below the Android status bar
            .statusBarsPadding()
// Smoothly follows keyboard edge when open; clears nav bar when closed
            .imePadding()
    ) {

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)  // fills all space above AddTaskItem; shrinks when keyboard opens
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
        ) {
            items(sortedTasks, key = { it.id }) { task ->
                TaskItem(
                    task = task,
                    onCompleted = { viewModel.onTaskCompleted(task) },
                    onUpdate = { viewModel.onUpdateTask(it) },
                    onUpdateDueDate = { oldMillis, newMillis ->
                        viewModel.onUpdateTaskDueDate(task, oldMillis, newMillis)
                    },
                    spotlightStrength = glowAmount.value
                )
            }
        }

        // Input card docked structurally below the list — never overlaps
        AddTaskItem(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 6.dp),
            onAddTask = { title, difficulty, dueDate ->
                viewModel.onAddTask(title, difficulty, dueDate)
            },
            requestFocus = focusAddTask,
            onFocusConsumed = onFocusHandled
        )
    }
}