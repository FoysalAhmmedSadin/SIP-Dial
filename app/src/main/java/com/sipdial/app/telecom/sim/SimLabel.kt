package com.sipdial.app.telecom.sim

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.Call
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.sipdial.app.telecom.TelecomHelper

fun simSlotLabel(context: Context, call: Call): String {
    return try {
        val handle = call.details?.accountHandle ?: return "SIM"
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val subId = handle.id?.toIntOrNull()
            if (subId != null) {
                val sm = context.getSystemService(SubscriptionManager::class.java)
                val info = sm?.getActiveSubscriptionInfo(subId)
                if (info != null && info.simSlotIndex >= 0) return "SIM ${info.simSlotIndex + 1}"
            }
            TelecomHelper.getAvailableSimAccounts(context)
                .firstOrNull { it.handle == handle }
                ?.let { return "SIM ${it.slotIndex}" }
        }
        "SIM"
    } catch (_: Exception) {
        "SIM"
    }
}
