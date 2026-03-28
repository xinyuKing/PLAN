package com.example.planreminder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PlanItem::class], version = 1, exportSchema = false)
abstract class PlanDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao

    companion object {
        @Volatile
        private var INSTANCE: PlanDatabase? = null

        fun getInstance(context: Context): PlanDatabase {
            return INSTANCE ?: synchronized(this) {
                // 使用 applicationContext，避免单例意外持有 Activity 引用。
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlanDatabase::class.java,
                    "plan_reminder.db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}
