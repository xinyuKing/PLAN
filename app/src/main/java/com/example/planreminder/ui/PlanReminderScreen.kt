package com.example.planreminder.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.planreminder.agent.AgentMessageRole
import com.example.planreminder.agent.AgentPlanDraft
import com.example.planreminder.agent.AgentSettings
import com.example.planreminder.agent.ReminderSettingsStore
import com.example.planreminder.agent.VoiceAgentUiState
import com.example.planreminder.data.PlanItem
import com.example.planreminder.i18n.AppLanguage
import com.example.planreminder.i18n.AppStrings
import com.example.planreminder.i18n.appStringsFor
import com.example.planreminder.i18n.optionLabel
import java.time.YearMonth
import java.time.*
import java.time.format.DateTimeFormatter
import android.widget.NumberPicker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay

private const val MILLIS_PER_MINUTE = 60_000L
private const val STATUS_REFRESH_INTERVAL_MILLIS = 30_000L

private data class PlanFormSeed(
    val planId: Long? = null,
    val title: String = "",
    val location: String = "",
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val reminderLeadMinutes: Int = ReminderSettingsStore.DEFAULT_REMINDER_LEAD_MINUTES,
    val enableAlarmSound: Boolean = true,
    val enableVibration: Boolean = true,
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
    appLanguage: AppLanguage,
    voiceAgentState: VoiceAgentUiState,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onAddPlan: (String, String, LocalDate, LocalTime, Int, Boolean, Boolean) -> Unit,
    onUpdatePlan: (Long, String, String, LocalDate, LocalTime, Int, Boolean, Boolean) -> Unit,
    onDeletePlan: (PlanItem) -> Unit,
    onSaveSettings: (String, String, String, Int, AppLanguage) -> Unit,
    onOpenVoiceAgent: () -> Unit,
    onOpenVoiceAgentForEdit: (PlanItem) -> Unit,
    onDismissVoiceAgent: () -> Unit,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
    onContinueVoiceConversation: () -> Unit,
    onConfirmVoicePlan: () -> Unit,
) {
    val strings = appStringsFor(appLanguage)
    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(STATUS_REFRESH_INTERVAL_MILLIS)
        }
    }
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
                        Text(strings.appTitle)
                        Text(strings.appSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenVoiceAgent) { Icon(Icons.Default.Mic, contentDescription = strings.voiceAddAction) }
                    IconButton(onClick = { showSettingsDialog = true }) { Icon(Icons.Default.Settings, contentDescription = strings.settings) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    formSeed = PlanFormSeed(reminderLeadMinutes = reminderLeadMinutes)
                    showAddDialog = true
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = strings.addPlan)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OverviewCard(strings, reminderLeadMinutes, onOpenVoiceAgent) { showSettingsDialog = true }

            if (!hasNotificationPermission) {
                PermissionCard(Icons.Default.NotificationsActive, strings.notificationsOffTitle, strings.notificationsOffDescription, strings.enableNotifications, onRequestNotificationPermission)
            }
            if (!canScheduleExactAlarms) {
                PermissionCard(Icons.Default.AlarmOn, strings.exactAlarmsRecommendedTitle, strings.exactAlarmsDescription(reminderLeadMinutes), strings.enableExactAlarms, onRequestExactAlarmPermission)
            }

            if (plans.isEmpty()) {
                EmptyStateCard(strings)
            } else {
                plans.forEach { plan ->
                    PlanCard(
                        strings = strings,
                        appLanguage = appLanguage,
                        plan = plan,
                        nowMillis = nowMillis,
                        onEditPlan = {
                            formSeed = plan.toPlanFormSeed()
                            showAddDialog = true
                        },
                        onVoiceEditPlan = { onOpenVoiceAgentForEdit(plan) },
                        onDeletePlan = { onDeletePlan(plan) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddPlanDialog(strings, formSeed, onDismiss = { showAddDialog = false }) { title, location, date, time, leadMinutes, enableAlarmSound, enableVibration ->
            formSeed.planId?.let { planId ->
                onUpdatePlan(planId, title, location, date, time, leadMinutes, enableAlarmSound, enableVibration)
            } ?: onAddPlan(title, location, date, time, leadMinutes, enableAlarmSound, enableVibration)
            showAddDialog = false
            formSeed = PlanFormSeed()
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(strings, agentSettings, reminderLeadMinutes, appLanguage, onDismiss = { showSettingsDialog = false }) { baseUrl, apiKey, model, minutes, language ->
            onSaveSettings(baseUrl, apiKey, model, minutes, language)
            showSettingsDialog = false
        }
    }

    if (voiceAgentState.isOpen) {
        if (voiceAgentState.isDraftPreviewVisible) {
            VoiceDraftPreviewDialog(strings, voiceAgentState, onDismissVoiceAgent, onContinueVoiceConversation, onManualAdjust = {
                formSeed = voiceAgentState.draft.toPlanFormSeed(
                    planId = voiceAgentState.editingPlanId,
                    reminderLeadMinutes = voiceAgentState.reminderLeadMinutes,
                    enableAlarmSound = voiceAgentState.enableAlarmSound,
                    enableVibration = voiceAgentState.enableVibration,
                )
                showAddDialog = true
                onDismissVoiceAgent()
            }, onConfirm = onConfirmVoicePlan)
        } else {
            VoiceConversationDialog(strings, agentSettings, voiceAgentState, onDismissVoiceAgent, onOpenSettings = {
                onDismissVoiceAgent()
                showSettingsDialog = true
            }, onStartVoiceInput = onStartVoiceInput, onStopVoiceInput = onStopVoiceInput)
        }
    }
}

@Composable
private fun OverviewCard(strings: AppStrings, reminderLeadMinutes: Int, onVoiceAdd: () -> Unit, onOpenSettings: () -> Unit) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Box(
            modifier = Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.surface)),
            ).padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.EditCalendar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Text(strings.overviewTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(strings.overviewDescription(reminderLeadMinutes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = onVoiceAdd) {
                        Icon(Icons.Default.Mic, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(strings.voiceAddAction)
                    }
                    OutlinedButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(strings.settings)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(icon: ImageVector, title: String, description: String, actionLabel: String, onAction: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun EmptyStateCard(strings: AppStrings) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.TaskAlt, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text(strings.noPlansYet, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(strings.addFirstReminder, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanCard(
    strings: AppStrings,
    appLanguage: AppLanguage,
    plan: PlanItem,
    nowMillis: Long,
    onEditPlan: () -> Unit,
    onVoiceEditPlan: () -> Unit,
    onDeletePlan: () -> Unit,
) {
    val normalizedLeadMinutes = ReminderSettingsStore.normalizeReminderLeadMinutes(plan.reminderLeadMinutes)
    val reminderAtMillis = plan.scheduledAtMillis - normalizedLeadMinutes * MILLIS_PER_MINUTE
    val status = plan.statusAt(nowMillis)
    val statusPalette = rememberPlanStatusPalette(status)
    val statusText = when (status) {
        PlanStatus.BEFORE_REMINDER -> strings.statusBeforeReminder
        PlanStatus.REMINDER_WINDOW -> strings.statusReminderWindow
        PlanStatus.OVERDUE -> strings.statusOverdue
    }
    val statusDescription = when (status) {
        PlanStatus.BEFORE_REMINDER -> strings.beforeReminderDescription(
            formatStatusDuration(reminderAtMillis - nowMillis, appLanguage),
        )
        PlanStatus.REMINDER_WINDOW -> strings.reminderWindowDescription(
            formatStatusDuration(plan.scheduledAtMillis - nowMillis, appLanguage),
        )
        PlanStatus.OVERDUE -> strings.overdueDescription(
            formatStatusDuration(nowMillis - plan.scheduledAtMillis, appLanguage),
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, statusPalette.borderColor),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(plan.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(formatDateTime(plan.scheduledAtMillis), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row {
                    IconButton(onClick = onEditPlan) {
                        Icon(Icons.Default.Edit, contentDescription = strings.editAction)
                    }
                    IconButton(onClick = onVoiceEditPlan) {
                        Icon(Icons.Default.Mic, contentDescription = strings.voiceEditAction)
                    }
                    IconButton(onClick = onDeletePlan) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = strings.deleteAction)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = statusPalette.chipContainerColor,
                        labelColor = statusPalette.chipContentColor,
                        leadingIconContentColor = statusPalette.chipContentColor,
                    ),
                    label = { Text(statusText) },
                    leadingIcon = {
                        Icon(
                            imageVector = when (status) {
                                PlanStatus.BEFORE_REMINDER -> Icons.Default.Schedule
                                PlanStatus.REMINDER_WINDOW -> Icons.Default.NotificationsActive
                                PlanStatus.OVERDUE -> Icons.Default.Warning
                            },
                            contentDescription = null,
                        )
                    },
                )
                AssistChip(
                    onClick = {},
                    label = { Text(strings.remindEarlyChip(normalizedLeadMinutes)) },
                    leadingIcon = { Icon(Icons.Default.AlarmOn, contentDescription = null) },
                )
                AssistChip(
                    onClick = {},
                    label = { Text(if (plan.enableAlarmSound) strings.alarmSoundOnChip else strings.alarmSoundOffChip) },
                    leadingIcon = { Icon(Icons.Default.AlarmOn, contentDescription = null) },
                )
                AssistChip(
                    onClick = {},
                    label = { Text(if (plan.enableVibration) strings.vibrationOnChip else strings.vibrationOffChip) },
                    leadingIcon = { Icon(Icons.Default.Vibration, contentDescription = null) },
                )
                if (plan.location.isNotBlank()) {
                    AssistChip(onClick = {}, label = { Text(plan.location) }, leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) })
                }
            }
            Text(
                text = when (status) {
                    PlanStatus.BEFORE_REMINDER -> buildString {
                        append(statusDescription)
                        append(" ")
                        append(strings.reminderTime(formatDateTime(reminderAtMillis)))
                    }
                    PlanStatus.REMINDER_WINDOW -> statusDescription
                    PlanStatus.OVERDUE -> statusDescription
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddPlanDialog(
    strings: AppStrings,
    initialSeed: PlanFormSeed,
    onDismiss: () -> Unit,
    onConfirm: (String, String, LocalDate, LocalTime, Int, Boolean, Boolean) -> Unit,
) {
    val fallbackDateTime = remember(initialSeed) { LocalDateTime.now().plusMinutes(30).withSecond(0).withNano(0) }
    val formScrollState = rememberScrollState()
    var title by remember(initialSeed) { mutableStateOf(initialSeed.title) }
    var location by remember(initialSeed) { mutableStateOf(initialSeed.location) }
    var selectedDate by remember(initialSeed) { mutableStateOf(initialSeed.date ?: fallbackDateTime.toLocalDate()) }
    var selectedTime by remember(initialSeed) { mutableStateOf(initialSeed.time ?: fallbackDateTime.toLocalTime()) }
    var leadMinutesText by remember(initialSeed) { mutableStateOf(initialSeed.reminderLeadMinutes.toString()) }
    var enableAlarmSound by remember(initialSeed) { mutableStateOf(initialSeed.enableAlarmSound) }
    var enableVibration by remember(initialSeed) { mutableStateOf(initialSeed.enableVibration) }
    var leadMinutesError by remember { mutableStateOf<String?>(null) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialSeed.planId == null) strings.addPlan else strings.editPlan) },
        text = {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 18.dp)
                        .verticalScroll(formScrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DateSelectorRow(strings, selectedDate.format(dateFormatter), selectedTime.format(timeFormatter), selectedDate, selectedTime, onPickDate = { year, month, day ->
                        selectedDate = LocalDate.of(year, month + 1, day)
                    }, onPickTime = { hour, minute ->
                        selectedTime = LocalTime.of(hour, minute)
                    })
                    OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(strings.taskLabel) })
                    OutlinedTextField(value = location, onValueChange = { location = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(strings.locationOptionalLabel) })
                    OutlinedTextField(
                        value = leadMinutesText,
                        onValueChange = { leadMinutesText = it; leadMinutesError = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(strings.reminderLeadMinutesLabel) },
                        singleLine = true,
                        isError = leadMinutesError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = {
                            Text(
                                leadMinutesError ?: strings.reminderLeadMinutesHint(
                                    ReminderSettingsStore.MIN_REMINDER_LEAD_MINUTES,
                                    ReminderSettingsStore.MAX_REMINDER_LEAD_MINUTES,
                                ),
                            )
                        },
                    )
                    ReminderFeedbackSection(
                        strings = strings,
                        enableAlarmSound = enableAlarmSound,
                        enableVibration = enableVibration,
                        onEnableAlarmSoundChange = { enableAlarmSound = it },
                        onEnableVibrationChange = { enableVibration = it },
                    )
                }

                if (formScrollState.maxValue > 0) {
                    DialogScrollIndicator(
                        scrollState = formScrollState,
                        containerHeight = maxHeight,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedLeadMinutes = leadMinutesText.toIntOrNull()
                val isValidLeadMinutes = parsedLeadMinutes != null &&
                    parsedLeadMinutes in ReminderSettingsStore.MIN_REMINDER_LEAD_MINUTES..ReminderSettingsStore.MAX_REMINDER_LEAD_MINUTES
                if (!isValidLeadMinutes) {
                    leadMinutesError = strings.reminderLeadMinutesHint(
                        ReminderSettingsStore.MIN_REMINDER_LEAD_MINUTES,
                        ReminderSettingsStore.MAX_REMINDER_LEAD_MINUTES,
                    )
                    return@TextButton
                }
                onConfirm(
                    title,
                    location,
                    selectedDate,
                    selectedTime,
                    parsedLeadMinutes!!,
                    enableAlarmSound,
                    enableVibration,
                )
            }) { Text(strings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

@Composable
private fun DialogScrollIndicator(
    scrollState: ScrollState,
    containerHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val thumbHeight = 52.dp
    val topBottomPadding = 6.dp
    val trackHeight = (containerHeight - topBottomPadding * 2).coerceAtLeast(thumbHeight)
    val maxOffset = (trackHeight - thumbHeight).coerceAtLeast(0.dp)
    val scrollFraction = if (scrollState.maxValue == 0) {
        0f
    } else {
        scrollState.value.toFloat() / scrollState.maxValue.toFloat()
    }

    Box(
        modifier = modifier
            .width(10.dp)
            .fillMaxHeight()
            .padding(vertical = topBottomPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(999.dp),
                ),
        )
        Box(
            modifier = Modifier
                .offset(y = maxOffset * scrollFraction)
                .width(6.dp)
                .height(thumbHeight)
                .background(
                    color = Color(0xFF3B82F6),
                    shape = RoundedCornerShape(999.dp),
                ),
        )
    }
}

@Composable
private fun ReminderFeedbackSection(
    strings: AppStrings,
    enableAlarmSound: Boolean,
    enableVibration: Boolean,
    onEnableAlarmSoundChange: (Boolean) -> Unit,
    onEnableVibrationChange: (Boolean) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(strings.reminderFeedbackLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(strings.reminderFeedbackHint, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ReminderToggleRow(
                icon = Icons.Default.AlarmOn,
                label = strings.alarmSoundToggleLabel,
                checked = enableAlarmSound,
                onCheckedChange = onEnableAlarmSoundChange,
            )
            ReminderToggleRow(
                icon = Icons.Default.Vibration,
                label = strings.vibrationToggleLabel,
                checked = enableVibration,
                onCheckedChange = onEnableVibrationChange,
            )
        }
    }
}

@Composable
private fun ReminderToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsDialog(
    strings: AppStrings,
    agentSettings: AgentSettings,
    reminderLeadMinutes: Int,
    currentLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, AppLanguage) -> Unit,
) {
    var baseUrl by remember(agentSettings) { mutableStateOf(agentSettings.baseUrl) }
    var apiKey by remember(agentSettings) { mutableStateOf(agentSettings.apiKey) }
    var model by remember(agentSettings) { mutableStateOf(agentSettings.model) }
    var leadMinutesText by remember(reminderLeadMinutes) { mutableStateOf(reminderLeadMinutes.toString()) }
    var selectedLanguage by remember(currentLanguage) { mutableStateOf(currentLanguage) }
    var leadMinutesError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.settings) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.settingsDescription, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = leadMinutesText,
                    onValueChange = { leadMinutesText = it; leadMinutesError = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(strings.reminderLeadMinutesLabel) },
                    singleLine = true,
                    isError = leadMinutesError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text(leadMinutesError ?: strings.reminderLeadMinutesHint(ReminderSettingsStore.MIN_REMINDER_LEAD_MINUTES, ReminderSettingsStore.MAX_REMINDER_LEAD_MINUTES))
                    },
                )
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text(strings.baseUrlLabel) }, singleLine = true)
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text(strings.apiKeyLabel) }, singleLine = true)
                OutlinedTextField(value = model, onValueChange = { model = it }, modifier = Modifier.fillMaxWidth(), label = { Text(strings.modelLabel) }, singleLine = true)
                Text(strings.languageLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(strings.languageDescription, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { language ->
                        if (language == selectedLanguage) {
                            FilledTonalButton(onClick = { selectedLanguage = language }) { Text(language.optionLabel()) }
                        } else {
                            OutlinedButton(onClick = { selectedLanguage = language }) { Text(language.optionLabel()) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedLeadMinutes = leadMinutesText.toIntOrNull()
                val isValidLeadMinutes = parsedLeadMinutes != null &&
                    parsedLeadMinutes in ReminderSettingsStore.MIN_REMINDER_LEAD_MINUTES..ReminderSettingsStore.MAX_REMINDER_LEAD_MINUTES
                if (!isValidLeadMinutes) {
                    leadMinutesError = strings.reminderLeadMinutesHint(ReminderSettingsStore.MIN_REMINDER_LEAD_MINUTES, ReminderSettingsStore.MAX_REMINDER_LEAD_MINUTES)
                    return@TextButton
                }
                onSave(baseUrl, apiKey, model, parsedLeadMinutes!!, selectedLanguage)
            }) { Text(strings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

@Composable
private fun VoiceConversationDialog(
    strings: AppStrings,
    settings: AgentSettings,
    state: VoiceAgentUiState,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 720.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.fillMaxWidth(0.82f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(strings.voicePlanAssistantTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(strings.voicePlanAssistantDescription(state.reminderLeadMinutes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onDismiss) { Text(strings.close) }
                }

                if (!settings.isConfigured()) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(strings.modelSettingsMissingTitle, fontWeight = FontWeight.SemiBold)
                            Text(strings.modelSettingsMissingDescription)
                            FilledTonalButton(onClick = onOpenSettings) { Text(strings.openSettings) }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.messages.forEach { message ->
                        val isAssistant = message.role == AgentMessageRole.ASSISTANT
                        Card(colors = CardDefaults.cardColors(containerColor = if (isAssistant) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (isAssistant) strings.assistantLabel else strings.youLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(message.text)
                            }
                        }
                    }
                }

                if (state.editingPlanId != null) {
                    DraftSummaryCard(
                        strings = strings,
                        draft = state.draft,
                        missingFields = state.missingFields,
                        reminderLeadMinutes = state.reminderLeadMinutes,
                        enableAlarmSound = state.enableAlarmSound,
                        enableVibration = state.enableVibration,
                    )
                }

                HorizontalDivider()

                if (state.isLoading) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(strings.structuringVoiceInput)
                    }
                }
                if (state.isRecording) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(strings.recordingStatus)
                    }
                } else if (state.isTranscribing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(strings.transcribingVoice)
                    }
                }

                if (state.liveTranscript.isNotBlank()) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(strings.liveTranscript, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.liveTranscript)
                        }
                    }
                }

                VoiceRecordButton(strings, settings.isConfigured() && !state.isLoading && !state.isTranscribing, state.isRecording, onStartVoiceInput, onStopVoiceInput)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceDraftPreviewDialog(
    strings: AppStrings,
    state: VoiceAgentUiState,
    onDismiss: () -> Unit,
    onContinueVoiceInput: () -> Unit,
    onManualAdjust: () -> Unit,
    onConfirm: () -> Unit,
) {
    val latestAssistantMessage = state.messages.lastOrNull { it.role == AgentMessageRole.ASSISTANT }?.text.orEmpty()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.fillMaxWidth(0.82f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(strings.prefilledVoiceDraftTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(strings.prefilledVoiceDraftDescription, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onDismiss) { Text(strings.close) }
                }

                if (latestAssistantMessage.isNotBlank()) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(strings.assistantLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(latestAssistantMessage)
                        }
                    }
                }

                DraftSummaryCard(
                    strings = strings,
                    draft = state.draft,
                    missingFields = state.missingFields,
                    reminderLeadMinutes = state.reminderLeadMinutes,
                    enableAlarmSound = state.enableAlarmSound,
                    enableVibration = state.enableVibration,
                )
                Text(if (state.readyForConfirmation) strings.prefilledReadyDescription else strings.prefilledMissingDescription, color = MaterialTheme.colorScheme.onSurfaceVariant)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = onContinueVoiceInput) { Text(strings.continueAddVoice) }
                    OutlinedButton(onClick = onManualAdjust) { Text(strings.manualAdjust) }
                    FilledTonalButton(onClick = onConfirm, enabled = state.readyForConfirmation) { Text(strings.confirmDirectly) }
                }
            }
        }
    }
}

