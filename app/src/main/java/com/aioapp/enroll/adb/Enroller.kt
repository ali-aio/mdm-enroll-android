package com.aioapp.enroll.adb

import com.aioapp.enroll.mdm.MdmApi
import java.io.File

val STEPS = listOf(
    "Checking the device", "Getting a token", "Installing the agent", "Setting Device Owner",
    "Granting permissions", "Starting the agent", "Registering with the MDM", "First check-in",
    "Closing up",
)

/** [closed]: debugging and Developer options are off and the device is restarting. */
data class Outcome(val live: Boolean, val checked: Boolean, val serial: String, val restaurant: String, val batteryPct: Int, val hasBattery: Boolean, val agentVersion: String, val closed: Boolean = false)

/**
 * Enrols one device over its adb link: the same steps as the desktop app and tools/enroll-adb.sh.
 * Where it goes is decided on the MDM (it lands in the onboarding inbox). Its type follows its
 * model, except for a model that can be two things: the wizard asks, and sends the pick first.
 * [progress] is called as each step in [STEPS] starts. Throws with a message for the person.
 */
fun enrollDevice(link: AdbLink, api: MdmApi, apkCache: File, progress: (Int) -> Unit): Outcome {
    progress(0)
    val probe = Probe.read(link)
    probe.blockedReason?.let { throw IllegalStateException("Can’t be enrolled: $it.") }

    progress(1)
    val tok = api.enrollToken()

    progress(2)
    apkCache.delete() // never install a stale copy from an earlier version
    api.downloadApk(apkCache)
    val inst = link.install(apkCache)
    if (!inst.contains("Success")) throw IllegalStateException("Install failed: ${inst.trim()}")

    progress(3)
    if (!probe.owner.ours) {
        val out = link.shell("dpm set-device-owner $DPC_PKG/$DPC_ADMIN")
        if (!out.contains("Success")) throw IllegalStateException("set-device-owner failed: ${out.trim()}")
    }

    progress(4)
    link.shell("pm grant $DPC_PKG android.permission.READ_LOGS")
    link.shell("pm grant $DPC_PKG android.permission.WRITE_SECURE_SETTINGS")
    link.shell("appops set $DPC_PKG GET_USAGE_STATS allow")
    link.shell("appops set $DPC_PKG PROJECT_MEDIA allow")

    val serial = probe.serial.ifEmpty { link.prop("ro.serialno") }
    // A re-enrolled device was already checking in: its first check-in must be a newer one.
    val seenBefore = runCatching { api.statuses(listOf(serial))[serial]?.lastSeenAt }.getOrNull()

    progress(5)
    link.shell("am start -W -n $DPC_PKG/.ui.MainActivity --es server_url '${tok.serverUrl}' --es enroll_token '${tok.token}'")

    progress(6)
    val deadline = System.currentTimeMillis() + 30_000
    var registered = false
    while (System.currentTimeMillis() < deadline) {
        if (runCatching { api.statuses(listOf(serial))[serial]?.enrolled }.getOrNull() == true) { registered = true; break }
        Thread.sleep(1000)
    }
    if (!registered) throw IllegalStateException("The server did not confirm within 30 s. Check the device has internet.")

    progress(7)
    val out = firstCheckIn(api, serial, tok.restaurant, seenBefore)
    if (!out.live) return out // not confirmed: leave debugging on so the setup can be run again

    // Closing up (28 Oct 2026): the agent turns off wireless and USB debugging and Developer
    // options, then restarts the device. It does it, not us: switching debugging off ends this
    // connection, and anything still queued on it with it. It answers "closing" first. An agent
    // from before 0.12.4 doesn't know the message, and the person is told to do it by hand.
    progress(8)
    val ans = runCatching { link.shell("am broadcast -n $DPC_PKG/.FinishSetupReceiver -a $DPC_PKG.FINISH_SETUP") }.getOrDefault("")
    return out.copy(closed = ans.contains("closing"))
}

private fun firstCheckIn(api: MdmApi, serial: String, restaurant: String, seenBefore: String?): Outcome {
    var out = Outcome(false, false, serial, restaurant, 0, false, "")
    val deadline = System.currentTimeMillis() + 60_000
    while (true) {
        val s = runCatching { api.statuses(listOf(serial))[serial] }.getOrNull()
        if (s != null) {
            out = out.copy(restaurant = s.restaurant.ifEmpty { out.restaurant }, batteryPct = s.batteryPct, hasBattery = s.hasBattery, agentVersion = s.agentVersion)
            val online = s.online ?: return out // an older server can't tell: don't wait for nothing
            out = out.copy(checked = true)
            if (online && s.lastSeenAt != null && s.lastSeenAt != seenBefore) return out.copy(live = true)
        }
        if (System.currentTimeMillis() > deadline) return out
        Thread.sleep(2000)
    }
}
