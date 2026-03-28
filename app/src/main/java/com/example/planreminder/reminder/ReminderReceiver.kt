package com.example.planreminder.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.planreminder.MainActivity
import com.example.planreminder.R
import com.example.planreminder.agent.ReminderSettingsStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// 接收 AlarmManager 回调，并转换成用户可见的本地通知。
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "计划提醒" }
        val location = intent.getStringExtra(EXTRA_LOCATION).orEmpty()
        val scheduledAt = intent.getLongExtra(EXTRA_SCHEDULED_AT, 0L)
        val leadMinutes = ReminderSettingsStore.normalizeReminderLeadMinutes(
            intent.getIntExtra(EXTRA_LEAD_MINUTES, ReminderSettingsStore.DEFAULT_REMINDER_LEAD_MINUTES),
        )

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        createChannel(notificationManager)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            // 如果应用已经打开，尽量复用现有任务栈。
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
            "未设置时间"
        }

        val contentText = buildString {
            append("开始时间：").append(formattedTime)
            if (location.isNotBlank()) {
                append(" | 地点：").append(location)
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("还有 $leadMinutes 分钟：$title")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(
            intent.getLongExtra(EXTRA_PLAN_ID, System.currentTimeMillis()).toInt(),
            notification,
        )
    }

    private fun createChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        // 通知渠道可重复创建，因此每次通知前创建一次是安全的。
        val channel = NotificationChannel(
            CHANNEL_ID,
            "计划提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "在计划开始前按设定分钟数发送本地通知提醒。"
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
        // 渠道 id 需要保持稳定，用户对通知渠道的设置才能持续生效。
        private const val CHANNEL_ID = "plan_reminder_channel"
    }
}
