package com.ss7guardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ss7guardian.service.GuardianService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            GuardianService.start(context)
        }
    }
}