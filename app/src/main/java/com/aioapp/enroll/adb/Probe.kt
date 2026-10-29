package com.aioapp.enroll.adb

const val DPC_PKG = "aio.app.mdmclient.dpc"
const val DPC_ADMIN = "aio.app.mdmclient.dpc.MdmDeviceAdminReceiver"
const val FIRMWARE_PKG = "com.aioapp.mdm"

data class Owner(val set: Boolean = false, val ours: Boolean = false, val pkg: String = "")

/** What the device says about itself; the same checks the desktop app makes. */
data class Probe(
    val serial: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val android: String = "",
    val product: String = "", // ro.product.name: the model key the agent reports (Build.PRODUCT)
    val owner: Owner = Owner(),
    val accounts: List<String> = emptyList(),
    val users: Int = 1,
    val firmwareVersion: String? = null, // AIO firmware (its MDM client is part of the image)
    val dpcVersion: String = "",
    val dpcCode: Long = 0,
) {
    val name get() = listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ").let { n ->
        // "Google Pixel 6"; drop the maker when the model already starts with it ("SUNMI SUNMI D2s")
        if (model.startsWith(manufacturer, ignoreCase = true)) model else n
    }

    /** Why our agent can't become Device Owner, or null. Accounts only matter before it is ours. */
    val blockedReason: String?
        get() = when {
            owner.set && !owner.ours -> "Owned by ${owner.pkg} — factory reset"
            accounts.isNotEmpty() && !owner.ours -> "Has an account — factory reset, don't add an account"
            users > 1 && !owner.ours -> "Has a second user — remove the work profile or guest user"
            else -> null
        }

    companion object {
        /**
         * Everything in one shell command. Each adb stream is a round trip and some of them stall
         * for seconds on real devices, so eleven of them made a probe take a minute; one takes
         * under a second. The sections come back between [MARK] lines.
         */
        fun read(l: AdbLink): Probe {
            val out = l.shell(
                listOf(
                    "echo $MARK:prop", "getprop ro.serialno", "getprop ro.product.manufacturer",
                    "getprop ro.product.model", "getprop ro.build.version.release", "getprop ro.product.name",
                    "echo $MARK:owner", "dumpsys device_policy",
                    "echo $MARK:acct", "dumpsys account",
                    "echo $MARK:users", "pm list users",
                    "echo $MARK:dpc", "dumpsys package $DPC_PKG",
                    "echo $MARK:fw", "dumpsys package $FIRMWARE_PKG",
                    "echo $MARK:end",
                ).joinToString("; ")
            )
            val part = split(out)
            val props = part["prop"].orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
            val fwDump = part["fw"].orEmpty()
            return Probe(
                serial = props.getOrElse(0) { "" },
                manufacturer = props.getOrElse(1) { "" },
                model = props.getOrElse(2) { "" },
                android = props.getOrElse(3) { "" },
                product = props.getOrElse(4) { "" },
                owner = parseOwner(part["owner"].orEmpty()),
                accounts = parseAccounts(part["acct"].orEmpty()),
                users = part["users"].orEmpty().lines().count { it.trim().startsWith("UserInfo{") }.coerceAtLeast(1),
                // "Unable to find package" when it isn't installed, and then there is no versionName
                firmwareVersion = versionName(fwDump).takeIf { it.isNotEmpty() },
                dpcVersion = versionName(part["dpc"].orEmpty()),
                dpcCode = versionCode(part["dpc"].orEmpty()),
            )
        }

        private const val MARK = "__aio__"

        /** Cuts the one big output back into the pieces each command produced. */
        fun split(out: String): Map<String, String> {
            val map = mutableMapOf<String, StringBuilder>()
            var cur: StringBuilder? = null
            for (line in out.lines()) {
                val t = line.trim()
                if (t.startsWith("$MARK:")) {
                    val name = t.removePrefix("$MARK:")
                    cur = if (name == "end") null else StringBuilder().also { map[name] = it }
                } else cur?.append(line)?.append('\n')
            }
            return map.mapValues { it.value.toString() }
        }

        fun parseOwner(dump: String): Owner {
            val lines = dump.lines()
            val i = lines.indexOfFirst { it.contains("Device Owner:") }
            if (i < 0) return Owner()
            val block = lines.drop(i + 1).take(3).joinToString("\n")
            if (!block.contains("admin=")) return Owner()
            val pkg = block.substringAfter("package=", "").split(Regex("\\s")).firstOrNull().orEmpty()
            return Owner(true, block.contains("$DPC_PKG/$DPC_ADMIN"), pkg)
        }

        fun parseAccounts(dump: String): List<String> = dump.lines().mapNotNull { l ->
            val rest = l.trim().removePrefix("Account {name=").takeIf { it != l.trim() } ?: return@mapNotNull null
            val name = rest.substringBefore(", type=")
            val type = rest.substringAfter(", type=", "").trimEnd('}')
            if (type == "com.google") "$name (Google)" else "$name (${type.substringAfterLast('.')})"
        }.distinct()

        fun versionName(dump: String) = dump.lines().firstNotNullOfOrNull { it.trim().removePrefix("versionName=").takeIf { v -> v != it.trim() } }?.split(" ")?.first().orEmpty()
        fun versionCode(dump: String) = dump.lines().firstNotNullOfOrNull { it.trim().removePrefix("versionCode=").takeIf { v -> v != it.trim() } }?.split(" ")?.first()?.toLongOrNull() ?: 0L
    }
}
