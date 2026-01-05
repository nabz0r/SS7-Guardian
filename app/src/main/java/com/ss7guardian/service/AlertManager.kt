package com.ss7guardian.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ss7guardian.R
import com.ss7guardian.SS7GuardianApp
import com.ss7guardian.data.entity.SecurityEventEntity
import com.ss7guardian.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages security alerts and notifications
 */
class AlertManager(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val database = SS7GuardianApp.instance.database

    private var alertIdCounter = 100

    /**
     * Raise a security alert
     */
    suspend fun raiseAlert(event: SecurityEventEntity) {
        withContext(Dispatchers.IO) {
            // Save to database
            val eventId = database.securityEventDao().insertEvent(event)
            Log.d(TAG, "Alert raised: ${event.eventType} (id=$eventId)")

            // Show notification if threat level is high enough
            if (event.threatLevel >= 2) {
                showNotification(event.copy(id = eventId))
                database.securityEventDao().markNotified(eventId)
            }
        }
    }

    private fun showNotification(event: SecurityEventEntity) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_EVENT_ID, event.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            event.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val color = when (event.threatLevel) {
            0 -> context.getColor(R.color.status_safe)
            1 -> context.getColor(R.color.status_low)
            2 -> context.getColor(R.color.status_medium)
            3 -> context.getColor(R.color.status_high)
            else -> context.getColor(R.color.status_critical)
        }

        val notification = NotificationCompat.Builder(context, SS7GuardianApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(event.title)
            .setContentText(event.description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(event.description))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setColor(color)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(alertIdCounter++, notification)
    }

    /**
     * Get threat level summary
     */
    suspend fun getCurrentThreatLevel(): ThreatSummary {
        return withContext(Dispatchers.IO) {
            val oneHourAgo = System.currentTimeMillis() - 3600_000
            val recentEvents = database.securityEventDao().getRecentEvents(oneHourAgo)

            val maxThreatLevel = recentEvents.maxOfOrNull { it.threatLevel } ?: 0
            val eventCount = recentEvents.size
            val undismissedCount = recentEvents.count { !it.dismissed }

            ThreatSummary(
                level = maxThreatLevel,
                recentEventCount = eventCount,
                activeAlertCount = undismissedCount
            )
        }
    }

    data class ThreatSummary(
        val level: Int,
        val recentEventCount: Int,
        val activeAlertCount: Int
    ) {
        val levelName: String
            get() = when (level) {
                0 -> "Safe"
                1 -> "Low"
                2 -> "Medium"
                3 -> "High"
                else -> "Critical"
            }
    }

    companion object {
        private const val TAG = "AlertManager"
        const val EXTRA_EVENT_ID = "event_id"
    }
}
