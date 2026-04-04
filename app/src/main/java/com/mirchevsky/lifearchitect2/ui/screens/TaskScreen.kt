package com.mirchevsky.lifearchitect2.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Scaffold
import com.mirchevsky.lifearchitect2.ui.composables.AddTaskItem
import com.mirchevsky.lifearchitect2.ui.composables.TaskItem
import com.mirchevsky.lifearchitect2.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

/**
 * Tasks screen — WhatsApp-style layout.
 *
 * Uses [Scaffold] with a bottomBar instead of a Box overlay or Modifier.weight.
 *
 * This keeps [AddTaskItem] docked at the bottom while the [LazyColumn] gets
 * proper content padding above it.
 *
 * [statusBarsPadding] keeps content below the Android status bar.
 *
 * [imePadding] allows the bottom input area to rise smoothly with the keyboard.
 *
 * A short [delay] before [animateScrollToItem] gives layout/insets time to settle
 * so the scroll targets the correct final offset.
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
    val totalItems = uiState.pendingTasks.size
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

    // When keyboard opens, wait briefly for layout to settle, then scroll to last item
    LaunchedEffect(isImeVisible) {
        if (isImeVisible && totalItems > 0) {
            delay(100)
            listState.animateScrollToItem(index = totalItems - 1)
        }
    }

    // Also scroll when a new task is added so it stays visible
    LaunchedEffect(uiState.pendingTasks.size) {
        val idx = uiState.pendingTasks.size - 1
        if (idx >= 0) {
            delay(50)
            listState.animateScrollToItem(index = idx)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        bottomBar = {
            AddTaskItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 6.dp),
                onAddTask = { title, difficulty, dueDate ->
                    viewModel.onAddTask(title, difficulty, dueDate)
                },
                requestFocus = focusAddTask,
                onFocusConsumed = onFocusHandled
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
        ) {
            items(uiState.pendingTasks, key = { it.id }) { task ->
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
    }
}