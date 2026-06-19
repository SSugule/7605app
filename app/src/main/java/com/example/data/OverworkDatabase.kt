package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Employee::class, WeeklyLog::class, DutyRule::class], version = 2, exportSchema = false)
abstract class OverworkDatabase : RoomDatabase() {
    abstract fun overworkDao(): OverworkDao

    companion object {
        @Volatile
        private var INSTANCE: OverworkDatabase? = null

        fun getDatabase(context: Context): OverworkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OverworkDatabase::class.java,
                    "overwork_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
