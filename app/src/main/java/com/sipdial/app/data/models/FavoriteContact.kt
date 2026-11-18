package com.sipdial.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sipdial.app.util.CallerIdFormatter

@Entity(tableName = "favorite_contacts")
data class FavoriteContact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val number: String,
    val normalizedNumber: String,
    val photoUri: String? = null,
    val preferredRoute: String = ROUTE_ASK,
    val sortOrder: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val ROUTE_ASK = "ASK"
        const val ROUTE_SIP = "SIP"
        const val ROUTE_SIM = "SIM"

        fun normalize(raw: String): String {
            val clean = CallerIdFormatter.extractNumber(raw)
            val digits = CallerIdFormatter.normalizeMatchDigits(clean)
            return if (digits.length >= 10) digits.takeLast(10) else digits.ifEmpty { clean }
        }
    }
}
