package com.example.planreminder.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.planreminder.agent.ReminderSettingsStore
import com.example.planreminder.data.PlanItem

// 对 AlarmManager 做一层封装，避免界面层和数据层直接处理调度细节。
class ReminderScheduler(
    private val context: Context,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(
        planItem: PlanItem,
        reminderLeadMinutes: Int,
    ) {
        val normalizedLeadMinutes = ReminderSettingsStore.normalizeReminderLeadMinutes(reminderLeadMinutes)
        val reminderAtMillis = reminderTimeFor(planItem, normalizedLeadMinutes)
        val pendingIntent = createPendingIntent(planItem, normalizedLeadMinutes)

        try {
            if (canScheduleExactAlarms()) {
                // 精确提醒更容易贴近用户设置的提醒时间点。
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderAtMillis,
                    pendingIntent,
                )
            } else {
                // 没有特殊权限时仍可退化使用普通提醒，但系统可能会略微延后触发。
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderAtMillis,
                    pendingIntent,
                )
            }
        } catch (_: SecurityException) {
            // 权限状态可能在检查后发生变化，因此这里仍然可能抛出异常。
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminderAtMillis,
                pendingIntent,
            )
        }
    }

    fun cancel(planId: Long) {
        alarmManager.cancel(createPendingIntent(planId))
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun reminderTimeFor(
        planItem: PlanItem,
        reminderLeadMinutes: Int,
    ): Long {
        val target = planItem.scheduledAtMillis - reminderLeadMinutes * 60_000L
        val now = System.currentTimeMillis()
        // 如果计划已经很接近开始时间，就尽快提醒，而不是直接丢弃这次提醒。
        return maxOf(target, now + 2_000L)
    }

    private fun createPendingIntent(
        planItem: PlanItem,
        reminderLeadMinutes: Int,
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            // 稳定的 data URI 能帮助 Android 把每条计划识别为不同的 PendingIntent。
            data = Uri.parse("plan://${planItem.id}")
            putExtra(ReminderReceiver.EXTRA_PLAN_ID, planItem.id)
            putExtra(ReminderReceiver.EXTRA_TITLE, planItem.title)
            putExtra(ReminderReceiver.EXTRA_LOCATION, planItem.location)
            putExtra(ReminderReceiver.EXTRA_SCHEDULED_AT, planItem.scheduledAtMillis)
            putExtra(ReminderReceiver.EXTRA_LEAD_MINUTES, reminderLeadMinutes)
        }
        return PendingIntent.getBroadcast(
            context,
            planItem.id.toRequestCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createPendingIntent(planId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            data = Uri.parse("plan://${planId}")
        }
        return PendingIntent.getBroadcast(
            context,
            planId.toRequestCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun Long.toRequestCode(): Int = (this xor (this ushr 32)).toInt()
}
