package com.sipdial.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sipdial.app.data.models.SipAccountData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sip_account")

class AccountRepository(private val context: Context) {

    companion object {
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_PASSWORD = stringPreferencesKey("password")
        private val KEY_DOMAIN = stringPreferencesKey("domain")
        private val KEY_PORT = intPreferencesKey("port")
        private val KEY_TRANSPORT = stringPreferencesKey("transport")
        private val KEY_PROXY = stringPreferencesKey("proxy")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_AUTO_START = booleanPreferencesKey("auto_start")

        private val KEY_ACCOUNTS = stringPreferencesKey("accounts_json")
        private val KEY_SELECTED = stringPreferencesKey("selected_account_id")
        private const val LEGACY_ID = "legacy"
    }

    private fun parseList(prefs: Preferences): List<SipAccountData> {
        val json = prefs[KEY_ACCOUNTS]
        if (json != null) {
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    SipAccountData(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        username = o.optString("username"),
                        authUsername = o.optString("authUsername"),
                        password = SecureCrypto.decrypt(o.optString("password")),
                        domain = o.optString("domain"),
                        port = o.optInt("port", 5060),
                        transport = o.optString("transport", "UDP"),
                        proxy = o.optString("proxy"),
                        displayName = o.optString("displayName"),
                        registrationExpiry = o.optInt("registrationExpiry", 60),
                        autoStart = o.optBoolean("autoStart", false),
                        enabled = o.optBoolean("enabled", true)
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
        val username = prefs[KEY_USERNAME] ?: return emptyList()
        val domain = prefs[KEY_DOMAIN] ?: return emptyList()
        if (username.isEmpty() || domain.isEmpty()) return emptyList()
        return listOf(
            SipAccountData(
                id = LEGACY_ID,
                name = username,
                username = username,
                password = SecureCrypto.decrypt(prefs[KEY_PASSWORD] ?: ""),
                domain = domain,
                port = prefs[KEY_PORT] ?: 5060,
                transport = prefs[KEY_TRANSPORT] ?: "UDP",
                proxy = prefs[KEY_PROXY] ?: "",
                displayName = prefs[KEY_DISPLAY_NAME] ?: "",
                autoStart = prefs[KEY_AUTO_START] ?: false
            )
        )
    }

    private fun serialize(list: List<SipAccountData>): String {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("name", a.name)
                    .put("username", a.username)
                    .put("authUsername", a.authUsername)
                    .put("password", SecureCrypto.encrypt(a.password))
                    .put("domain", a.domain)
                    .put("port", a.port)
                    .put("transport", a.transport)
                    .put("proxy", a.proxy)
                    .put("displayName", a.displayName)
                    .put("registrationExpiry", a.registrationExpiry)
                    .put("autoStart", a.autoStart)
                    .put("enabled", a.enabled)
            )
        }
        return arr.toString()
    }

    private fun pickSelected(prefs: Preferences, list: List<SipAccountData>): SipAccountData? {
        val id = prefs[KEY_SELECTED]
        return list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    fun getAccounts(): Flow<List<SipAccountData>> =
        context.dataStore.data.map { parseList(it) }.flowOn(Dispatchers.Default)

    fun getAccount(): Flow<SipAccountData?> =
        context.dataStore.data.map { prefs -> pickSelected(prefs, parseList(prefs)) }.flowOn(Dispatchers.Default)

    fun getAccountsState(): Flow<Pair<List<SipAccountData>, SipAccountData?>> =
        context.dataStore.data.map { prefs ->
            val list = parseList(prefs)
            list to pickSelected(prefs, list)
        }.flowOn(Dispatchers.Default)

    suspend fun saveAccount(account: SipAccountData): SipAccountData {
        var saved = account
        context.dataStore.edit { prefs ->
            val list = parseList(prefs).toMutableList()
            val id = account.id.ifBlank { UUID.randomUUID().toString() }
            saved = account.copy(id = id, name = account.name.trim())
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) list[idx] = saved else list.add(saved)
            prefs[KEY_ACCOUNTS] = serialize(list)
            prefs[KEY_SELECTED] = id
        }
        return saved
    }

    suspend fun importAccounts(imported: List<SipAccountData>): Int {
        var count = 0
        context.dataStore.edit { prefs ->
            val list = parseList(prefs).toMutableList()
            imported.forEach { a ->
                if (a.username.isBlank() || a.domain.isBlank()) return@forEach
                val i = list.indexOfFirst {
                    it.username.equals(a.username, true) && it.domain.equals(a.domain, true)
                }
                if (i >= 0) {
                    list[i] = a.copy(id = list[i].id, name = a.name.ifBlank { list[i].name })
                } else {
                    var name = a.name.ifBlank { a.username }
                    while (list.any { it.name.equals(name, true) }) name += " (2)"
                    val id = if (a.id.isBlank() || a.id == LEGACY_ID || list.any { it.id == a.id }) {
                        UUID.randomUUID().toString()
                    } else {
                        a.id
                    }
                    list.add(a.copy(id = id, name = name))
                }
                count++
            }
            prefs[KEY_ACCOUNTS] = serialize(list)
            val sel = prefs[KEY_SELECTED]
            if (sel == null || list.none { it.id == sel }) {
                list.firstOrNull()?.let { prefs[KEY_SELECTED] = it.id }
            }
        }
        return count
    }

    suspend fun selectAccount(id: String) {
        context.dataStore.edit { prefs ->
            val list = parseList(prefs)
            if (list.any { it.id == id }) {
                prefs[KEY_ACCOUNTS] = serialize(list)
                prefs[KEY_SELECTED] = id
            }
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val list = parseList(prefs).map { if (it.id == id) it.copy(enabled = enabled) else it }
            prefs[KEY_ACCOUNTS] = serialize(list)
        }
    }

    suspend fun deleteAccount(id: String) {
        context.dataStore.edit { prefs ->
            val list = parseList(prefs).filter { it.id != id }
            prefs[KEY_ACCOUNTS] = serialize(list)
            val selected = prefs[KEY_SELECTED]
            if (selected == id || list.none { it.id == selected }) {
                if (list.isEmpty()) prefs.remove(KEY_SELECTED) else prefs[KEY_SELECTED] = list.first().id
            }
        }
    }

    suspend fun clearAccount() {
        context.dataStore.edit { it.clear() }
    }
}
