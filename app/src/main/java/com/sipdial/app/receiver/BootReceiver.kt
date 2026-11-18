package com.sipdial.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sipdial.app.data.AccountRepository
import com.sipdial.app.service.KeepAliveState
import com.sipdial.app.service.SipForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.i(TAG, "Boot/update (${intent.action}), checking account")

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = AccountRepository(context)
                    val account = repository.getAccount().first()
                    if (account != null && account.enabled && account.autoStart && !KeepAliveState.stoppedByUser(context)) {
                        SipForegroundService.start(context)
                        Log.i(TAG, "SIP service auto-started")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to auto-start SIP service", e)
                }
            }
        }
    }
}
