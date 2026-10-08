package com.aioapp.enroll.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aioapp.enroll.adb.CLASSES
import com.aioapp.enroll.adb.STEPS
import kotlinx.coroutines.delay

/**
 * One connected device: what it is, what it will be enrolled as, and the eight steps while it is
 * enrolling. When it can't be enrolled yet, the checklist is re-read until it is clean.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(u: Ui, vm: EnrollVm) {
    val conn = u.openConn
    if (conn == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val probe = conn.probe
    val job = u.job?.takeIf { it.host == conn.host }
    val blocked = probe.blockedReason
    val cls = u.chosen[conn.host].orEmpty()
    val status = u.statuses[probe.serial]
    val enrolled = status?.enrolled == true

    // Re-read every few seconds so each item in the checklist ticks off as it is fixed.
    LaunchedEffect(conn.host, blocked) {
        while (blocked != null) {
            delay(3_000)
            vm.refreshProbe(conn.host)
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(conn.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            TextButton({ vm.disconnect(conn.host) }) { Text("Disconnect") }
        }

        if (job?.outcome != null) {
            Done(job, cls, u, vm, conn.host)
        } else if (job != null) {
            Steps(job, vm)
        } else {
            Header(conn, probe, u)
            if (blocked != null) {
                Blocked(probe)
            } else {
                // Once it is enrolled the class is what the MDM says it is, not a choice.
                if (!enrolled) {
                    Group {
                        SectionLabel("USED AS")
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            CLASSES.forEachIndexed { i, c ->
                                SegmentedButton(
                                    selected = cls == c,
                                    onClick = { vm.setClass(conn.host, c) },
                                    shape = SegmentedButtonDefaults.itemShape(i, CLASSES.size),
                                    label = { Text(c) },
                                )
                            }
                        }
                    }
                }
                Group {
                    KV("Status", if (enrolled) "Enrolled" else "Not enrolled")
                    if (enrolled) {
                        u.classes[probe.serial]?.deviceClass?.takeIf { it.isNotEmpty() }?.let { KV("Used as", it) }
                    }
                    KV("Goes to", u.site.name.ifEmpty { "Nowhere yet — pick a restaurant" })
                    KV("Enrolled by", u.profile)
                    KV("Connection", "Wi-Fi · ${conn.host}")
                }
                // Nothing to offer on a device that is already enrolled.
                if (!enrolled) {
                    Button(
                        onClick = { vm.enrol(conn.host) },
                        enabled = cls in CLASSES,
                        modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 4.dp),
                    ) { Text("Enrol") }
                    if (cls !in CLASSES) Hint("Pick what this device is used as first.")
                }
            }

            if (enrolled && status != null) {
                SectionLabel("ON THE MDM")
                Group {
                    onlineLabel(status)?.let { (text, tone) ->
                        KVChip("Status", text, tone)
                    }
                    KV("Last check-in", status.lastSeenAt?.let { agoShort(it) + " ago" } ?: "—")
                    if (status.hasBattery) KV("Battery", "${status.batteryPct}%")
                    KV("Agent", status.agentVersion.ifEmpty { "—" })
                }
                Dashboard(u, probe.serial)
            }
        }
        Box(Modifier.height(24.dp))
    }
}

@Composable
private fun Header(conn: Conn, probe: com.aioapp.enroll.adb.Probe, u: Ui) = Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = RoundedCornerShape(20.dp),
    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Glyph(u.classes[probe.serial], u.chosen[conn.host].orEmpty(), size = 52)
        Column {
            Text(conn.name, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(
                    probe.blockedReason?.let { "Needs attention" } ?: "Ready to enrol",
                    probe.android.takeIf { it.isNotEmpty() }?.let { "Android $it" },
                    probe.firmwareVersion?.let { "AIO firmware $it" },
                    probe.serial.takeIf { it.isNotEmpty() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What has to be cleared before our agent can become Device Owner. */
