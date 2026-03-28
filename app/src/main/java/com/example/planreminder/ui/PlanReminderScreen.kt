package com.example.planreminder.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.planreminder.agent.AgentMessageRole
import com.example.planreminder.agent.AgentPlanDraft
import com.example.planreminder.agent.AgentSettings
import com.example.planreminder.agent.ReminderSettingsStore
import com.example.planreminder.agent.VoiceAgentUiState
import com.example.planreminder.data.PlanItem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow

private const val MILLIS_PER_MINUTE = 60_000L

private data class PlanFormSeed(
    val title: String = "",
    val location: String = "",
    val date: LocalDate? = null,
    val time: LocalTime? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanReminderScreen(
    plans: List<PlanItem>,
    hasNotificationPermission: Boolean,
    canScheduleExactAlarms: Boolean,
    reminderLeadMinutes: Int,
    messages: Flow<String>,
    agentSettings: AgentSettings,
    voiceAgentState: VoiceAgentUiState,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onAddPlan: (String, String, LocalDate, LocalTime) -> Unit,
    onDeletePlan: (PlanItem) -> Unit,
    onSaveSettings: (String, String, String, Int) -> Unit,
    onOpenVoiceAgent: () -> Unit,
    onDismissVoiceAgent: () -> Unit,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
    onConfirmVoicePlan: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var formSeed by remember { mutableStateOf(PlanFormSeed()) }

    LaunchedEffect(messages) {
        messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("计划提醒")
                        Text(
                            text = "本地保存，无需后端",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenVoiceAgent) {
                        Icon(Icons.Default.Mic, contentDescription = "语音添加")
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "助手设置")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    formSeed = PlanFormSeed()
                    showAddDialog = true
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = "手动添加计划")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OverviewCard(
                reminderLeadMinutes = reminderLeadMinutes,
                onVoiceAdd = onOpenVoiceAgent,
                onOpenSettings = { showSettingsDialog = true },
            )

            if (!hasNotificationPermission) {
                PermissionCard(
                    icon = Icons.Default.NotificationsActive,
                    title = "通知权限未开启",
                    description = "开启通知权限后，提醒才能按时送达。",
                    actionLabel = "开启通知",
                    onAction = onRequestNotificationPermission,
                )
            }

            if (!canScheduleExactAlarms) {
                PermissionCard(
                    icon = Icons.Default.AlarmOn,
                    title = "建议开启精确提醒",
                    description = "开启后，更容易在计划开始前 $reminderLeadMinutes 分钟准时提醒。",
                    actionLabel = "开启精确提醒",
                    onAction = onRequestExactAlarmPermission,
                )
            }

            if (plans.isEmpty()) {
                EmptyStateCard()
            } else {
                plans.forEach { plan ->
                    PlanCard(
                        plan = plan,
                        reminderLeadMinutes = reminderLeadMinutes,
                        onDeletePlan = { onDeletePlan(plan) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddPlanDialog(
            initialSeed = formSeed,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, location, date, time ->
                onAddPlan(title, location, date, time)
                showAddDialog = false
                formSeed = PlanFormSeed()
            },
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            agentSettings = agentSettings,
            reminderLeadMinutes = reminderLeadMinutes,
            onDismiss = { showSettingsDialog = false },
            onSave = { baseUrl, apiKey, model, minutes ->
                onSaveSettings(baseUrl, apiKey, model, minutes)
                showSettingsDialog = false
            },
        )
    }

    if (voiceAgentState.isOpen) {
        VoiceAgentDialog(
            settings = agentSettings,
            reminderLeadMinutes = reminderLeadMinutes,
            state = voiceAgentState,
            onDismiss = onDismissVoiceAgent,
            onOpenSettings = {
                onDismissVoiceAgent()
                showSettingsDialog = true
            },
            onStartVoiceInput = onStartVoiceInput,
            onStopVoiceInput = onStopVoiceInput,
            onManualAdjust = {
                formSeed = voiceAgentState.draft.toPlanFormSeed()
                showAddDialog = true
                onDismissVoiceAgent()
            },
            onConfirm = onConfirmVoicePlan,
        )
    }
}

@Composable
private fun OverviewCard(
    reminderLeadMinutes: Int,
    onVoiceAdd: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                )
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.EditCalendar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }

                Text(
                    text = "提前安排好你的下一项计划",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "支持手动创建，也支持开始录音和结束录音让智能助手补全事项和时间。地点选填，确认后会按提前 $reminderLeadMinutes 分钟保存本地提醒。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = onVoiceAdd) {
                        Icon(Icons.Default.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("语音添加")
                    }
                    OutlinedButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("设置")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun EmptyStateCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.TaskAlt,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("还没有计划", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("点击右下角按钮，添加你的第一个提醒。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanCard(
    plan: PlanItem,
    reminderLeadMinutes: Int,
    onDeletePlan: () -> Unit,
) {
    val reminderAtMillis = plan.scheduledAtMillis - reminderLeadMinutes * MILLIS_PER_MINUTE
    val isNearStart = plan.scheduledAtMillis - System.currentTimeMillis() <=
        reminderLeadMinutes * MILLIS_PER_MINUTE

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = plan.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(text = formatDateTime(plan.scheduledAtMillis), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                IconButton(onClick = onDeletePlan) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除计划")
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text("提前 $reminderLeadMinutes 分钟提醒") },
                    leadingIcon = { Icon(Icons.Default.AlarmOn, contentDescription = null) },
                )

                if (plan.location.isNotBlank()) {
                    AssistChip(
                        onClick = {},
                        label = { Text(plan.location) },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    )
                }
            }

            Text(
                text = if (isNearStart) {
                    "这个计划距离开始已不足 $reminderLeadMinutes 分钟，系统会尽快提醒你。"
                } else {
                    "提醒时间：${formatDateTime(reminderAtMillis)}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddPlanDialog(
    initialSeed: PlanFormSeed,
    onDismiss: () -> Unit,
    onConfirm: (String, String, LocalDate, LocalTime) -> Unit,
) {
    val fallbackDateTime = remember(initialSeed) {
        LocalDateTime.now().plusMinutes(30).withSecond(0).withNano(0)
    }
    var title by remember(initialSeed) { mutableStateOf(initialSeed.title) }
    var location by remember(initialSeed) { mutableStateOf(initialSeed.location) }
    var selectedDate by remember(initialSeed) { mutableStateOf(initialSeed.date ?: fallbackDateTime.toLocalDate()) }
    var selectedTime by remember(initialSeed) { mutableStateOf(initialSeed.time ?: fallbackDateTime.toLocalTime()) }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加计划") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("事项 / 实践内容") },
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("地点（选填）") },
                )
                DateSelectorRow(
                    selectedDateText = selectedDate.format(dateFormatter),
                    selectedTimeText = selectedTime.format(timeFormatter),
                    initialDate = selectedDate,
                    initialTime = selectedTime,
                    onPickDate = { year, month, day -> selectedDate = LocalDate.of(year, month + 1, day) },
                    onPickTime = { hour, minute -> selectedTime = LocalTime.of(hour, minute) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, location, selectedDate, selectedTime) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun SettingsDialog(
    agentSettings: AgentSettings,
    reminderLeadMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int) -> Unit,
) {
    var baseUrl by remember(agentSettings) { mutableStateOf(agentSettings.baseUrl) }
    var apiKey by remember(agentSettings) { mutableStateOf(agentSettings.apiKey) }
    var model by remember(agentSettings) { mutableStateOf(agentSettings.model) }
    var leadMinutesText by remember(reminderLeadMinutes) { mutableStateOf(reminderLeadMinutes.toString()) }
    var leadMinutesError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "应用只在客户端本地保存计划，并直接访问你配置的 Qwen 接口，不需要自建后端。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = leadMinutesText,
                    onValueChange = {
                        leadMinutesText = it
                        leadMinutesError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("提前提醒分钟数") },
                    singleLine = true,
                    isError = leadMinutesError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text(
                            leadMinutesError
                                ?: "请输入 ${ReminderSettingsStore.MIN_REMINDER_LEAD_MINUTES}-${ReminderSettingsStore.MAX_REMINDER_LEAD_MINUTES} 之间的整数。",
                        )
                    },
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("接口地址") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("接口密钥") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模型名称") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedLeadMinutes = leadMinutesText.toIntOrNull()
                    val isValidLeadMinutes = parsedLeadMinutes != null &&
                        parsedLeadMinutes in ReminderSettingsStore.MIN_REMINDER_LEAD_MINUTES..ReminderSettingsStore.MAX_REMINDER_LEAD_MINUTES

                    if (!isValidLeadMinutes) {
                        leadMinutesError =
                            "请输入 ${ReminderSettingsStore.MIN_REMINDER_LEAD_MINUTES}-${ReminderSettingsStore.MAX_REMINDER_LEAD_MINUTES} 之间的整数。"
                        return@TextButton
                    }

                    onSave(baseUrl, apiKey, model, parsedLeadMinutes!!)
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceAgentDialog(
    settings: AgentSettings,
    reminderLeadMinutes: Int,
    state: VoiceAgentUiState,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
    onManualAdjust: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("语音计划助手", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "点击开始录音，说完后点击结束录音即可识别。地点选填，信息不完整时我会继续提问，确认后按提前 $reminderLeadMinutes 分钟保存提醒。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                }

                if (!settings.isConfigured()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("还没有配置 Qwen 接口", fontWeight = FontWeight.SemiBold)
                            Text("请先在设置里填写接口地址、接口密钥和模型名称。")
                            FilledTonalButton(onClick = onOpenSettings) {
                                Text("去设置")
                            }
                        }
                    }
                }

                DraftSummaryCard(
                    draft = state.draft,
                    missingFields = state.missingFields,
                    reminderLeadMinutes = reminderLeadMinutes,
                )

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.messages.forEach { message ->
                        val isAssistant = message.role == AgentMessageRole.ASSISTANT
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isAssistant) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                            ),
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (isAssistant) "助手" else "你",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(message.text)
                            }
                        }
                    }
                }

                if (state.isLoading) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("正在让助手整理计划信息…")
                    }
                }

                if (state.isRecording) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("正在录音，请点击“结束录音”。")
                    }
                } else if (state.isTranscribing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("正在识别语音…")
                    }
                }

                if (state.liveTranscript.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "实时识别",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(state.liveTranscript)
                        }
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    VoiceRecordButton(
                        enabled = settings.isConfigured() && !state.isLoading && !state.isTranscribing,
                        isRecording = state.isRecording,
                        onStartRecording = onStartVoiceInput,
                        onStopRecording = onStopVoiceInput,
                    )

                    OutlinedButton(
                        onClick = onManualAdjust,
                        enabled = !state.isLoading && !state.isRecording && !state.isTranscribing,
                    ) {
                        Text("手动调整")
                    }

                    if (state.readyForConfirmation) {
                        FilledTonalButton(
                            onClick = onConfirm,
                            enabled = !state.isLoading && !state.isRecording && !state.isTranscribing,
                        ) {
                            Text("确认保存")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceRecordButton(
    enabled: Boolean,
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        isRecording -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        isRecording -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val buttonText = if (isRecording) "结束录音" else "开始录音"

    FilledTonalButton(
        onClick = {
            if (isRecording) {
                onStopRecording()
            } else {
                onStartRecording()
            }
        },
        enabled = enabled,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        ),
        modifier = Modifier.heightIn(min = 56.dp),
    ) {
        Icon(Icons.Default.Mic, contentDescription = null)
        Spacer(modifier = Modifier.size(8.dp))
        Text(buttonText)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftSummaryCard(
    draft: AgentPlanDraft,
    missingFields: List<String>,
    reminderLeadMinutes: Int,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("当前草稿", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            DraftField(label = "事项", value = draft.title)
            DraftField(label = "地点", value = draft.location)
            DraftField(label = "日期", value = draft.date)
            DraftField(label = "时间", value = draft.time)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text("提醒：提前 $reminderLeadMinutes 分钟") },
                    leadingIcon = { Icon(Icons.Default.AlarmOn, contentDescription = null) },
                )

                if (missingFields.isNotEmpty()) {
                    missingFields.forEach { field ->
                        AssistChip(onClick = {}, label = { Text("待补充：${missingFieldLabel(field)}") })
                    }
                } else {
                    AssistChip(onClick = {}, label = { Text("信息已补全，等待确认") })
                }
            }
        }
    }
}

