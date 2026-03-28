package com.example.planreminder.agent

data class AgentSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
) {
    fun isConfigured(): Boolean = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    companion object {
        const val DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
        const val DEFAULT_MODEL = "qwen-plus"
    }
}

enum class AgentMessageRole {
    USER,
    ASSISTANT,
}

data class AgentMessage(
    val role: AgentMessageRole,
    val text: String,
)

data class AgentPlanDraft(
    val title: String = "",
    val location: String = "",
    val date: String = "",
    val time: String = "",
) {
    fun merge(other: AgentPlanDraft): AgentPlanDraft {
        return copy(
            title = other.title.ifBlank { title },
            location = other.location.ifBlank { location },
            date = other.date.ifBlank { date },
            time = other.time.ifBlank { time },
        )
    }
}

data class VoiceAgentUiState(
    val isOpen: Boolean = false,
    val editingPlanId: Long? = null,
    val reminderLeadMinutes: Int = ReminderSettingsStore.DEFAULT_REMINDER_LEAD_MINUTES,
    val enableAlarmSound: Boolean = true,
    val enableVibration: Boolean = true,
    val isDraftPreviewVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val liveTranscript: String = "",
    val messages: List<AgentMessage> = emptyList(),
    val draft: AgentPlanDraft = AgentPlanDraft(),
    val missingFields: List<String> = listOf("title", "date", "time"),
    val readyForConfirmation: Boolean = false,
)

data class QwenAgentReply(
    val assistantMessage: String,
    val draft: AgentPlanDraft,
    val missingFields: List<String>,
    val readyForConfirmation: Boolean,
)
