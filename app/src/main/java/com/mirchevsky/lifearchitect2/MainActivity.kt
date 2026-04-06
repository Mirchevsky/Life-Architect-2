package com.mirchevsky.lifearchitect2

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.mirchevsky.lifearchitect2.data.Theme
import com.mirchevsky.lifearchitect2.data.TrendsRepository
import com.mirchevsky.lifearchitect2.ui.MainScreen
import com.mirchevsky.lifearchitect2.ui.Screen
import com.mirchevsky.lifearchitect2.ui.composables.XpPopup
import com.mirchevsky.lifearchitect2.ui.theme.AppTheme
import com.mirchevsky.lifearchitect2.ui.viewmodel.MainViewModel
import com.mirchevsky.lifearchitect2.ui.viewmodel.MainViewModelFactory

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_START_SCREEN_ROUTE = "start_screen_route"
        const val ROUTE_ANALYTICS = "analytics"
    }

    private val repository by lazy { (application as LifeArchitectApplication).repository }
    private val trendsRepository by lazy { TrendsRepository() }

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            repository = repository,
            trendsRepository = trendsRepository,
            appContext = applicationContext
        )
    }

    private lateinit var consentInformation: ConsentInformation
    private var startScreenRoute by mutableStateOf(Screen.Tasks.route)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge BEFORE super.onCreate so the window is configured
        // before the decor is attached. This removes the empty status-bar strip and
        // lets the app draw behind both the status bar and the system navigation bar.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        startScreenRoute = resolveStartScreenRoute(intent)

        // Tell the window that WE handle system-bar insets (Compose will apply them).
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // --- AdMob: Request consent and initialize SDK ---
        consentInformation = UserMessagingPlatform.getConsentInformation(this)

        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { formError ->
                    if (formError != null) {
                        Log.w("AdMob", "Consent form error: ${formError.message}")
                    }
                    initializeMobileAdsSdk()
                }
            },
            { requestError ->
                Log.w("AdMob", "Consent request error: ${requestError.message}")
                initializeMobileAdsSdk()
            }
        )

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()

            val isDarkTheme = when (uiState.themePreference) {
                Theme.DARK   -> true
                Theme.LIGHT  -> false
                Theme.SYSTEM -> systemDark
            }

            AppTheme(darkTheme = isDarkTheme) {
                // Match the system navigation bar colour to the app's NavigationBar
                // background so Samsung and other OEM gesture/button bars blend in.
                val isDark = isDarkTheme
                SideEffect {
                    // Set the system navigation bar to transparent so our
                    // NavigationBar composable draws seamlessly over it on
                    // all OEM devices (Samsung, OnePlus, etc.).
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightNavigationBars = !isDark
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainScreen(
                            viewModel = viewModel,
                            requestedRoute = startScreenRoute
                        )

                        if (uiState.xpPopupVisible) {
                            XpPopup(
                                amount = uiState.xpPopupAmount,
                                isCritical = uiState.xpPopupIsCritical,
                                onDismiss = { viewModel.onDismissXpPopup() },
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun initializeMobileAdsSdk() {
        if (consentInformation.canRequestAds()) {
            MobileAds.initialize(this) { initializationStatus ->
                Log.d("AdMob", "SDK initialized: $initializationStatus")
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startScreenRoute = resolveStartScreenRoute(intent)
    }

    private fun resolveStartScreenRoute(intent: android.content.Intent?): String {
        return when (intent?.getStringExtra(EXTRA_START_SCREEN_ROUTE)) {
            ROUTE_ANALYTICS -> Screen.Analytics.route
            else -> Screen.Tasks.route
        }
    }
}