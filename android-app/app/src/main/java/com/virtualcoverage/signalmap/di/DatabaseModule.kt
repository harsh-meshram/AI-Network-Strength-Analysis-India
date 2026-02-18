package com.virtualcoverage.signalmap.di

import android.content.Context
import androidx.room.Room
import com.virtualcoverage.signalmap.data.local.SignalDatabase
import com.virtualcoverage.signalmap.data.local.dao.SignalMeasurementDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSignalDatabase(
        @ApplicationContext context: Context
    ): SignalDatabase {
        return Room.databaseBuilder(
            context,
            SignalDatabase::class.java,
            "signal_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideSignalMeasurementDao(database: SignalDatabase): SignalMeasurementDao {
        return database.signalMeasurementDao()
    }
}
