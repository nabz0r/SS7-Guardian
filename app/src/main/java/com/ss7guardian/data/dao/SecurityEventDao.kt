package com.ss7guardian.data.dao

import androidx.room.*
import com.ss7guardian.data.entity.SecurityEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SecurityEventDao {
    @Query("SELECT * FROM security_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<SecurityEventEntity>>

    @Query("SELECT * FROM security_events WHERE timestamp > :since")
    suspend fun getRecentEvents(since: Long): List<SecurityEventEntity>

    @Insert
    suspend fun insertEvent(event: SecurityEventEntity): Long

    @Query("UPDATE security_events SET dismissed = 1 WHERE id = :eventId")
    suspend fun dismissEvent(eventId: Long)

    @Query("UPDATE security_events SET notified = 1 WHERE id = :eventId")
    suspend fun markNotified(eventId: Long)
}