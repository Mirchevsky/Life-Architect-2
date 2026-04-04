package com.mirchevsky.lifearchitect2.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import com.mirchevsky.lifearchitect2.R
import com.mirchevsky.lifearchitect2.data.db.AppDatabase
import com.mirchevsky.lifearchitect2.data.db.entity.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking

/**
 * TaskWidgetItemFactory
 *
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * RemoteViewsFactory that populates the widget's ListView with pending tasks
 * (and calendar events) from Room.
 *
 * Key design notes:
 *
 * Use a direct suspend DAO query, not a Flow.
 *
 * [RemoteViewsFactory.onDataSetChanged] runs on a background thread managed
 * by the AppWidgetService framework. The framework imposes a hard timeout on
 * how long it waits for [getViewAt] to return a view. If [onDataSetChanged]
 * takes too long (e.g. because Flow collection adds overhead), the framework
 * shows its built-in "Loading…" placeholder for every row even though
 * [getCount] already returned the correct count.
 *
 * Using [TaskDao.getPendingTasksForUser] (a plain suspend query) instead of
 * observePendingTasksForUser(...).first() eliminates the Flow overhead and
 * makes [onDataSetChanged] return as fast as possible.
 *
 * Provide a real [getLoadingView].
 *
 * Returning null from [getLoadingView] tells Android to use its own default
 * loading placeholder — the grey "Loading…" text. Returning the actual row
 * layout (with blank text) ensures the widget always shows the correct view
 * type and prevents the default placeholder from ever appearing.
 *
 * runBlocking is intentional.
 *
 * [onDataSetChanged] is always called on a background thread by the framework;
 * blocking that thread while Room returns data is the correct pattern.
 *
 * Event rows (status == "event").
 *
 * Calendar events created from the widget are stored as TaskEntity rows with
 * status = "event". They are rendered with:
 *
 * The event title (no emoji prefix).
 *
 * A date chip below the title showing the smart-formatted date:
 *
 * "Today, 13:37" / "Tomorrow, 09:30" / "Feb 14, 09:30"
 *
 * "Today" / "Tomorrow" / "Feb 14" (all-day, stored at UTC midnight)
 *
 * A calendar icon (widget_ic_calendar) next to the date chip.
 *
 * Flag and pin icons are always shown (same as regular tasks).
 *
 * Place at:
 *
 * app/src/main/java/com/mirchevsky/lifearchitect2/widget/TaskWidgetItemFactory.kt
 */
class TaskWidgetItemFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var tasks: List<TaskEntity> = emptyList()

    // Colours resolved from @color resources — automatically picks light or dark
    // variant based on system night-mode. No hardcoded values.
    private val colorUrgent by lazy { ContextCompat.getColor(context, R.color.widget_urgent) }
    private val colorPinned by lazy { ContextCompat.getColor(context, R.color.widget_pinned) }
    private val colorPrimary by lazy { ContextCompat.getColor(context, R.color.widget_text_primary) }
    private val colorDefault by lazy { ContextCompat.getColor(context, R.color.widget_icon_inactive) }
    private val colorPurple by lazy { ContextCompat.getColor(context, R.color.widget_accent_purple) }

    // ── Factory lifecycle ────────────────────────────────────────────────────

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
        val isEvent = task.status == "event"

        val rv = RemoteViews(context.packageName, R.layout.widget_task_item)

        // ── Title ─────────────────────────────────────────────────────────────
        rv.setTextViewText(R.id.widget_item_title, task.title)

        // ── Title text colour ─────────────────────────────────────────────────
        val titleColor = when {
            task.isUrgent -> colorUrgent
            task.isPinned -> colorPinned
            else -> colorPrimary
        }
        rv.setTextColor(R.id.widget_item_title, titleColor)

        // ── Flag icon (always visible; red when urgent, muted otherwise) ──────
        rv.setViewVisibility(R.id.widget_item_flag, View.VISIBLE)
        val flagColor = if (task.isUrgent) colorUrgent else colorDefault
        rv.setInt(R.id.widget_item_flag, "setColorFilter", flagColor)

        // ── Pin icon (always visible; amber when pinned, muted otherwise) ─────
        rv.setViewVisibility(R.id.widget_item_pin, View.VISIBLE)
        val pinColor = if (task.isPinned) colorPinned else colorDefault
        rv.setInt(R.id.widget_item_pin, "setColorFilter", pinColor)

        // ── Completion checkbox ───────────────────────────────────────────────
        rv.setImageViewResource(R.id.widget_item_dot, getCheckboxDrawableRes(task))

        // ── Date chip (below title) ───────────────────────────────────────────
        val dueDate: Long? = task.dueDate
        if (dueDate != null) {
            val formatted = formatDueDate(dueDate)
            rv.setTextViewText(R.id.widget_item_due, formatted)
            rv.setViewVisibility(R.id.widget_item_due, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.widget_item_due, View.GONE)
        }

        // ── Calendar icon (only for event rows) ───────────────────────────────
        if (isEvent) {
            rv.setViewVisibility(R.id.widget_item_calendar, View.VISIBLE)
            rv.setInt(R.id.widget_item_calendar, "setColorFilter", colorPurple)
        } else {
            rv.setViewVisibility(R.id.widget_item_calendar, View.GONE)
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
     * Returning null here causes Android to show its own built-in "Loading…"
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
     * Synchronously loads pending tasks and calendar events from Room using a
     * direct one-shot suspend query ([TaskDao.getPendingTasksForUser]).
     *
     * Ordered: urgent first, then pinned, then by creation date descending —
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

    /**
     * Formats a dueDate epoch millis value for display in the widget row.
     *
     * All-day detection: if the stored millis falls exactly on UTC midnight
     * (hour == 0, minute == 0, second == 0 in UTC), the event was stored as
     * all-day and only the date portion is shown.
     *
     * Relative labels:
     *
     * Today → "Today" or "Today, HH:mm"
     * Tomorrow → "Tomorrow" or "Tomorrow, HH:mm"
     * Yesterday → "Yesterday" or "Yesterday, HH:mm"
     * Other → "MMM d" or "MMM d, HH:mm"
     */
    private fun formatDueDate(epochMillis: Long): String {
        val zoneId = ZoneId.systemDefault()
        val instant = Instant.ofEpochMilli(epochMillis)
        val zdt = instant.atZone(zoneId)

        // All-day detection: UTC midnight means the event was stored as all-day.
        val utcZdt = instant.atZone(ZoneOffset.UTC)
        val isAllDay = utcZdt.hour == 0 && utcZdt.minute == 0 && utcZdt.second == 0

        val today = LocalDate.now(zoneId)
        val eventDate = zdt.toLocalDate()

        val dateLabel = when (eventDate) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> eventDate.format(DateTimeFormatter.ofPattern("MMM d"))
        }

        return if (isAllDay) {
            dateLabel
        } else {
            val timeLabel = zdt.format(DateTimeFormatter.ofPattern("HH:mm"))
            "$dateLabel, $timeLabel"
        }
    }

    /** Builds a blank row view used as the loading placeholder. */
    private fun buildLoadingView(): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_task_item).also { rv ->
            rv.setTextViewText(R.id.widget_item_title, "")
            rv.setViewVisibility(R.id.widget_item_due, View.GONE)
            rv.setViewVisibility(R.id.widget_item_flag, View.GONE)
            rv.setViewVisibility(R.id.widget_item_pin, View.GONE)
            rv.setViewVisibility(R.id.widget_item_calendar, View.GONE)
        }

    private fun getCheckboxDrawableRes(task: TaskEntity): Int {
        return when {
            task.isUrgent -> R.drawable.widget_checkbox_glow_urgent
            task.isPinned -> R.drawable.widget_checkbox_glow_pinned
            else -> R.drawable.widget_checkbox_glow
        }
    }
}