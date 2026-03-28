package com.example.planreminder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.planreminder.agent.AgentMessage
import com.example.planreminder.agent.AgentMessageRole
import com.example.planreminder.agent.AgentPlanDraft
import com.example.planreminder.agent.AgentSettings
import com.example.planreminder.agent.AgentSettingsStore
import com.example.planreminder.agent.QwenPlanAgentClient
import com.example.planreminder.agent.ReminderSettingsStore
import com.example.planreminder.agent.VoiceAgentUiState
import com.example.planreminder.data.PlanDatabase
import com.example.planreminder.data.PlanItem
import com.example.planreminder.data.PlanRepository
import com.example.planreminder.i18n.AppLanguage
import com.example.planreminder.i18n.LanguageSettingsStore
import com.example.planreminder.i18n.appStringsFor
import com.example.planreminder.reminder.ReminderScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 集中管理计划列表、保存流程、提醒重排和语音助手状态，是页面层的主要业务入口。
class PlanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlanRepository(PlanDatabase.getInstance(application).planDao())
    private val reminderScheduler = ReminderScheduler(application)
    private val agentSettingsStore = AgentSettingsStore(application)
    private val reminderSettingsStore = ReminderSettingsStore(application)
    private val languageSettingsStore = LanguageSettingsStore(application)
    private val qwenPlanAgentClient = QwenPlanAgentClient()

    // 直接暴露 Room 的实时数据流，方便 Compose 页面持续订阅最新计划列表。
    val plans = repository.observePlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val agentSettings: StateFlow<AgentSettings> = agentSettingsStore.settings
    val reminderLeadMinutes: StateFlow<Int> = reminderSettingsStore.reminderLeadMinutes
    val appLanguage: StateFlow<AppLanguage> = languageSettingsStore.language

    private val _voiceAgentState = MutableStateFlow(VoiceAgentUiState())
    val voiceAgentState = _voiceAgentState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    init {
        refreshReminderSchedules()
    }

    fun addPlan(
        title: String,
        location: String,
        date: LocalDate,
        time: LocalTime,
        reminderLeadMinutes: Int,
        enableAlarmSound: Boolean,
        enableVibration: Boolean,
    ) {
        viewModelScope.launch {
            savePlanInternal(
                title = title,
                location = location,
                date = date,
                time = time,
                reminderLeadMinutes = reminderLeadMinutes,
                enableAlarmSound = enableAlarmSound,
                enableVibration = enableVibration,
            )
        }
    }

    fun updatePlan(
        planId: Long,
        title: String,
        location: String,
        date: LocalDate,
        time: LocalTime,
        reminderLeadMinutes: Int,
        enableAlarmSound: Boolean,
        enableVibration: Boolean,
    ) {
        viewModelScope.launch {
            savePlanInternal(
                title = title,
                location = location,
                date = date,
                time = time,
                reminderLeadMinutes = reminderLeadMinutes,
                enableAlarmSound = enableAlarmSound,
                enableVibration = enableVibration,
                existingPlanId = planId,
            )
        }
    }

    fun deletePlan(planItem: PlanItem) {
        viewModelScope.launch {
            runCatching {
                repository.deletePlan(planItem)
                reminderScheduler.cancel(planItem.id)
            }.onSuccess {
                _messages.emit(currentStrings().planDeletedMessage)
            }.onFailure {
                _messages.emit(currentStrings().deletePlanFailedMessage)
            }
        }
    }

    fun saveSettings(
        baseUrl: String,
        apiKey: String,
        model: String,
        reminderLeadMinutes: Int,
        language: AppLanguage,
    ) {
        val normalizedLeadMinutes = ReminderSettingsStore.normalizeReminderLeadMinutes(reminderLeadMinutes)
        val settings = AgentSettings(
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            model = model.trim(),
        )

        agentSettingsStore.save(settings)
        reminderSettingsStore.saveReminderLeadMinutes(normalizedLeadMinutes)
        languageSettingsStore.saveLanguage(language)

        viewModelScope.launch {
            rescheduleUpcomingPlans()
            _messages.emit(appStringsFor(language).settingsSavedMessage)
        }
    }

    fun openVoiceAgent() {
        val settings = agentSettings.value
        val strings = currentStrings()
        _voiceAgentState.value = VoiceAgentUiState(
            isOpen = true,
            reminderLeadMinutes = reminderLeadMinutes.value,
            enableAlarmSound = true,
            enableVibration = true,
            isDraftPreviewVisible = false,
            messages = listOf(
                AgentMessage(
                    role = AgentMessageRole.ASSISTANT,
                    text = if (settings.isConfigured()) {
                        strings.initialVoiceConfiguredMessage
                    } else {
                        strings.initialVoiceNeedConfigMessage
                    },
                ),
            ),
        )
    }

    fun openVoiceAgentForEdit(planItem: PlanItem) {
        val settings = agentSettings.value
        val strings = currentStrings()
        val draft = planItem.toAgentPlanDraft()
        _voiceAgentState.value = VoiceAgentUiState(
            isOpen = true,
            editingPlanId = planItem.id,
            reminderLeadMinutes = planItem.reminderLeadMinutes,
            enableAlarmSound = planItem.enableAlarmSound,
            enableVibration = planItem.enableVibration,
            isDraftPreviewVisible = false,
            draft = draft,
            missingFields = calculateMissingFields(draft),
            readyForConfirmation = true,
            messages = listOf(
                AgentMessage(
                    role = AgentMessageRole.ASSISTANT,
                    text = if (settings.isConfigured()) {
                        strings.initialVoiceEditMessage
                    } else {
                        strings.initialVoiceNeedConfigMessage
                    },
                ),
            ),
        )
    }

    fun closeVoiceAgent() {
        _voiceAgentState.value = VoiceAgentUiState()
    }

    fun markVoiceRecordingStarted() {
        _voiceAgentState.update {
            it.copy(
                isOpen = true,
                isDraftPreviewVisible = false,
                isRecording = true,
                isLoading = false,
                isTranscribing = false,
                liveTranscript = "",
            )
        }
    }

    fun markVoiceRecordingStopped() {
        _voiceAgentState.update {
            it.copy(
                isRecording = false,
                isTranscribing = true,
            )
        }
    }

    fun updateLiveTranscript(text: String) {
        _voiceAgentState.update {
            it.copy(
                isOpen = true,
                isDraftPreviewVisible = false,
                liveTranscript = text,
            )
        }
    }

    fun clearVoiceCaptureState() {
        _voiceAgentState.update {
            it.copy(
                isDraftPreviewVisible = false,
                isRecording = false,
                isTranscribing = false,
                liveTranscript = "",
            )
        }
    }

    fun continueVoiceConversation() {
        _voiceAgentState.update {
            it.copy(
                isOpen = true,
                isDraftPreviewVisible = false,
                isLoading = false,
                isRecording = false,
                isTranscribing = false,
                liveTranscript = "",
            )
        }
    }

    fun submitVoiceTranscript(transcript: String) {
        val normalized = transcript.trim()
        if (normalized.isBlank()) {
            viewModelScope.launch {
                _messages.emit(currentStrings().noClearVoiceMessage)
            }
            return
        }

        val settings = agentSettings.value
        if (!settings.isConfigured()) {
            viewModelScope.launch {
                _messages.emit(currentStrings().configureApiBeforeVoiceMessage)
            }
            return
        }

        val currentState = _voiceAgentState.value
        val updatedHistory = currentState.messages + AgentMessage(
            role = AgentMessageRole.USER,
            text = normalized,
        )

        _voiceAgentState.value = currentState.copy(
            isOpen = true,
            isDraftPreviewVisible = false,
            isLoading = true,
            isRecording = false,
            isTranscribing = false,
            liveTranscript = normalized,
            messages = updatedHistory,
        )

        viewModelScope.launch {
            runCatching {
                qwenPlanAgentClient.continueConversation(
                    settings = settings,
                    history = updatedHistory,
                    draft = currentState.draft,
                    language = appLanguage.value,
                )
            }.onSuccess { reply ->
                _voiceAgentState.value = _voiceAgentState.value.copy(
                    isOpen = true,
                    isDraftPreviewVisible = true,
                    isLoading = false,
                    draft = reply.draft,
                    missingFields = reply.missingFields,
                    readyForConfirmation = reply.readyForConfirmation,
                    messages = updatedHistory + AgentMessage(
                        role = AgentMessageRole.ASSISTANT,
                        text = reply.assistantMessage,
                    ),
                )
            }.onFailure { error ->
                _voiceAgentState.value = _voiceAgentState.value.copy(
                    isDraftPreviewVisible = false,
                    isLoading = false,
                )
                _messages.emit(error.message ?: currentStrings().qwenFailedMessage)
            }
        }
    }

    fun confirmVoicePlan() {
        val state = _voiceAgentState.value
        val draft = state.draft
        val date = runCatching { LocalDate.parse(draft.date, DATE_FORMATTER) }.getOrNull()
        val time = runCatching { LocalTime.parse(draft.time, TIME_FORMATTER) }.getOrNull()

        if (date == null || time == null) {
            viewModelScope.launch {
                _messages.emit(currentStrings().incompleteDateTimeMessage)
            }
            return
        }

        viewModelScope.launch {
            if (
                savePlanInternal(
                    draft.title,
                    draft.location,
                    date,
                    time,
                    state.reminderLeadMinutes,
                    state.enableAlarmSound,
                    state.enableVibration,
                    state.editingPlanId,
                )
            ) {
                closeVoiceAgent()
            }
        }
    }

    fun showMessage(message: String) {
        viewModelScope.launch {
            _messages.emit(message)
        }
    }

    fun canScheduleExactAlarms(): Boolean = reminderScheduler.canScheduleExactAlarms()

    fun refreshReminderSchedules() {
        viewModelScope.launch {
            rescheduleUpcomingPlans()
        }
    }

    private suspend fun savePlanInternal(
        title: String,
        location: String,
        date: LocalDate,
        time: LocalTime,
        reminderLeadMinutes: Int,
        enableAlarmSound: Boolean,
        enableVibration: Boolean,
        existingPlanId: Long? = null,
    ): Boolean {
        val normalizedTitle = title.trim()
        val normalizedLocation = location.trim()
        val normalizedLeadMinutes = ReminderSettingsStore.normalizeReminderLeadMinutes(reminderLeadMinutes)

        if (normalizedTitle.isBlank()) {
            _messages.emit(currentStrings().fillTaskMessage)
            return false
        }

        val scheduledAtMillis = LocalDateTime.of(date, time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        if (scheduledAtMillis <= System.currentTimeMillis()) {
            _messages.emit(currentStrings().planTimeFutureMessage)
            return false
        }

        return runCatching {
            val existingPlan = existingPlanId?.let { repository.findPlan(it) }
            val targetPlan = PlanItem(
                id = existingPlanId ?: 0,
                title = normalizedTitle,
                location = normalizedLocation,
                scheduledAtMillis = scheduledAtMillis,
                reminderLeadMinutes = normalizedLeadMinutes,
                enableAlarmSound = enableAlarmSound,
                enableVibration = enableVibration,
                createdAtMillis = existingPlan?.createdAtMillis ?: System.currentTimeMillis(),
            )

            val savedPlan = if (existingPlanId == null) {
                repository.addPlan(targetPlan)
            } else {
                checkNotNull(existingPlan)
                repository.updatePlan(targetPlan)
            }

            savedPlan.also { reminderScheduler.schedule(it) }
        }.onSuccess {
            val strings = currentStrings()
            _messages.emit(
                if (scheduledAtMillis - System.currentTimeMillis() <= normalizedLeadMinutes * 60_000L) {
                    strings.planSavedNearStart(normalizedLeadMinutes)
                } else {
                    strings.planSavedLead(normalizedLeadMinutes)
                },
            )
        }.onFailure {
            _messages.emit(currentStrings().savePlanFailedMessage)
        }.isSuccess
    }

    private suspend fun rescheduleUpcomingPlans() {
        repository.getUpcomingPlans(System.currentTimeMillis()).forEach { plan ->
            reminderScheduler.schedule(plan)
        }
    }

    private fun calculateMissingFields(draft: AgentPlanDraft): List<String> {
        return buildList {
            if (draft.title.isBlank()) add("title")
            if (draft.date.isBlank()) add("date")
            if (draft.time.isBlank()) add("time")
        }
    }

    private fun currentStrings() = appStringsFor(appLanguage.value)

    companion object {
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

private fun PlanItem.toAgentPlanDraft(): AgentPlanDraft {
    val dateTime = Instant.ofEpochMilli(scheduledAtMillis).atZone(ZoneId.systemDefault())
    return AgentPlanDraft(
        title = title,
        location = location,
        date = dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
        time = dateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
    )
}