@Composable
private fun DraftField(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value.ifBlank {
                if (label == "地点") "未填写（选填）" else "待补充"
            },
        )
    }
}

@Composable
private fun DateSelectorRow(
    selectedDateText: String,
    selectedTimeText: String,
    initialDate: LocalDate,
    initialTime: LocalTime,
    onPickDate: (Int, Int, Int) -> Unit,
    onPickTime: (Int, Int) -> Unit,
) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("开始时间", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth -> onPickDate(year, month, dayOfMonth) },
                        initialDate.year,
                        initialDate.monthValue - 1,
                        initialDate.dayOfMonth,
                    ).show()
                },
            ) {
                Icon(Icons.Default.EditCalendar, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(selectedDateText)
            }

            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hourOfDay, minute -> onPickTime(hourOfDay, minute) },
                        initialTime.hour,
                        initialTime.minute,
                        true,
                    ).show()
                },
            ) {
                Icon(Icons.Default.AccessTime, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(selectedTimeText)
            }
        }
    }
}

private fun AgentPlanDraft.toPlanFormSeed(): PlanFormSeed {
    val date = runCatching { LocalDate.parse(this.date, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }.getOrNull()
    val time = runCatching { LocalTime.parse(this.time, DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()
    return PlanFormSeed(title = title, location = location, date = date, time = time)
}

private fun missingFieldLabel(field: String): String {
    return when (field) {
        "title" -> "事项"
        "location" -> "地点"
        "date" -> "日期"
        "time" -> "时间"
        else -> field
    }
}

private fun formatDateTime(millis: Long): String {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}
