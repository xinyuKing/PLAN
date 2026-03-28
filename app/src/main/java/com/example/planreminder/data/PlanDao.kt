package com.example.planreminder.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// 将数据库查询控制在清晰边界内，让上层逻辑专注于业务编排。
@Dao
interface PlanDao {
    @Query("SELECT * FROM plans ORDER BY scheduledAtMillis ASC")
    fun observeAll(): Flow<List<PlanItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(planItem: PlanItem): Long

    @Delete
    suspend fun delete(planItem: PlanItem)

    @Update
    suspend fun update(planItem: PlanItem)

    @Query("SELECT * FROM plans WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): PlanItem?

    @Query("SELECT * FROM plans WHERE scheduledAtMillis > :now ORDER BY scheduledAtMillis ASC")
    suspend fun upcomingPlans(now: Long): List<PlanItem>
}
