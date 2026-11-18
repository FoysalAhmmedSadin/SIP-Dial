package com.sipdial.app.telecom.sim

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.sipdial.app.R
import com.sipdial.app.util.RecordingStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SimRecordService : Service() {

    companion object {
        private const val TAG = "SimRecordService"
        const val EXTRA_LABEL = "label"
        private const val CHANNEL = "sipdial_sim_record"
        private const val NOTIF_ID = 7102

        fun start(context: Context, label: String, silent: Boolean = false) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                if (!silent) {
                    Toast.makeText(context, "Microphone permission is needed to record", Toast.LENGTH_LONG).show()
                }
                return
            }
            try {
                val i = Intent(context, SimRecordService::class.java).putExtra(EXTRA_LABEL, label)
                ContextCompat.startForegroundService(context, i)
            } catch (e: Exception) {
                Log.w(TAG, "could not start recording service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SimRecordService::class.java))
        }
    }

    private var recorder: MediaRecorder? = null
    private var tempFile: File? = null
    private var label = "call"
    private val main = Handler(Looper.getMainLooper())

    private val sources = listOf(
        MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION",
        MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
        MediaRecorder.AudioSource.MIC to "MIC",
        MediaRecorder.AudioSource.UNPROCESSED to "UNPROCESSED"
    )
    private var sourceIndex = 0
    private var heardSound = false
    private var sampleTicks = 0

    private val sampler = object : Runnable {
        override fun run() {
            val r = recorder ?: return
            val amp = try { r.maxAmplitude } catch (_: Exception) { 0 }
            if (amp > 300) heardSound = true
            sampleTicks++
            if (!heardSound && sampleTicks >= 6 && sourceIndex < sources.size - 1) {
                switchToNextSource()
            } else {
                main.postDelayed(this, 500)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        label = intent?.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { "call" }
        startAsForeground()
        if (recorder == null && !startRecorder()) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "SIM call recording", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val n = b.setSmallIcon(R.drawable.ic_phone)
            .setContentTitle("Recording SIM call")
            .setContentText("Microphone capture active")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun startRecorder(): Boolean {
        val ok = startRecorderWith(sources[sourceIndex].first)
        if (ok) {
            heardSound = false
            sampleTicks = 0
            main.postDelayed(sampler, 500)
        }
        return ok
    }

    private fun switchToNextSource() {
        val r = recorder
        recorder = null
        try { r?.stop() } catch (_: RuntimeException) {}
        try { r?.release() } catch (_: Exception) {}
        tempFile?.delete()
        tempFile = null
        sourceIndex++
        if (!startRecorderWith(sources[sourceIndex].first)) {
            stopSelf()
            return
        }
        heardSound = false
        sampleTicks = 0
        main.postDelayed(sampler, 500)
    }

    private fun startRecorderWith(audioSource: Int): Boolean {
        return try {
            val file = File(cacheDir, "sim_rec_${System.currentTimeMillis()}.m4a")
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.setAudioSource(audioSource)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            tempFile = file
            SimCallManager.recording.value = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            false
        }
    }

    override fun onDestroy() {
        finishRecording()
        SimCallManager.recording.value = false
        super.onDestroy()
    }

    private fun finishRecording() {
        main.removeCallbacks(sampler)
        val r = recorder ?: return
        recorder = null
        val file = tempFile
        tempFile = null
        var valid = true
        try { r.stop() } catch (_: RuntimeException) { valid = false }
        try { r.release() } catch (_: Exception) {}
        if (file == null) return
        if (!valid || file.length() < 2048) {
            file.delete()
            return
        }
        val digits = label.filter { it.isDigit() || it == '+' }.ifBlank { "call" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val srcName = sources[sourceIndex].second
        val name = "SIM_${digits}_${stamp}_$srcName.m4a"
        val where = export(file, name)
        main.post {
            Toast.makeText(
                applicationContext,
                if (where != null) "SIM call saved to $where" else "Could not save recording",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun export(src: File, name: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    "Recordings/${RecordingStorage.FOLDER}"
                } else {
                    "Music/${RecordingStorage.FOLDER}"
                }
                val resolver = applicationContext.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, dir)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                try {
                    resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
                        ?: throw IllegalStateException("no output stream")
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                } catch (e: Exception) {
                    try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                    throw e
                }
                src.delete()
                dir
            } else {
                val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return null
                src.copyTo(File(dir, name), overwrite = true)
                src.delete()
                dir.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "export failed", e)
            null
        }
    }
}
