package com.example.planreminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// 这是计划数据的唯一事实来源，既给页面显示，也给提醒调度使用。
@Entity(tableName = "plans")
data class PlanItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val location: String,
    val scheduledAtMillis: Long,
    val reminderLeadMinutes: Int = 10,
    val createdAtMillis: Long = System.currentTimeMillis(),
)
