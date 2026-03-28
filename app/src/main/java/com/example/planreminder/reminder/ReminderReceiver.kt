package com.example.planreminder.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.planreminder.MainActivity
import com.example.planreminder.R
import com.example.planreminder.agent.ReminderSettingsStore
import com.example.planreminder.i18n.AppStrings
import com.example.planreminder.i18n.LanguageSettingsStore
import com.example.planreminder.i18n.appStringsFor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// 接收提醒广播并生成本地通知，同时根据计划配置决定提示音和震动行为。
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val strings = appStringsFor(LanguageSettingsStore(context).currentLanguage())
        val title = intent.getStringExtra(EXTRA_TITLE)
            .orEmpty()
            .ifBlank { strings.notificationDefaultTitle }
        val location = intent.getStringExtra(EXTRA_LOCATION).orEmpty()
        val scheduledAt = intent.getLongExtra(EXTRA_SCHEDULED_AT, 0L)
        val leadMinutes = ReminderSettingsStore.normalizeReminderLeadMinutes(
            intent.getIntExtra(
                EXTRA_LEAD_MINUTES,
                ReminderSettingsStore.DEFAULT_REMINDER_LEAD_MINUTES,
            ),
        )
        val enableAlarmSound = intent.getBooleanExtra(EXTRA_ENABLE_ALARM_SOUND, true)
        val enableVibration = intent.getBooleanExtra(EXTRA_ENABLE_VIBRATION, true)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        createChannels(notificationManager, strings)
        val channelSpec = ReminderChannelSpec.from(
            enableAlarmSound = enableAlarmSound,
            enableVibration = enableVibration,
        )

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val formattedTime = if (scheduledAt > 0) {
            Instant.ofEpochMilli(scheduledAt)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        } else {
            strings.notificationUnsetTime
        }

        val contentText = strings.notificationContent(formattedTime, location)
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 1200)

        val notificationBuilder = NotificationCompat.Builder(context, channelSpec.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(strings.notificationTitle(leadMinutes, title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            notificationBuilder.setDefaults(0)
            if (enableAlarmSound && soundUri != null) {
                notificationBuilder.setSound(soundUri)
            }
            if (enableVibration) {
                notificationBuilder.setVibrate(vibrationPattern)
            }
            if (!enableAlarmSound && !enableVibration) {
                notificationBuilder.setSilent(true)
            }
        }

        val notification = notificationBuilder.build()

        notificationManager.notify(
            intent.getLongExtra(EXTRA_PLAN_ID, System.currentTimeMillis()).toInt(),
            notification,
        )
    }

    private fun createChannels(notificationManager: NotificationManager, strings: AppStrings) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 1200)

        // Android 8.0 及以上的声音和震动由通知渠道控制，因此要预先创建固定的组合渠道。
        ReminderChannelSpec.entries.forEach { spec ->
            val channel = NotificationChannel(
                spec.channelId,
                spec.channelName(strings),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = strings.notificationChannelDescription
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(spec.enableVibration)
                if (spec.enableVibration) {
                    setVibrationPattern(vibrationPattern)
                }
                if (spec.enableAlarmSound && soundUri != null) {
                    setSound(
                        soundUri,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                } else {
                    setSound(null, null)
                }
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_REMIND = "com.example.planreminder.ACTION_REMIND"
        const val EXTRA_PLAN_ID = "extra_plan_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_LOCATION = "extra_location"
        const val EXTRA_SCHEDULED_AT = "extra_scheduled_at"
        const val EXTRA_LEAD_MINUTES = "extra_lead_minutes"
        const val EXTRA_ENABLE_ALARM_SOUND = "extra_enable_alarm_sound"
        const val EXTRA_ENABLE_VIBRATION = "extra_enable_vibration"
    }
}

private enum class ReminderChannelSpec(
    val channelId: String,
    val enableAlarmSound: Boolean,
    val enableVibration: Boolean,
) {
    SILENT(
        channelId = "plan_reminder_silent_v1",
        enableAlarmSound = false,
        enableVibration = false,
    ),
    VIBRATE(
        channelId = "plan_reminder_vibrate_v1",
        enableAlarmSound = false,
        enableVibration = true,
    ),
    SOUND(
        channelId = "plan_reminder_sound_v1",
        enableAlarmSound = true,
        enableVibration = false,
    ),
    SOUND_AND_VIBRATE(
        channelId = "plan_reminder_sound_vibrate_v1",
        enableAlarmSound = true,
        enableVibration = true,
    ),
    ;

    fun channelName(strings: AppStrings): String {
        return when (this) {
            SILENT -> strings.notificationChannelSilentName
            VIBRATE -> strings.notificationChannelVibrateName
            SOUND -> strings.notificationChannelSoundName
            SOUND_AND_VIBRATE -> strings.notificationChannelSoundVibrateName
        }
    }

    companion object {
        fun from(enableAlarmSound: Boolean, enableVibration: Boolean): ReminderChannelSpec {
            return when {
                enableAlarmSound && enableVibration -> SOUND_AND_VIBRATE
                enableAlarmSound -> SOUND
                enableVibration -> VIBRATE
                else -> SILENT
            }
        }
    }
}
