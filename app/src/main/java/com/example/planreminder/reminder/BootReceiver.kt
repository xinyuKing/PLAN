package com.example.planreminder.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.planreminder.data.PlanDatabase
import com.example.planreminder.data.PlanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// 在系统重启、应用更新或精确提醒权限变化后，重新注册未来计划的提醒。
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = PlanRepository(PlanDatabase.getInstance(context).planDao())
                val scheduler = ReminderScheduler(context)
                repository.getUpcomingPlans(System.currentTimeMillis()).forEach { plan ->
                    scheduler.schedule(plan)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
