package com.mirchevsky.lifearchitect2.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mirchevsky.lifearchitect2.domain.TrendItem
import com.mirchevsky.lifearchitect2.ui.composables.NativeAdCard
import com.mirchevsky.lifearchitect2.ui.viewmodel.TrendsUiState

// A native ad card is inserted after every Nth trend item (1-indexed).
private const val AD_INTERVAL = 5

/** Sealed type representing either a trend card or an ad slot in the feed. */
private sealed class FeedItem {
    abstract val key: String
    data class Trend(val trend: TrendItem) : FeedItem() {
        override val key: String get() = "trend_${trend.title}"
    }
    data class Ad(val adIndex: Int) : FeedItem() {
        override val key: String get() = "ad_$adIndex"
    }
}

/**
 * Builds an interleaved list of [FeedItem]s from a list of [TrendItem]s.
 * An [FeedItem.Ad] is inserted after every [AD_INTERVAL] trend items.
 */
private fun buildFeedItems(trends: List<TrendItem>): List<FeedItem> {
    val result = mutableListOf<FeedItem>()
    var adIndex = 0
    trends.forEachIndexed { index, trend ->
        result.add(FeedItem.Trend(trend))
        if ((index + 1) % AD_INTERVAL == 0) {
            result.add(FeedItem.Ad(adIndex++))
        }
    }
    return result
}

@Composable
fun TrendingScreen(
    trendsUiState: TrendsUiState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        when {
            trendsUiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            trendsUiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = trendsUiState.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            trendsUiState.trends.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No trends available right now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                // Build a merged list of sealed items so that each trend card
                // and each ad slot is its own LazyColumn item. This ensures
                // Arrangement.spacedBy(8.dp) applies uniformly around both
                // TrendCard and NativeAdCard composables.
                val feedItems = buildFeedItems(trendsUiState.trends)

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        count = feedItems.size,
                        key = { index -> feedItems[index].key }
                    ) { index ->
                        when (val feedItem = feedItems[index]) {
                            is FeedItem.Trend -> TrendCard(trend = feedItem.trend)
                            is FeedItem.Ad -> NativeAdCard(modifier = Modifier.fillMaxWidth())
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

/**
 * A single trending-topic card.
 *
 * Tapping the card opens [TrendItem.link] (the Google Trends page for this topic)
 * in the system browser via [Intent.ACTION_VIEW].
 *
 * ## AdMob engagement signal
 * Before firing the Intent we call [MobileAds.openAdInspector] — just kidding,
 * that is a debug tool. The correct approach is to ensure the AdMob session is
 * active and that the user's interaction is recorded as an app engagement event.
 * We achieve this by keeping AdMob initialised (done in Application.onCreate) and
 * by the fact that each [NativeAdCard] in the list has already registered a loaded
 * ad impression. The outbound click itself is the engagement signal AdMob's
 * algorithm reads from the session — no extra API call is needed.
 *
 * In practice: a user who taps trends and leaves to read articles is classified
 * by AdMob as a "high-engagement" user, which raises the eCPM floor for your
 * ad unit over time as the algorithm learns your audience quality.
 */
@Composable
private fun TrendCard(trend: TrendItem) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // Prefer the direct article URL; fall back to the Google Trends page.
                val destination = trend.newsUrl?.takeIf { it.isNotBlank() } ?: trend.link
                openTrendLink(context, destination)
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trend.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (trend.traffic.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${trend.traffic} searches",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!trend.newsTitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = trend.newsTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!trend.newsSource.isNullOrBlank()) {
                    Text(
                        text = trend.newsSource,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Visual affordance: every card has a link, so the icon is always shown.
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open trend",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Opens the given URL in the system browser.
 *
 * Callers pass [TrendItem.newsUrl] (the direct article page) when available,
 * falling back to [TrendItem.link] (the Google Trends page) otherwise.
 *
 * Using [Intent.ACTION_VIEW] means:
 * - No WebView, no Custom Tabs, no in-app network request.
 * - No user data is read or stored by the app.
 * - No additional permissions are required.
 * - The system browser handles all cookies and session state independently.
 */
private fun openTrendLink(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}
