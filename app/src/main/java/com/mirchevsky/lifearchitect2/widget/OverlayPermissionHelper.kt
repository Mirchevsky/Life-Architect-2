package com.mirchevsky.lifearchitect2.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

private const val PREFS_WIDGET_PERMISSION_RESUME = "widget_permission_resume"
private const val KEY_PENDING_KIND = "pending_kind"
private const val KEY_PENDING_INTENT_URI = "pending_intent_uri"

private const val KIND_SERVICE = "service"
private const val KIND_BROADCAST = "broadcast"

/**
 * A durable, SharedPreferences-backed store for a single pending widget action.
 * This allows the app to reliably resume a user's action after they grant a
 * required permission, even if the app process is killed while in Settings.
 */
object PendingWidgetActionStore {

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_WIDGET_PERMISSION_RESUME, Context.MODE_PRIVATE)

    fun saveServiceIntent(context: Context, intent: Intent) {
        val explicitIntent = Intent(intent).apply {
            component = ComponentName(context, WidgetOverlayService::class.java)
            `package` = context.packageName
        }

        prefs(context).edit()
            .putString(KEY_PENDING_KIND, KIND_SERVICE)
            .putString(KEY_PENDING_INTENT_URI, explicitIntent.toUri(Intent.URI_INTENT_SCHEME))
            .apply()
    }

    fun saveBroadcastIntent(context: Context, intent: Intent) {
        val explicitIntent = Intent(intent).apply {
            component = ComponentName(context, TaskWidgetProvider::class.java)
            `package` = context.packageName
        }

        prefs(context).edit()
            .putString(KEY_PENDING_KIND, KIND_BROADCAST)
            .putString(KEY_PENDING_INTENT_URI, explicitIntent.toUri(Intent.URI_INTENT_SCHEME))
            .apply()
    }

    fun hasPendingAction(context: Context): Boolean {
        return prefs(context).contains(KEY_PENDING_KIND) &&
                prefs(context).contains(KEY_PENDING_INTENT_URI)
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun dispatchAndClear(context: Context): Boolean {
        val sharedPrefs = prefs(context)
        val kind = sharedPrefs.getString(KEY_PENDING_KIND, null) ?: return false
        val intentUri = sharedPrefs.getString(KEY_PENDING_INTENT_URI, null) ?: return false

        val restoredIntent = try {
            Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME).apply {
                `package` = context.packageName
            }
        } catch (t: Throwable) {
            clear(context)
            return false
        }

        clear(context)

        return try {
            when (kind) {
                KIND_SERVICE -> {
                    ContextCompat.startForegroundService(context, restoredIntent)
                    true
                }
                KIND_BROADCAST -> {
                    context.sendBroadcast(restoredIntent)
                    true
                }
                else -> false
            }
        } catch (t: Throwable) {
            false
        }
    }
}

/**
 * A transparent activity that gates overlay permission. It immediately opens the
 * system settings screen. When the user returns, onResume() checks if the
 * permission was granted and, if so, dispatches the pending action.
 */
class OverlayPermissionDialogActivity : ComponentActivity() {

    private var openedSettings = false

    override fun onStart() {
        super.onStart()

        if (Settings.canDrawOverlays(this)) {
            if (PendingWidgetActionStore.hasPendingAction(this)) {
                PendingWidgetActionStore.dispatchAndClear(this)
            }
            finish()
            return
        }

        if (!openedSettings) {
            openedSettings = true
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()

        if (openedSettings) {
            if (Settings.canDrawOverlays(this)) {
                PendingWidgetActionStore.dispatchAndClear(this)
            }
            finish()
        }
    }
}
