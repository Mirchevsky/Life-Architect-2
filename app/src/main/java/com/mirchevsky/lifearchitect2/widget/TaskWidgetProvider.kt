package com.mirchevsky.lifearchitect2.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews
import com.mirchevsky.lifearchitect2.R
import com.mirchevsky.lifearchitect2.data.db.AppDatabase
import com.mirchevsky.lifearchitect2.data.db.entity.TaskEntity
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * TaskWidgetProvider
 * ─────────────────────────────────────────────────────────────────────────────
 * AppWidgetProvider for the Life Architect task list widget.
 *
 * This implementation uses the modern [RemoteViews.RemoteCollectionItems] API
 * (API 31+) instead of the deprecated [android.widget.RemoteViewsService] /
 * [android.widget.RemoteViewsService.RemoteViewsFactory] pattern. All item
 * construction happens inline inside [pushWidget].
 *
 * ## Why runBlocking instead of a coroutine scope?
 *
 * [AppWidgetProvider.onReceive] and [onUpdate] run on the **main thread** and
 * return immediately. If we launch a coroutine on Dispatchers.IO and call
 * [AppWidgetManager.updateAppWidget] from inside it, the home screen receives
 * the RemoteViews **after** the provider has already returned — which works for
 * the outer RemoteViews frame, but the [RemoteViews.RemoteCollectionItems]
 * payload is evaluated synchronously by the launcher at the moment
 * [updateAppWidget] is called. Because the coroutine runs on a background
 * thread, the launcher may read the collection before Room has finished
 * populating it, resulting in rows that render with the correct count but
 * **blank text** (the view hierarchy is inflated but no setTextViewText calls
 * have been applied yet).
 *
 * The fix is to call [pushWidget] from a background thread (via
 * [android.os.AsyncTask]-style dispatch or [goAsync]) and use **runBlocking**
 * inside it — exactly the same pattern that [TaskWidgetItemFactory.loadTasks]
 * used with [RemoteViewsFactory.onDataSetChanged]. This guarantees that Room
 * returns a fully-populated list before [buildCollectionItems] is called, and
 * that [updateAppWidget] is called only after the complete RemoteViews is ready.
 *
 * ## Refresh strategy
 *
 * Every data change (add, complete, revert, edit) sends a
 * [ACTION_WIDGET_REFRESH] broadcast. [onReceive] handles it by calling
 * [pushWidgetAsync] for every active widget ID. [pushWidgetAsync] calls
 * [goAsync] to extend the BroadcastReceiver deadline, then dispatches to a
 * background thread via [Thread] where [pushWidget] blocks on Room.
 *
 * ## Task completion
 *
 * Each row carries a fill-in intent with the task ID. The provider intercepts
 * [ACTION_COMPLETE_TASK], marks the task done in Room on a background thread,
 * then immediately calls [pushWidget] to refresh the list.
 *
 * Place at:
 *   app/src/main/java/com/mirchevsky/lifearchitect2/widget/TaskWidgetProvider.kt
 */
class TaskWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_REFRESH = "com.mirchevsky.lifearchitect2.WIDGET_REFRESH"
        const val ACTION_COMPLETE_TASK  = "com.mirchevsky.lifearchitect2.COMPLETE_TASK"
        const val EXTRA_TASK_ID         = "task_id"

        // Colours for urgent / pinned / default rows
        private val COLOR_URGENT  = Color.parseColor("#E53935")    // red
        private val COLOR_PINNED  = Color.parseColor("#F59E0B")    // amber
        private val COLOR_DEFAULT = Color.parseColor("#80FFFFFF")  // muted white (dark mode)

        private val DUE_DATE_FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM d, HH:mm")

        /** Returns true if the SYSTEM_ALERT_WINDOW permission has been granted. */
        fun hasOverlayPermission(context: Context): Boolean =
            Settings.canDrawOverlays(context)

        /**
         * Sends [ACTION_WIDGET_REFRESH] to all active instances of this provider.
         * Safe to call from any context (service, activity, ViewModel).
         */
        fun sendRefreshBroadcast(context: Context) {
            val intent = Intent(ACTION_WIDGET_REFRESH)
                .setClass(context, TaskWidgetProvider::class.java)
            context.sendBroadcast(intent)
        }
    }

    // ── AppWidgetProvider callbacks ───────────────────────────────────────────

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ) {
        ids.forEach { id -> pushWidgetAsync(context, manager, id) }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, TaskWidgetProvider::class.java)
        )
        onUpdate(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {

            ACTION_WIDGET_REFRESH -> {
                // Sent by MainViewModel (and WidgetOverlayService) after any
                // task mutation. Rebuild and push the full widget for every
                // active instance.
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, TaskWidgetProvider::class.java)
                )
                ids.forEach { id -> pushWidgetAsync(context, manager, id) }
            }

            ACTION_COMPLETE_TASK -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                // goAsync() extends the BroadcastReceiver deadline so the
                // background thread has time to write to Room and push the
                // updated widget without triggering an ANR.
                val pendingResult = goAsync()
                Thread {
                    try {
                        val dao = AppDatabase.getDatabase(context).taskDao()
                        // Use the direct suspend query (same pattern as the old
                        // TaskWidgetItemFactory) to avoid Flow type-inference issues.
                        val task = runBlocking {
                            dao.getPendingTasksForUser("local_user")
                        }.firstOrNull { it.id == taskId }

                        if (task != null) {
                            runBlocking {
                                dao.upsertTask(
                                    task.copy(
                                        isCompleted = true,
                                        status = "completed",
                                        completedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        }

                        // Push fresh widget immediately after the DB write
                        val manager = AppWidgetManager.getInstance(context)
                        val ids = manager.getAppWidgetIds(
                            ComponentName(context, TaskWidgetProvider::class.java)
                        )
                        ids.forEach { id -> pushWidget(context, manager, id) }
                    } finally {
                        pendingResult.finish()
                    }
                }.start()
            }
        }
    }

    // ── Widget construction ───────────────────────────────────────────────────

    /**
     * Dispatches [pushWidget] to a background thread via [goAsync], extending
     * the BroadcastReceiver deadline so Room can be queried without blocking
     * the main thread or risking an ANR.
     *
     * Called from [onUpdate] and [onReceive] (ACTION_WIDGET_REFRESH).
     */
    private fun pushWidgetAsync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val pendingResult = goAsync()
        Thread {
            try {
                pushWidget(context, appWidgetManager, appWidgetId)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    /**
     * Queries Room **synchronously** (via [runBlocking]) for the current pending
     * task list, builds a full [RemoteViews] including a
     * [RemoteViews.RemoteCollectionItems] for the task list, and pushes it to
     * the home screen via [AppWidgetManager.updateAppWidget].
     *
     * **Must be called from a background thread.** Using [runBlocking] here is
     * intentional and correct — it is the same pattern used by the old
     * [TaskWidgetItemFactory.onDataSetChanged] with [kotlinx.coroutines.runBlocking].
     * It guarantees that Room returns a fully-populated list before
     * [buildCollectionItems] is called, so the launcher never sees blank rows.
     */
    private fun pushWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // 1. Load tasks from Room synchronously (called on a background thread)
        val tasks: List<TaskEntity> = runBlocking {
            AppDatabase.getDatabase(context)
                .taskDao()
                .getPendingTasksForUser("local_user")
        }

        // 2. Build the RemoteCollectionItems from the task list
        val items = buildCollectionItems(context, tasks)

        // 3. Build the outer widget RemoteViews
        val rv = RemoteViews(context.packageName, R.layout.widget_task_list)

        // ── 3a. Bind the collection using the modern non-deprecated API ──────
        rv.setRemoteAdapter(R.id.widget_task_list, items)
        rv.setEmptyView(R.id.widget_task_list, R.id.widget_empty_text)

        // ── 3b. Task completion template intent ───────────────────────────────
        val completionTemplate = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            Intent(ACTION_COMPLETE_TASK).setClass(context, TaskWidgetProvider::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        rv.setPendingIntentTemplate(R.id.widget_task_list, completionTemplate)

        // ── 3c. Mic button ────────────────────────────────────────────────────
        rv.setOnClickPendingIntent(
            R.id.widget_btn_mic,
            overlayServiceIntent(context, WidgetOverlayService.ACTION_MIC, appWidgetId, 10)
        )

        // ── 3d. Calendar button ───────────────────────────────────────────────
        rv.setOnClickPendingIntent(
            R.id.widget_btn_add_event,
            overlayServiceIntent(context, WidgetOverlayService.ACTION_ADD_EVENT, appWidgetId, 20)
        )

        // ── 3e. Add Task button ───────────────────────────────────────────────
        rv.setOnClickPendingIntent(
            R.id.widget_btn_add_task,
            overlayServiceIntent(context, WidgetOverlayService.ACTION_ADD_TASK, appWidgetId, 30)
        )

        // 4. Push the complete RemoteViews to the home screen
        appWidgetManager.updateAppWidget(appWidgetId, rv)
    }

    // ── Collection item builder ───────────────────────────────────────────────

    /**
     * Converts a list of [TaskEntity] objects into a
     * [RemoteViews.RemoteCollectionItems] ready to be passed to
     * [RemoteViews.setRemoteAdapter].
     *
     * Each item is a [RemoteViews] inflated from [R.layout.widget_task_item]
     * with the same visual logic previously handled by [TaskWidgetItemFactory]:
     *   - Urgent tasks: red title text + red flag icon
     *   - Pinned tasks: amber title text + pin icon
     *   - Normal tasks: default muted text, no icons
     *   - Due date shown when present, hidden otherwise
     *   - Fill-in intent carries the task ID for completion handling
     */
    private fun buildCollectionItems(
        context: Context,
        tasks: List<TaskEntity>
    ): RemoteViews.RemoteCollectionItems {
        val builder = RemoteViews.RemoteCollectionItems.Builder()
            .setHasStableIds(true)
            .setViewTypeCount(1)

        tasks.forEach { task ->
            val itemRv = buildTaskRow(context, task)
            // Use a stable long derived from the task's string ID
            val stableId = task.id.hashCode().toLong()
            builder.addItem(stableId, itemRv)
        }

        return builder.build()
    }

    /**
     * Builds a single row [RemoteViews] for [task], matching the visual style
     * of the in-app task list.
     */
    private fun buildTaskRow(context: Context, task: TaskEntity): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_task_item)

        // ── Title ─────────────────────────────────────────────────────────────
        rv.setTextViewText(R.id.widget_item_title, task.title)

        // ── Title colour + flag / pin icon visibility ─────────────────────────
        //
        // For urgent and pinned tasks we override the XML default text colour.
        // For normal tasks we deliberately do NOT call setTextColor so that the
        // theme-aware @color/widget_text_primary defined in widget_task_item.xml
        // is used. Hardcoding a near-white value (#EEEEEE) here caused the title
        // to be invisible on light-mode (white) widget backgrounds.
        when {
            task.isUrgent -> {
                rv.setTextColor(R.id.widget_item_title, COLOR_URGENT)
                rv.setViewVisibility(R.id.widget_item_flag, View.VISIBLE)
                rv.setInt(R.id.widget_item_flag, "setColorFilter", COLOR_URGENT)
                rv.setViewVisibility(R.id.widget_item_pin, View.GONE)
                rv.setInt(R.id.widget_item_pin, "setColorFilter", 0)  // clear any stale tint
            }
            task.isPinned -> {
                rv.setTextColor(R.id.widget_item_title, COLOR_PINNED)
                rv.setViewVisibility(R.id.widget_item_pin, View.VISIBLE)
                rv.setInt(R.id.widget_item_pin, "setColorFilter", COLOR_PINNED)
                rv.setViewVisibility(R.id.widget_item_flag, View.GONE)
                rv.setInt(R.id.widget_item_flag, "setColorFilter", 0)  // clear any stale tint
            }
            else -> {
                // Let the XML-defined @color/widget_text_primary apply — do not
                // override with a hardcoded colour that may be invisible on light
                // widget backgrounds.
                rv.setViewVisibility(R.id.widget_item_flag, View.GONE)
                rv.setInt(R.id.widget_item_flag, "setColorFilter", 0)
                rv.setViewVisibility(R.id.widget_item_pin, View.GONE)
                rv.setInt(R.id.widget_item_pin, "setColorFilter", 0)
            }
        }

        // ── Checkbox tint ─────────────────────────────────────────────────────
        val dotColor = when {
            task.isUrgent -> COLOR_URGENT
            task.isPinned -> COLOR_PINNED
            else          -> COLOR_DEFAULT
        }
        rv.setInt(R.id.widget_item_dot, "setColorFilter", dotColor)

        // ── Due date ──────────────────────────────────────────────────────────
        val dueDate = task.dueDate
        if (dueDate != null) {
            val formatted = Instant.ofEpochMilli(dueDate)
                .atZone(ZoneId.systemDefault())
                .format(DUE_DATE_FMT)
            rv.setTextViewText(R.id.widget_item_due, formatted)
            rv.setViewVisibility(R.id.widget_item_due, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.widget_item_due, View.GONE)
        }

        // ── Fill-in intent: carries task ID to the completion template ─────────
        val fillIn = Intent().apply {
            putExtra(EXTRA_TASK_ID, task.id)
        }
        rv.setOnClickFillInIntent(R.id.widget_item_root, fillIn)

        return rv
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun overlayServiceIntent(
        context: Context,
        action: String,
        widgetId: Int,
        requestOffset: Int
    ): PendingIntent {
        val intent = WidgetOverlayService.buildIntent(context, action, widgetId)
        return PendingIntent.getForegroundService(
            context,
            widgetId * 100 + requestOffset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
