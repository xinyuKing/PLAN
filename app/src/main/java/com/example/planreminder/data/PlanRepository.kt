package com.example.planreminder.data

import kotlinx.coroutines.flow.Flow

// 通过轻量仓库层隔离 Room 细节，让 ViewModel 只处理业务流程。
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