@Composable
private fun VoiceRecordButton(
    strings: AppStrings,
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

    FilledTonalButton(
        onClick = { if (isRecording) onStopRecording() else onStartRecording() },
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
        Spacer(Modifier.size(8.dp))
        Text(if (isRecording) strings.stopRecording else strings.startRecording)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftSummaryCard(
    strings: AppStrings,
    draft: AgentPlanDraft,
    missingFields: List<String>,
    reminderLeadMinutes: Int,
    enableAlarmSound: Boolean,
    enableVibration: Boolean,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(strings.currentDraft, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            DraftField(strings.taskField, draft.title, strings.missingPlaceholder)
            DraftField(strings.locationField, draft.location, strings.optionalPlaceholder)
            DraftField(strings.dateField, draft.date, strings.missingPlaceholder)
            DraftField(strings.timeField, draft.time, strings.missingPlaceholder)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(strings.reminderLeadChip(reminderLeadMinutes)) }, leadingIcon = { Icon(Icons.Default.AlarmOn, contentDescription = null) })
                AssistChip(
                    onClick = {},
                    label = { Text(if (enableAlarmSound) strings.alarmSoundOnChip else strings.alarmSoundOffChip) },
                    leadingIcon = { Icon(Icons.Default.AlarmOn, contentDescription = null) },
                )
                AssistChip(
                    onClick = {},
                    label = { Text(if (enableVibration) strings.vibrationOnChip else strings.vibrationOffChip) },
                    leadingIcon = { Icon(Icons.Default.Vibration, contentDescription = null) },
                )
                if (missingFields.isNotEmpty()) {
                    missingFields.forEach { field ->
                        AssistChip(onClick = {}, label = { Text(strings.missingFieldChip(missingFieldLabel(field, strings))) })
                    }
                } else {
                    AssistChip(onClick = {}, label = { Text(strings.readyToConfirm) })
                }
            }
        }
    }
}

