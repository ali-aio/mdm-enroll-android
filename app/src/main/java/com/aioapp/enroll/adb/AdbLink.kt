package com.aioapp.enroll.adb

import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit

/**
 * One adb connection from this phone to another device's adbd, over Wi-Fi (plain TCP or TLS).
 * Blocking: call off the main thread.
 */
class AdbLink(val identity: AdbIdentity, val host: String, val port: Int, timeoutSec: Long = 10) : AbsAdbConnectionManager() {
    init {
        setApi(android.os.Build.VERSION.SDK_INT)
        setTimeout(timeoutSec, TimeUnit.SECONDS)
        setThrowOnUnauthorised(true) // a key the device doesn't trust fails fast instead of waiting on its prompt
    }

    val address get() = "$host:$port"

    override fun getPrivateKey(): PrivateKey = identity.privateKey
    override fun getCertificate(): Certificate = identity.certificate
    override fun getDeviceName(): String = "AIO Enroll"

    fun open(): Boolean {
        resetTlsContext()
        return connect(host, port)
    }

    /**
     * libadb builds its TLS context once and keeps it in a private static field, so a second
     * connection would silently reuse the first key. We try two keys per device (fleet, then this
     * phone's), so clear it and let [identity] be the one that is really offered.
     */
    private fun resetTlsContext() {
        runCatching {
            Class.forName("io.github.muntashirakon.adb.SslUtils")
                .getDeclaredField("sslContext").apply { isAccessible = true }.set(null, null)
        }
    }

    /** Runs a shell command and returns everything it printed. */
    fun shell(cmd: String): String {
        val t0 = System.currentTimeMillis()
        return try {
            readAll(openStream("shell:$cmd")).also {
                android.util.Log.i(TAG, "$address \$ ${cmd.take(40)} -> ${it.length}B ${System.currentTimeMillis() - t0}ms")
            }
        } catch (e: Throwable) {
            android.util.Log.w(TAG, "$address \$ ${cmd.take(40)} -> ${System.currentTimeMillis() - t0}ms $e")
            throw e
        }
    }

    fun prop(name: String) = shell("getprop $name").trim()

    /** Installs (or updates, keeping data) an APK on the device: the same as `adb install -r`. */
    fun install(apk: File): String {
        val s = openStream("exec:cmd package install -r -S ${apk.length()}")
        s.openOutputStream().use { out -> apk.inputStream().use { it.copyTo(out) }; out.flush() }
        return readAll(s)
    }

    /**
     * adb streams end by reporting EOF, and a device that stops answering mid-command never does,
     * so a plain read loop hangs for good. A watchdog closes the stream, which makes the read fail
     * and ends the loop.
     */
    private fun readAll(s: AdbStream): String = s.use {
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        val guard = Thread {
            runCatching { Thread.sleep(READ_TIMEOUT_MS) }
            if (!done.get()) runCatching { s.close() }
        }.apply { isDaemon = true; start() }
        try {
            val buf = ByteArrayOutputStream()
            val inp = it.openInputStream()
            val b = ByteArray(8192)
            while (true) {
                val n = try { inp.read(b) } catch (_: Exception) { -1 }
                if (n < 0) break
                buf.write(b, 0, n)
            }
            buf.toString(Charsets.UTF_8.name())
        } finally {
            done.set(true)
            guard.interrupt()
        }
    }

    fun closeQuietly() = runCatching { close() }

    private companion object {
        /** One shell command is a getprop or a dumpsys; neither takes anything like this long. */
        const val READ_TIMEOUT_MS = 20_000L
        const val TAG = "AioAdb"
    }
}
