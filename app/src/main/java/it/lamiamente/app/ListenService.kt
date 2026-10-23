package it.lamiamente.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.util.Locale

/**
 * Resta in ascolto della frase "ehi mente" (anche a schermo spento),
 * poi registra quello che dici e lo salva come appunto.
 */
class ListenService : Service(), RecognitionListener {

    private enum class Mode { WAKE, DICTATION }

    private var model: Model? = null
    private var speech: SpeechService? = null
    private var mode = Mode.WAKE
    private val handler = Handler(Looper.getMainLooper())
    private val buffer = StringBuilder()
    private var tts: TextToSpeech? = null
    private var sr: SpeechRecognizer? = null
    private var ttsReady = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

    private val finishRunnable = Runnable { finishDictation() }
    private val restartRunnable = Runnable { startWake() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (started) return START_STICKY
        started = true

        createChannel()
        val notification = buildNotification("Carico l'ascolto…")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        NoteStore.setRunning(this, true)
        NoteStore.setStatus(this, "Carico il riconoscimento vocale…")

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mente:ascolto").apply { acquire() }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.ITALIAN
                ttsReady = true
            }
        }

        StorageService.unpack(
            this, "model-it", "model",
            { m ->
                model = m
                startWake()
            },
            { e ->
                setStatus("Errore nel caricare il modello: " + e.message)
            }
        )
        return START_STICKY
    }

    private fun startWake() {
        val m = model ?: return
        stopSpeech()
        mode = Mode.WAKE
        try {
            val rec = Recognizer(m, 16000.0f)
            speech = SpeechService(rec, 16000.0f).also { it.startListening(this) }
            setStatus("In ascolto: di' «ehi mente»")
        } catch (e: Exception) {
            setStatus("Errore microfono: " + e.message)
            handler.postDelayed(restartRunnable, 3000)
        }
    }

    private fun startDictation() {
        stopSpeech()
        mode = Mode.DICTATION
        buffer.setLength(0)
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            startVoskDictation()
            return
        }
        try {
            destroySr()
            val r = SpeechRecognizer.createSpeechRecognizer(this)
            r.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    beep()
                    setStatus("Ti ascolto… parla pure")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(partialResults: Bundle?) {
                    val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!t.isNullOrBlank()) NoteStore.setStatus(this@ListenService, "Sto scrivendo: " + t)
                }
                override fun onResults(results: Bundle?) {
                    val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!t.isNullOrBlank()) buffer.append(t)
                    finishDictation()
                }
                override fun onError(error: Int) {
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        finishDictation()
                    } else {
                        setStatus("Errore del riconoscimento ($error), provo l'altro metodo")
                        destroySr()
                        startVoskDictation()
                    }
                }
            })
            sr = r
            val i = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            r.startListening(i)
            handler.removeCallbacks(finishRunnable)
            handler.postDelayed(finishRunnable, 25000)
        } catch (e: Exception) {
            setStatus("Errore: " + e.message + ", provo l'altro metodo")
            destroySr()
            startVoskDictation()
        }
    }

    private fun destroySr() {
        sr?.let {
            try { it.cancel() } catch (e: Exception) { }
            try { it.destroy() } catch (e: Exception) { }
        }
        sr = null
    }

    private fun startVoskDictation() {
        val m = model ?: return
        stopSpeech()
        mode = Mode.DICTATION
        buffer.setLength(0)
        try {
            val rec = Recognizer(m, 16000.0f)
            speech = SpeechService(rec, 16000.0f).also { it.startListening(this) }
            beep()
            setStatus("Ti ascolto… parla pure")
            handler.removeCallbacks(finishRunnable)
            handler.postDelayed(finishRunnable, 7000)
        } catch (e: Exception) {
            setStatus("Errore microfono: " + e.message)
            handler.postDelayed(restartRunnable, 3000)
        }
    }

    private fun finishDictation() {
        handler.removeCallbacks(finishRunnable)
        stopSpeech()
        destroySr()
        val text = buffer.toString().trim()
        if (text.isNotEmpty()) {
            NoteStore.add(this, text)
            setStatus("Segnato: " + text)
            say("Segnato")
        } else {
            setStatus("Non ho sentito niente")
        }
        handler.postDelayed(restartRunnable, 1800)
    }

    private fun wakeDetected() {
        stopSpeech()
        handler.post { startDictation() }
    }

    // --- RecognitionListener ---

    override fun onPartialResult(hypothesis: String?) {
        val p = try { JSONObject(hypothesis ?: "{}").optString("partial") } catch (e: Exception) { "" }
        if (mode == Mode.WAKE) {
            if (p.isNotBlank()) NoteStore.setStatus(this, "Ho sentito: " + p)
            if (p.contains("mente")) wakeDetected()
        } else if (p.isNotBlank()) {
            handler.removeCallbacks(finishRunnable)
            handler.postDelayed(finishRunnable, 2500)
        }
    }

    override fun onResult(hypothesis: String?) {
        val t = try { JSONObject(hypothesis ?: "{}").optString("text") } catch (e: Exception) { "" }
        if (mode == Mode.WAKE) {
            if (t.isNotBlank()) NoteStore.setStatus(this, "Ho sentito: " + t)
            if (t.contains("mente")) wakeDetected()
        } else if (t.isNotBlank()) {
            if (buffer.isNotEmpty()) buffer.append(' ')
            buffer.append(t)
            handler.removeCallbacks(finishRunnable)
            handler.postDelayed(finishRunnable, 1800)
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        if (mode == Mode.DICTATION) {
            val t = try { JSONObject(hypothesis ?: "{}").optString("text") } catch (e: Exception) { "" }
            if (t.isNotBlank() && !buffer.toString().endsWith(t)) {
                if (buffer.isNotEmpty()) buffer.append(' ')
                buffer.append(t)
            }
        }
    }

    override fun onError(exception: Exception?) {
        setStatus("Errore: " + exception?.message)
        handler.postDelayed(restartRunnable, 3000)
    }

    override fun onTimeout() {
        if (mode == Mode.DICTATION) finishDictation() else startWake()
    }

    // --- utilità ---

    private fun stopSpeech() {
        speech?.let {
            try { it.stop() } catch (e: Exception) { }
            try { it.shutdown() } catch (e: Exception) { }
        }
        speech = null
    }

    private fun beep() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
            handler.postDelayed({ tg.release() }, 500)
        } catch (e: Exception) { }
    }

    private fun say(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mente")
    }

    private fun setStatus(s: String) {
        NoteStore.setStatus(this, s)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(s))
    }

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CHANNEL, "Ascolto della mente", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ListenService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("La mia mente")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Spegni", stop).build())
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopSpeech()
        destroySr()
        model?.close()
        tts?.shutdown()
        wakeLock?.let { if (it.isHeld) it.release() }
        NoteStore.setRunning(this, false)
        NoteStore.setStatus(this, "Spento")
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "it.lamiamente.app.STOP"
        private const val CHANNEL = "ascolto"
        private const val NOTIF_ID = 1
    }
}
