package com.sipdial.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_history")
data class CallRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val remoteAddress: String,
    val displayName: String = "",
    val isIncoming: Boolean,
    val isAnswered: Boolean = true,
    val duration: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val recordingPath: String = "",
    val accountUser: String = "",
    val accountId: String = "",
    val accountName: String = ""
) {
    fun isSimCall(): Boolean =
        accountId.equals("SIM", ignoreCase = true) ||
            accountName.equals("SIM", ignoreCase = true) ||
            accountUser.equals("SIM", ignoreCase = true)

    fun resolvedAccountName(): String =
        accountName.trim()
            .ifEmpty { accountUser.trim() }
            .ifEmpty { if (isSimCall()) "SIM" else "SIP" }
}
