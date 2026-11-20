package com.sipdial.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.sipdial.app.data.models.CallRecord
import com.sipdial.app.data.models.FavoriteContact
import com.sipdial.app.data.models.SavedContact
import com.sipdial.app.data.models.SipAccountData
import com.sipdial.app.service.SipForegroundService
import com.sipdial.app.sip.SipEngine
import com.sipdial.app.telecom.TelecomHelper
import com.sipdial.app.ui.theme.AccentColor
import com.sipdial.app.ui.theme.ThemeMode
import com.sipdial.app.ui.theme.ThemePreferences
import com.sipdial.app.util.AppLocalePrefs
import com.sipdial.app.util.AudioQualityPrefs
import com.sipdial.app.util.BlockList
import com.sipdial.app.util.CallingPrefs
import com.sipdial.app.util.GeneralPrefs
import com.sipdial.app.util.NotificationPrefs
import com.sipdial.app.util.PowerPrefs
import com.sipdial.app.util.SecurityPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted backup/restore for call history, SIP accounts, saved contacts, favorites,
 * blocked numbers, and app preferences.
 * Format: "SIPBK1" + salt(16) + iv(12) + AES-256-GCM(gzip(JSON)); key derived via PBKDF2-HMAC-SHA256.
 */
object BackupManager {
    private val MAGIC = "SIPBK1".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 150_000
    private const val FORMAT_VERSION = 3

    data class BackupOptions(
        val includeCalls: Boolean = true,
        val includeAccounts: Boolean = true,
        val includeContactsAndFavorites: Boolean = true,
        val includeSettings: Boolean = true
    ) {
        fun hasAnySelection(): Boolean =
            includeCalls || includeAccounts || includeContactsAndFavorites || includeSettings

        fun summaryLabel(): String {
            val parts = mutableListOf<String>()
            if (includeCalls) parts += "Calls"
            if (includeAccounts) parts += "SIP Accounts"
            if (includeContactsAndFavorites) parts += "Contacts & Favorites"
            if (includeSettings) parts += "Settings"
            return if (parts.isEmpty()) "Empty" else parts.joinToString(", ")
        }
    }

    data class Summary(
        val accounts: Int,
        val calls: Int,
        val contacts: Int = 0,
        val favorites: Int = 0,
        val settingsIncluded: Boolean = false
    )

    class WrongPassphraseException : Exception("Wrong passphrase or corrupted backup file")

