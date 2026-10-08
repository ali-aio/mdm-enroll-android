package com.aioapp.enroll.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

const val TAG = "AioDiscovery"

/** A device found on the Wi-Fi. kind: "pairing" (its pairing-code dialog is open) or "connect". */
data class Svc(val name: String, val kind: String, val host: String, val port: Int, val plain: Boolean) {
    val address get() = "$host:$port"

    /** adb-<SERIAL>-<random> (Wireless debugging) or adb-<SERIAL> (plain adb, e.g. AIO firmware on 5555). */
    val serial: String
        get() = Regex("^adb-(.+)-[A-Za-z0-9]{4,8}$").find(name)?.groupValues?.get(1)
            ?: Regex("^adb-([A-Za-z0-9]{8,20})$").find(name)?.groupValues?.get(1) ?: ""
}

/**
 * Watches the Wi-Fi for adb services with Android's own mDNS (NsdManager), which resolves each
 * service to the address it really answers on. Three kinds: pairing screens, Wireless-debugging
 * connect ports, and plain adb (AIO firmware with the fleet key, or `adb tcpip`).
 */
class Discovery(ctx: Context) {
    private val nsd = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var lock: WifiManager.MulticastLock? = null
    private val types = mapOf("_adb-tls-pairing._tcp" to "pairing", "_adb-tls-connect._tcp" to "connect", "_adb._tcp" to "connect")
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val watches = ConcurrentHashMap<String, Any>() // key -> the per-service callback, API 34+
    private val found = ConcurrentHashMap<String, Svc>()   // key: type|name
    private val resolver = Executors.newSingleThreadExecutor()
    private val _services = MutableStateFlow<List<Svc>>(emptyList())
    val services: StateFlow<List<Svc>> = _services

    private fun publish() {
        val mine = ownAddresses()
        _services.value = found.values.filter { it.host !in mine }.sortedWith(compareBy({ it.kind }, { it.name }))
        Log.i(TAG, "publish: ${found.size} found, ${_services.value.size} shown (mine=$mine)")
    }

    fun start() {
        if (listeners.isNotEmpty()) return
        lock = wifi.createMulticastLock("aio-enroll").apply { setReferenceCounted(false); acquire() }
        for ((type, kind) in types) {
            val l = object : NsdManager.DiscoveryListener {
                override fun onServiceFound(info: NsdServiceInfo) {
                    Log.i(TAG, "found $type ${info.serviceName}")
                    resolve(type, kind, info)
                }
                override fun onServiceLost(info: NsdServiceInfo) {
                    Log.i(TAG, "lost $type ${info.serviceName}")
                    drop("$type|${info.serviceName}")
                }
                override fun onDiscoveryStarted(t: String) { Log.i(TAG, "started $t") }
                override fun onDiscoveryStopped(t: String) { Log.i(TAG, "stopped $t") }
                override fun onStartDiscoveryFailed(t: String, code: Int) { Log.w(TAG, "start failed $t code=$code") }
                override fun onStopDiscoveryFailed(t: String, code: Int) { Log.w(TAG, "stop failed $t code=$code") }
            }
            runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, l); listeners += l }
                .onFailure { Log.w(TAG, "discoverServices $type: $it") }
        }
    }

    fun stop() {
        listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        listeners.clear()
        if (Build.VERSION.SDK_INT >= 34) {
            watches.values.forEach { cb -> runCatching { nsd.unregisterServiceInfoCallback(cb as NsdManager.ServiceInfoCallback) } }
        }
        watches.clear()
        found.clear(); publish()
        runCatching { lock?.release() }
    }

    private fun drop(key: String) {
        found.remove(key)
        if (Build.VERSION.SDK_INT >= 34) {
            (watches.remove(key) as? NsdManager.ServiceInfoCallback)?.let { runCatching { nsd.unregisterServiceInfoCallback(it) } }
        } else {
            watches.remove(key)
        }
        publish()
    }

    private fun resolve(type: String, kind: String, info: NsdServiceInfo) {
        val key = "$type|${info.serviceName}"
        if (watches.containsKey(key)) return
        if (Build.VERSION.SDK_INT >= 34) watch34(key, type, kind, info) else resolveLegacy(key, type, kind, info)
    }

    /**
     * Android 14 deprecated resolveService, and on 14+ it hands back a service whose `host` is null,
     * so nothing was ever published. The callback below is the supported way, and it keeps the
     * record up to date (a device that changes port updates in place).
     */
    private fun watch34(key: String, type: String, kind: String, info: NsdServiceInfo) {
        val cb = object : NsdManager.ServiceInfoCallback {
            override fun onServiceUpdated(i: NsdServiceInfo) {
                val host = i.hostAddresses.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }
                    .firstOrNull { !it.startsWith("10.0.2.") }
                Log.i(TAG, "resolved ${i.serviceName} -> $host:${i.port}")
                if (host != null) {
                    found[key] = Svc(i.serviceName, kind, host, i.port, type == "_adb._tcp")
                    publish()
                }
            }
            override fun onServiceLost() { drop(key) }
            override fun onServiceInfoCallbackUnregistered() {}
            override fun onServiceInfoCallbackRegistrationFailed(code: Int) {
                Log.w(TAG, "watch failed ${info.serviceName} code=$code")
                watches.remove(key)
            }
        }
        watches[key] = cb
        runCatching { nsd.registerServiceInfoCallback(info, resolver, cb) }
            .onFailure { Log.w(TAG, "registerServiceInfoCallback: $it"); watches.remove(key) }
    }

    /** NsdManager resolves one service at a time on older Android, so resolve in a queue. */
    @Suppress("DEPRECATION")
    private fun resolveLegacy(key: String, type: String, kind: String, info: NsdServiceInfo) {
        watches[key] = info
        resolver.execute {
            val done = java.util.concurrent.CountDownLatch(1)
            runCatching {
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(i: NsdServiceInfo, code: Int) {
                        Log.w(TAG, "resolve failed ${i.serviceName} code=$code")
                        watches.remove(key); done.countDown()
                    }
                    override fun onServiceResolved(i: NsdServiceInfo) {
                        val host = (i.host as? Inet4Address)?.hostAddress
                        Log.i(TAG, "resolved ${i.serviceName} -> $host:${i.port}")
                        if (host != null && !host.startsWith("10.0.2.")) {
                            found[key] = Svc(i.serviceName, kind, host, i.port, type == "_adb._tcp")
                            publish()
                        }
                        done.countDown()
                    }
                })
            }.onFailure { watches.remove(key); done.countDown() }
            done.await(6, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    /** Drops a service right away (e.g. a pairing screen that was used or closed). */
    fun forget(s: Svc) {
        found.entries.filter { it.value == s }.map { it.key }.forEach { drop(it) }
    }

    private fun ownAddresses(): Set<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { n -> n.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }.toSet()
    }.getOrDefault(emptySet())
}
