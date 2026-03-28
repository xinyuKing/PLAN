package com.example.planreminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.planreminder.agent.AgentSettings
import com.example.planreminder.agent.DashScopeRealtimeSpeechClient
import com.example.planreminder.ui.PlanReminderScreen
import com.example.planreminder.ui.theme.PlanReminderTheme

// 承载 Compose 界面，并把 Android 权限和语音输入能力接入页面状态。
class MainActivity : ComponentActivity() {
    private val viewModel: PlanViewModel by viewModels()
    private val realtimeSpeechClient = DashScopeRealtimeSpeechClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PlanReminderTheme {
                var hasNotificationPermission by remember { mutableStateOf(checkNotificationPermission()) }
                var canScheduleExactAlarms by remember { mutableStateOf(viewModel.canScheduleExactAlarms()) }

                val plans by viewModel.plans.collectAsStateWithLifecycle()
                val agentSettings by viewModel.agentSettings.collectAsStateWithLifecycle()
                val reminderLeadMinutes by viewModel.reminderLeadMinutes.collectAsStateWithLifecycle()
                val voiceAgentState by viewModel.voiceAgentState.collectAsStateWithLifecycle()

                val requestNotificationPermission = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) {
                    hasNotificationPermission = checkNotificationPermission()
                }

                val requestAudioPermission = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (granted) {
                        startAppVoiceInput(agentSettings)
                    } else {
                        viewModel.showMessage("请先允许麦克风权限，再使用语音输入。")
                    }
                }

                DisposableEffect(lifecycle) {
                    // 用户从系统设置返回后，需要重新检查通知和精确提醒权限状态。
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            hasNotificationPermission = checkNotificationPermission()
                            canScheduleExactAlarms = viewModel.canScheduleExactAlarms()
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                PlanReminderScreen(
                    plans = plans,
                    hasNotificationPermission = hasNotificationPermission,
                    canScheduleExactAlarms = canScheduleExactAlarms,
                    reminderLeadMinutes = reminderLeadMinutes,
                    messages = viewModel.messages,
                    agentSettings = agentSettings,
                    voiceAgentState = voiceAgentState,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onRequestExactAlarmPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val exactAlarmIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            runCatching { startActivity(exactAlarmIntent) }
                                .onFailure { startActivity(appDetailsIntent) }
                        }
                    },
                    onAddPlan = viewModel::addPlan,
                    onDeletePlan = viewModel::deletePlan,
                    onSaveSettings = viewModel::saveSettings,
                    onOpenVoiceAgent = viewModel::openVoiceAgent,
                    onDismissVoiceAgent = {
                        cancelAppVoiceInput()
                        viewModel.closeVoiceAgent()
                    },
                    onStartVoiceInput = {
                        if (checkAudioPermission()) {
                            startAppVoiceInput(agentSettings)
                        } else {
                            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStopVoiceInput = ::stopAppVoiceInput,
                    onConfirmVoicePlan = viewModel::confirmVoicePlan,
                )
            }
        }
    }

    override fun onDestroy() {
        realtimeSpeechClient.release()
        super.onDestroy()
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startAppVoiceInput(settings: AgentSettings) {
        if (!settings.isConfigured()) {
            viewModel.showMessage("请先完成接口设置，再使用语音添加。")
            return
        }

        realtimeSpeechClient.start(
            settings = settings,
            listener = object : DashScopeRealtimeSpeechClient.Listener {
                override fun onRecordingStarted() {
                    viewModel.markVoiceRecordingStarted()
                    viewModel.showMessage("已开始录音，说完后点击“结束录音”。")
                }

                override fun onRecordingStopped() {
                    viewModel.markVoiceRecordingStopped()
                }

                override fun onPartialTranscript(text: String) {
                    viewModel.updateLiveTranscript(text)
                }

                override fun onFinalTranscript(text: String) {
                    viewModel.clearVoiceCaptureState()
                    viewModel.submitVoiceTranscript(text)
                }

                override fun onError(message: String) {
                    viewModel.clearVoiceCaptureState()
                    viewModel.showMessage(message)
                }
            },
        )
    }

    private fun stopAppVoiceInput() {
        realtimeSpeechClient.stop()
    }

    private fun cancelAppVoiceInput() {
        realtimeSpeechClient.cancel()
        viewModel.clearVoiceCaptureState()
    }
}