@Composable
private fun DraftField(label: String, value: String, placeholder: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { placeholder })
    }
}

@Composable
private fun DateSelectorRow(
    strings: AppStrings,
    selectedDateText: String,
    selectedTimeText: String,
    initialDate: LocalDate,
    initialTime: LocalTime,
    onPickDate: (Int, Int, Int) -> Unit,
    onPickTime: (Int, Int) -> Unit,
) {
    val currentYear = remember { LocalDate.now().year }
    val yearRange = remember(initialDate, currentYear) {
        val start = minOf(currentYear, initialDate.year)
        val end = maxOf(currentYear + 10, initialDate.year + 5)
        start..end
    }
    var selectedYear by remember(initialDate) { mutableIntStateOf(initialDate.year) }
    var selectedMonth by remember(initialDate) { mutableIntStateOf(initialDate.monthValue) }
    var selectedDay by remember(initialDate) { mutableIntStateOf(initialDate.dayOfMonth) }
    var selectedHour by remember(initialTime) { mutableIntStateOf(initialTime.hour) }
    var selectedMinute by remember(initialTime) { mutableIntStateOf(initialTime.minute) }
    val maxDay = remember(selectedYear, selectedMonth) {
        YearMonth.of(selectedYear, selectedMonth).lengthOfMonth()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(strings.startTimeLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "$selectedDateText $selectedTimeText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = strings.dateField,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WheelPicker(
                        label = strings.yearWheelLabel,
                        value = selectedYear,
                        range = yearRange,
                        modifier = Modifier.weight(1.5f),
                        wrapSelectorWheel = false,
                    ) { newYear ->
                        selectedYear = newYear
                        val adjustedDay = minOf(selectedDay, YearMonth.of(newYear, selectedMonth).lengthOfMonth())
                        if (adjustedDay != selectedDay) {
                            selectedDay = adjustedDay
                        }
                        onPickDate(selectedYear, selectedMonth - 1, selectedDay)
                    }
                    WheelPicker(
                        label = strings.monthWheelLabel,
                        value = selectedMonth,
                        range = 1..12,
                        modifier = Modifier.weight(1f),
                        formatValue = { value -> value.toString().padStart(2, '0') },
                    ) { newMonth ->
                        selectedMonth = newMonth
                        val adjustedDay = minOf(selectedDay, YearMonth.of(selectedYear, newMonth).lengthOfMonth())
                        if (adjustedDay != selectedDay) {
                            selectedDay = adjustedDay
                        }
                        onPickDate(selectedYear, selectedMonth - 1, selectedDay)
                    }
                    WheelPicker(
                        label = strings.dayWheelLabel,
                        value = selectedDay.coerceIn(1, maxDay),
                        range = 1..maxDay,
                        modifier = Modifier.weight(1f),
                        formatValue = { value -> value.toString().padStart(2, '0') },
                    ) { newDay ->
                        selectedDay = newDay
                        onPickDate(selectedYear, selectedMonth - 1, selectedDay)
                    }
                }
                Text(
                    text = strings.timeField,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WheelPicker(
                        label = strings.hourWheelLabel,
                        value = selectedHour,
                        range = 0..23,
                        modifier = Modifier.weight(1f),
                        formatValue = { value -> value.toString().padStart(2, '0') },
                    ) { newHour ->
                        selectedHour = newHour
                        onPickTime(selectedHour, selectedMinute)
                    }
                    WheelPicker(
                        label = strings.minuteWheelLabel,
                        value = selectedMinute,
                        range = 0..59,
                        modifier = Modifier.weight(1f),
                        formatValue = { value -> value.toString().padStart(2, '0') },
                    ) { newMinute ->
                        selectedMinute = newMinute
                        onPickTime(selectedHour, selectedMinute)
                    }
                }
            }
        }
    }
}

