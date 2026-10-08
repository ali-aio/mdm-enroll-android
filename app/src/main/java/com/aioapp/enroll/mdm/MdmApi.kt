package com.aioapp.enroll.mdm

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

const val DEFAULT_SERVER = com.aioapp.enroll.BuildConfig.SERVER

/** A signed-in dashboard account; [token] is the server-side session id, sent as a bearer. */
data class Session(val server: String, val token: String, val username: String, val role: String) {
    fun toJson(): String = JSONObject().put("server", server).put("token", token).put("username", username).put("role", role).toString()

    companion object {
        fun fromJson(s: String?): Session? = runCatching {
            val o = JSONObject(s ?: return null)
            Session(o.getString("server"), o.getString("token"), o.getString("username"), o.optString("role"))
        }.getOrNull()
    }
}

/** Why a call failed. [unauthorized] means the session is dead: back to sign-in. */
class MdmError(message: String, val unauthorized: Boolean = false, val notFound: Boolean = false) : IOException(message)

data class Status(
    val enrolled: Boolean,
    val status: String,
    val deviceClass: String,
    val agentVersion: String,
    val lastSeenAt: String?,
    val enrolledByName: String,
    val online: Boolean?, // null: the server is too old to say
    val batteryPct: Int,
    val hasBattery: Boolean,
    val restaurant: String,
) {
    /** The MDM knows it and hasn't retired or wiped it (firmware devices register as `auto`). */
    val known get() = status.isNotEmpty() && status != "retired" && status != "wiped"
}

data class SerialClass(
    val kind: String, // fleet | production | family | lookalike | other
    val production: String = "",
    val family: String = "",
    val familyCount: Int = 0,
    val deviceClass: String = "",
    val name: String = "",
    var lastSeen: String? = null, // fleet only, filled from enroll-status
    var checked: Boolean = false,
) {
    val ours get() = kind in setOf("fleet", "production", "family", "lookalike")
}

data class Restaurant(val id: String, val name: String, val address: String)
data class Profile(val name: String, val adbKeyVersion: Int)
data class AgentInfo(val version: String, val versionCode: Long)
data class FleetKey(val version: Int, val fingerprint: String, val privateKeyPem: String)
data class EnrollToken(val token: String, val serverUrl: String, val restaurant: String)

