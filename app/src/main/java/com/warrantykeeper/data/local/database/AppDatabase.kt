package com.warrantykeeper.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DocumentEntity::class],
    version = 3,          // 1→2: userId; 2→3: totalAmount, currency
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    companion object {
        /** Migration 1→2: add userId column */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE documents ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Migration 2→3: add totalAmount and currency columns for receipt parsing */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE documents ADD COLUMN totalAmount REAL")
                database.execSQL("ALTER TABLE documents ADD COLUMN currency TEXT")
            }
        }
    }
}