    private fun deriveKey(pass: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pass, salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    suspend fun exportToBytes(
        context: Context,
        passphrase: CharArray,
        options: BackupOptions = BackupOptions()
    ): Pair<ByteArray, Summary> {
        val out = ByteArrayOutputStream()
        val summary = export(context, passphrase, out, options)
        return out.toByteArray() to summary
    }

    suspend fun restoreFromBytes(
        context: Context,
        passphrase: CharArray,
        bytes: ByteArray
    ): Summary = restore(context, passphrase, ByteArrayInputStream(bytes))

    suspend fun export(
        context: Context,
        passphrase: CharArray,
        out: OutputStream,
        options: BackupOptions = BackupOptions()
    ): Summary =
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            val accounts = if (options.includeAccounts) AccountRepository(context).getAccounts().first() else emptyList()
            val calls = if (options.includeCalls) db.callHistoryDao().getAllOnce() else emptyList()
            val savedContacts = if (options.includeContactsAndFavorites) db.savedContactDao().getAllOnce() else emptyList()
            val favorites = if (options.includeContactsAndFavorites) db.favoritesDao().getAllOnce() else emptyList()

            val root = JSONObject().put("version", FORMAT_VERSION)
            if (options.includeAccounts) {
                root.put("accounts", JSONArray().also { arr ->
                    accounts.forEach { a ->
                        arr.put(
                            JSONObject()
                                .put("id", a.id).put("name", a.name)
                                .put("username", a.username).put("authUsername", a.authUsername)
                                .put("password", a.password)
                                .put("domain", a.domain).put("port", a.port)
                                .put("transport", a.transport).put("proxy", a.proxy)
                                .put("displayName", a.displayName)
                                .put("registrationExpiry", a.registrationExpiry)
                                .put("autoStart", a.autoStart)
                                .put("enabled", a.enabled)
                        )
                    }
                })
            }
            if (options.includeCalls) {
                root.put("calls", JSONArray().also { arr ->
                    calls.forEach { c ->
                        arr.put(
                            JSONObject()
                                .put("a", c.remoteAddress).put("n", c.displayName)
                                .put("in", c.isIncoming).put("ans", c.isAnswered)
                                .put("d", c.duration).put("t", c.timestamp)
                                .put("u", c.accountUser)
                                .put("aid", c.accountId)
                                .put("aname", c.accountName)
                        )
                    }
                })
            }
            if (options.includeContactsAndFavorites) {
                root.put("contacts", JSONArray().also { arr ->
                    savedContacts.forEach { c ->
                        arr.put(
                            JSONObject()
                                .put("cat", c.category).put("n", c.name).put("num", c.number)
                                .put("co", c.company).put("em", c.email).put("ad", c.address)
                                .put("bd", c.birthday).put("nt", c.notes)
                                .put("wa", c.hasWhatsapp).put("tg", c.hasTelegram).put("t", c.createdAt)
                        )
                    }
                })
                root.put("favorites", JSONArray().also { arr ->
                    favorites.forEach { f ->
                        arr.put(
                            JSONObject()
                                .put("n", f.name)
                                .put("num", f.number)
                                .put("norm", f.normalizedNumber)
                                .put("route", f.preferredRoute)
                                .put("ord", f.sortOrder)
                                .put("t", f.addedAt)
                        )
                    }
                })
            }
            if (options.includeSettings) {
                BlockList.init(context)
                ThemePreferences.init(context)
                val appearance = ThemePreferences.state.value
                val settingsObj = JSONObject()
                    .put("themeMode", appearance.themeMode.key)
                    .put("accentColor", appearance.accentColor.key)
                    .put("textScale", appearance.textScale.toDouble())
                    .put("powerMode", PowerPrefs.mode(context))
                    .put("verifyTls", SecurityPrefs.verifyTls(context))
                    .put("srtp", SecurityPrefs.srtp(context))
                    .put("routingMode", TelecomHelper.getNativeCallRoutingMode(context))
                    .put("autoRecord", TelecomHelper.isAutoRecordEnabled(context))
                    .put("confirmCall", CallingPrefs.isCallConfirmationEnabled(context))
                    .put("autoAnswer", CallingPrefs.isAutoAnswerEnabled(context))
                    .put("autoAnswerDelay", CallingPrefs.getAutoAnswerDelaySeconds(context))
                    .put("dtmfTones", CallingPrefs.isDtmfTonesEnabled(context))
                    .put("dtmfMode", CallingPrefs.getDtmfMode(context))
                    .put("hdCodecs", AudioQualityPrefs.isHdCodecsEnabled(context))
                    .put("micBoostDb", AudioQualityPrefs.getMicBoostDb(context))
                    .put("vibrate", NotificationPrefs.isVibrateEnabled(context))
                    .put("incomingBanner", NotificationPrefs.isIncomingBannerEnabled(context))
                    .put("regAlerts", NotificationPrefs.isRegistrationAlertsEnabled(context))
                    .put("defaultOpeningTab", GeneralPrefs.getDefaultOpeningTab(context))
                    .put("soundEffects", GeneralPrefs.isSoundEffectsEnabled(context))
                    .put("hapticFeedback", GeneralPrefs.isHapticFeedbackEnabled(context))
                    .put("numberFormatting", GeneralPrefs.isNumberFormattingEnabled(context))
                    .put("appLocale", AppLocalePrefs.getLocaleTag(context))
                    .put("blockedNumbers", JSONArray().also { arr ->
                        BlockList.numbers.value.sorted().forEach { arr.put(it) }
                    })
                AudioQualityPrefs.getCodecConfig(context)?.let { codecs ->
                    settingsObj.put("codecs", JSONArray().also { arr ->
                        codecs.forEach { c ->
                            arr.put(JSONObject().put("k", c.key).put("e", c.enabled))
                        }
                    })
                }
                root.put("settings", settingsObj)
            }

            val gz = ByteArrayOutputStream()
            GZIPOutputStream(gz).use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }

            val rnd = SecureRandom()
            val salt = ByteArray(16).also { rnd.nextBytes(it) }
            val iv = ByteArray(12).also { rnd.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(gz.toByteArray())

            out.write(MAGIC)
            out.write(salt)
            out.write(iv)
            out.write(encrypted)
            out.flush()
            Summary(
                accounts = accounts.size,
                calls = calls.size,
                contacts = savedContacts.size,
                favorites = favorites.size,
                settingsIncluded = options.includeSettings
            )
        }

