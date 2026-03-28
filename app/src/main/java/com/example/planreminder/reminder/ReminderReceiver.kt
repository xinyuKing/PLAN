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

// 接收 AlarmManager 广播，并转换成带声音和震动的本地提醒通知。
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

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        createChannel(notificationManager, strings)

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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(strings.notificationTitle(leadMinutes, title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()

        notificationManager.notify(
            intent.getLongExtra(EXTRA_PLAN_ID, System.currentTimeMillis()).toInt(),
            notification,
        )
    }

    private fun createChannel(notificationManager: NotificationManager, strings: AppStrings) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 1200)

        // Android 8+ 的声音和震动样式由通知渠道控制，因此这里集中配置。
        val channel = NotificationChannel(
            CHANNEL_ID,
            strings.notificationChannelName,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = strings.notificationChannelDescription
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            setVibrationPattern(vibrationPattern)
            setSound(
                soundUri,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_REMIND = "com.example.planreminder.ACTION_REMIND"
        const val EXTRA_PLAN_ID = "extra_plan_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_LOCATION = "extra_location"
        const val EXTRA_SCHEDULED_AT = "extra_scheduled_at"
        const val EXTRA_LEAD_MINUTES = "extra_lead_minutes"

        // 渠道配置创建后不可被代码覆盖，因此升级为新的渠道 id。
        private const val CHANNEL_ID = "plan_reminder_alarm_channel_v2"
    }
}
