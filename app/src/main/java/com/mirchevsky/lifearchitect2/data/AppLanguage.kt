package com.mirchevsky.lifearchitect2.data

import androidx.annotation.StringRes
import com.mirchevsky.lifearchitect2.R

enum class AppLanguage(
    val id: String,
    @StringRes val labelResId: Int,
    val localeTag: String?,
    val speechTag: String?
) {
    SYSTEM("system", R.string.language_system_default, null, null),
    ENGLISH("en", R.string.language_english, "en-US", "en-US"),
    HEBREW("he", R.string.language_hebrew, "he-IL", "he-IL"),
    RUSSIAN("ru", R.string.language_russian, "ru-RU", "ru-RU"),
    ARABIC("ar", R.string.language_arabic, "ar-SA", "ar-SA");

    companion object {
        fun fromId(id: String?): AppLanguage = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}
