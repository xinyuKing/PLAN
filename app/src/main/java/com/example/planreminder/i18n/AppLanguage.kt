package com.example.planreminder.i18n

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage(val storageValue: String) {
    SIMPLIFIED_CHINESE("zh-CN"),
    TRADITIONAL_CHINESE("zh-TW"),
    ENGLISH("en");

    companion object {
        fun fromStorageValue(value: String?): AppLanguage {
            return entries.firstOrNull { it.storageValue == value } ?: SIMPLIFIED_CHINESE
        }
    }
}

class LanguageSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _language = MutableStateFlow(loadLanguage())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun saveLanguage(language: AppLanguage) {
        preferences.edit()
            .putString(KEY_LANGUAGE, language.storageValue)
            .apply()
        _language.value = language
    }

    fun currentLanguage(): AppLanguage = _language.value

    private fun loadLanguage(): AppLanguage {
        return AppLanguage.fromStorageValue(preferences.getString(KEY_LANGUAGE, AppLanguage.SIMPLIFIED_CHINESE.storageValue))
    }

    companion object {
        private const val PREFS_NAME = "language_settings"
        private const val KEY_LANGUAGE = "app_language"
    }
}
