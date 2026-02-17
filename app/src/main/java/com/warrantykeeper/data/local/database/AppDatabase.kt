package com.warrantykeeper.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DocumentEntity::class],
    version = 2,          // bumped from 1 → 2 for userId column
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    companion object {
        /**
         * Migration 1→2: add userId column (empty string for existing rows,
         * so old data won't be visible to anyone — they'll just appear in no user's list).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE documents ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
