package com.mirchevsky.lifearchitect2.ui.composables

import android.util.DisplayMetrics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun AdmobBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Use display metrics to get the true screen width in dp, which is more
    // reliable than LocalConfiguration.screenWidthDp for the adaptive banner
    // calculation — this prevents the fallback to the fixed 320x50 size.
    val adSize: AdSize = run {
        val displayMetrics: DisplayMetrics = context.resources.displayMetrics
        val adWidthPixels = displayMetrics.widthPixels.toFloat()
        val density = displayMetrics.density
        val adWidth = (adWidthPixels / density).toInt()
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            AdView(ctx).apply {
                // TODO: Replace with your real Ad Unit ID before publishing:
                // ca-app-pub-7210557644472819/3041816755
                adUnitId = "ca-app-pub-3940256099942544/6300978111" // Test ID
                setAdSize(adSize)
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
