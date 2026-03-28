package com.example.planreminder.agent

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgentSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AgentSettings> = _settings.asStateFlow()

    fun save(settings: AgentSettings) {
        preferences.edit()
            .putString(KEY_BASE_URL, settings.baseUrl)
            .putString(KEY_API_KEY, settings.apiKey)
            .putString(KEY_MODEL, settings.model)
            .apply()
        _settings.value = settings
    }

    private fun loadSettings(): AgentSettings {
        return AgentSettings(
            baseUrl = preferences.getString(KEY_BASE_URL, AgentSettings.DEFAULT_BASE_URL).orEmpty(),
            apiKey = preferences.getString(KEY_API_KEY, "").orEmpty(),
            model = preferences.getString(KEY_MODEL, AgentSettings.DEFAULT_MODEL).orEmpty(),
        )
    }

    companion object {
        private const val PREFS_NAME = "agent_settings"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
    }
}
