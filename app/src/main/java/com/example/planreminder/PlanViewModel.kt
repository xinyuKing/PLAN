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
import com.example.planreminder.reminder.ReminderScheduler
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

// 统一协调页面状态、计划持久化、提醒调度和语音助手状态。
class PlanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlanRepository(PlanDatabase.getInstance(application).planDao())
    private val reminderScheduler = ReminderScheduler(application)
    private val agentSettingsStore = AgentSettingsStore(application)
    private val reminderSettingsStore = ReminderSettingsStore(application)
    private val qwenPlanAgentClient = QwenPlanAgentClient()

    // 用 stateIn 保留最新的 Room 数据快照，方便 Compose 直接订阅。
    val plans = repository.observePlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val agentSettings: StateFlow<AgentSettings> = agentSettingsStore.settings
    val reminderLeadMinutes: StateFlow<Int> = reminderSettingsStore.reminderLeadMinutes

    private val _voiceAgentState = MutableStateFlow(VoiceAgentUiState())
    val voiceAgentState = _voiceAgentState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    fun addPlan(
        title: String,
        location: String,
        date: LocalDate,
        time: LocalTime,
    ) {
        viewModelScope.launch {
            savePlanInternal(title, location, date, time)
        }
    }

    fun deletePlan(planItem: PlanItem) {
        viewModelScope.launch {
            runCatching {
                repository.deletePlan(planItem)
                reminderScheduler.cancel(planItem.id)
            }.onSuccess {
                _messages.emit("计划已删除。")
            }.onFailure {
                _messages.emit("删除计划失败，请稍后重试。")
            }
        }
    }

    fun saveSettings(
        baseUrl: String,
        apiKey: String,
        model: String,
        reminderLeadMinutes: Int,
    ) {
        val normalizedLeadMinutes = ReminderSettingsStore.normalizeReminderLeadMinutes(reminderLeadMinutes)
        val settings = AgentSettings(
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            model = model.trim(),
        )

        agentSettingsStore.save(settings)
        reminderSettingsStore.saveReminderLeadMinutes(normalizedLeadMinutes)

        viewModelScope.launch {
            rescheduleUpcomingPlans(normalizedLeadMinutes)
            _messages.emit("设置已保存，未来计划会按新的提醒时间重新调度。")
        }
    }

    fun openVoiceAgent() {
        val settings = agentSettings.value
        _voiceAgentState.value = VoiceAgentUiState(
            isOpen = true,
            messages = listOf(
                AgentMessage(
                    role = AgentMessageRole.ASSISTANT,
                    text = if (settings.isConfigured()) {
                        "你可以点击开始录音，说完后再点结束录音。例如：明天晚上七点在健身房练腿。地点是选填项，信息不完整也没关系，我会继续追问。"
                    } else {
                        "请先在设置里填写千问的接口地址、接口密钥和模型名称。"
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
                isRecording = true,
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
                liveTranscript = text,
            )
        }
    }

    fun clearVoiceCaptureState() {
        _voiceAgentState.update {
            it.copy(
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
                _messages.emit("没有识别到清晰的语音内容，请再试一次。")
            }
            return
        }

        val settings = agentSettings.value
        if (!settings.isConfigured()) {
            viewModelScope.launch {
                _messages.emit("请先完成接口设置，再使用语音添加。")
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
                )
            }.onSuccess { reply ->
                _voiceAgentState.value = _voiceAgentState.value.copy(
                    isOpen = true,
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
                _voiceAgentState.value = _voiceAgentState.value.copy(isLoading = false)
                _messages.emit(error.message ?: "调用千问失败，请检查网络或接口配置。")
            }
        }
    }

    fun confirmVoicePlan() {
        val draft = _voiceAgentState.value.draft
        val date = runCatching { LocalDate.parse(draft.date, DATE_FORMATTER) }.getOrNull()
        val time = runCatching { LocalTime.parse(draft.time, TIME_FORMATTER) }.getOrNull()

        if (date == null || time == null) {
            viewModelScope.launch {
                _messages.emit("语音识别得到的日期或时间格式不完整，请继续补充或改为手动调整。")
            }
            return
        }

        viewModelScope.launch {
            if (savePlanInternal(draft.title, draft.location, date, time)) {
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

    private suspend fun savePlanInternal(
        title: String,
        location: String,
        date: LocalDate,
        time: LocalTime,
    ): Boolean {
        val normalizedTitle = title.trim()
        val normalizedLocation = location.trim()
        val leadMinutes = reminderLeadMinutes.value

        if (normalizedTitle.isBlank()) {
            _messages.emit("请填写事项或实践内容。")
            return false
        }

        val scheduledAtMillis = LocalDateTime.of(date, time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        if (scheduledAtMillis <= System.currentTimeMillis()) {
            _messages.emit("计划时间必须晚于当前时间。")
            return false
        }

        return runCatching {
            repository.addPlan(
                PlanItem(
                    title = normalizedTitle,
                    location = normalizedLocation,
                    scheduledAtMillis = scheduledAtMillis,
                ),
            ).also { reminderScheduler.schedule(it, leadMinutes) }
        }.onSuccess {
            _messages.emit(
                if (scheduledAtMillis - System.currentTimeMillis() <= leadMinutes * 60_000L) {
                    "计划已保存，距离开始不足 $leadMinutes 分钟，系统会尽快提醒你。"
                } else {
                    "计划已保存，系统会在开始前 $leadMinutes 分钟提醒你。"
                },
            )
        }.onFailure {
            _messages.emit("保存计划失败，请稍后重试。")
        }.isSuccess
    }

    private suspend fun rescheduleUpcomingPlans(leadMinutes: Int) {
        repository.getUpcomingPlans(System.currentTimeMillis()).forEach { plan ->
            reminderScheduler.schedule(plan, leadMinutes)
        }
    }

    companion object {
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
