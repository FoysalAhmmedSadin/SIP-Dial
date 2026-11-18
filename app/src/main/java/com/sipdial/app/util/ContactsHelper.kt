package com.sipdial.app.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceContact(
    val id: String,
    val name: String,
    val number: String,
    val normalizedDigits: String,
    val photoUri: String? = null
)

object ContactsHelper {

    fun chatDigits(number: String): String {
        val trimmed = number.trim()
        var d = trimmed.filter { it.isDigit() }
        if (trimmed.startsWith("+")) return d
        if (d.startsWith("00")) return d.removePrefix("00")
        if (d.startsWith("0") && d.length == 11) d = "88$d"
        return d
    }

    suspend fun detectMessengers(context: Context, number: String): Pair<Boolean, Boolean> =
        withContext(Dispatchers.IO) {
            if (!hasContactsPermission(context) || number.filter { it.isDigit() }.length < 6) {
                return@withContext false to false
            }
            var wa = false
            var tg = false
            try {
                val lookup = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(number)
                )
                val ids = mutableListOf<String>()
                context.contentResolver.query(
                    lookup,
                    arrayOf(ContactsContract.PhoneLookup._ID),
                    null,
                    null,
                    null
                )?.use { c -> while (c.moveToNext()) ids.add(c.getString(0)) }
                for (id in ids) {
                    context.contentResolver.query(
                        ContactsContract.Data.CONTENT_URI,
                        arrayOf(ContactsContract.Data.MIMETYPE),
                        "${ContactsContract.Data.CONTACT_ID} = ?",
                        arrayOf(id),
                        null
                    )?.use { c ->
                        while (c.moveToNext()) {
                            val mt = c.getString(0).orEmpty().lowercase()
                            if (mt.contains("whatsapp")) wa = true
                            if (mt.contains("telegram")) tg = true
                        }
                    }
                }
            } catch (_: Exception) {
            }
            wa to tg
        }

    class PhoneIndex internal constructor(
        private val byExactClean: Map<String, DeviceContact>,
        private val byDigits: Map<String, DeviceContact>,
        private val byLast10: Map<String, DeviceContact>,
        private val bySuffix9: Map<String, DeviceContact>,
        private val bySuffix8: Map<String, DeviceContact>,
        private val bySuffix7: Map<String, DeviceContact>,
        private val shortDigitContacts: List<Pair<String, DeviceContact>>
    ) {
        fun find(address: String): DeviceContact? {
            val clean = CallerIdFormatter.extractNumber(address)
            if (clean.isBlank()) return null

            byExactClean[clean.lowercase()]?.let { return it }

            val digits = CallerIdFormatter.normalizeMatchDigits(clean)
            if (digits.isEmpty()) return null

            byDigits[digits]?.let { return it }

            if (digits.length >= 10) {
                byLast10[digits.takeLast(10)]?.let { return it }
                for (i in shortDigitContacts.indices) {
                    val (shortDigits, contact) = shortDigitContacts[i]
                    if (digits.endsWith(shortDigits)) return contact
                }
                return null
            }

            if (digits.length >= 7) {
                when (digits.length) {
                    9 -> bySuffix9[digits]?.let { return it }
                    8 -> bySuffix8[digits]?.let { return it }
                    7 -> bySuffix7[digits]?.let { return it }
                }
                for (i in shortDigitContacts.indices) {
                    val (otherDigits, contact) = shortDigitContacts[i]
                    if (otherDigits.length > digits.length && otherDigits.endsWith(digits)) return contact
                    if (otherDigits.length < digits.length && digits.endsWith(otherDigits)) return contact
                }
            }
            return null
        }
    }

    fun buildPhoneIndex(contacts: List<DeviceContact>): PhoneIndex {
        if (contacts.isEmpty()) {
            return PhoneIndex(
                byExactClean = emptyMap(),
                byDigits = emptyMap(),
                byLast10 = emptyMap(),
                bySuffix9 = emptyMap(),
                bySuffix8 = emptyMap(),
                bySuffix7 = emptyMap(),
                shortDigitContacts = emptyList()
            )
        }
        val cap = maxOf(16, contacts.size * 2)
        val byExactClean = HashMap<String, DeviceContact>(cap)
        val byDigits = HashMap<String, DeviceContact>(cap)
        val byLast10 = HashMap<String, DeviceContact>(cap)
        val bySuffix9 = HashMap<String, DeviceContact>(cap)
        val bySuffix8 = HashMap<String, DeviceContact>(cap)
        val bySuffix7 = HashMap<String, DeviceContact>(cap)
        val shortDigitContacts = ArrayList<Pair<String, DeviceContact>>()

        for (contact in contacts) {
            val cleanLower = CallerIdFormatter.extractNumber(contact.number).lowercase()
            if (cleanLower.isNotEmpty()) {
                byExactClean.putIfAbsent(cleanLower, contact)
            }
            val digits = CallerIdFormatter.normalizeMatchDigits(contact.normalizedDigits.ifBlank { contact.number })
            if (digits.isNotEmpty()) {
                byDigits.putIfAbsent(digits, contact)
                if (digits.length >= 10) {
                    byLast10.putIfAbsent(digits.takeLast(10), contact)
                    bySuffix9.putIfAbsent(digits.takeLast(9), contact)
                    bySuffix8.putIfAbsent(digits.takeLast(8), contact)
                    bySuffix7.putIfAbsent(digits.takeLast(7), contact)
                } else if (digits.length >= 7) {
                    shortDigitContacts.add(digits to contact)
                    if (digits.length >= 9) bySuffix9.putIfAbsent(digits.takeLast(9), contact)
                    if (digits.length >= 8) bySuffix8.putIfAbsent(digits.takeLast(8), contact)
                    bySuffix7.putIfAbsent(digits.takeLast(7), contact)
                }
            }
        }

        return PhoneIndex(
            byExactClean = byExactClean,
            byDigits = byDigits,
            byLast10 = byLast10,
            bySuffix9 = bySuffix9,
            bySuffix8 = bySuffix8,
            bySuffix7 = bySuffix7,
            shortDigitContacts = shortDigitContacts
        )
    }

    fun contactFor(address: String, contacts: List<DeviceContact>): DeviceContact? {
        val clean = CallerIdFormatter.extractNumber(address)
        if (clean.isBlank()) return null

        contacts.firstOrNull {
            CallerIdFormatter.extractNumber(it.number).equals(clean, ignoreCase = true)
        }?.let { return it }

        val digits = CallerIdFormatter.normalizeMatchDigits(clean)
        if (digits.isNotEmpty()) {
            contacts.firstOrNull {
                CallerIdFormatter.normalizeMatchDigits(it.normalizedDigits.ifBlank { it.number }) == digits
            }?.let { return it }
        }

        if (digits.length >= 7) {
            return contacts.firstOrNull {
                CallerIdFormatter.numbersMatch(clean, it.normalizedDigits.ifBlank { it.number })
            }
        }
        return null
    }

    fun nameFor(address: String, contacts: List<DeviceContact>): String? =
        contactFor(address, contacts)?.name

    fun photoFor(address: String, contacts: List<DeviceContact>): String? =
        contactFor(address, contacts)?.photoUri

    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun openInsertContactIntent(context: Context, number: String, name: String = "") {
        try {
            val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                type = ContactsContract.RawContacts.CONTENT_TYPE
                if (number.isNotBlank()) putExtra(ContactsContract.Intents.Insert.PHONE, number)
                if (name.isNotBlank()) putExtra(ContactsContract.Intents.Insert.NAME, name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    suspend fun loadContacts(context: Context): List<DeviceContact> = withContext(Dispatchers.IO) {
        if (!hasContactsPermission(context)) return@withContext emptyList()

        val contacts = mutableListOf<DeviceContact>()
        val seenKeys = HashSet<String>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        )

        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getString(idIdx).orEmpty() else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                    val rawNumber = if (numIdx >= 0) cursor.getString(numIdx)?.trim().orEmpty() else ""
                    val photo = if (photoIdx >= 0) cursor.getString(photoIdx) else null
                    if (name.isEmpty() || rawNumber.isEmpty()) continue

                    val digits = rawNumber.filter { it.isDigit() || it == '+' }
                    if (digits.isEmpty()) continue

                    val dedupKey = "${name.lowercase()}|${digits.takeLast(10)}"
                    if (seenKeys.add(dedupKey)) {
                        contacts.add(
                            DeviceContact(
                                id = id,
                                name = name,
                                number = rawNumber,
                                normalizedDigits = digits,
                                photoUri = photo
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }

        contacts
    }
}
