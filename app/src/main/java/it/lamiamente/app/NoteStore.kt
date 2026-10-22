package it.lamiamente.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Appunti salvati sul telefono (per ora solo in locale). */
object NoteStore {
    private const val PREFS = "mente"
    private const val KEY = "notes"

    fun add(ctx: Context, text: String) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        val o = JSONObject()
        o.put("t", System.currentTimeMillis())
        o.put("text", text)
        arr.put(o)
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    /** Dal più recente al più vecchio, già formattati. */
    fun list(ctx: Context): List<String> {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.ITALY)
        val out = ArrayList<String>()
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.getJSONObject(i)
            out.add(fmt.format(Date(o.getLong("t"))) + "  ·  " + o.getString("text"))
        }
        return out
    }

    fun setRunning(ctx: Context, running: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("running", running).apply()
    }

    fun isRunning(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("running", false)

    fun setStatus(ctx: Context, status: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("status", status).apply()
    }

    fun status(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("status", "Spento") ?: "Spento"
}
