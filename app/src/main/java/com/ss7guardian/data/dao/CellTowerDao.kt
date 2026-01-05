package com.ss7guardian.data.dao

import androidx.room.*
import com.ss7guardian.data.entity.CellTowerEntity
import kotlinx.coroutines.flow.Flow

/**
 * CellTowerDao - Data Access Object for Cell Towers
 * ===================================================
 * 
 * Provides database operations for the cell_towers table.
 * All queries are suspend functions for coroutine support,
 * except for Flow-returning queries which provide reactive updates.
 * 
 * Usage Pattern:
 * 1. Check if tower is known: isTowerKnown()
 * 2. If known, update observation: updateTowerSeen()
 * 3. If unknown, insert new record: insertTower()
 * 
 * @author SS7 Guardian Team
 * @since 0.1.0
 */
@Dao
interface CellTowerDao {
    
    /**
     * Get all known cell towers ordered by last seen time
     * Returns a Flow that automatically emits updates.
     */
    @Query("SELECT * FROM cell_towers ORDER BY lastSeen DESC")
    fun getAllTowers(): Flow<List<CellTowerEntity>>
    
    /**
     * Get total count of known towers
     */
    @Query("SELECT COUNT(*) FROM cell_towers")
    suspend fun getTowerCount(): Int
    
    /**
     * Check if a specific tower is already known
     * Uses composite index for fast O(1) lookup.
     */
    @Query("""
        SELECT COUNT(*) FROM cell_towers 
        WHERE cellId = :cellId AND lac = :lac AND mcc = :mcc AND mnc = :mnc
    """)
    suspend fun isTowerKnown(cellId: Int, lac: Int, mcc: Int, mnc: Int): Int
    
    /**
     * Get a specific tower by its identifiers
     */
    @Query("""
        SELECT * FROM cell_towers 
        WHERE cellId = :cellId AND lac = :lac AND mcc = :mcc AND mnc = :mnc
        LIMIT 1
    """)
    suspend fun getTower(cellId: Int, lac: Int, mcc: Int, mnc: Int): CellTowerEntity?
    
    /**
     * Get towers observed near a location (bounding box)
     */
    @Query("""
        SELECT * FROM cell_towers 
        WHERE latitude BETWEEN :lat - :radius AND :lat + :radius
        AND longitude BETWEEN :lon - :radius AND :lon + :radius
        ORDER BY lastSeen DESC
    """)
    suspend fun getTowersNear(lat: Double, lon: Double, radius: Double = 0.05): List<CellTowerEntity>
    
    /**
     * Get towers with low trust scores (potentially suspicious)
     */
    @Query("SELECT * FROM cell_towers WHERE trustScore < :maxScore ORDER BY trustScore ASC")
    suspend fun getSuspiciousTowers(maxScore: Float = 0.3f): List<CellTowerEntity>
    
    /**
     * Insert a new cell tower (REPLACE on conflict)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTower(tower: CellTowerEntity): Long
    
    /**
     * Insert tower only if it doesn't exist
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnoreTower(tower: CellTowerEntity): Long
    
    /**
     * Update tower observation timestamp and count
     */
    @Query("""
        UPDATE cell_towers 
        SET lastSeen = :timestamp, timesObserved = timesObserved + 1 
        WHERE cellId = :cellId AND lac = :lac AND mcc = :mcc AND mnc = :mnc
    """)
    suspend fun updateTowerSeen(cellId: Int, lac: Int, mcc: Int, mnc: Int, timestamp: Long)
    
    /**
     * Update trust score for a tower
     */
    @Query("UPDATE cell_towers SET trustScore = :score WHERE id = :id")
    suspend fun updateTrustScore(id: Long, score: Float)
    
    /**
     * Delete towers not seen since timestamp
     */
    @Query("DELETE FROM cell_towers WHERE lastSeen < :timestamp")
    suspend fun deleteStaleTowers(timestamp: Long): Int
    
    /**
     * Delete all towers
     */
    @Query("DELETE FROM cell_towers")
    suspend fun deleteAllTowers()
}