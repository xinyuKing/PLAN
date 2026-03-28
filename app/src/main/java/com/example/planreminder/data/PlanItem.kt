package com.example.planreminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// 计划实体既驱动界面展示，也作为提醒调度和恢复的持久化来源。
@Entity(tableName = "plans")
data class PlanItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val location: String,
    val scheduledAtMillis: Long,
    val reminderLeadMinutes: Int = 10,
    val enableAlarmSound: Boolean = true,
    val enableVibration: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
)
