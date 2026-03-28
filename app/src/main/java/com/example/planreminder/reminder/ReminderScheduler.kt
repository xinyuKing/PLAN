package com.example.planreminder.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.planreminder.agent.ReminderSettingsStore
import com.example.planreminder.data.PlanItem

// 统一封装提醒注册与取消逻辑，避免界面层直接处理 AlarmManager 细节。
class ReminderScheduler(
    private val context: Context,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(planItem: PlanItem) {
        val normalizedLeadMinutes = ReminderSettingsStore.normalizeReminderLeadMinutes(
            planItem.reminderLeadMinutes,
        )
        val reminderAtMillis = reminderTimeFor(planItem, normalizedLeadMinutes)
        val pendingIntent = createPendingIntent(planItem)

        // 先撤销同一计划的旧提醒，尽量避免不同系统实现下出现重复提醒。
        alarmManager.cancel(pendingIntent)

        try {
            if (canScheduleExactAlarms()) {
                // 有精确提醒能力时，尽量贴近用户设置的提醒时刻触发。
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderAtMillis,
                    pendingIntent,
                )
            } else {
                // 无法使用精确提醒时退化到普通提醒，触发时间可能被系统轻微延后。
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderAtMillis,
                    pendingIntent,
                )
            }
        } catch (_: SecurityException) {
            // 权限状态可能在检查后发生变化，因此这里仍需兜底降级。
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
        // 如果距离开始时间已经很近，就尽快触发提醒，而不是直接放弃这一次通知。
        return maxOf(target, now + 2_000L)
    }

    private fun createPendingIntent(planItem: PlanItem): PendingIntent {
        val normalizedLeadMinutes = ReminderSettingsStore.normalizeReminderLeadMinutes(
            planItem.reminderLeadMinutes,
        )
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            // 通过稳定的 data URI 区分每条计划，避免 PendingIntent 互相覆盖。
            data = Uri.parse("plan://${planItem.id}")
            putExtra(ReminderReceiver.EXTRA_PLAN_ID, planItem.id)
            putExtra(ReminderReceiver.EXTRA_TITLE, planItem.title)
            putExtra(ReminderReceiver.EXTRA_LOCATION, planItem.location)
            putExtra(ReminderReceiver.EXTRA_SCHEDULED_AT, planItem.scheduledAtMillis)
            putExtra(ReminderReceiver.EXTRA_LEAD_MINUTES, normalizedLeadMinutes)
            putExtra(ReminderReceiver.EXTRA_ENABLE_ALARM_SOUND, planItem.enableAlarmSound)
            putExtra(ReminderReceiver.EXTRA_ENABLE_VIBRATION, planItem.enableVibration)
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