/** The MDM's `/api/v1/app/…` endpoints (the same ones the desktop app uses). Blocking: call off the main thread. */
class MdmApi(val session: Session) {
    companion object {
        fun login(server: String, username: String, password: String): Session {
            val s = server.trim().trimEnd('/').ifEmpty { DEFAULT_SERVER }
            val o = request("POST", "$s/api/v1/app/login", null, JSONObject().put("username", username).put("password", password), loginCall = true)
            return Session(s, o.getString("token"), o.optString("username", username), o.optString("role"))
        }

        private fun request(method: String, url: String, bearer: String?, body: JSONObject?, loginCall: Boolean = false, timeoutMs: Int = 20000): JSONObject {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 8000
                readTimeout = timeoutMs
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "AIOEnroll-Android/0.1")
                setRequestProperty("X-AIO-Device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            try {
                if (body != null) c.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = c.responseCode
                val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code in 200..299) return if (text.isBlank()) JSONObject() else JSONObject(text)
                val msg = runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty().ifEmpty { "HTTP $code" }
                throw when {
                    code == 401 && !loginCall -> MdmError(msg, unauthorized = true)
                    code == 404 -> MdmError(msg, notFound = true)
                    else -> MdmError(msg)
                }
            } catch (e: MdmError) {
                throw e
            } catch (e: IOException) {
                throw MdmError("Can't reach the MDM: ${e.message}")
            } finally {
                c.disconnect()
            }
        }
    }

    private fun get(path: String) = request("GET", session.server + path, session.token, null)
    private fun post(path: String, body: JSONObject) = request("POST", session.server + path, session.token, body)

    fun logout() = runCatching { post("/api/v1/app/logout", JSONObject()) }

    fun profile(): Profile {
        val o = get("/api/v1/app/me")
        return Profile(o.optString("name", session.username), o.optInt("adb_key_version", 0))
    }

    fun avatar(): ByteArray? = runCatching {
        val c = (URL(session.server + "/api/v1/app/avatar").openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer ${session.token}")
            connectTimeout = 8000; readTimeout = 15000
        }
        android.util.Log.i("AioMdm", "avatar -> ${c.responseCode} ${c.contentType}")
        if (c.responseCode != 200) null else c.inputStream.use { it.readBytes() }
    }.onFailure { android.util.Log.w("AioMdm", "avatar: $it") }.getOrNull()

    fun statuses(serials: List<String>): Map<String, Status> {
        if (serials.isEmpty()) return emptyMap()
        val q = URLEncoder.encode(serials.take(100).joinToString(","), "UTF-8")
        val d = get("/api/v1/app/enroll-status?serials=$q").optJSONObject("devices") ?: return emptyMap()
        return d.keys().asSequence().associateWith { k ->
            val o = d.getJSONObject(k)
            Status(
                enrolled = o.optBoolean("enrolled"),
                status = o.optString("status"),
                deviceClass = o.optString("class"),
                agentVersion = o.optString("agent_version"),
                lastSeenAt = o.optString("last_seen_at").takeIf { it.isNotEmpty() && it != "null" },
                enrolledByName = o.optString("enrolled_by_name").ifEmpty { o.optString("enrolled_by") },
                online = if (o.has("online")) o.optBoolean("online") else null,
                batteryPct = o.optInt("battery_pct"),
                hasBattery = o.optBoolean("has_battery"),
                restaurant = o.optString("restaurant"),
            )
        }
    }

    fun classify(serials: List<String>): Map<String, SerialClass> {
        if (serials.isEmpty()) return emptyMap()
        val o = post("/api/v1/app/classify", JSONObject().put("serials", JSONArray(serials.take(100)))).optJSONObject("serials") ?: return emptyMap()
        return o.keys().asSequence().associateWith { k ->
            val c = o.getJSONObject(k)
            SerialClass(c.optString("class", "other"), c.optString("production"), c.optString("family"), c.optInt("family_count"), c.optString("device_class"), c.optString("name"))
        }
    }

    fun restaurants(): List<Restaurant> {
        val a = get("/api/v1/app/restaurants").optJSONArray("restaurants") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it).let { r -> Restaurant(r.getString("id"), r.optString("name"), r.optString("address")) } }
    }

    fun agentInfo(): AgentInfo? = runCatching {
        val o = get("/api/v1/app/agent")
        AgentInfo(o.optString("version"), o.optLong("version_code"))
    }.getOrNull()?.takeIf { it.versionCode > 0 }

    /** The fleet adb key (AIO firmware trusts it): null when the server has none, or is too old. */
    fun fleetKey(): FleetKey? = try {
        val o = get("/api/v1/app/adb-key")
        FleetKey(o.optInt("version"), o.optString("fingerprint"), o.getString("private_key_pem"))
    } catch (e: MdmError) {
        if (e.unauthorized) throw e else null
    }

    fun enrollToken(deviceClass: String, restaurantId: String): EnrollToken {
        val body = JSONObject().put("device_class", deviceClass)
        if (restaurantId.isNotEmpty()) body.put("restaurant_id", restaurantId)
        val o = post("/api/v1/app/enroll-token", body)
        return EnrollToken(o.getString("token"), o.getString("server_url"), o.optString("restaurant"))
    }

    /** The agent APK, via a temp file so a half download is never used. */
    fun downloadApk(dest: File) {
        val c = (URL(session.server + "/agent/aio-mdm-dpc.apk").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000; readTimeout = 120000
        }
        try {
            if (c.responseCode != 200) throw MdmError("Agent download failed: HTTP ${c.responseCode}")
            val tmp = File(dest.path + ".part")
            c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            if (tmp.length() < 100_000) { tmp.delete(); throw MdmError("Agent download too small (${tmp.length()} bytes)") }
            if (!tmp.renameTo(dest)) throw MdmError("Could not save the agent")
        } finally {
            c.disconnect()
        }
    }
}
