package com.ss7guardian.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ss7guardian.SS7GuardianApp
import com.ss7guardian.data.entity.SecurityEventEntity
import com.ss7guardian.monitor.CellMonitor
import com.ss7guardian.monitor.NetworkMonitor
import com.ss7guardian.monitor.SmsMonitor
import com.ss7guardian.ui.MainActivity
import kotlinx.coroutines.*
import java.util.concurrent.Executors

class GuardianService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var cellMonitor: CellMonitor
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var smsMonitor: SmsMonitor
    private var locationManager: LocationManager? = null
    private var monitoringJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        cellMonitor = CellMonitor(this)
        networkMonitor = NetworkMonitor(this)
        smsMonitor = SmsMonitor(this)
        setupCallbacks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        scope.cancel()
    }

    private fun startMonitoring() {
        startForeground()
        networkMonitor.startMonitoring(executor)
        smsMonitor.startMonitoring()
        startLocationUpdates()
        monitoringJob = scope.launch {
            while (isActive) { scanCells(); delay(10000) }
        }
        Log.d(TAG, "Monitoring started")
    }

    private fun stopMonitoring() {
        monitoringJob?.cancel()
        networkMonitor.stopMonitoring()
        smsMonitor.stopMonitoring()
        locationManager?.removeUpdates(locationListener)
    }

    private fun startForeground() {
        val intent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, SS7GuardianApp.CHANNEL_SERVICE)
            .setContentTitle("SS7 Guardian Active")
            .setContentText("Monitoring cellular network security")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setContentIntent(intent)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(1, notification)
    }

    private fun setupCallbacks() {
        cellMonitor.setCallback(object : CellMonitor.CellMonitorCallback {
            override fun onCellChanged(o: CellMonitor.CellObservation?, n: CellMonitor.CellObservation) {}
            override fun onUnknownTowerDetected(cell: CellMonitor.CellObservation) {
                scope.launch { saveEvent(SecurityEventEntity.EventType.UNKNOWN_CELL_TOWER, 2,
                    "Unknown Cell Tower", "Connected to unknown tower CID:${cell.cellId}") }
            }
            override fun onSignalAnomaly(cell: CellMonitor.CellObservation, p: Int, c: Int) {
                scope.launch { saveEvent(SecurityEventEntity.EventType.SIGNAL_ANOMALY, 1,
                    "Signal Anomaly", "Signal jumped from ${p}dBm to ${c}dBm") }
            }
            override fun onRapidCellChanges(count: Int, window: Long) {
                scope.launch { saveEvent(SecurityEventEntity.EventType.RAPID_CELL_CHANGE, 2,
                    "Rapid Cell Changes", "$count changes in ${window/1000}s") }
            }
        })

        networkMonitor.setCallback(object : NetworkMonitor.NetworkMonitorCallback {
            override fun onNetworkTypeChanged(o: NetworkMonitor.NetworkType, n: NetworkMonitor.NetworkType) {}
            override fun on2GDowngradeDetected(from: NetworkMonitor.NetworkType) {
                scope.launch { saveEvent(SecurityEventEntity.EventType.NETWORK_DOWNGRADE_2G, 3,
                    "2G Downgrade Detected", "Network downgraded from ${from.displayName} to 2G") }
            }
        })

        smsMonitor.setCallback(object : SmsMonitor.SmsMonitorCallback {
            override fun onSuspiciousSmsDetected(sms: SmsMonitor.SuspiciousSms) {
                val type = when (sms.type) {
                    SmsMonitor.SmsType.CLASS_0_FLASH -> SecurityEventEntity.EventType.SILENT_SMS_CLASS0
                    SmsMonitor.SmsType.TYPE_0_SILENT -> SecurityEventEntity.EventType.SILENT_SMS_TYPE0
                    SmsMonitor.SmsType.WAP_PUSH -> SecurityEventEntity.EventType.WAP_PUSH
                    else -> SecurityEventEntity.EventType.OTHER
                }
                scope.launch { saveEvent(type, 2, "Suspicious SMS", "${sms.type} detected") }
            }
        })
    }

    private suspend fun saveEvent(type: SecurityEventEntity.EventType, level: Int, title: String, desc: String) {
        SS7GuardianApp.instance.database.securityEventDao().insertEvent(
            SecurityEventEntity(eventType = type, threatLevel = level, title = title, description = desc)
        )
        if (level >= 2) showAlert(title, desc)
    }

    private fun showAlert(title: String, desc: String) {
        val notification = NotificationCompat.Builder(this, SS7GuardianApp.CHANNEL_ALERTS)
            .setContentTitle(title).setContentText(desc)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true).build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun scanCells() {
        cellMonitor.getCurrentCellInfo()?.filter { it.isRegistered }?.forEach { info ->
            cellMonitor.parseCellInfo(info)?.let { obs ->
                scope.launch {
                    val db = SS7GuardianApp.instance.database
                    val known = db.cellTowerDao().isTowerKnown(obs.cellId, obs.lac, obs.mcc, obs.mnc) > 0
                    cellMonitor.analyzeChange(cellMonitor.cellHistory.value.firstOrNull(), obs, known)
                    cellMonitor.recordObservation(obs)
                    if (known) db.cellTowerDao().updateTowerSeen(obs.cellId, obs.lac, obs.mcc, obs.mnc, System.currentTimeMillis())
                    else locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let {
                        db.cellTowerDao().insertTower(cellMonitor.toEntity(obs, it))
                    }
                }
            }
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(loc: Location) { cellMonitor.updateLocation(loc) }
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        try { locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000, 100f, locationListener) }
        catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "GuardianService"
        const val ACTION_START = "com.ss7guardian.START"
        const val ACTION_STOP = "com.ss7guardian.STOP"

        fun start(ctx: Context) {
            val intent = Intent(ctx, GuardianService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
            else ctx.startService(intent)
        }
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, GuardianService::class.java))
    }
}