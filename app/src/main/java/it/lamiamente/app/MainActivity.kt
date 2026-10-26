package it.lamiamente.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var toggle: Button
    private lateinit var notes: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1500)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(36), dp(20), dp(20))
            setBackgroundColor(Color.parseColor("#F4F1FB"))
        }
        val title = TextView(this).apply {
            text = "La mia mente"
            textSize = 28f
            setTextColor(Color.parseColor("#3B3F95"))
        }
        status = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#444444"))
            setPadding(0, dp(8), 0, dp(12))
        }
        toggle = Button(this).apply {
            textSize = 18f
            setOnClickListener { onToggle() }
        }
        val battery = Button(this).apply {
            text = "Consenti l'ascolto in background"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        val map = Button(this).apply {
            text = "Apri la mappa e la chat"
            setOnClickListener { startActivity(Intent(this@MainActivity, WebActivity::class.java)) }
        }
        val browser = Button(this).apply {
            text = "Apri nel browser del telefono"
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SITE_URL))) }
        }
        val hint = TextView(this).apply {
            text = "Per farla funzionare a telefono bloccato, tocca il pulsante qui sopra e metti \"La mia mente\" tra le app senza limiti di batteria."
            textSize = 13f
            setTextColor(Color.parseColor("#777777"))
            setPadding(0, dp(6), 0, dp(14))
        }
        val head = TextView(this).apply {
            text = "I tuoi appunti"
            textSize = 18f
            setTextColor(Color.parseColor("#3B3F95"))
            gravity = Gravity.START
            setPadding(0, dp(4), 0, dp(6))
        }
        notes = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#222222"))
        }
        val scroll = ScrollView(this).apply { addView(notes) }

        root.addView(title)
        root.addView(status)
        root.addView(toggle)
        root.addView(map)
        root.addView(browser)
        root.addView(battery)
        root.addView(hint)
        root.addView(head)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun onToggle() {
        if (NoteStore.isRunning(this)) {
            startService(Intent(this, ListenService::class.java).setAction(ListenService.ACTION_STOP))
            return
        }
        val need = ArrayList<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (need.isNotEmpty()) {
            requestPermissions(need.toTypedArray(), 1)
        } else {
            startListening()
        }
    }

    private fun startListening() {
        startForegroundService(Intent(this, ListenService::class.java))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening()
        }
    }

    private fun render() {
        val running = NoteStore.isRunning(this)
        toggle.text = if (running) "Spegni l'ascolto" else "Accendi l'ascolto"
        status.text = NoteStore.status(this)
        val list = NoteStore.list(this)
        notes.text = if (list.isEmpty()) "Ancora nessun appunto. Di' «ehi mente» e poi detta." else list.joinToString("\n\n")
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    companion object {
        const val SITE_URL = "https://claude.ai/artifact/Chs9ns9c5KXfSdFTnbv9De"
    }
}
