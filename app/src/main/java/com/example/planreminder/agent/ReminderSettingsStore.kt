package com.example.planreminder.agent

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ReminderSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _reminderLeadMinutes = MutableStateFlow(loadReminderLeadMinutes())
    val reminderLeadMinutes: StateFlow<Int> = _reminderLeadMinutes.asStateFlow()

    fun saveReminderLeadMinutes(minutes: Int) {
        val normalized = normalizeReminderLeadMinutes(minutes)
        preferences.edit()
            .putInt(KEY_REMINDER_LEAD_MINUTES, normalized)
            .apply()
        _reminderLeadMinutes.value = normalized
    }

    private fun loadReminderLeadMinutes(): Int {
        return normalizeReminderLeadMinutes(
            preferences.getInt(KEY_REMINDER_LEAD_MINUTES, DEFAULT_REMINDER_LEAD_MINUTES),
        )
    }

    companion object {
        const val DEFAULT_REMINDER_LEAD_MINUTES = 10
        const val MIN_REMINDER_LEAD_MINUTES = 1
        const val MAX_REMINDER_LEAD_MINUTES = 1_440

        private const val PREFS_NAME = "reminder_settings"
        private const val KEY_REMINDER_LEAD_MINUTES = "reminder_lead_minutes"

        fun normalizeReminderLeadMinutes(minutes: Int): Int {
            return minutes.coerceIn(MIN_REMINDER_LEAD_MINUTES, MAX_REMINDER_LEAD_MINUTES)
        }
    }
}
