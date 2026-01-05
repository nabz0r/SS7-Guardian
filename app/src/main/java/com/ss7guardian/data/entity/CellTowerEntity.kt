package com.ss7guardian.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cell_towers",
    indices = [Index(value = ["cellId", "lac", "mcc", "mnc"], unique = true)]
)
data class CellTowerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cellId: Int,
    val lac: Int,
    val mcc: Int,
    val mnc: Int,
    val networkType: String,
    val signalStrengthDbm: Int,
    val latitude: Double,
    val longitude: Double,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val timesObserved: Int = 1,
    val trustScore: Float = 0.5f
)