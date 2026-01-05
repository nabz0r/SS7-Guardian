package com.ss7guardian.data

import android.content.Context
import androidx.room.*
import com.ss7guardian.data.dao.CellTowerDao
import com.ss7guardian.data.dao.SecurityEventDao
import com.ss7guardian.data.entity.CellTowerEntity
import com.ss7guardian.data.entity.SecurityEventEntity

@Database(
    entities = [CellTowerEntity::class, SecurityEventEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cellTowerDao(): CellTowerDao
    abstract fun securityEventDao(): SecurityEventDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "ss7_guardian.db")
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromEventType(value: SecurityEventEntity.EventType) = value.name
    @TypeConverter
    fun toEventType(value: String) = SecurityEventEntity.EventType.valueOf(value)
}