package com.ss7guardian.data.dao

import androidx.room.*
import com.ss7guardian.data.entity.SecurityEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * SecurityEventDao - Data Access Object for Security Events
 * ===========================================================
 * 
 * Provides database operations for the security_events table.
 * Events are never auto-deleted to maintain an audit trail.
 * 
 * @author SS7 Guardian Team
 * @since 0.1.0
 */
@Dao
interface SecurityEventDao {
    
    /**
     * Get all events as a reactive Flow (newest first)
     */
    @Query("SELECT * FROM security_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<SecurityEventEntity>>
    
    /**
     * Get undismissed (active) events as Flow
     */
    @Query("SELECT * FROM security_events WHERE dismissed = 0 ORDER BY timestamp DESC")
    fun getActiveEvents(): Flow<List<SecurityEventEntity>>
    
    /**
     * Get events since a given timestamp (for threat calculation)
     */
    @Query("SELECT * FROM security_events WHERE timestamp > :since ORDER BY timestamp DESC")
    suspend fun getRecentEvents(since: Long): List<SecurityEventEntity>
    
    /**
     * Get events within a date range
     */
    @Query("""
        SELECT * FROM security_events 
        WHERE timestamp BETWEEN :startTime AND :endTime 
        ORDER BY timestamp DESC
    """)
    suspend fun getEventsByDateRange(startTime: Long, endTime: Long): List<SecurityEventEntity>
    
    /**
     * Get single event by ID
     */
    @Query("SELECT * FROM security_events WHERE id = :eventId")
    suspend fun getEventById(eventId: Long): SecurityEventEntity?
    
    /**
     * Get highest recent threat level
     */
    @Query("SELECT COALESCE(MAX(threatLevel), 0) FROM security_events WHERE timestamp > :since AND dismissed = 0")
    suspend fun getMaxRecentThreatLevel(since: Long): Int
    
    /**
     * Count undismissed events
     */
    @Query("SELECT COUNT(*) FROM security_events WHERE dismissed = 0")
    suspend fun getActiveEventCount(): Int
    
    /**
     * Insert a new security event
     */
    @Insert
    suspend fun insertEvent(event: SecurityEventEntity): Long
    
    /**
     * Mark event as dismissed by user
     */
    @Query("UPDATE security_events SET dismissed = 1 WHERE id = :eventId")
    suspend fun dismissEvent(eventId: Long)
    
    /**
     * Dismiss all active events
     */
    @Query("UPDATE security_events SET dismissed = 1 WHERE dismissed = 0")
    suspend fun dismissAllEvents()
    
    /**
     * Mark event as notified
     */
    @Query("UPDATE security_events SET notified = 1 WHERE id = :eventId")
    suspend fun markNotified(eventId: Long)
    
    /**
     * Get events needing notification
     */
    @Query("""
        SELECT * FROM security_events 
        WHERE notified = 0 AND threatLevel >= :minThreatLevel 
        ORDER BY threatLevel DESC, timestamp DESC
    """)
    suspend fun getUnnotifiedEvents(minThreatLevel: Int = 2): List<SecurityEventEntity>
    
    /**
     * Delete old events
     */
    @Query("DELETE FROM security_events WHERE timestamp < :timestamp")
    suspend fun deleteOldEvents(timestamp: Long): Int
    
    /**
     * Delete all events
     */
    @Query("DELETE FROM security_events")
    suspend fun deleteAllEvents()
}