    suspend fun restore(context: Context, passphrase: CharArray, input: InputStream): Summary =
        withContext(Dispatchers.IO) {
            val all = input.readBytes()
            if (all.size < MAGIC.size + 28 || !all.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
                throw IllegalArgumentException("This is not a SIP Dial backup file")
            }
            val salt = all.copyOfRange(MAGIC.size, MAGIC.size + 16)
            val iv = all.copyOfRange(MAGIC.size + 16, MAGIC.size + 28)
            val body = all.copyOfRange(MAGIC.size + 28, all.size)

            val json = try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
                val plain = cipher.doFinal(body)
                GZIPInputStream(ByteArrayInputStream(plain)).use { String(it.readBytes(), Charsets.UTF_8) }
            } catch (_: Exception) {
                throw WrongPassphraseException()
            }
            val root = JSONObject(json)

            val accounts = mutableListOf<SipAccountData>()
            root.optJSONArray("accounts")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    accounts.add(
                        SipAccountData(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            username = o.optString("username"),
                            authUsername = o.optString("authUsername"),
                            password = o.optString("password"),
                            domain = o.optString("domain"),
                            port = o.optInt("port", 5060),
                            transport = o.optString("transport", "UDP"),
                            proxy = o.optString("proxy"),
                            displayName = o.optString("displayName"),
                            registrationExpiry = o.optInt("registrationExpiry", 60),
                            autoStart = o.optBoolean("autoStart", false),
                            enabled = o.optBoolean("enabled", true)
                        )
                    )
                }
            }
            val accCount = if (accounts.isNotEmpty()) AccountRepository(context).importAccounts(accounts) else 0

            val db = AppDatabase.getInstance(context)

            var callCount = 0
            val callDao = db.callHistoryDao()
            root.optJSONArray("calls")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val addr = o.optString("a")
                    val ts = o.optLong("t")
                    if (callDao.countSame(ts, addr) > 0) continue
                    callDao.insertCallRecord(
                        CallRecord(
                            remoteAddress = addr,
                            displayName = o.optString("n"),
                            isIncoming = o.optBoolean("in"),
                            isAnswered = o.optBoolean("ans", true),
                            duration = o.optInt("d"),
                            timestamp = ts,
                            accountUser = o.optString("u", ""),
                            accountId = o.optString("aid", ""),
                            accountName = o.optString("aname", "")
                        )
                    )
                    callCount++
                }
            }

            var contactCount = 0
            val contactDao = db.savedContactDao()
            root.optJSONArray("contacts")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val cat = o.optString("cat", SavedContact.CATEGORY_CLIENT)
                    val num = o.optString("num")
                    if (num.isBlank() || contactDao.countSame(cat, num) > 0) continue
                    contactDao.insert(
                        SavedContact(
                            category = cat,
                            name = o.optString("n"),
                            number = num,
                            company = o.optString("co"),
                            email = o.optString("em"),
                            address = o.optString("ad"),
                            birthday = o.optString("bd"),
                            notes = o.optString("nt"),
                            hasWhatsapp = o.optBoolean("wa", false),
                            hasTelegram = o.optBoolean("tg", false),
                            createdAt = o.optLong("t", System.currentTimeMillis())
                        )
                    )
                    contactCount++
                }
            }

            var favCount = 0
            val favDao = db.favoritesDao()
            root.optJSONArray("favorites")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val num = o.optString("num")
                    val norm = o.optString("norm").ifBlank { FavoriteContact.normalize(num) }
                    if (num.isBlank() || favDao.countByNormalized(norm) > 0) continue
                    favDao.insert(
                        FavoriteContact(
                            name = o.optString("n").ifBlank { num },
                            number = num,
                            normalizedNumber = norm,
                            preferredRoute = o.optString("route", FavoriteContact.ROUTE_ASK),
                            sortOrder = o.optInt("ord", i),
                            addedAt = o.optLong("t", System.currentTimeMillis())
                        )
                    )
                    favCount++
                }
            }

            var settingsRestored = false
            root.optJSONObject("settings")?.let { s ->
                settingsRestored = true
                if (s.has("themeMode")) {
                    ThemePreferences.setThemeMode(context, ThemeMode.fromKey(s.optString("themeMode")))
                }
                if (s.has("accentColor")) {
                    ThemePreferences.setAccentColor(context, AccentColor.fromKey(s.optString("accentColor")))
                }
                if (s.has("textScale")) {
                    ThemePreferences.setTextScale(context, s.optDouble("textScale", 1.0).toFloat())
                }
                if (s.has("powerMode")) PowerPrefs.setMode(context, s.optString("powerMode"))
                if (s.has("verifyTls")) SecurityPrefs.setVerifyTls(context, s.optBoolean("verifyTls", true))
                if (s.has("srtp")) SecurityPrefs.setSrtp(context, s.optBoolean("srtp", false))
                if (s.has("routingMode")) TelecomHelper.setNativeCallRoutingMode(context, s.optInt("routingMode"))
                if (s.has("autoRecord")) TelecomHelper.setAutoRecordEnabled(context, s.optBoolean("autoRecord"))
                if (s.has("confirmCall")) CallingPrefs.setCallConfirmationEnabled(context, s.optBoolean("confirmCall"))
                if (s.has("autoAnswer")) CallingPrefs.setAutoAnswerEnabled(context, s.optBoolean("autoAnswer"))
                if (s.has("autoAnswerDelay")) CallingPrefs.setAutoAnswerDelaySeconds(context, s.optInt("autoAnswerDelay", 2))
                if (s.has("dtmfTones")) CallingPrefs.setDtmfTonesEnabled(context, s.optBoolean("dtmfTones", true))
                if (s.has("dtmfMode")) CallingPrefs.setDtmfMode(context, s.optString("dtmfMode", CallingPrefs.DTMF_AUTO))
                if (s.has("hdCodecs")) AudioQualityPrefs.setHdCodecsEnabled(context, s.optBoolean("hdCodecs"))
                if (s.has("micBoostDb")) AudioQualityPrefs.setMicBoostDb(context, s.optInt("micBoostDb", AudioQualityPrefs.DEFAULT_MIC_BOOST_DB))
                if (s.has("vibrate")) NotificationPrefs.setVibrateEnabled(context, s.optBoolean("vibrate", true))
                if (s.has("incomingBanner")) NotificationPrefs.setIncomingBannerEnabled(context, s.optBoolean("incomingBanner", true))
                if (s.has("regAlerts")) NotificationPrefs.setRegistrationAlertsEnabled(context, s.optBoolean("regAlerts", true))
                if (s.has("defaultOpeningTab")) GeneralPrefs.setDefaultOpeningTab(context, s.optString("defaultOpeningTab", GeneralPrefs.TAB_RECENTS))
                if (s.has("soundEffects")) GeneralPrefs.setSoundEffectsEnabled(context, s.optBoolean("soundEffects", true))
                if (s.has("hapticFeedback")) GeneralPrefs.setHapticFeedbackEnabled(context, s.optBoolean("hapticFeedback", true))
                if (s.has("numberFormatting")) GeneralPrefs.setNumberFormattingEnabled(context, s.optBoolean("numberFormatting", true))
                if (s.has("appLocale")) AppLocalePrefs.setLocaleTag(context, s.optString("appLocale"))
                s.optJSONArray("blockedNumbers")?.let { arr ->
                    BlockList.init(context)
                    for (i in 0 until arr.length()) {
                        val num = arr.optString(i)
                        if (num.isNotBlank()) BlockList.add(context, num)
                    }
                }
                s.optJSONArray("codecs")?.let { arr ->
                    val list = mutableListOf<AudioQualityPrefs.CodecEntry>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val k = obj.optString("k")
                        if (k.isNotBlank()) {
                            list += AudioQualityPrefs.CodecEntry(k, obj.optBoolean("e", true))
                        }
                    }
                    if (list.isNotEmpty()) AudioQualityPrefs.setCodecConfig(context, list)
                }
            }

            if (accCount > 0) {
                try {
                    val appContext = context.applicationContext
                    Handler(Looper.getMainLooper()).post {
                        try {
                            SipEngine.getInstance(appContext).registerAllSavedAsync()
                            SipForegroundService.start(appContext)
                        } catch (_: Throwable) {
                        }
                    }
                } catch (_: Throwable) {
                }
            }
            Summary(accCount, callCount, contactCount, favCount, settingsRestored)
        }
}