@Composable
private fun WheelPicker(
    label: String,
    value: Int,
    range: IntRange,
    modifier: Modifier = Modifier,
    wrapSelectorWheel: Boolean = true,
    formatValue: (Int) -> String = { it.toString() },
    onValueChange: (Int) -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp),
            factory = { context ->
                NumberPicker(context).apply {
                    descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                    minValue = range.first
                    maxValue = range.last
                    setWrapSelectorWheel(wrapSelectorWheel)
                    setFormatter { number -> formatValue(number) }
                    this.value = value.coerceIn(range.first, range.last)
                    setOnValueChangedListener { _, _, newValue ->
                        onValueChange(newValue)
                    }
                }
            },
            update = { picker ->
                picker.setOnValueChangedListener(null)
                picker.minValue = range.first
                picker.maxValue = range.last
                picker.setWrapSelectorWheel(wrapSelectorWheel)
                picker.setFormatter { number -> formatValue(number) }
                val safeValue = value.coerceIn(range.first, range.last)
                if (picker.value != safeValue) {
                    picker.value = safeValue
                }
                picker.setOnValueChangedListener { _, _, newValue ->
                    onValueChange(newValue)
                }
            },
        )
    }
}

private fun AgentPlanDraft.toPlanFormSeed(
    planId: Long? = null,
    reminderLeadMinutes: Int = ReminderSettingsStore.DEFAULT_REMINDER_LEAD_MINUTES,
    enableAlarmSound: Boolean = true,
    enableVibration: Boolean = true,
): PlanFormSeed {
    val date = runCatching { LocalDate.parse(this.date, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }.getOrNull()
    val time = runCatching { LocalTime.parse(this.time, DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()
    return PlanFormSeed(
        planId = planId,
        title = title,
        location = location,
        date = date,
        time = time,
        reminderLeadMinutes = reminderLeadMinutes,
        enableAlarmSound = enableAlarmSound,
        enableVibration = enableVibration,
    )
}

private fun PlanItem.toPlanFormSeed(): PlanFormSeed {
    val dateTime = Instant.ofEpochMilli(scheduledAtMillis).atZone(ZoneId.systemDefault())
    return PlanFormSeed(
        planId = id,
        title = title,
        location = location,
        date = dateTime.toLocalDate(),
        time = dateTime.toLocalTime().withSecond(0).withNano(0),
        reminderLeadMinutes = reminderLeadMinutes,
        enableAlarmSound = enableAlarmSound,
        enableVibration = enableVibration,
    )
}

@Composable
private fun rememberPlanStatusPalette(status: PlanStatus): PlanStatusPalette {
    val colorScheme = MaterialTheme.colorScheme
    return remember(status, colorScheme) {
        when (status) {
            PlanStatus.BEFORE_REMINDER -> PlanStatusPalette(
                borderColor = colorScheme.secondary.copy(alpha = 0.42f),
                chipContainerColor = colorScheme.secondaryContainer,
                chipContentColor = colorScheme.onSecondaryContainer,
            )
            PlanStatus.REMINDER_WINDOW -> PlanStatusPalette(
                borderColor = colorScheme.tertiary.copy(alpha = 0.52f),
                chipContainerColor = colorScheme.tertiaryContainer,
                chipContentColor = colorScheme.onTertiaryContainer,
            )
            PlanStatus.OVERDUE -> PlanStatusPalette(
                borderColor = colorScheme.error.copy(alpha = 0.58f),
                chipContainerColor = colorScheme.errorContainer,
                chipContentColor = colorScheme.onErrorContainer,
            )
        }
    }
}

private data class PlanStatusPalette(
    val borderColor: Color,
    val chipContainerColor: Color,
    val chipContentColor: Color,
)

private fun missingFieldLabel(field: String, strings: AppStrings): String = when (field) {
    "title" -> strings.taskField
    "location" -> strings.locationField
    "date" -> strings.dateField
    "time" -> strings.timeField
    else -> field
}

private fun formatDateTime(millis: Long): String {
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}

private enum class PlanStatus {
    BEFORE_REMINDER,
    REMINDER_WINDOW,
    OVERDUE,
}

private fun PlanItem.statusAt(nowMillis: Long): PlanStatus {
    val reminderAtMillis = scheduledAtMillis -
        ReminderSettingsStore.normalizeReminderLeadMinutes(reminderLeadMinutes) * MILLIS_PER_MINUTE
    return when {
        nowMillis >= scheduledAtMillis -> PlanStatus.OVERDUE
        nowMillis >= reminderAtMillis -> PlanStatus.REMINDER_WINDOW
        else -> PlanStatus.BEFORE_REMINDER
    }
}

private fun formatStatusDuration(durationMillis: Long, language: AppLanguage): String {
    val safeMillis = durationMillis.coerceAtLeast(0L)
    val totalMinutes = safeMillis / MILLIS_PER_MINUTE
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60

    if (totalMinutes <= 0L) {
        return when (language) {
            AppLanguage.SIMPLIFIED_CHINESE -> "不到1分钟"
            AppLanguage.TRADITIONAL_CHINESE -> "不到 1 分鐘"
            AppLanguage.ENGLISH -> "less than 1 minute"
        }
    }

    val parts = mutableListOf<String>()
    when (language) {
        AppLanguage.SIMPLIFIED_CHINESE -> {
            if (days > 0) parts += "${days}天"
            if (hours > 0) parts += "${hours}小时"
            if (minutes > 0) parts += "${minutes}分钟"
        }
        AppLanguage.TRADITIONAL_CHINESE -> {
            if (days > 0) parts += "${days}天"
            if (hours > 0) parts += "${hours}小時"
            if (minutes > 0) parts += "${minutes}分鐘"
        }
        AppLanguage.ENGLISH -> {
            if (days > 0) parts += if (days == 1L) "1 day" else "$days days"
            if (hours > 0) parts += if (hours == 1L) "1 hour" else "$hours hours"
            if (minutes > 0) parts += if (minutes == 1L) "1 minute" else "$minutes minutes"
        }
    }

    return parts.take(2).joinToString(
        separator = when (language) {
            AppLanguage.ENGLISH -> " "
            else -> ""
        },
    )
}
