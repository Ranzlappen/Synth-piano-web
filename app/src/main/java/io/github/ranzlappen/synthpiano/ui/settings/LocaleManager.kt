package io.github.ranzlappen.synthpiano.ui.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import io.github.ranzlappen.synthpiano.R

/**
 * Per-app language switching. We persist the user's choice as a BCP-47
 * language tag (or null = follow the system locale) in DataStore and
 * call [applyLocale] both at Activity startup (so the first composition
 * already reflects the saved choice) and whenever the picker changes.
 *
 * AppCompatDelegate handles the platform differences for us — on API 33+
 * it forwards to LocaleManager, and on API 26-32 the AndroidX library
 * back-fills locale propagation through its content-provider autoinit.
 */
object LocaleManager {

    fun applyLocale(tag: String?) {
        val list = if (tag.isNullOrBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(list)
    }
}

data class LanguageOption(
    val tag: String?,
    @StringRes val labelRes: Int,
)

val LANGUAGE_OPTIONS: List<LanguageOption> = listOf(
    LanguageOption(tag = null, labelRes = R.string.settings_language_system),
    LanguageOption(tag = "en", labelRes = R.string.settings_language_english),
    LanguageOption(tag = "de", labelRes = R.string.settings_language_german),
    LanguageOption(tag = "es", labelRes = R.string.settings_language_spanish),
    LanguageOption(tag = "fr", labelRes = R.string.settings_language_french),
)
