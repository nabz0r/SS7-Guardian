package com.ss7guardian

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.ss7guardian.data.AppDatabase

class SS7GuardianApp : Application() {
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE, "Guardian Service", NotificationManager.IMPORTANCE_LOW
            )
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS, "Security Alerts", NotificationManager.IMPORTANCE_HIGH
            ).apply { enableVibration(true) }
            
            manager.createNotificationChannels(listOf(serviceChannel, alertsChannel))
        }
    }

    companion object {
        const val CHANNEL_SERVICE = "ss7_guardian_service"
        const val CHANNEL_ALERTS = "ss7_guardian_alerts"
        lateinit var instance: SS7GuardianApp
            private set
    }
}