package com.example.planreminder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PlanItem::class], version = 3, exportSchema = false)
abstract class PlanDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao

    companion object {
        @Volatile
        private var INSTANCE: PlanDatabase? = null

        fun getInstance(context: Context): PlanDatabase {
            return INSTANCE ?: synchronized(this) {
                // 使用 applicationContext 创建数据库单例，避免意外持有页面级上下文。
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlanDatabase::class.java,
                    "plan_reminder.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE plans ADD COLUMN reminderLeadMinutes INTEGER NOT NULL DEFAULT 10",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE plans ADD COLUMN enableAlarmSound INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL(
                    "ALTER TABLE plans ADD COLUMN enableVibration INTEGER NOT NULL DEFAULT 1",
                )
            }
        }
    }
}
