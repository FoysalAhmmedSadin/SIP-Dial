package com.sipdial.app.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.sipdial.app.CHANNEL_CALL_ID
import com.sipdial.app.IncomingCallActivity
import com.sipdial.app.MainActivity
import com.sipdial.app.R
import com.sipdial.app.receiver.CallActionReceiver
import com.sipdial.app.sip.SipEngine

object NotificationHelper {

    private const val INCOMING_CALL_NOTIFICATION_ID = 2001
    private const val ONGOING_CALL_NOTIFICATION_ID = 2002

    private fun displayNumber(callerAddress: String): String =
        CallerIdFormatter.extractNumber(callerAddress)

    private fun actionIntent(context: Context, action: String, callerAddress: String, requestCode: Int): PendingIntent {
        val i = Intent(context, CallActionReceiver::class.java).apply {
            this.action = action
            putExtra("caller_address", callerAddress)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun accountNotificationId(accountId: String) = 3000 + (accountId.hashCode() and 0xFFFF)

    fun showAccountProblem(context: Context, accountId: String, accountName: String, detail: String) {
        if (!NotificationPrefs.isRegistrationAlertsEnabled(context)) return
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pi = PendingIntent.getActivity(
                context, accountNotificationId(accountId), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(context, CHANNEL_CALL_ID)
                .setContentTitle("SIP account not connected")
                .setContentText("$accountName: $detail")
                .setStyle(NotificationCompat.BigTextStyle().bigText("$accountName: $detail\nYou may miss incoming calls on this account."))
                .setSmallIcon(R.drawable.ic_phone)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setSilent(true)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(accountNotificationId(accountId), n)
        } catch (_: Exception) {
        }
    }

    fun clearAccountProblem(context: Context, accountId: String) {
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(accountNotificationId(accountId))
        } catch (_: Exception) {
        }
    }

    private fun action(icon: Int, title: String, pi: PendingIntent): NotificationCompat.Action =
        NotificationCompat.Action.Builder(icon, title, pi)
            .setAuthenticationRequired(false)
            .build()

    fun showIncomingCallNotification(context: Context, callerAddress: String) {
        val intent = IncomingCallActivity.createLaunchIntent(
            context = context,
            callerAddress = callerAddress,
            incoming = true
        )

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val answerPi = actionIntent(context, CallActionReceiver.ACTION_ANSWER, callerAddress, 11)
        val declinePi = actionIntent(context, CallActionReceiver.ACTION_DECLINE, callerAddress, 12)

        val engine = SipEngine.getInstance(context)
        val accountLabel = engine.callAccountName.value?.takeIf { it.isNotBlank() }
            ?: engine.callAccountUser.value?.takeIf { it.isNotBlank() }
        val builder = NotificationCompat.Builder(context, CHANNEL_CALL_ID)
            .setContentTitle(context.getString(R.string.incoming_call))
            .setContentText(
                displayNumber(callerAddress) +
                    (accountLabel?.let { " • via $it" } ?: "")
            )
            .setSmallIcon(R.drawable.ic_phone)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .addAction(action(R.drawable.ic_call_end, "Decline", declinePi))
            .addAction(action(R.drawable.ic_phone, "Answer", answerPi))

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(INCOMING_CALL_NOTIFICATION_ID, builder.build())
    }

    fun showOngoingCallNotification(context: Context, callerAddress: String) {
        val intent = IncomingCallActivity.createLaunchIntent(
            context = context,
            callerAddress = callerAddress,
            incoming = false
        )
        val contentPi = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val hangupPi = actionIntent(context, CallActionReceiver.ACTION_HANGUP, callerAddress, 13)

        val builder = NotificationCompat.Builder(context, CHANNEL_CALL_ID)
            .setContentTitle("Call in progress")
            .setContentText(displayNumber(callerAddress))
            .setSmallIcon(R.drawable.ic_phone)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setContentIntent(contentPi)
            .addAction(action(R.drawable.ic_call_end, "Hang up", hangupPi))

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ONGOING_CALL_NOTIFICATION_ID, builder.build())
    }

    fun cancelIncomingCallNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(INCOMING_CALL_NOTIFICATION_ID)
    }

    fun cancelOngoingCallNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ONGOING_CALL_NOTIFICATION_ID)
    }
}
