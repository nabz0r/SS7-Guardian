package com.ss7guardian.data

import android.content.Context
import androidx.room.*
import com.ss7guardian.data.dao.CellTowerDao
import com.ss7guardian.data.dao.SecurityEventDao
import com.ss7guardian.data.entity.CellTowerEntity
import com.ss7guardian.data.entity.SecurityEventEntity

/**
 * AppDatabase - Room Database Configuration
 * ==========================================
 * 
 * Central database for SS7 Guardian using Room persistence library.
 * Stores all local data including cell tower history and security events.
 * 
 * Tables:
 * - cell_towers: Historical record of all observed cell towers
 * - security_events: Log of all detected security anomalies
 * 
 * Design Decisions:
 * - exportSchema=false: Don't export schema for simplicity (enable for production)
 * - fallbackToDestructiveMigration: Recreate DB on version change (dev only)
 * - Singleton pattern: Only one database instance per app
 * 
 * Database Location: /data/data/com.ss7guardian/databases/ss7_guardian.db
 * 
 * @see CellTowerEntity Cell tower data structure
 * @see SecurityEventEntity Security event data structure
 * @author SS7 Guardian Team
 * @since 0.1.0
 */
@Database(
    entities = [
        CellTowerEntity::class,
        SecurityEventEntity::class
    ],
    version = 1,
    exportSchema = false // TODO: Enable for production with proper migrations
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    /**
     * Access cell tower data
     * 
     * Operations:
     * - Query known towers by location identifiers
     * - Insert new tower observations
     * - Update trust scores based on observation frequency
     */
    abstract fun cellTowerDao(): CellTowerDao
    
    /**
     * Access security event log
     * 
     * Operations:
     * - Insert new security events
     * - Query recent events for threat assessment
     * - Mark events as dismissed/notified
     */
    abstract fun securityEventDao(): SecurityEventDao

    companion object {
        /**
         * Database filename
         * Located in app's private storage
         */
        private const val DATABASE_NAME = "ss7_guardian.db"
        
        /**
         * Singleton instance with volatile for thread safety
         */
        @Volatile 
        private var INSTANCE: AppDatabase? = null

        /**
         * Get or create database instance
         * 
         * Thread-safe singleton using double-checked locking.
         * Database is created on first access, not at app startup.
         * 
         * @param context Application context (avoids memory leaks)
         * @return Singleton database instance
         */
        fun getInstance(context: Context): AppDatabase {
            // First check without lock (fast path)
            return INSTANCE ?: synchronized(this) {
                // Second check with lock (safe path)
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        /**
         * Build the Room database
         * 
         * Configuration:
         * - fallbackToDestructiveMigration: For development only
         * - Main thread queries disabled (default)
         * - Write-ahead logging enabled (default for API 16+)
         */
        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
            // TODO: Replace with proper migration strategy before production
            .fallbackToDestructiveMigration()
            .build()
        }
    }
}

/**
 * Type Converters for Room
 * 
 * Handles conversion between Kotlin types and SQLite storage types.
 * Room can't store enums directly, so we convert to/from String.
 */
class Converters {
    /**
     * Convert EventType enum to String for storage
     */
    @TypeConverter
    fun fromEventType(value: SecurityEventEntity.EventType): String {
        return value.name
    }
    
    /**
     * Convert stored String back to EventType enum
     */
    @TypeConverter
    fun toEventType(value: String): SecurityEventEntity.EventType {
        return SecurityEventEntity.EventType.valueOf(value)
    }
}