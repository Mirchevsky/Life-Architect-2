package com.mirchevsky.lifearchitect2.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews
import androidx.core.text.BidiFormatter
import com.mirchevsky.lifearchitect2.R
import com.mirchevsky.lifearchitect2.data.db.AppDatabase
import com.mirchevsky.lifearchitect2.data.db.entity.TaskEntity
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking

class TaskWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_REFRESH = "com.mirchevsky.lifearchitect2.WIDGET_REFRESH"
        const val ACTION_WIDGET_ROW_ACTION = "com.mirchevsky.lifearchitect2.WIDGET_ROW_ACTION"
        private const val ACTION_GLOW_FADE = "com.mirchevsky.lifearchitect2.WIDGET_GLOW_FADE"
        private const val GLOW_WINDOW_MS = 1800L
        private const val GLOW_DEBOUNCE_MS = 4000L
        private const val PREFS_WIDGET = "widget_glow_prefs"
        private const val KEY_GLOW_UNTIL = "glow_until_ms"
        private const val KEY_LAST_GLOW_TRIGGER = "last_glow_trigger_ms"

        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_ROW_ACTION = "row_action"

        const val ROW_ACTION_COMPLETE = "complete"
        const val ROW_ACTION_TOGGLE_FLAG = "toggle_flag"
        const val ROW_ACTION_TOGGLE_PIN = "toggle_pin"

        fun sendRefreshBroadcast(context: Context) {
            val intent = Intent(ACTION_WIDGET_REFRESH)
                .setClass(context, TaskWidgetProvider::class.java)
            context.sendBroadcast(intent)
        }
    }

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
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, TaskWidgetProvider::class.java)
                )
                ids.forEach { id -> pushWidgetAsync(context, manager, id) }
            }

            Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                triggerGlowForHomeExposure(context)
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, TaskWidgetProvider::class.java)
                )
                ids.forEach { id -> pushWidgetAsync(context, manager, id) }
            }

            ACTION_GLOW_FADE -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, TaskWidgetProvider::class.java)
                )
                ids.forEach { id -> pushWidgetAsync(context, manager, id) }
            }

            ACTION_WIDGET_ROW_ACTION -> {
                if (!Settings.canDrawOverlays(context)) {
                    PendingWidgetActionStore.saveBroadcastIntent(context, intent)
                    val permissionIntent = Intent(context, OverlayPermissionDialogActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(permissionIntent)
                    return
                }

                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                val rowAction = intent.getStringExtra(EXTRA_ROW_ACTION) ?: return

                val pendingResult = goAsync()
                Thread {
                    try {
                        val dao = AppDatabase.getDatabase(context).taskDao()
                        val task: TaskEntity? = runBlocking {
                            dao.getPendingTasksForUser("local_user")
                        }.firstOrNull { it.id == taskId }

                        var xpGained = 0

                        if (task != null) {
                            val updated = when (rowAction) {
                                ROW_ACTION_COMPLETE -> {
                                    xpGained = when (task.difficulty.lowercase()) {
                                        "easy" -> 10
                                        "medium" -> 20
                                        "hard" -> 35
                                        else -> 15
                                    }
                                    task.copy(
                                        isCompleted = true,
                                        status = "completed",
                                        completedAt = System.currentTimeMillis()
                                    )
                                }

                                ROW_ACTION_TOGGLE_FLAG -> task.copy(isUrgent = !task.isUrgent)
                                ROW_ACTION_TOGGLE_PIN -> task.copy(isPinned = !task.isPinned)
                                else -> null
                            }

                            if (updated != null) {
                                runBlocking { dao.upsertTask(updated) }
                            }
                        }

                        val manager = AppWidgetManager.getInstance(context)
                        val ids = manager.getAppWidgetIds(
                            ComponentName(context, TaskWidgetProvider::class.java)
                        )
                        ids.forEach { id -> pushWidget(context, manager, id) }

                        if (xpGained > 0) {
                            context.startService(
                                Intent(context, WidgetOverlayService::class.java).apply {
                                    action = WidgetOverlayService.ACTION_SHOW_XP
                                    putExtra(WidgetOverlayService.EXTRA_XP_AMOUNT, xpGained)
                                    putExtra(WidgetOverlayService.EXTRA_XP_LABEL, "+$xpGained XP")
                                }
                            )
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }.start()
            }
        }
    }

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

    private fun pushWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val tasks: List<TaskEntity> = runBlocking {
            AppDatabase.getDatabase(context)
                .taskDao()
                .getPendingTasksForUser("local_user")
        }

        val items = buildCollectionItems(context, tasks)
        val rv = RemoteViews(context.packageName, R.layout.widget_task_list)

        rv.setRemoteAdapter(R.id.widget_task_list, items)
        rv.setEmptyView(R.id.widget_task_list, R.id.widget_empty_text)

        val rowActionTemplate = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            Intent(ACTION_WIDGET_ROW_ACTION)
                .setClass(context, TaskWidgetProvider::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        rv.setPendingIntentTemplate(R.id.widget_task_list, rowActionTemplate)

        rv.setOnClickPendingIntent(
            R.id.widget_btn_mic,
            getServiceIntent(context, WidgetOverlayService.ACTION_MIC, appWidgetId, 10)
        )
        rv.setOnClickPendingIntent(
            R.id.widget_btn_add_event,
            getServiceIntent(context, WidgetOverlayService.ACTION_ADD_EVENT, appWidgetId, 20)
        )
        rv.setOnClickPendingIntent(
            R.id.widget_btn_add_task,
            getServiceIntent(context, WidgetOverlayService.ACTION_ADD_TASK, appWidgetId, 30)
        )

        appWidgetManager.updateAppWidget(appWidgetId, rv)
    }

    private fun buildCollectionItems(
        context: Context,
        tasks: List<TaskEntity>
    ): RemoteViews.RemoteCollectionItems {
        val distinctLayoutCount = tasks
            .asSequence()
            .map { getTaskRowLayoutRes(it) }
            .distinct()
            .count()

        val builder = RemoteViews.RemoteCollectionItems.Builder()
            .setHasStableIds(true)
            .setViewTypeCount(maxOf(1, distinctLayoutCount))

        tasks.forEach { task ->
            val itemRv = buildTaskRow(context, task)
            val stableId = task.id.hashCode().toLong()
            builder.addItem(stableId, itemRv)
        }

        return builder.build()
    }

    private fun buildTaskRow(context: Context, task: TaskEntity): RemoteViews {
        val layoutRes = getTaskRowLayoutRes(task)

        val rv = RemoteViews(context.packageName, layoutRes)
        val colorIconInactive = context.getColor(R.color.widget_icon_inactive)
        val isGlowActive = isGlowActive(context)
        val isDarkTheme =
            (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
        val titleColor = if (isDarkTheme) context.getColor(R.color.white) else context.getColor(R.color.black)
        val inactiveFlagPinTint = if (isDarkTheme) colorIconInactive else context.getColor(R.color.black)
        rv.setImageViewResource(
            R.id.widget_item_dot,
            getCheckboxDrawableRes(task)
        )

        val safeTitle = BidiFormatter.getInstance().unicodeWrap(task.title)
        rv.setTextViewText(R.id.widget_item_title, safeTitle)
        rv.setTextColor(R.id.widget_item_title, titleColor)
        rv.setTextViewTextSize(
            R.id.widget_item_title,
            android.util.TypedValue.COMPLEX_UNIT_SP,
            if (isGlowActive) 14f else 13f
        )

        rv.setInt(R.id.widget_item_flag, "setColorFilter", inactiveFlagPinTint)
        rv.setInt(R.id.widget_item_pin, "setColorFilter", inactiveFlagPinTint)

        val isEvent = task.status == "event"
        rv.setViewVisibility(R.id.widget_item_calendar, if (isEvent) View.VISIBLE else View.GONE)
        if (isEvent) {
            rv.setInt(R.id.widget_item_calendar, "setColorFilter", colorIconInactive)
        }

        val dueDate = task.dueDate
        if (dueDate != null) {
            val zone = ZoneId.systemDefault()
            val dueInstant = Instant.ofEpochMilli(dueDate)
            val dueUtc = dueInstant.atZone(ZoneOffset.UTC)
            val isAllDay = dueUtc.hour == 0 && dueUtc.minute == 0 && dueUtc.second == 0
            val dueZdt = dueInstant.atZone(zone)
            val dueLocal = dueZdt.toLocalDate()
            val today = java.time.LocalDate.now(zone)

            val label = if (isAllDay) {
                when (dueLocal) {
                    today -> "Today"
                    today.plusDays(1) -> "Tomorrow"
                    today.minusDays(1) -> "Yesterday"
                    else -> dueZdt.format(DateTimeFormatter.ofPattern("MMM d"))
                }
            } else {
                val timePart = dueZdt.format(DateTimeFormatter.ofPattern("HH:mm"))
                when (dueLocal) {
                    today -> "Today, $timePart"
                    today.plusDays(1) -> "Tomorrow, $timePart"
                    today.minusDays(1) -> "Yesterday, $timePart"
                    else -> dueZdt.format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
                }
            }

            rv.setTextViewText(R.id.widget_item_due, label)
            rv.setViewVisibility(R.id.widget_item_date_row, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.widget_item_date_row, View.GONE)
        }

        rv.setOnClickFillInIntent(R.id.widget_item_dot, Intent().apply {
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_ROW_ACTION, ROW_ACTION_COMPLETE)
        })
        rv.setOnClickFillInIntent(R.id.widget_item_flag, Intent().apply {
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_ROW_ACTION, ROW_ACTION_TOGGLE_FLAG)
        })
        rv.setOnClickFillInIntent(R.id.widget_item_pin, Intent().apply {
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_ROW_ACTION, ROW_ACTION_TOGGLE_PIN)
        })

        return rv
    }

    private fun getTaskRowLayoutRes(task: TaskEntity): Int {
        return when {
            task.isUrgent && task.isPinned -> R.layout.widget_task_item_urgent_pinned
            task.isUrgent -> R.layout.widget_task_item_urgent
            task.isPinned -> R.layout.widget_task_item_pinned
            else -> R.layout.widget_task_item
        }
    }

    private fun getCheckboxDrawableRes(task: TaskEntity): Int {
        return when {
            task.isUrgent -> R.drawable.widget_checkbox_glow_urgent
            task.isPinned -> R.drawable.widget_checkbox_glow_pinned
            else -> R.drawable.widget_checkbox_glow
        }
    }

    private fun triggerGlowForHomeExposure(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastTrigger = prefs.getLong(KEY_LAST_GLOW_TRIGGER, 0L)
        if (now - lastTrigger < GLOW_DEBOUNCE_MS) return

        prefs.edit()
            .putLong(KEY_LAST_GLOW_TRIGGER, now)
            .putLong(KEY_GLOW_UNTIL, now + GLOW_WINDOW_MS)
            .apply()

        Handler(Looper.getMainLooper()).postDelayed(
            {
                context.sendBroadcast(
                    Intent(ACTION_GLOW_FADE).setClass(context, TaskWidgetProvider::class.java)
                )
            },
            GLOW_WINDOW_MS + 120L
        )
    }

    private fun isGlowActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE)
        return System.currentTimeMillis() <= prefs.getLong(KEY_GLOW_UNTIL, 0L)
    }

    private fun blendTowardWhite(color: Int, ratio: Float): Int {
        val clamped = ratio.coerceIn(0f, 1f)
        val r = Color.red(color) + ((255 - Color.red(color)) * clamped).toInt()
        val g = Color.green(color) + ((255 - Color.green(color)) * clamped).toInt()
        val b = Color.blue(color) + ((255 - Color.blue(color)) * clamped).toInt()
        return Color.rgb(r, g, b)
    }

    private fun getServiceIntent(
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