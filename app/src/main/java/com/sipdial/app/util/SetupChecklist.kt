package com.sipdial.app.util

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sipdial.app.CHANNEL_CALL_ID

data class SetupItem(
    val title: String,
    val hint: String,
    val ok: Boolean?,
    val fix: () -> Unit
)

object SetupChecklist {

    private fun open(context: Context, vararg intents: Intent) {
        val all = intents.toList() + Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        for (intent in all) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }

    private fun pkgUri(context: Context) = Uri.parse("package:${context.packageName}")

    private fun oemAutoStartIntents(): Array<Intent> {
        fun c(pkg: String, cls: String) = Intent().setClassName(pkg, cls)
        return when (Build.MANUFACTURER.lowercase()) {
            "xiaomi", "redmi", "poco" -> arrayOf(
                c("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            )
            "vivo", "iqoo" -> arrayOf(
                c("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                c("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                c("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
            )
            "oppo", "realme", "oneplus" -> arrayOf(
                c("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                c("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                c("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
            )
            "huawei", "honor" -> arrayOf(
                c("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                c("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            )
            "samsung" -> arrayOf(
                c("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")
            )
            else -> emptyArray()
        }
    }

    fun evaluate(context: Context): List<SetupItem> {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val items = mutableListOf<SetupItem>()

        items += SetupItem(
            title = "Microphone permission",
            hint = "Required so the other party can hear you during calls",
            ok = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
            fix = { open(context) }
        )

        val notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val channelOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val ch = nm.getNotificationChannel(CHANNEL_CALL_ID)
                ch == null || ch.importance != NotificationManager.IMPORTANCE_NONE
            } catch (_: Exception) {
                true
            }
        } else {
            true
        }
        items += SetupItem(
            title = "Notifications",
            hint = "Allow Incoming Calls and lock-screen alerts",
            ok = notificationsOn && channelOn,
            fix = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    open(
                        context,
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                    )
                } else {
                    open(context)
                }
            }
        )

        items += SetupItem(
            title = "Battery: allow background running",
            hint = "Prevents the system from killing SIP registration when the screen is off",
            ok = BatteryOptimization.isIgnoring(context),
            fix = { BatteryOptimization.request(context) }
        )

        items += SetupItem(
            title = "Auto-start / background launch",
            hint = "Enable background launch on OEM devices so incoming SIP calls wake the phone",
            ok = null,
            fix = { open(context, *oemAutoStartIntents()) }
        )

        items += SetupItem(
            title = "Display over other apps",
            hint = "Shows the floating incoming/ongoing call banner over other apps",
            ok = Settings.canDrawOverlays(context),
            fix = {
                open(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { data = pkgUri(context) })
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            items += SetupItem(
                title = "Full-screen notifications",
                hint = "Allows incoming calls to wake and display on the lock screen",
                ok = nm.canUseFullScreenIntent(),
                fix = {
                    open(
                        context,
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply { data = pkgUri(context) }
                    )
                }
            )
        }

        return items
    }
}
