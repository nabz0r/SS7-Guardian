package com.ss7guardian.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "security_events")
data class SecurityEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: EventType,
    val threatLevel: Int,
    val title: String,
    val description: String,
    val cellId: Int? = null,
    val lac: Int? = null,
    val networkType: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val notified: Boolean = false,
    val dismissed: Boolean = false
) {
    enum class EventType {
        NETWORK_DOWNGRADE_2G, UNKNOWN_CELL_TOWER, SILENT_SMS_CLASS0,
        SILENT_SMS_TYPE0, WAP_PUSH, SIGNAL_ANOMALY, RAPID_CELL_CHANGE, OTHER
    }
}