package com.sipdial.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.sipdial.app.sip.SipEngine
import com.sipdial.app.util.PowerPrefs

object KeepAliveScheduler {
    private const val TAG = "KeepAliveScheduler"
    private const val ACTION = "com.sipdial.app.action.KEEPALIVE"
    private const val REQUEST_CODE = 7001
    const val INTERVAL_MS = 30_000L

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context.applicationContext,
            REQUEST_CODE,
            Intent(context.applicationContext, KeepAliveReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun schedule(context: Context, delayMs: Long = INTERVAL_MS) {
        val am = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = SystemClock.elapsedRealtime() + delayMs
        val pi = pendingIntent(context)
        try {
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        } catch (e: Exception) {
            Log.w(TAG, "schedule failed: ${e.message}")
        }
    }

    fun cancel(context: Context) {
        try {
            val am = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pendingIntent(context))
        } catch (_: Exception) {
        }
    }
}

class KeepAliveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mode = PowerPrefs.mode(context)
        if (!SipForegroundService.isActive() && !KeepAliveState.stoppedByUser(context)) {
            SipForegroundService.start(context.applicationContext)
            KeepAliveScheduler.schedule(context)
            return
        }
        if (mode == PowerPrefs.RELIABLE) {
            KeepAliveScheduler.schedule(context)
            return
        }
        if (!SipForegroundService.isActive()) return
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SipDial:KeepAlive").apply {
                setReferenceCounted(false)
                acquire(8_000L)
            }
        } catch (_: Exception) {
        }
        try {
            SipEngine.getInstance(context.applicationContext).keepAliveTick()
        } catch (e: Exception) {
            Log.w("KeepAliveReceiver", "tick failed: ${e.message}")
        }
        KeepAliveScheduler.schedule(context)
    }
}
