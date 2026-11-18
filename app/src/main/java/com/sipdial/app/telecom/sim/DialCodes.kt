package com.sipdial.app.telecom.sim

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telecom.PhoneAccountHandle
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.sipdial.app.telecom.TelecomHelper

object DialCodes {

    private const val TAG = "DialCodes"
    private val secretRegex = Regex("""^\*#\*#(.+)#\*#\*$""")
    private val shortSecretRegex = Regex("""^\*#(\d{3,})#$""")

    fun isCode(n: String): Boolean {
        val s = n.trim()
        if (secretRegex.matches(s)) return true
        return s.length >= 3 && (s.startsWith("*") || s.startsWith("#")) && s.endsWith("#")
    }

    fun isAutoCode(n: String): Boolean {
        val s = n.trim()
        return s == "*#06#" || secretRegex.matches(s) || shortSecretRegex.matches(s)
    }

    fun handle(ctx: Context, input: String, simHandle: PhoneAccountHandle?, show: (String) -> Unit): Boolean {
        val s = input.trim()
        if (!isCode(s)) return false
        Log.i(TAG, "Handling dial code: $s")
        when {
            s == "*#06#" -> showImei(ctx, show)
            secretRegex.matches(s) -> sendSecret(ctx, secretRegex.find(s)!!.groupValues[1], show)
            shortSecretRegex.matches(s) -> sendSecret(ctx, shortSecretRegex.find(s)!!.groupValues[1], show)
            else -> TelecomHelper.placeSimCallWithHandle(ctx, s, simHandle)
        }
        return true
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    private fun showImei(ctx: Context, show: (String) -> Unit) {
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val lines = mutableListOf<String>()
        val hasPhoneState = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPhoneState) {
            try {
                val slots = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    maxOf(1, tm?.activeModemCount ?: 1)
                } else {
                    2
                }
                for (slot in 0 until slots) {
                    @Suppress("DEPRECATION")
                    val imei = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) tm?.getImei(slot) else tm?.deviceId
                    if (!imei.isNullOrBlank()) lines += "IMEI ${slot + 1}: $imei"
                }
            } catch (e: Exception) {
                Log.w(TAG, "IMEI not readable", e)
            }
        }
        if (lines.isNotEmpty()) {
            show(lines.joinToString("\n"))
        } else {
            show("Android restricts reading IMEI directly. Opening About Phone so you can view Status information.")
            try {
                ctx.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
        }
    }

    private fun sendSecret(ctx: Context, code: String, show: (String) -> Unit) {
        var sent = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                tm.sendDialerSpecialCode(code)
                sent = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "sendDialerSpecialCode failed", e)
        }
        show(
            if (sent) {
                "Code $code sent. If supported by this device, the system screen will open."
            } else {
                "Could not send code $code. Set SIP Dial as your default phone app in Settings > Calling Settings."
            }
        )
    }
}
