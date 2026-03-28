package com.example.planreminder.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.planreminder.agent.ReminderSettingsStore
import com.example.planreminder.data.PlanDatabase
import com.example.planreminder.data.PlanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// 在设备重启、应用更新或精确提醒权限变化后恢复提醒任务。
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 广播处理可能长于 onReceive 生命周期，所以后台任务结束后要显式完成。
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = PlanRepository(PlanDatabase.getInstance(context).planDao())
                val scheduler = ReminderScheduler(context)
                val leadMinutes = ReminderSettingsStore(context).reminderLeadMinutes.value
                // 只需要重新注册未来的计划，过去的计划无需恢复。
                repository.getUpcomingPlans(System.currentTimeMillis()).forEach { scheduler.schedule(it, leadMinutes) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
