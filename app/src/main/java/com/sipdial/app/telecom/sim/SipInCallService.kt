package com.sipdial.app.telecom.sim

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import com.sipdial.app.R
import com.sipdial.app.util.CallerIdFormatter

class SipInCallService : InCallService() {

    companion object {
        private const val TAG = "SipInCallService"
        private const val CHANNEL_ID = "sipdial_sim_call"
        private const val CHANNEL_QUIET_ID = "sipdial_sim_call_quiet"
        private const val NOTIF_ID = 7101
    }

    override fun onCreate() {
        super.onCreate()
        SimCallManager.service = this
        SimCallBar.attach(this)
    }

    override fun onDestroy() {
        SimCallManager.service = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val dialed = call.details?.handle?.schemeSpecificPart.orEmpty()
        if (DialCodes.isCode(dialed)) return
        SimCallManager.add(call)
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state != Call.STATE_RINGING) cancelRingingNotification()
            }
        })
        if (call.compatState() == Call.STATE_RINGING) {
            showIncomingNotification()
        } else {
            openCallScreen()
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        SimCallManager.remove(call)
        cancelRingingNotification()
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        SimCallManager.audioState.value = audioState
    }

    private fun openCallScreen() {
        try {
            val i = Intent(this, SimCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(i)
        } catch (e: Exception) {
            Log.w(TAG, "Could not open call screen", e)
        }
    }

    private fun showIncomingNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val locked = km?.isKeyguardLocked == true || pm?.isInteractive == false
        val overlayCard = !locked && Settings.canDrawOverlays(this)
        val channelId = if (overlayCard) CHANNEL_QUIET_ID else CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "SIM incoming calls", NotificationManager.IMPORTANCE_HIGH)
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_QUIET_ID, "SIM incoming calls (pop-up)", NotificationManager.IMPORTANCE_LOW)
            )
        }
        fun pi(code: Int, action: String?): PendingIntent {
            val i = Intent(this, SimCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (action != null) putExtra(SimCallActivity.EXTRA_ACTION, action)
            }
            return PendingIntent.getActivity(
                this, code, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val who = SimCallManager.ringingCall()?.let { callTitle(it) } ?: "Unknown"
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        b.setSmallIcon(R.drawable.ic_phone)
            .setContentTitle("Incoming SIM call")
            .setContentText(who)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(pi(1, null))
            .addAction(Notification.Action.Builder(null, "Decline", pi(3, SimCallActivity.ACTION_DECLINE)).build())
            .addAction(Notification.Action.Builder(null, "Answer", pi(4, SimCallActivity.ACTION_ANSWER)).build())
        if (!overlayCard) b.setFullScreenIntent(pi(2, null), true)
        try {
            nm.notify(NOTIF_ID, b.build())
        } catch (e: Exception) {
            Log.w(TAG, "notify failed", e)
            openCallScreen()
        }
    }

    private fun cancelRingingNotification() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
    }
}

fun callTitle(call: Call): String {
    val d = call.details
    val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) d?.contactDisplayName else null
    if (!name.isNullOrBlank()) return name
    val cnap = d?.callerDisplayName
    if (!cnap.isNullOrBlank()) return cnap
    val num = d?.handle?.schemeSpecificPart?.let { CallerIdFormatter.extractNumber(it) }
    return if (num.isNullOrBlank()) "Unknown" else num
}
