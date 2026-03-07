package com.mirchevsky.lifearchitect2

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.mirchevsky.lifearchitect2.data.AppRepository
import com.mirchevsky.lifearchitect2.data.db.AppDatabase
import com.mirchevsky.lifearchitect2.data.db.entity.UserEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Custom Application class for Life Architect 2.
 *
 * Holds singleton instances of the database and repository, and ensures
 * the local user record is seeded into the database on first launch.
 */
class LifeArchitectApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database by lazy {
        AppDatabase.getDatabase(applicationContext)
    }

    val repository by lazy {
        AppRepository(
            userDao = database.userDao(),
            goalDao = database.goalDao(),
            taskDao = database.taskDao()
        )
    }

    override fun onCreate() {
        super.onCreate()

        // ── AdMob initialisation ───────────────────────────────────────────
        // Register test devices so the SDK returns fully populated test ads
        // during development. The hash below is the emulator's device ID
        // extracted from Logcat (UserMessagingPlatform tag).
        // TODO: Remove this block (or clear the list) before publishing.
        val testDeviceIds = listOf("B3EEABB8EE11C2BE770B684D95219ECB")
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(testDeviceIds)
                .build()
        )
        MobileAds.initialize(this) { /* initialisation complete */ }

        // ── Database seeding ───────────────────────────────────────────────
        // Ensure the local user row exists before any ViewModel tries to read it.
        // This is idempotent — upsertUser will update if the row already exists,
        // or insert a fresh default user if it does not.
        applicationScope.launch {
            val existing = repository.getUser().first()
            if (existing == null) {
                repository.upsertUser(UserEntity(googleId = "local_user"))
            }
        }
    }
}
