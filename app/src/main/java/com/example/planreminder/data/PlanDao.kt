package com.example.planreminder.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// 让 Room 查询保持精简，上层只需要关注应用行为本身。
@Dao
interface PlanDao {
    @Query("SELECT * FROM plans ORDER BY scheduledAtMillis ASC")
    fun observeAll(): Flow<List<PlanItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(planItem: PlanItem): Long

    @Delete
    suspend fun delete(planItem: PlanItem)

    @Query("SELECT * FROM plans WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): PlanItem?

    @Query("SELECT * FROM plans WHERE scheduledAtMillis > :now ORDER BY scheduledAtMillis ASC")
    suspend fun upcomingPlans(now: Long): List<PlanItem>
}
