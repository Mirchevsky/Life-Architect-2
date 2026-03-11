package com.mirchevsky.lifearchitect2.widget

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class CalendarPermissionActivity : ComponentActivity() {

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val readGranted = result[Manifest.permission.READ_CALENDAR] == true
            val writeGranted = result[Manifest.permission.WRITE_CALENDAR] == true

            if (readGranted && writeGranted) {
                PendingWidgetActionStore.dispatchAndClear(this)
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            )
        )
    }
}
