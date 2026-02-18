package com.virtualcoverage.signalmap.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.virtualcoverage.signalmap.data.local.dao.SignalMeasurementDao
import com.virtualcoverage.signalmap.data.local.entity.SignalMeasurementEntity

@Database(
    entities = [SignalMeasurementEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SignalDatabase : RoomDatabase() {
    abstract fun signalMeasurementDao(): SignalMeasurementDao
}
