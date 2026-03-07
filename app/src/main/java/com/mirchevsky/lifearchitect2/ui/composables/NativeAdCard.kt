package com.mirchevsky.lifearchitect2.ui.composables

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Renders an AdMob Native Ad styled to match [TrendCard] in the trending feed.
 *
 * ## Key design constraint
 *
 * The AdMob SDK validates asset views by checking [android.view.View.isAttachedToWindow].
 * If [NativeAdView.setNativeAd] is called before the view is attached to a window
 * (e.g. inside the `factory` lambda of [AndroidView]), the SDK silently skips populating
 * all asset views — headline, body, icon, CTA all remain blank.
 *
 * **Solution:** The `factory` lambda only builds the view hierarchy and registers asset
 * view references. [NativeAdView.setNativeAd] is called exclusively in the `update`
 * lambda, which runs after the view is attached to the window.
 *
 * TODO: Replace [AD_UNIT_ID] with your real Native ad unit ID before publishing.
 */

// Test ID — replace before publishing with your real Native ad unit ID:
private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"

@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }

    // Capture theme colours while still in Compose scope.
    val onSurfaceArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val onSurfaceVariantArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val primaryArgb = MaterialTheme.colorScheme.primary.toArgb()

    DisposableEffect(Unit) {
        val loader = AdLoader.Builder(context, AD_UNIT_ID)
            .forNativeAd { ad -> nativeAd = ad }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    // Ad stays null; composable returns early — no blank slot.
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()

        loader.loadAd(AdRequest.Builder().build())

        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    // Return early (render nothing) until an ad has loaded.
    val ad = nativeAd ?: return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                // Minimum height prevents Compose from collapsing the view to zero
                // on the first measurement pass before the View tree is laid out.
                .heightIn(min = 80.dp),
            factory = { ctx ->
                // Build the view hierarchy and register asset views.
                // Do NOT call setNativeAd() here — the view is not yet attached
                // to a window, so the SDK will silently skip populating assets.
                buildNativeAdView(ctx, onSurfaceArgb, onSurfaceVariantArgb, primaryArgb)
            },
            update = { adView ->
                // update() runs after the view is attached to the window.
                // This is the correct place to call setNativeAd().
                adView.setNativeAd(ad)

                // Re-apply theme colours after SDK populates the views,
                // overriding any colour the ad creative may have set.
                adView.post {
                    (adView.headlineView as? TextView)?.setTextColor(onSurfaceArgb)
                    (adView.bodyView as? TextView)?.setTextColor(onSurfaceVariantArgb)
                    (adView.callToActionView as? TextView)?.setTextColor(primaryArgb)
                }
            }
        )
    }
}

/**
 * Builds a [NativeAdView] with all asset views registered but WITHOUT calling
 * [NativeAdView.setNativeAd]. The caller must call [NativeAdView.setNativeAd]
 * after the view is attached to a window (i.e. in [AndroidView]'s `update` lambda).
 */
private fun buildNativeAdView(
    ctx: Context,
    onSurfaceArgb: Int,
    onSurfaceVariantArgb: Int,
    primaryArgb: Int
): NativeAdView {
    val dp = { value: Int ->
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            ctx.resources.displayMetrics
        ).toInt()
    }

    // ── "Ad" attribution badge ─────────────────────────────────────────────
    val adBadge = TextView(ctx).apply {
        text = "Ad"
        setTextColor(Color.BLACK)
        setBackgroundColor(Color.parseColor("#FFCC00"))
        textSize = 10f
        setPadding(dp(4), dp(1), dp(4), dp(1))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = dp(6) }
    }

    // ── Ad icon ────────────────────────────────────────────────────────────
    val iconView = ImageView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).also {
            it.marginEnd = dp(12)
            it.gravity = Gravity.CENTER_VERTICAL
        }
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    // ── Headline ───────────────────────────────────────────────────────────
    val headlineView = TextView(ctx).apply {
        setTextColor(onSurfaceArgb)
        textSize = 16f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    // ── Body ───────────────────────────────────────────────────────────────
    val bodyView = TextView(ctx).apply {
        setTextColor(onSurfaceVariantArgb)
        textSize = 12f
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dp(4) }
    }

    // ── CTA / advertiser ───────────────────────────────────────────────────
    val ctaView = TextView(ctx).apply {
        setTextColor(primaryArgb)
        textSize = 11f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    // ── Text column ────────────────────────────────────────────────────────
    val textColumn = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        addView(headlineView)
        addView(bodyView)
        addView(ctaView)
    }

    // ── Content row: icon + text column ───────────────────────────────────
    val contentRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        addView(iconView)
        addView(textColumn)
    }

    // ── Root column: badge + content row ──────────────────────────────────
    val rootColumn = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        addView(adBadge)
        addView(contentRow)
    }

    // ── NativeAdView ───────────────────────────────────────────────────────
    return NativeAdView(ctx).apply {
        setBackgroundColor(Color.TRANSPARENT)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        addView(rootColumn)

        // Register asset views. setNativeAd() will be called by the caller
        // (AndroidView's update lambda) once the view is attached to a window.
        this.headlineView = headlineView
        this.bodyView = bodyView
        this.callToActionView = ctaView
        this.iconView = iconView
    }
}
