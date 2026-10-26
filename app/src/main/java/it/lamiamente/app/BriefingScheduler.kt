package it.lamiamente.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar
import java.util.TimeZone

/** Programma la notifica del mattino: dal lunedì al sabato alle 7:15. */
object BriefingScheduler {
    fun scheduleNext(ctx: Context) {
        val c = Calendar.getInstance(TimeZone.getTimeZone("Europe/Rome"))
        c.set(Calendar.HOUR_OF_DAY, 7)
        c.set(Calendar.MINUTE, 15)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        while (c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) c.add(Calendar.DAY_OF_YEAR, 1)

        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, 100, Intent(ctx, BriefingReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pi)
    }
}