@Composable
private fun Blocked(probe: com.aioapp.enroll.adb.Probe) {
    SectionLabel("BEFORE IT CAN BE ENROLLED")
    Group {
        Text(
            probe.blockedReason.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Item(probe.accounts.isEmpty(), if (probe.accounts.isEmpty()) "No accounts on the device" else "Remove: " + probe.accounts.joinToString(", "))
        Item(!probe.owner.set || probe.owner.ours, if (probe.owner.set && !probe.owner.ours) "Another device admin: ${probe.owner.pkg}" else "No other device admin")
        Item(probe.users <= 1, if (probe.users > 1) "${probe.users - 1} extra user or work profile to remove" else "One user only")
        Text(
            "A factory reset clears all three. Don't add an account during setup.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Item(ok: Boolean, text: String) = Row(
    Modifier.fillMaxWidth().padding(vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
) {
    val x = LocalExtra.current
    Surface(color = if (ok) x.okContainer else MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape, modifier = Modifier.size(20.dp)) {
        Box(contentAlignment = Alignment.Center) {
            if (ok) Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = x.ok)
        }
    }
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Steps(job: Job, vm: EnrollVm) {
    Group {
        STEPS.forEachIndexed { i, s ->
            val done = i < job.step
            val now = i == job.step && job.error == null
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val x = LocalExtra.current
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    when {
                        done -> Surface(color = x.ok, shape = CircleShape, modifier = Modifier.size(20.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = Color.White) }
                        }
                        now -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else -> Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape, modifier = Modifier.size(20.dp)) {}
                    }
                }
                Text(s, style = MaterialTheme.typography.bodyMedium, color = if (i > job.step) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    LinearProgressIndicator(
        progress = { job.step.toFloat() / STEPS.size },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    )
    AnimatedVisibility(job.error != null) {
        Column {
            Text(
                job.error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 14.dp),
            )
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ vm.clearJob() }, Modifier.weight(1f)) { Text("Back") }
                Button({ vm.enrol(job.host) }, Modifier.weight(1f)) { Text("Try again") }
            }
        }
    }
}

@Composable
private fun Done(job: Job, cls: String, u: Ui, vm: EnrollVm, host: String) {
    val out = job.outcome ?: return
    val x = LocalExtra.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = if (out.live) x.ok else x.warn, contentColor = Color.White, shape = CircleShape, modifier = Modifier.size(72.dp).padding(top = 0.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, null, Modifier.size(38.dp)) }
        }
        Text(
            if (out.live) "Live on the MDM" else if (out.checked) "Enrolled — waiting for its first check-in" else "Enrolled",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            listOfNotNull("Enrolled as $cls", out.restaurant.takeIf { it.isNotEmpty() }).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    Group {
        KV("Serial", out.serial)
        if (out.hasBattery) KV("Battery", "${out.batteryPct}%")
        KV("Agent", out.agentVersion.ifEmpty { "—" })
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Dashboard(u, out.serial, Modifier.weight(1f))
        Button({ vm.clearJob(); vm.back() }, Modifier.weight(1f)) { Text("Next device") }
    }
}

@Composable
private fun Dashboard(u: Ui, serial: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val server = u.session?.server ?: return
    OutlinedButton(
        onClick = {
            runCatching {
                ctx.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("$server/devices/$serial"))
                )
            }
        },
        modifier = modifier,
    ) {
        Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp))
        Text(" Dashboard")
    }
}

@Composable
fun SectionLabel(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 4.dp),
)

@Composable
fun Group(content: @Composable () -> Unit) = Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = RoundedCornerShape(20.dp),
    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) { content() }
}

@Composable
fun KV(k: String, v: String) = Row(
    Modifier.fillMaxWidth().padding(vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    Text(v, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
}

@Composable
private fun KVChip(k: String, v: String, tone: ChipTone) = Row(
    Modifier.fillMaxWidth().padding(vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    Chip(v, tone)
}

@Composable
private fun Hint(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 6.dp, top = 6.dp),
)
