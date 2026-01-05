package com.ss7guardian.data.dao

import androidx.room.*
import com.ss7guardian.data.entity.CellTowerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CellTowerDao {
    @Query("SELECT * FROM cell_towers ORDER BY lastSeen DESC")
    fun getAllTowers(): Flow<List<CellTowerEntity>>

    @Query("SELECT COUNT(*) FROM cell_towers WHERE cellId = :cellId AND lac = :lac AND mcc = :mcc AND mnc = :mnc")
    suspend fun isTowerKnown(cellId: Int, lac: Int, mcc: Int, mnc: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTower(tower: CellTowerEntity): Long

    @Query("UPDATE cell_towers SET lastSeen = :timestamp, timesObserved = timesObserved + 1 WHERE cellId = :cellId AND lac = :lac AND mcc = :mcc AND mnc = :mnc")
    suspend fun updateTowerSeen(cellId: Int, lac: Int, mcc: Int, mnc: Int, timestamp: Long)
}