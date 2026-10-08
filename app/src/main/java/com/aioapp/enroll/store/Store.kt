package com.aioapp.enroll.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aioapp.enroll.mdm.Session
import org.json.JSONArray
import org.json.JSONObject

data class Account(val username: String, val name: String, val saved: Boolean)
data class Site(val id: String, val name: String)
data class LogEntry(val t: Long, val name: String, val serial: String, val cls: String, val restaurant: String, val by: String)

/**
 * Everything the app keeps, encrypted with a key held in the Android Keystore: the session, saved
 * accounts and passwords, this phone's own adb key, the fleet adb key, the chosen restaurant and
 * the enrolment log. The fleet key is removed on sign-out.
 */
class Store(ctx: Context) {
    private val p: SharedPreferences = EncryptedSharedPreferences.create(
        ctx, "aio-enroll",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var session: Session?
        get() = Session.fromJson(p.getString("session", null))
        set(v) = p.edit().apply { if (v == null) remove("session") else putString("session", v.toJson()) }.apply()

    fun accounts(): List<Account> = runCatching {
        val a = JSONArray(p.getString("accounts", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Account(o.getString("u"), o.optString("n"), p.contains("pw:" + o.getString("u"))) } }
    }.getOrDefault(emptyList())

    fun rememberAccount(username: String, name: String, password: String?) {
        val list = accounts().filter { it.username != username }.toMutableList()
        list.add(0, Account(username, name.ifEmpty { username }, password != null))
        val a = JSONArray()
        list.take(8).forEach { a.put(JSONObject().put("u", it.username).put("n", it.name)) }
        p.edit().putString("accounts", a.toString()).apply { if (password != null) putString("pw:$username", password) }.apply()
    }

    fun forgetAccount(username: String) {
        val a = JSONArray()
        accounts().filter { it.username != username }.forEach { a.put(JSONObject().put("u", it.username).put("n", it.name)) }
        p.edit().putString("accounts", a.toString()).remove("pw:$username").apply()
    }

    fun password(username: String): String? = p.getString("pw:$username", null)

    // ---- adb keys (PKCS#8 DER, base64) ----
    var ownKey: String?
        get() = p.getString("ownKey", null)
        set(v) = p.edit().putString("ownKey", v).apply()

    var fleetKeyPem: String?
        get() = p.getString("fleetKey", null)
        set(v) = p.edit().apply { if (v == null) remove("fleetKey") else putString("fleetKey", v) }.apply()

    var fleetKeyVersion: Int
        get() = p.getInt("fleetKeyV", 0)
        set(v) = p.edit().putInt("fleetKeyV", v).apply()

    var fleetKeyFingerprint: String
        get() = p.getString("fleetKeyFp", "") ?: ""
        set(v) = p.edit().putString("fleetKeyFp", v).apply()

    // ---- where devices go ----
    fun site(user: String): Site = runCatching {
        val o = JSONObject(p.getString("site:$user", null) ?: return Site("", ""))
        Site(o.optString("id"), o.optString("name"))
    }.getOrDefault(Site("", ""))

    fun setSite(user: String, s: Site) = p.edit().putString("site:$user", JSONObject().put("id", s.id).put("name", s.name).toString()).apply()

    // ---- what was enrolled (kept a week) ----
    fun log(): List<LogEntry> = runCatching {
        val a = JSONArray(p.getString("log", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> LogEntry(o.getLong("t"), o.optString("name"), o.optString("serial"), o.optString("cls"), o.optString("rest"), o.optString("by")) } }
    }.getOrDefault(emptyList())

    fun addLog(e: LogEntry) {
        val week = System.currentTimeMillis() - 7 * 86_400_000L
        val list = listOf(e) + log().filter { it.t > week && it.serial != e.serial }
        val a = JSONArray()
        list.take(500).forEach { a.put(JSONObject().put("t", it.t).put("name", it.name).put("serial", it.serial).put("cls", it.cls).put("rest", it.restaurant).put("by", it.by)) }
        p.edit().putString("log", a.toString()).apply()
    }

    var onlyOurs: Boolean
        get() = p.getBoolean("onlyOurs", false)
        set(v) = p.edit().putBoolean("onlyOurs", v).apply()
}
