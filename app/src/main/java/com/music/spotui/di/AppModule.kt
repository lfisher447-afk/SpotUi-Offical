package com.music.spotui.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.work.WorkManager
import com.music.spotui.data.dao.TrashBinDao
import com.music.spotui.data.db.AppDatabase
import com.music.spotui.data.db.SpotUIDatabase
import com.music.spotui.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** Provides the structured process scope used by app-lifetime coordinators. */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Provides application-scoped preferences for settings that are not relational data. */
    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    /** Provides the Room database used by the clean data layer. */
    @Provides
    @Singleton
    fun provideSpotUIDatabase(@ApplicationContext context: Context): SpotUIDatabase =
        SpotUIDatabase.getInstance(context)

    /** Provides WorkManager for durable background downloads. */
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return runCatching {
            WorkManager.getInstance(context)
        }.getOrElse {
            val app = context.applicationContext as? com.music.spotui.MyApplication
            if (app != null) {
                runCatching { WorkManager.initialize(context, app.workManagerConfiguration) }
            }
            WorkManager.getInstance(context)
        }
    }

    @Provides
    @Singleton
    fun provideTrashBinDao(database: AppDatabase): TrashBinDao {
        return database.trashBinDao()
    }
}
