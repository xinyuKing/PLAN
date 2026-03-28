package com.example.planreminder.data

import kotlinx.coroutines.flow.Flow

// 用一层轻量仓库隔离数据库细节，避免 ViewModel 直接依赖 Room。
class PlanRepository(
    private val planDao: PlanDao,
) {
    fun observePlans(): Flow<List<PlanItem>> = planDao.observeAll()

    suspend fun addPlan(planItem: PlanItem): PlanItem {
        val id = planDao.insert(planItem)
        return planItem.copy(id = id)
    }

    suspend fun updatePlan(planItem: PlanItem): PlanItem {
        planDao.update(planItem)
        return planItem
    }

    suspend fun deletePlan(planItem: PlanItem) = planDao.delete(planItem)

    suspend fun findPlan(id: Long): PlanItem? = planDao.findById(id)

    suspend fun getUpcomingPlans(now: Long): List<PlanItem> = planDao.upcomingPlans(now)
}
