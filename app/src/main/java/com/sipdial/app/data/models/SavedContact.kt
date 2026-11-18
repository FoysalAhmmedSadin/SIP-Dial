package com.sipdial.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_contacts")
data class SavedContact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String = CATEGORY_CLIENT,
    val name: String,
    val number: String,
    val company: String = "",
    val email: String = "",
    val address: String = "",
    val birthday: String = "",
    val notes: String = "",
    val hasWhatsapp: Boolean = false,
    val hasTelegram: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val CATEGORY_CLIENT = "client"
        const val CATEGORY_OFFICE = "office"
    }
}
