package it.lamiamente.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Dopo il riavvio del telefono riprogramma la notifica del mattino. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) BriefingScheduler.scheduleNext(context)
    }
}
