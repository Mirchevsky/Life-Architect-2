package com.mirchevsky.lifearchitect2.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.mirchevsky.lifearchitect2.R
import com.mirchevsky.lifearchitect2.data.db.AppDatabase
import com.mirchevsky.lifearchitect2.data.db.entity.TaskEntity
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * TaskWidgetItemFactory
 * ─────────────────────────────────────────────────────────────────────────────
 * RemoteViewsFactory that populates the widget's ListView with pending tasks
 * from Room.
 *
 * Key design notes:
 *
 * 1. **Use a direct suspend DAO query, not a Flow.**
 *    [RemoteViewsFactory.onDataSetChanged] runs on a background thread managed
 *    by the AppWidgetService framework. The framework imposes a hard timeout on
 *    how long it waits for [getViewAt] to return a view. If [onDataSetChanged]
 *    takes too long (e.g. because Flow collection adds overhead), the framework
 *    shows its built-in "Loading…" placeholder for every row even though
 *    [getCount] already returned the correct count.
 *
 *    Using [TaskDao.getPendingTasksForUser] (a plain `suspend` query) instead of
 *    `observePendingTasksForUser(...).first()` eliminates the Flow overhead and
 *    makes [onDataSetChanged] return as fast as possible.
 *
 * 2. **Provide a real [getLoadingView].**
 *    Returning `null` from [getLoadingView] tells Android to use its own default
 *    loading placeholder — the grey "Loading…" text. Returning the actual row
 *    layout (with blank text) ensures the widget always shows the correct view
 *    type and prevents the default placeholder from ever appearing.
 *
 * 3. **runBlocking is intentional.**
 *    [onDataSetChanged] is always called on a background thread by the framework;
 *    blocking that thread while Room returns data is the correct pattern.
 *
 * Place at:
 *   app/src/main/java/com/mirchevsky/lifearchitect2/widget/TaskWidgetItemFactory.kt
 */
class TaskWidgetItemFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var tasks: List<TaskEntity> = emptyList()

    private val dueDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, HH:mm")

    // Colours applied via RemoteViews.setInt(id, "setColorFilter", color)
    // and RemoteViews.setTextColor(id, color).
    private val colorUrgent  = Color.parseColor("#E53935")  // red
    private val colorPinned  = Color.parseColor("#F59E0B")  // amber
    private val colorDefault = Color.parseColor("#80FFFFFF") // muted white (dark mode)

    // ── Factory lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        // Eagerly load on creation so the first getCount() call is never 0
        // when data actually exists.
        loadTasks()
    }

    override fun onDataSetChanged() {
        // Called by notifyAppWidgetViewDataChanged() — reload from Room.
        // This runs on a dedicated background thread managed by the framework;
        // runBlocking is the correct pattern here.
        loadTasks()
    }

    override fun onDestroy() {
        tasks = emptyList()
    }

    // ── List data ─────────────────────────────────────────────────────────────

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= tasks.size) return buildLoadingView()
        val task = tasks[position]
        val rv = RemoteViews(context.packageName, R.layout.widget_task_item)

        // ── Title ─────────────────────────────────────────────────────────────
        rv.setTextViewText(R.id.widget_item_title, task.title)

        // ── Title text colour + flag/pin icon visibility ───────────────────────
        when {
            task.isUrgent -> {
                // Red text, red flag visible, pin hidden
                rv.setTextColor(R.id.widget_item_title, colorUrgent)
                rv.setViewVisibility(R.id.widget_item_flag, View.VISIBLE)
                rv.setInt(R.id.widget_item_flag, "setColorFilter", colorUrgent)
                rv.setViewVisibility(R.id.widget_item_pin, View.GONE)
            }
            task.isPinned -> {
                // Amber text, pin visible, flag hidden
                rv.setTextColor(R.id.widget_item_title, colorPinned)
                rv.setViewVisibility(R.id.widget_item_pin, View.VISIBLE)
                rv.setInt(R.id.widget_item_pin, "setColorFilter", colorPinned)
                rv.setViewVisibility(R.id.widget_item_flag, View.GONE)
            }
            else -> {
                // Default text colour, both icons hidden
                rv.setTextColor(R.id.widget_item_title, Color.parseColor("#EEEEEE"))
                rv.setViewVisibility(R.id.widget_item_flag, View.GONE)
                rv.setViewVisibility(R.id.widget_item_pin, View.GONE)
            }
        }

        // ── Checkbox tint ─────────────────────────────────────────────────────
        val dotColor = when {
            task.isUrgent -> colorUrgent
            task.isPinned -> colorPinned
            else          -> colorDefault
        }
        rv.setInt(R.id.widget_item_dot, "setColorFilter", dotColor)

        // ── Due date ──────────────────────────────────────────────────────────
        val dueDate: Long? = task.dueDate
        if (dueDate != null) {
            val formatted = Instant.ofEpochMilli(dueDate)
                .atZone(ZoneId.systemDefault())
                .format(dueDateFormatter)
            rv.setTextViewText(R.id.widget_item_due, formatted)
            rv.setViewVisibility(R.id.widget_item_due, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.widget_item_due, View.GONE)
        }

        // ── Fill-in intent: carries task ID to TaskWidgetProvider on tap ──────
        val fillIn = Intent().apply {
            putExtra(TaskWidgetProvider.EXTRA_TASK_ID, task.id)
        }
        rv.setOnClickFillInIntent(R.id.widget_item_root, fillIn)

        return rv
    }

    /**
     * Returns the actual row layout with blank text as the loading placeholder.
     *
     * Returning `null` here causes Android to show its own built-in "Loading…"
     * grey text for every row while [onDataSetChanged] is running. By returning
     * the real row layout (with empty fields) we ensure the correct view type is
     * always used and the "Loading…" placeholder never appears.
     */
    override fun getLoadingView(): RemoteViews = buildLoadingView()

    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long =
        tasks.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Synchronously loads pending tasks from Room using a direct one-shot
     * suspend query ([TaskDao.getPendingTasksForUser]).
     *
     * Ordered: pinned first, then urgent, then by creation date descending —
     * matching the in-app task list sort order.
     *
     * The app always uses "local_user" as the userId (no Google sign-in
     * persists a different ID), so this is hardcoded for simplicity.
     */
    private fun loadTasks() {
        tasks = runBlocking {
            AppDatabase.getDatabase(context)
                .taskDao()
                .getPendingTasksForUser("local_user")
        }
    }

    /** Builds a blank row view used as the loading placeholder. */
    private fun buildLoadingView(): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_task_item).also { rv ->
            rv.setTextViewText(R.id.widget_item_title, "")
            rv.setViewVisibility(R.id.widget_item_due, View.GONE)
            rv.setViewVisibility(R.id.widget_item_flag, View.GONE)
            rv.setViewVisibility(R.id.widget_item_pin, View.GONE)
        }
}
