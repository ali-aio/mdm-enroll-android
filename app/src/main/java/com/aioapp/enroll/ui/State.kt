package com.aioapp.enroll.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aioapp.enroll.App
import com.aioapp.enroll.adb.AdbLink
import com.aioapp.enroll.adb.CLASSES
import com.aioapp.enroll.adb.Probe
import com.aioapp.enroll.adb.STEPS
import com.aioapp.enroll.adb.Svc
import com.aioapp.enroll.adb.Outcome
import com.aioapp.enroll.adb.enrollDevice
import com.aioapp.enroll.mdm.DEFAULT_SERVER
import com.aioapp.enroll.mdm.MdmApi
import com.aioapp.enroll.mdm.MdmError
import com.aioapp.enroll.mdm.Restaurant
import com.aioapp.enroll.mdm.SerialClass
import com.aioapp.enroll.mdm.Session
import com.aioapp.enroll.mdm.Status
import com.aioapp.enroll.store.Account
import com.aioapp.enroll.store.LogEntry
import com.aioapp.enroll.store.Site
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException

enum class Tab { DEVICES, TODAY, ACCOUNT }

/** Where the pair sheet is: trying the keys, waiting for the device's dialog, asking, pairing, done. */
enum class Phase { TRYING, WAITING, CODE, PAIRING, DONE }

/** One live adb connection, with the last thing the device said about itself. */
class Conn(val link: AdbLink, val name: String, val probe: Probe, val ready: Boolean = false) {
    val host get() = link.host
    val serial get() = probe.serial
}

data class Sheet(
    val host: String,
    val name: String,
    val serial: String,
    val cls: SerialClass?,
    val phase: Phase,
    val pairAddr: String? = null, // its pairing service, once it has announced one
    val code: String = "",
    val error: String? = null,
    val noCode: Boolean = false,  // connected on a key it already trusted
)

/** An enrolment in flight (or just finished) on one device. */
data class Job(val host: String, val step: Int = 0, val error: String? = null, val outcome: Outcome? = null)

/** A device as a row in the list: the same device keeps the same [key] across scans. */
data class Dev(
    val key: String,
    val name: String,
    val host: String,
    val port: Int,
    val serial: String,
    val cls: SerialClass?,
    val status: Status?,
    val connected: Boolean,
) {
    val enrolled get() = cls?.kind == "fleet" && status?.enrolled == true
}

data class Ui(
    val booting: Boolean = true,
    val session: Session? = null,
    val accounts: List<Account> = emptyList(),
    val signingIn: String? = null,
    val signInError: String? = null,
    val profile: String = "",
    val avatar: Bitmap? = null,
    val site: Site = Site("", ""),
    val restaurants: List<Restaurant> = emptyList(),
    val fleetKey: Boolean = false,
    val tab: Tab = Tab.DEVICES,
    val svcs: List<Svc> = emptyList(),
    val classes: Map<String, SerialClass> = emptyMap(),
    val statuses: Map<String, Status> = emptyMap(),
    val conns: Map<String, Conn> = emptyMap(),
    val chosen: Map<String, String> = emptyMap(), // host -> device class picked for it
    val sheet: Sheet? = null,
    val openHost: String? = null,
    val job: Job? = null,
    val log: List<LogEntry> = emptyList(),
    val banner: String? = null,
    val onlyOurs: Boolean = false,
    val showAll: Boolean = false, // the Wi-Fi list is cut to SHOWN_AT_FIRST until this is set
) {
    private fun labelled(serial: String) = classes[serial]
    private fun named(serial: String, fallback: String) = classes[serial]?.name?.ifEmpty { null } ?: fallback

    /** Devices with their pairing dialog open right now. */
    val pairing: List<Dev>
        get() = svcs.filter { it.kind == "pairing" && it.host !in conns.keys }
            .map { s -> Dev("p:" + s.serial.ifEmpty { s.host }, named(s.serial, "Device"), s.host, s.port, s.serial, labelled(s.serial), statuses[s.serial], false) }
            .distinctBy { it.key }

    /** Already in the MDM: our agent owns it, or the MDM says the serial is one of its own. */
    private fun done(c: Conn) = c.probe.owner.ours || classes[c.serial]?.kind == "fleet"

    /** Connected on their own and not enrolled yet: one tap away. */
    val ready: List<Dev>
        get() = conns.values.filter { it.ready && !done(it) }.sortedBy { it.name }.map { it.dev() }

    /** Enrolled, however they got connected. */
    val enrolled: List<Dev>
        get() = conns.values.filter { done(it) }.sortedBy { it.name }.map { it.dev() }

    /** Paired by hand with a code, not enrolled yet. */
    val connected: List<Dev>
        get() = conns.values.filter { !it.ready && !done(it) }.sortedBy { it.name }.map { it.dev() }

    private fun Conn.dev() = Dev("c:$host", name, host, link.port, serial, labelled(serial), statuses[serial], true)

    /** Everything else on the Wi-Fi, ours first once the MDM has told us which are ours. */
    val nearby: List<Dev>
        get() {
            val busy = conns.keys + pairing.map { it.host }
            var list = svcs.filter { it.kind == "connect" && it.host !in busy }
                .map { s -> Dev(s.serial.ifEmpty { s.host }, named(s.serial, "Device"), s.host, s.port, s.serial, labelled(s.serial), statuses[s.serial], false) }
                .distinctBy { it.key }
                .sortedWith(compareByDescending<Dev> { it.cls?.ours == true }.thenBy { it.host })
            if (onlyOurs) list = list.filter { it.cls?.ours == true }
            return list
        }

    val nearbyTotal: Int get() = svcs.count { it.kind == "connect" && it.host !in (conns.keys + pairing.map { r -> r.host }) }
    val openConn: Conn? get() = openHost?.let { conns[it] }
}

