package io.lifephysics.architect2.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.lifephysics.architect2.ui.composables.AddTaskItem
import io.lifephysics.architect2.ui.composables.TaskItem
import io.lifephysics.architect2.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

/**
 * Tasks screen — WhatsApp-style layout.
 *
 * Uses a structural [Column] with [Modifier.weight] instead of a [Box] overlay.
 * This means the [LazyColumn] physically stops where [AddTaskItem] begins — no
 * items can ever be hidden behind the card, no manual height math needed.
 *
 * [windowInsetsPadding] on the root Column handles both the nav-bar inset (when
 * keyboard is closed) and the IME inset (when keyboard is open) in one smooth
 * animated modifier — no manual pixel-to-dp conversion required.
 *
 * A [delay] of 100 ms before [animateScrollToItem] gives the layout engine time
 * to begin its resize animation so the scroll targets the correct final offset.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksScreen(
    viewModel: MainViewModel,
    focusAddTask: Boolean = false,
    onFocusHandled: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val isImeVisible = WindowInsets.isImeVisible
    val totalItems = 1 + uiState.pendingTasks.size

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
            // Smoothly follows keyboard edge when open; clears nav bar when closed
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
    ) {

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)  // fills all space above AddTaskItem; shrinks when keyboard opens
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 0.dp, bottom = 8.dp)
        ) {
            item {
                Text(
                    text = "Tasks",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp)
                )
            }

            items(uiState.pendingTasks, key = { it.id }) { task ->
                TaskItem(
                    task = task,
                    onCompleted = { viewModel.onTaskCompleted(task) }
                )
            }
        }

        // Input card docked structurally below the list — never overlaps
        AddTaskItem(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 8.dp),
            onAddTask = { title, difficulty, dueDate ->
                viewModel.onAddTask(title, difficulty, dueDate)
            },
            requestFocus = focusAddTask,
            onFocusConsumed = onFocusHandled
        )
    }
}
