package com.aioapp.enroll.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aioapp.enroll.mdm.SerialClass
import com.aioapp.enroll.mdm.Status

const val STALE_MS = 3_600_000L // not heard from for an hour: not managed right now

/** "7 min", "3 h", "12 d" — the same shortening as the desktop app. */
fun agoShort(iso: String?): String {
    val t = parseIso(iso) ?: return ""
    val s = (System.currentTimeMillis() - t) / 1000.0
    return when {
        s < 3600 -> "${Math.round(s / 60)} min"
        s < 86400 -> "${Math.round(s / 3600)} h"
        else -> "${Math.round(s / 86400)} d"
    }
}

fun parseIso(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
}

/** What the MDM knows about a device, in two or three words. Null when it has told us nothing. */
fun classLabel(c: SerialClass?): Pair<String, ChipTone>? {
    if (c == null) return null
    val dc = if (c.deviceClass.isNotEmpty()) " · " + c.deviceClass else ""
    val lastSeen = parseIso(c.lastSeen)
    return when (c.kind) {
        "fleet" ->
            if (!c.checked || (lastSeen != null && System.currentTimeMillis() - lastSeen < STALE_MS)) "✓ Enrolled$dc" to ChipTone.OK
            else (if (c.lastSeen != null) "Offline " + agoShort(c.lastSeen) else "In MDM · never seen") to ChipTone.WARN
        "production" -> "AIO · new" to ChipTone.WARN
        "family" -> "Known model$dc" to ChipTone.WARN
        "lookalike" -> "Serial like ours" to ChipTone.NEUTRAL
        "other" -> "Not ours" to ChipTone.NEUTRAL
        else -> null
    }
}

/** The full sentence, for the long-press tooltip. */
fun classWhy(c: SerialClass?): String = when (c?.kind) {
    "fleet" -> {
        val seen = parseIso(c.lastSeen)
        if (!c.checked || (seen != null && System.currentTimeMillis() - seen < STALE_MS))
            "Already enrolled in the MDM" + if (c.deviceClass.isNotEmpty()) " as ${c.deviceClass}" else ""
        else "The MDM has this device on record${if (c.deviceClass.isNotEmpty()) " as ${c.deviceClass}" else ""}, but it hasn't checked in ${if (c.lastSeen != null) "for " + agoShort(c.lastSeen) else "yet"}. Its MDM client may be missing — for example firmware built without it."
    }
    "production" -> "Serial is in production “${c.production.ifEmpty { "unknown batch" }}”, not enrolled yet"
    "family" -> "Same model as ${if (c.familyCount > 0) c.familyCount.toString() else "other"} enrolled device${if (c.familyCount == 1) "" else "s"}" +
        (if (c.family.isNotEmpty()) " (${c.family})" else "") + (if (c.deviceClass.isNotEmpty()) ", used as ${c.deviceClass}" else "")
    "lookalike" -> "Serial follows our pattern but matches no production"
    "other" -> "The MDM knows nothing about this device"
    else -> ""
}

enum class ChipTone { OK, WARN, NEUTRAL }

@Composable
fun Chip(text: String, tone: ChipTone, modifier: Modifier = Modifier) {
    val x = LocalExtra.current
    val bg = when (tone) {
        ChipTone.OK -> x.okContainer
        ChipTone.WARN -> x.warnContainer
        ChipTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val fg = when (tone) {
        ChipTone.OK -> x.ok
        ChipTone.WARN -> x.warn
        ChipTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** The square icon at the start of a row: shape from what the device is, colour from whose it is. */
@Composable
fun Glyph(cls: SerialClass?, deviceClass: String, size: Int = 40) {
    val icon = iconFor(deviceClass.ifEmpty { cls?.deviceClass.orEmpty() })
    val tint = when {
        cls?.kind == "fleet" -> Color(0xFFF9674E)
        cls?.ours == true -> Color(0xFFFF9F0A)
        else -> Color(0xFF8E8E93)
    }
    Surface(color = tint, contentColor = Color.White, shape = RoundedCornerShape((size / 3.3).dp), modifier = Modifier.size(size.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, modifier = Modifier.size((size * 0.52).dp))
        }
    }
}

fun iconFor(deviceClass: String): ImageVector = when (deviceClass) {
    "pos", "mpos", "payment" -> Icons.Outlined.PointOfSale
    "kds", "kiosk", "t7", "tablet" -> Icons.Outlined.TabletAndroid
    "dongle" -> Icons.Outlined.Tv
    else -> Icons.Outlined.Smartphone
}

/** "● Online" / "Offline 3 d" / nothing at all when the server is too old to say. */
fun onlineLabel(s: Status?): Pair<String, ChipTone>? {
    val st = s ?: return null
    return when (st.online) {
        true -> "● Online" to ChipTone.OK
        false -> ("Offline " + agoShort(st.lastSeenAt)).trim() to ChipTone.WARN
        null -> null
    }
}
