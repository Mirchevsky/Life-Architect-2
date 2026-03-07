package com.mirchevsky.lifearchitect2.domain

/**
 * Represents a single trending search topic parsed from the Google Trends RSS feed.
 *
 * @param title      The trending search term (e.g. "World Cup 2026").
 * @param traffic    Approximate search volume string as provided by the feed (e.g. "2M+").
 * @param link       The URL to the Google Trends page for this topic (the RSS <link> tag).
 * @param imageUrl   Optional URL to a news image associated with the trend.
 * @param newsTitle  Optional title of the top news article associated with the trend.
 * @param newsSource Optional source name of the top news article (e.g. "BBC News").
 * @param newsUrl    Optional direct URL to the top news article (ht:news_item_url).
 *                   This is the human-readable article page, as opposed to [link] which
 *                   points to the raw Google Trends RSS feed entry.
 */
data class TrendItem(
    val title: String,
    val traffic: String,
    val link: String,
    val imageUrl: String? = null,
    val newsTitle: String? = null,
    val newsSource: String? = null,
    val newsUrl: String? = null
)
