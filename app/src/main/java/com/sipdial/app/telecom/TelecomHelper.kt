package com.sipdial.app.telecom

import android.Manifest
import android.app.KeyguardManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.sipdial.app.IncomingCallActivity
import com.sipdial.app.MainActivity
import com.sipdial.app.sip.SipEngine
import com.sipdial.app.util.NotificationHelper
import com.sipdial.app.util.NotificationPrefs
import kotlinx.coroutines.flow.MutableStateFlow

data class SimAccountInfo(
    val handle: PhoneAccountHandle,
    val label: String,
    val subLabel: String,
    val slotIndex: Int
)

object TelecomHelper {

    private const val TAG = "TelecomHelper"
    private const val PHONE_ACCOUNT_ID = "sip_dial_account"
    private const val PREFS_NAME = "telecom_prefs"
    private const val KEY_ROUTING_MODE = "native_call_routing_mode"
    private const val KEY_AUTO_RECORD = "auto_call_record_enabled"

    const val MODE_ASK_EVERY_TIME = 0
    const val MODE_ALWAYS_SIP = 1
    const val MODE_SIM_ONLY = 2

    val defaultDialerHeld = MutableStateFlow(false)

    fun getPhoneAccountHandle(context: Context): PhoneAccountHandle {
        val componentName = ComponentName(context.applicationContext, SipConnectionService::class.java)
        return PhoneAccountHandle(componentName, PHONE_ACCOUNT_ID)
    }

    fun registerPhoneAccount(context: Context) {
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecomManager.unregisterPhoneAccount(getPhoneAccountHandle(context))
        } catch (_: Exception) {
        }
    }

    fun getAvailableSimAccounts(context: Context): List<SimAccountInfo> {
        val result = mutableListOf<SimAccountInfo>()
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return result
            }
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val ourHandle = getPhoneAccountHandle(context)
            val handles = telecomManager.callCapablePhoneAccounts

            val subCarriers = mutableMapOf<Int, String>()
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                @Suppress("DEPRECATION")
                subManager?.activeSubscriptionInfoList?.forEach { info ->
                    val slot = info.simSlotIndex + 1
                    val carrier = info.displayName?.toString()?.takeIf { it.isNotBlank() }
                        ?: info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                    if (!carrier.isNullOrBlank()) {
                        subCarriers[slot] = carrier
                    }
                }
            } catch (_: Exception) {
            }

            var index = 1
            for (handle in handles) {
                if (handle == ourHandle || handle.componentName.packageName == context.packageName) {
                    continue
                }
                val account = telecomManager.getPhoneAccount(handle)
                val rawLabel = account?.label?.toString()?.takeIf { it.isNotBlank() }
                    ?: subCarriers[index]
                    ?: "SIM $index"
                val desc = account?.shortDescription?.toString()?.takeIf { it.isNotBlank() && it != rawLabel }
                    ?: account?.address?.schemeSpecificPart?.takeIf { it.isNotBlank() }
                    ?: "Mobile Network • Slot $index"

                val formattedTitle = if (rawLabel.startsWith("SIM", ignoreCase = true)) {
                    rawLabel
                } else {
                    "SIM $index — $rawLabel"
                }

                result.add(
                    SimAccountInfo(
                        handle = handle,
                        label = formattedTitle,
                        subLabel = desc,
                        slotIndex = index
                    )
                )
                index++
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query SIM accounts", e)
        }
        return result
    }

    fun isPhoneAccountEnabled(context: Context): Boolean {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val account = telecomManager.getPhoneAccount(getPhoneAccountHandle(context))
            account?.isEnabled == true
        } catch (_: Exception) {
            false
        }
    }

    fun isCallRedirectionRoleHeld(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = context.getSystemService(RoleManager::class.java)
                roleManager?.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION) == true
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }
    }

    fun refreshDefaultDialer(context: Context): Boolean {
        val held = isDefaultDialerRoleHeld(context)
        defaultDialerHeld.value = held
        return held
    }

    fun isDefaultDialerRoleHeld(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
            } else {
                val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                tm.defaultDialerPackage == context.packageName
            }
        } catch (_: Exception) {
            false
        }
    }

    fun createDefaultDialerRoleIntent(context: Context): Intent? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                } else {
                    null
                }
            } else {
                @Suppress("DEPRECATION")
                Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create dialer role request intent", e)
            null
        }
    }

    fun createCallRedirectionRoleIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_REDIRECTION)) {
                    return roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create role request intent", e)
            }
        }
        return null
    }

    fun getNativeCallRoutingMode(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_ROUTING_MODE, MODE_ALWAYS_SIP)
    }

    fun setNativeCallRoutingMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_ROUTING_MODE, mode).apply()
    }

    fun isAutoRecordEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_RECORD, false)
    }

    fun setAutoRecordEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_RECORD, enabled).apply()
    }

    fun hasOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun createOverlayPermissionIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )

    fun openPhoneAccountSettings(context: Context) {
        registerPhoneAccount(context)
        val intents = listOf(
            Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS),
            Intent(TelecomManager.ACTION_CONFIGURE_PHONE_ACCOUNT).apply {
                putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, getPhoneAccountHandle(context))
            }
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        Toast.makeText(
            context,
            "Calling Accounts menu is not accessible on this device.",
            Toast.LENGTH_LONG
        ).show()
    }

    fun placeSimCallWithHandle(context: Context, number: String, simHandle: PhoneAccountHandle?) {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(context, "Phone permission required for SIM calls", Toast.LENGTH_SHORT).show()
                return
            }
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null)
            val extras = Bundle().apply {
                if (simHandle != null) {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, simHandle)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                SipCallRedirectionService.bypassNextNumber = number
                SipCallRedirectionService.bypassUntilMs = System.currentTimeMillis() + 8000L
            }
            telecomManager.placeCall(uri, extras)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to place SIM call with handle", e)
        }
    }

    fun reportIncomingCall(context: Context, callerAddress: String) {
        NotificationHelper.showIncomingCallNotification(context, callerAddress)
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val locked = km?.isKeyguardLocked == true || pm?.isInteractive == false
        val showBanner = NotificationPrefs.isIncomingBannerEnabled(context)
        if (!locked && showBanner && NativeDialerOverlay.canShowOverlay(context)) {
            NativeDialerOverlay.showIncomingCallOverlay(context, callerAddress)
        } else {
            IncomingUiRequest.request()
            try {
                val callIntent = IncomingCallActivity.createLaunchIntent(
                    context = context,
                    callerAddress = callerAddress,
                    incoming = true
                )
                context.startActivity(callIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not auto-launch IncomingCallActivity on incoming call", e)
            }
        }
    }

    fun placeSipCallFallback(context: Context, number: String) {
        val engine = SipEngine.getInstance(context.applicationContext)
        if (engine.makeCall(number)) {
            if (NativeDialerOverlay.canShowOverlay(context.applicationContext)) {
                NativeDialerOverlay.openAppCallScreen(context.applicationContext)
            } else {
                val intent = IncomingCallActivity.createLaunchIntent(
                    context = context,
                    callerAddress = number,
                    incoming = false
                )
                context.startActivity(intent)
            }
        }
    }

    fun placeOutgoingCall(context: Context, destination: String): Boolean = false
}
