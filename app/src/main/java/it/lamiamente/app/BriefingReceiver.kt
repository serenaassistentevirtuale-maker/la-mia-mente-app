package it.lamiamente.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Mostra la notifica "Il briefing di Alba è pronto" e programma la prossima. */
class BriefingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("briefing", "Briefing di Alba", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context, 101,
            Intent(Intent.ACTION_VIEW, Uri.parse(MainActivity.SITE_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(context, "briefing")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Buongiorno da Alba")
            .setContentText("Il briefing di oggi è pronto: tocca per aprire la tua mente.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(200, n)
        BriefingScheduler.scheduleNext(context)
    }
}
