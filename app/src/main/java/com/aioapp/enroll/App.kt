package com.aioapp.enroll

import android.app.Application
import com.aioapp.enroll.adb.AdbIdentity
import com.aioapp.enroll.adb.Discovery
import com.aioapp.enroll.store.Store

/** Holds the three things the whole app shares: storage, the Wi-Fi watcher and this phone's adb key. */
class App : Application() {
    val store: Store by lazy { Store(this) }
    val discovery: Discovery by lazy { Discovery(this) }

    /** This phone's own adb identity: made once, then kept. Pairing makes a device trust it. */
    val ownIdentity: AdbIdentity by lazy {
        store.ownKey?.let { runCatching { AdbIdentity.fromPkcs8Base64("this phone", it) }.getOrNull() }
            ?: AdbIdentity.generate().also { store.ownKey = it.pkcs8Base64() }
    }

    /** The fleet keys the server has given us, one per vendor. AIO firmware trusts one of them, so no code is needed. */
    val fleetIdentities: List<AdbIdentity>
        get() = store.fleetKeys.mapNotNull { (label, pem) -> runCatching { AdbIdentity.fromPem(label, pem) }.getOrNull() }
}