private const val TAG = "AioPair"

/** How many of the devices on the Wi-Fi to show before "Show more". */
const val SHOWN_AT_FIRST = 5

/** How long a device that refused the fleet key is left alone before trying it again. */
private const val RETRY_MS = 60_000L

class EnrollVm(private val app: App) : AndroidViewModel(app) {
    private val store = app.store
    private val disc = app.discovery
    private var api: MdmApi? = null
    private var lastStatuses = 0L
    private val dismissed = mutableSetOf<String>() // pairing addresses the person closed
    private val popped = mutableSetOf<String>()    // pairing addresses already shown once
    private val trying = mutableSetOf<String>()    // hosts an auto-connect is running for
    private val refused = mutableMapOf<String, Long>() // host -> when it last turned us down

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui

    init {
        val sess = store.session
        _ui.update { it.copy(booting = false, session = sess, accounts = store.accounts(), log = store.log(), onlyOurs = store.onlyOurs) }
        if (sess != null) afterSignIn(sess)
        watchWifi()
        viewModelScope.launch {
            while (isActive) {
                if (_ui.value.session != null) refreshLabels()
                delay(8_000)
            }
        }
    }

    // ---- sign-in ----

    fun signIn(username: String, password: String, save: Boolean) {
        val u = username.trim()
        if (u.isEmpty() || password.isEmpty()) return
        _ui.update { it.copy(signingIn = u, signInError = null) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { MdmApi.login(DEFAULT_SERVER, u, password) } }
                .onSuccess { sess ->
                    store.session = sess
                    store.rememberAccount(sess.username, sess.username, if (save) password else null)
                    _ui.update { it.copy(session = sess, signingIn = null, accounts = store.accounts()) }
                    afterSignIn(sess)
                }
                .onFailure { e -> _ui.update { it.copy(signingIn = null, signInError = e.message ?: "Sign-in failed") } }
        }
    }

    /** A saved account with a saved password signs in on one tap. */
    fun signInSaved(a: Account) {
        val pw = store.password(a.username)
        if (pw == null) _ui.update { it.copy(signInError = null, signingIn = null) } else signIn(a.username, pw, true)
    }

    fun forgetAccount(a: Account) {
        store.forgetAccount(a.username)
        _ui.update { it.copy(accounts = store.accounts()) }
    }

    fun clearSignInError() = _ui.update { it.copy(signInError = null) }

    fun showAll() = _ui.update { it.copy(showAll = true) }

    private fun afterSignIn(sess: Session) {
        val a = MdmApi(sess)
        api = a
        _ui.update { it.copy(profile = sess.username, site = store.site(sess.username)) }
        viewModelScope.launch {
            val p = io { a.profile() } ?: return@launch
            store.rememberAccount(sess.username, p.name, null) // the saved password, if any, stays
            _ui.update { it.copy(profile = p.name.ifEmpty { sess.username }, accounts = store.accounts()) }
            if (store.fleetKeyPem == null || store.fleetKeyVersion != p.adbKeyVersion) fetchFleetKey(a)
            else _ui.update { it.copy(fleetKey = true) }
            val bytes = io { a.avatar() }
            android.util.Log.i("AioMdm", "avatar bytes=${bytes?.size ?: -1}")
            bytes?.let {
                val bm = BitmapFactory.decodeByteArray(it, 0, it.size)
                android.util.Log.i("AioMdm", "avatar decoded=${bm != null}")
                bm?.let { b -> _ui.update { u -> u.copy(avatar = b) } }
            }
            io { a.restaurants() }?.let { r -> _ui.update { it.copy(restaurants = r) } }
        }
    }

    /** The key AIO firmware trusts. A 404 just means none is uploaded: codes still work. */
    private suspend fun fetchFleetKey(a: MdmApi) {
        val k = io { a.fleetKey() }
        if (k == null) {
            store.fleetKeyPem = null
            _ui.update { it.copy(fleetKey = false) }
            return
        }
        store.fleetKeyPem = k.privateKeyPem
        store.fleetKeyVersion = k.version
        store.fleetKeyFingerprint = k.fingerprint
        _ui.update { it.copy(fleetKey = app.fleetIdentity != null) }
    }

    fun signOut() {
        val a = api
        _ui.value.conns.values.forEach { it.link.closeQuietly() }
        disc.stop()
        api = null
        store.session = null
        store.fleetKeyPem = null
        store.fleetKeyVersion = 0
        trying.clear()
        refused.clear()
        _ui.value = Ui(booting = false, accounts = store.accounts(), log = store.log(), onlyOurs = store.onlyOurs)
        viewModelScope.launch { withContext(Dispatchers.IO) { a?.logout() } }
    }

    // ---- what is on the Wi-Fi ----

    fun resume() {
        val signedIn = _ui.value.session != null
        android.util.Log.i("AioDiscovery", "resume: signedIn=$signedIn")
        if (signedIn) disc.start()
    }

    fun pause() = disc.stop()

    private fun watchWifi() = viewModelScope.launch {
        disc.services.collect { list ->
            _ui.update { u ->
                var sheet = u.sheet
                val live = list.filter { it.kind == "pairing" }
                if (sheet != null && sheet.phase == Phase.WAITING) {
                    live.firstOrNull { it.host == sheet.host }?.let { sheet = sheet!!.copy(phase = Phase.CODE, pairAddr = it.address) }
                }
                u.copy(svcs = list, sheet = sheet)
            }
            autoSheet(list)
            autoConnect(list)
        }
    }

    /**
     * Every address we can see, tried on :5555 with the fleet key. Anything that answers is running
     * AIO firmware — that key is built into the image — so it goes straight into the connected list
     * as enrollable, with nobody tapping Pair. A device that refuses is left alone for [RETRY_MS].
     */
    private fun autoConnect(list: List<Svc>) {
        if (app.fleetIdentity == null) { android.util.Log.i(TAG, "auto: no fleet key"); return }
        val now = System.currentTimeMillis()
        val hosts = list.map { it.host }.distinct()
            .filter { it !in _ui.value.conns.keys && it !in trying && now - (refused[it] ?: 0) > RETRY_MS }
        if (hosts.isNotEmpty()) android.util.Log.i(TAG, "auto: trying $hosts")
        for (host in hosts) {
            if (trying.size >= 4) return // a phone's Wi-Fi, not a scanner: a few at a time
            trying += host
            viewModelScope.launch {
                try {
                    val link = withTimeoutOrNull(20_000) { connectFleet(host) }
                    if (link == null) {
                        android.util.Log.i(TAG, "auto: $host refused")
                        refused[host] = System.currentTimeMillis()
                        return@launch
                    }
                    if (host in _ui.value.conns.keys) { link.closeQuietly(); return@launch } // beaten to it
                    android.util.Log.i(TAG, "auto-connected $host")
                    val probe = withTimeoutOrNull(45_000) {
                        withContext(Dispatchers.IO) { runCatching { Probe.read(link) }.getOrNull() }
                    }
                    if (probe == null) { link.closeQuietly(); refused[host] = System.currentTimeMillis() }
                    else register(link, probe, ready = true)
                } finally { trying -= host }
            }
        }
    }

    /** The fleet key on :5555, and on any plain port the device advertises. */
    private suspend fun connectFleet(host: String): AdbLink? = withContext(Dispatchers.IO) {
        val id = app.fleetIdentity ?: return@withContext null
        val ports = (listOf(5555) + _ui.value.svcs.filter { it.host == host && it.plain }.map { it.port }).distinct()
        for (port in ports) {
            if (!reachable(host, port)) { android.util.Log.i(TAG, "auto:   $host:$port closed"); continue }
            val l = AdbLink(id, host, port, timeoutSec = 6)
            val r = runCatching { l.open() }
            android.util.Log.i(TAG, "auto:   $host:$port fleet -> ${r.getOrNull() ?: r.exceptionOrNull()}")
            if (r.getOrDefault(false)) return@withContext l
            l.closeQuietly()
        }
        null
    }

    /**
     * A new pairing dialog opens the code sheet by itself; one the person closed stays closed until
     * it goes away and comes back. A sheet whose dialog disappears was cancelled on the device.
     */
    private fun autoSheet(list: List<Svc>) {
        val live = list.filter { it.kind == "pairing" }.map { it.address }.toSet()
        (popped - live).forEach { popped -= it; dismissed -= it }
        val sh = _ui.value.sheet
        if (sh != null) {
            if (sh.phase == Phase.CODE && sh.pairAddr != null && sh.pairAddr !in live) {
                _ui.update { it.copy(sheet = null, banner = "Pairing was cancelled on the device.") }
            }
            return
        }
        val fresh = list.firstOrNull { it.kind == "pairing" && it.address !in popped && it.address !in dismissed && it.host !in _ui.value.conns.keys }
            ?: return
        popped += fresh.address
        val u = _ui.value
        _ui.update {
            it.copy(sheet = Sheet(fresh.host, u.classes[fresh.serial]?.name?.ifEmpty { null } ?: "Device", fresh.serial, u.classes[fresh.serial], Phase.CODE, fresh.address))
        }
    }

    /** What the MDM knows about the serials we can see. Quiet on failure: labels are a nicety. */
    private suspend fun refreshLabels() {
        val a = api ?: return
        val u = _ui.value
        val seen = (u.svcs.map { it.serial } + u.conns.values.map { it.serial }).filter { it.isNotEmpty() }.distinct()
        val need = seen.filter { it !in u.classes }
        if (need.isNotEmpty()) {
            io(quiet = true) { a.classify(need) }?.let { m ->
                _ui.update { it.copy(classes = it.classes + m) }
            }
        }
        val now = System.currentTimeMillis()
        if (now - lastStatuses < 20_000) return
        lastStatuses = now
        val cls = _ui.value.classes
        val want = (seen.filter { cls[it]?.kind == "fleet" } + u.conns.values.map { it.serial } +
            if (u.tab == Tab.TODAY) u.log.map { it.serial } else emptyList()).filter { it.isNotEmpty() }.distinct()
        if (want.isEmpty()) return
        io(quiet = true) { a.statuses(want) }?.let { m ->
            _ui.update { u2 ->
                // "Enrolled" only while it is still checking in; the chip shows "Offline 3 d" otherwise
                val marked = u2.classes.mapValues { (s, c) ->
                    if (c.kind != "fleet") c else c.copy().also { it.lastSeen = m[s]?.lastSeenAt; it.checked = m.containsKey(s) }
                }
                u2.copy(statuses = u2.statuses + m, classes = marked)
            }
        }
    }

    fun setOnlyOurs(v: Boolean) {
        store.onlyOurs = v
        _ui.update { it.copy(onlyOurs = v) }
    }

    // ---- connecting and pairing ----

    /**
     * Pair, the way the desktop app does it: try the keys the device may already trust and only ask
     * for a code when both are refused.
     */
    fun pair(row: Dev) {
        _ui.update { it.copy(sheet = Sheet(row.host, row.name, row.serial, row.cls, Phase.TRYING)) }
        viewModelScope.launch {
            // Blocking adb handshakes can outlast their own timeout; never spin forever.
            val link = withTimeoutOrNull(30_000) { connectKnown(row.host) }
            if (_ui.value.sheet?.host != row.host) { link?.closeQuietly(); return@launch } // closed meanwhile
            if (link != null) return@launch adopt(link, noCode = true)
            val p = _ui.value.svcs.firstOrNull { it.kind == "pairing" && it.host == row.host }
            if (p != null) dismissed -= p.address
            android.util.Log.i(TAG, "no key worked for ${row.host}; pairing dialog=${p?.address}")
            val why = if (app.fleetIdentity == null)
                "No fleet key on this server, and its pairing screen is not open."
            else "No key it trusts, and its pairing screen is not open."
            _ui.update { u ->
                u.copy(sheet = u.sheet?.copy(
                    phase = if (p != null) Phase.CODE else Phase.WAITING,
                    pairAddr = p?.address,
                    // Say why we are waiting, or the sheet is an unexplained spinner forever.
                    error = if (p != null) null else why,
                ))
            }
        }
    }

    fun openSheet(row: Dev) {
        val addr = _ui.value.svcs.firstOrNull { it.kind == "pairing" && it.host == row.host }?.address
        dismissed -= (addr ?: "")
        _ui.update { it.copy(sheet = Sheet(row.host, row.name, row.serial, row.cls, if (addr != null) Phase.CODE else Phase.WAITING, addr)) }
    }

    fun typeCode(code: String) = _ui.update { u ->
        u.copy(sheet = u.sheet?.copy(code = code.filter { it.isDigit() }.take(6), error = null))
    }

    fun closeSheet() {
        _ui.value.sheet?.pairAddr?.let { dismissed += it }
        _ui.update { it.copy(sheet = null) }
    }

    fun submitCode() {
        val sh = _ui.value.sheet ?: return
        val addr = sh.pairAddr ?: return
        if (sh.code.length != 6 || sh.phase == Phase.PAIRING) return
        _ui.update { it.copy(sheet = sh.copy(phase = Phase.PAIRING, error = null)) }
        viewModelScope.launch {
            val host = addr.substringBeforeLast(':')
            val port = addr.substringAfterLast(':').toIntOrNull() ?: return@launch
            val paired = withContext(Dispatchers.IO) {
                runCatching {
                    val l = AdbLink(app.ownIdentity, host, port)
                    try { l.pair(host, port, sh.code) } finally { l.closeQuietly() }
                }
            }
            if (_ui.value.sheet?.host != sh.host) return@launch
            paired.onFailure { e ->
                _ui.update { it.copy(sheet = it.sheet?.copy(phase = Phase.CODE, code = "", error = pairMessage(e))) }
                return@launch
            }
            // The code is spent and the device now trusts this phone: all that is left is connecting.
            val link = connectAfterPair(sh.host)
            if (_ui.value.sheet?.host != sh.host) { link?.closeQuietly(); return@launch }
            if (link == null) {
                _ui.update { it.copy(sheet = it.sheet?.copy(phase = Phase.CODE, code = "", error = "Paired, but it didn't answer. Check Wireless debugging is still on.")) }
            } else {
                adopt(link, noCode = false)
            }
        }
    }

    private fun pairMessage(e: Throwable): String = when {
        e.message?.contains("Failed to pair", true) == true -> "Wrong code, or the dialog closed. Open it again and read the new code."
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Pairing failed."
    }

    /** The addresses this device answers on, each tried with the fleet key and this phone's own key. */
    private suspend fun connectKnown(host: String): AdbLink? = withContext(Dispatchers.IO) {
        val ports = (_ui.value.svcs.filter { it.host == host && it.kind == "connect" }.map { it.port to it.plain } + (5555 to true))
            .distinctBy { it.first }
        val fleet = app.fleetIdentity
        android.util.Log.i(TAG, "connect $host: ports=${ports.map { it.first }} fleetKey=${fleet != null}")
        for ((port, plain) in ports) {
            // libadb's own timeout covers the adb handshake, not the TCP connect, and a port with
            // nothing on it would block for minutes. Knock first.
            if (!reachable(host, port)) { android.util.Log.i(TAG, "  $host:$port closed"); continue }
            // Plain adb checks the key itself; TLS only accepts one the device already trusts.
            val ids = if (plain) listOfNotNull(fleet, app.ownIdentity) else listOfNotNull(app.ownIdentity, fleet)
            for (id in ids) {
                val which = if (id === fleet) "fleet" else "own"
                val l = AdbLink(id, host, port, timeoutSec = 6) // on a LAN, adbd answers at once
                val t0 = System.currentTimeMillis()
                val r = runCatching { l.open() }
                android.util.Log.i(TAG, "  $host:$port $which ${System.currentTimeMillis() - t0}ms -> ${r.getOrNull() ?: r.exceptionOrNull()}")
                if (r.getOrDefault(false)) return@withContext l
                l.closeQuietly()
            }
        }
        null
    }

    /** Is anything listening? A 1.5 s knock, so a dead port costs 1.5 s instead of a TCP timeout. */
    private fun reachable(host: String, port: Int): Boolean = runCatching {
        java.net.Socket().use { it.connect(java.net.InetSocketAddress(host, port), 1_500) }
    }.isSuccess

    /** After pairing, the device re-announces its connect port; give it a moment to turn up. */
    private suspend fun connectAfterPair(host: String): AdbLink? {
        val deadline = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < deadline) {
            connectKnown(host)?.let { return it }
            delay(1_500)
        }
        return null
    }

    private suspend fun adopt(link: AdbLink, noCode: Boolean) {
        android.util.Log.i(TAG, "adopted ${link.address}; reading")
        val probe = withTimeoutOrNull(45_000) {
            withContext(Dispatchers.IO) { runCatching { Probe.read(link) }.onFailure { android.util.Log.w(TAG, "probe: $it") }.getOrNull() }
        }
        android.util.Log.i(TAG, "probe of ${link.address}: ${probe?.serial ?: "failed"}")
        if (probe == null) {
            link.closeQuietly()
            _ui.update { it.copy(sheet = it.sheet?.copy(phase = Phase.CODE, error = "Connected, but it stopped answering.")) }
            return
        }
        val name = register(link, probe, ready = noCode)
        _ui.update { u -> u.copy(sheet = u.sheet?.copy(phase = Phase.DONE, noCode = noCode, name = name, serial = probe.serial)) }
        delay(1_000)
        if (_ui.value.sheet?.host == link.host) _ui.update { it.copy(sheet = null, openHost = link.host, tab = Tab.DEVICES) }
    }

    /** Puts a live connection in the list, and returns the name to show for it. */
    private suspend fun register(link: AdbLink, probe: Probe, ready: Boolean = false): String {
        val name = probe.name.ifEmpty { _ui.value.sheet?.name?.takeIf { _ui.value.sheet?.host == link.host } ?: "Device" }
        _ui.update { u ->
            u.copy(
                conns = u.conns + (link.host to Conn(link, name, probe, ready)),
                chosen = if (u.chosen.containsKey(link.host)) u.chosen else u.chosen + (link.host to suggest(probe.serial)),
            )
        }
        refreshLabels()
        return name
    }

    /** The class the enrolled devices of this model already use, when the MDM knows one. */
    private fun suggest(serial: String): String =
        _ui.value.classes[serial]?.deviceClass?.takeIf { it in CLASSES } ?: ""

    fun disconnect(host: String) {
        refused[host] = System.currentTimeMillis() // a deliberate disconnect must not reconnect itself
        _ui.value.conns[host]?.link?.closeQuietly()
        _ui.update { it.copy(conns = it.conns - host, openHost = if (it.openHost == host) null else it.openHost, job = if (it.job?.host == host) null else it.job) }
    }

    // ---- the device page ----

    fun open(row: Dev) = _ui.update { it.copy(openHost = row.host) }
    fun back() = _ui.update { it.copy(openHost = null, job = if (it.job?.outcome != null || it.job?.error != null) null else it.job) }
    fun setTab(t: Tab) = _ui.update { it.copy(tab = t, openHost = null) }
    fun setClass(host: String, cls: String) = _ui.update { it.copy(chosen = it.chosen + (host to cls)) }
    fun dismissBanner() = _ui.update { it.copy(banner = null) }

    /** Say something once, in the snackbar: what a label means, why something didn't work. */
    fun say(msg: String) {
        if (msg.isNotBlank()) _ui.update { it.copy(banner = msg) }
    }

    /** Re-read the device, so the "before it can be enrolled" checklist ticks off as it is fixed. */
    fun refreshProbe(host: String) {
        val c = _ui.value.conns[host] ?: return
        viewModelScope.launch {
            val p = withContext(Dispatchers.IO) { runCatching { Probe.read(c.link) }.getOrNull() } ?: return@launch
            _ui.update { u ->
                if (u.conns[host] !== c) u else u.copy(conns = u.conns + (host to Conn(c.link, p.name.ifEmpty { c.name }, p)))
            }
        }
    }

    fun setSite(r: Restaurant) {
        val user = _ui.value.session?.username ?: return
        val s = Site(r.id, listOf(r.name, r.address).filter { it.isNotBlank() }.joinToString(" · "))
        store.setSite(user, s)
        _ui.update { it.copy(site = s) }
    }

    /**
     * The Enrol button on a ready row. It needs a class; without one there is nothing to do but
     * open the device and let the person pick.
     */
    fun enrolFromList(row: Dev) {
        val u = _ui.value
        // No restaurant is fine: the device lands in the MDM's onboarding inbox.
        if (u.chosen[row.host].orEmpty() !in CLASSES) { open(row); say("Pick what it is used as.") }
        else { open(row); enrol(row.host) }
    }

    fun enrol(host: String) {
        val conn = _ui.value.conns[host] ?: return
        val a = api ?: return
        val cls = _ui.value.chosen[host].orEmpty()
        if (cls !in CLASSES) return
        val site = _ui.value.site
        _ui.update { it.copy(job = Job(host)) }
        viewModelScope.launch {
            val apk = File(app.cacheDir, "aio-mdm-dpc.apk")
            withContext(Dispatchers.IO) {
                runCatching {
                    enrollDevice(conn.link, a, cls, site.id, apk) { step ->
                        _ui.update { u -> if (u.job?.host == host) u.copy(job = u.job.copy(step = step)) else u }
                    }
                }
            }.onSuccess { out ->
                _ui.update { it.copy(job = Job(host, STEPS.size, outcome = out)) }
                store.addLog(LogEntry(System.currentTimeMillis(), conn.name, out.serial, cls, out.restaurant.ifEmpty { site.name }, _ui.value.profile))
                _ui.update { it.copy(log = store.log(), classes = it.classes - out.serial) }
                lastStatuses = 0
                refreshProbe(host)
                refreshLabels()
            }.onFailure { e ->
                if (e is MdmError && e.unauthorized) signOut()
                else _ui.update { u -> if (u.job?.host == host) u.copy(job = u.job.copy(error = human(e))) else u }
            }
        }
    }

    fun clearJob() = _ui.update { it.copy(job = null) }

    private fun human(e: Throwable): String = when {
        e is MdmError -> e.message ?: "The MDM refused that."
        e is IOException -> "The device stopped answering — it may have gone to sleep. Wake it and try again."
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Enrolment failed."
    }

    /** One blocking call off the main thread; a dead session signs out. */
    private suspend fun <T> io(quiet: Boolean = false, block: () -> T): T? =
        withContext(Dispatchers.IO) { runCatching(block) }.fold({ it }, { e ->
            if (e is MdmError && e.unauthorized) withContext(Dispatchers.Main) { signOut() }
            else if (!quiet) _ui.update { it.copy(banner = e.message) }
            null
        })

    override fun onCleared() {
        _ui.value.conns.values.forEach { it.link.closeQuietly() }
        disc.stop()
    }

    companion object {
        fun factory(app: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = EnrollVm(app as App) as T
        }
    }
}
