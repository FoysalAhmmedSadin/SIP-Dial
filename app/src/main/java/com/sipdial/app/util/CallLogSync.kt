package com.sipdial.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.sipdial.app.data.AppDatabase
import com.sipdial.app.data.models.CallRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CallLogSync {
    const val SIM_LABEL = "SIM"
    private const val MAX_ROWS = 1000

    private const val PREFS = "call_log_sync"
    private const val KEY_DELETED = "deleted_keys"
    private const val KEY_CLEARED_BEFORE = "cleared_before"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(ts: Long, number: String) = "$ts|$number"

    fun markDeleted(context: Context, record: CallRecord) {
        if (!record.isSimCall()) return
        val p = prefs(context)
        val set = (p.getStringSet(KEY_DELETED, emptySet()) ?: emptySet()).toMutableSet()
        set.add(key(record.timestamp, record.remoteAddress))
        p.edit().putStringSet(KEY_DELETED, set).apply()
    }

    fun markAllCleared(context: Context) {
        prefs(context).edit()
            .putLong(KEY_CLEARED_BEFORE, System.currentTimeMillis())
            .remove(KEY_DELETED)
            .apply()
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun syncFromSystem(context: Context): Int = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext 0
        val dao = AppDatabase.getInstance(context).callHistoryDao()
        var added = 0
        val deleted = prefs(context).getStringSet(KEY_DELETED, emptySet()) ?: emptySet()
        val clearedBefore = prefs(context).getLong(KEY_CLEARED_BEFORE, 0L)
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n < MAX_ROWS) {
                    n++
                    val number = c.getString(0).orEmpty().ifBlank { "Unknown" }
                    val name = c.getString(1).orEmpty()
                    val type = c.getInt(2)
                    val ts = c.getLong(3)
                    val dur = c.getInt(4)
                    if (ts <= clearedBefore || key(ts, number) in deleted) continue
                    if (dao.countSame(ts, number) > 0) continue

                    val incoming = type != CallLog.Calls.OUTGOING_TYPE
                    val answered = when (type) {
                        CallLog.Calls.MISSED_TYPE,
                        CallLog.Calls.REJECTED_TYPE,
                        CallLog.Calls.BLOCKED_TYPE,
                        CallLog.Calls.VOICEMAIL_TYPE -> false
                        else -> true
                    }
                    dao.insertCallRecord(
                        CallRecord(
                            remoteAddress = number,
                            displayName = name,
                            isIncoming = incoming,
                            isAnswered = answered,
                            duration = if (answered) dur else 0,
                            timestamp = ts,
                            accountUser = SIM_LABEL,
                            accountId = SIM_LABEL,
                            accountName = SIM_LABEL
                        )
                    )
                    added++
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
        added
    }
}
