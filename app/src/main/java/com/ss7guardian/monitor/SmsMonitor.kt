package com.ss7guardian.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SmsMonitor(private val context: Context) {
    private val _events = MutableStateFlow<List<SuspiciousSms>>(emptyList())
    val suspiciousSmsEvents: StateFlow<List<SuspiciousSms>> = _events.asStateFlow()
    private var callback: SmsMonitorCallback? = null
    private var receiver: BroadcastReceiver? = null
    private var registered = false

    data class SuspiciousSms(
        val type: SmsType, val from: String?, val body: String?,
        val messageClass: Int, val protocolId: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class SmsType { CLASS_0_FLASH, BINARY, WAP_PUSH, TYPE_0_SILENT, OTHER }

    interface SmsMonitorCallback { fun onSuspiciousSmsDetected(sms: SuspiciousSms) }

    fun setCallback(cb: SmsMonitorCallback) { callback = cb }

    fun startMonitoring() {
        if (registered) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> handleSms(intent)
                    Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION -> handleWap(intent)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
            addAction(Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION)
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else context.registerReceiver(receiver, filter)
        registered = true
    }

    fun stopMonitoring() {
        if (!registered) return
        receiver?.let { try { context.unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null; registered = false
    }

    private fun handleSms(intent: Intent) {
        Telephony.Sms.Intents.getMessagesFromIntent(intent).forEach { analyzeSms(it) }
    }

    private fun analyzeSms(sms: SmsMessage) {
        val cls = getMessageClass(sms)
        val pid = sms.protocolIdentifier
        Log.d(TAG, "SMS Class:$cls PID:$pid")
        
        if (cls == 0) {
            Log.w(TAG, "CLASS 0 SMS DETECTED")
            record(SuspiciousSms(SmsType.CLASS_0_FLASH, sms.originatingAddress, sms.messageBody, cls, pid))
        }
        if (pid == 0x40) {
            Log.w(TAG, "TYPE 0 SILENT SMS")
            record(SuspiciousSms(SmsType.TYPE_0_SILENT, sms.originatingAddress, null, cls, pid))
        }
        if (sms.messageBody == null && sms.userData != null) {
            Log.w(TAG, "BINARY SMS")
            record(SuspiciousSms(SmsType.BINARY, sms.originatingAddress, null, cls, pid))
        }
    }

    private fun handleWap(intent: Intent) {
        Log.w(TAG, "WAP PUSH")
        record(SuspiciousSms(SmsType.WAP_PUSH, intent.getStringExtra("address"), null, -1, -1))
    }

    private fun getMessageClass(sms: SmsMessage) = try {
        when (SmsMessage::class.java.getMethod("getMessageClass").invoke(sms).toString()) {
            "CLASS_0" -> 0; "CLASS_1" -> 1; "CLASS_2" -> 2; "CLASS_3" -> 3; else -> -1
        }
    } catch (_: Exception) { -1 }

    private fun record(sms: SuspiciousSms) {
        _events.value = listOf(sms) + _events.value.take(99)
        callback?.onSuspiciousSmsDetected(sms)
    }

    companion object { private const val TAG = "SmsMonitor" }
}