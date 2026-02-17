package com.warrantykeeper.di

import android.content.Context
import androidx.room.Room
import com.warrantykeeper.data.local.database.AppDatabase
import com.warrantykeeper.data.local.database.DocumentDao
import com.warrantykeeper.data.remote.GoogleDriveManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "warranty_keeper_db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration() // safety net — if migration fails, recreate
            .build()
    }

    @Provides
    @Singleton
    fun provideDocumentDao(database: AppDatabase): DocumentDao {
        return database.documentDao()
    }

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun provideGoogleDriveManager(@ApplicationContext context: Context): GoogleDriveManager {
        return GoogleDriveManager(context)
    }
}
