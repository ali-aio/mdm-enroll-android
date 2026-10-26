package com.aioapp.enroll.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * "Can't find it?": everything on the Wi-Fi that isn't set up yet, for when the wizard doesn't
 * spot a device by itself. Devices already in the MDM are left out; they live under Done.
 * Rows are keyed by the device, so a scan that re-finds it does not rebuild the list.
 */
@Composable
fun NearbyScreen(u: Ui, vm: EnrollVm) {
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
        item("top") { BackTitle("Devices on this Wi-Fi", { vm.setTab(Tab.HOME) }) }

        if (u.pairing.isNotEmpty()) {
            item("pl") { Label("ASKING TO CONNECT", live = true) }
            items(u.pairing, key = { it.key }) { r ->
                DeviceRow(r, vm, highlight = true, action = "Type code", onAction = { vm.openSheet(r) }, onClick = { vm.openSheet(r) })
            }
        }
        if (u.ready.isNotEmpty()) {
            item("rl") { Label("READY TO SET UP") }
            items(u.ready, key = { it.key }) { r ->
                DeviceRow(r, vm, action = "Set up", onAction = { vm.setUp(r.host) }, onClick = { vm.setUp(r.host) })
            }
        }
        item("nl") { NearbyLabel(u, vm) }
        val shown = if (u.showAll) u.nearby else u.nearby.take(SHOWN_AT_FIRST)
        items(shown, key = { it.key }) { r ->
            DeviceRow(r, vm, action = "Connect", onAction = { vm.pair(r) }, onClick = { vm.say(classWhy(r.cls)) })
        }
        if (shown.size < u.nearby.size) {
            item("more") {
                TextButton({ vm.showAll() }, Modifier.padding(start = 2.dp, top = 2.dp)) {
                    Text("Show ${u.nearby.size - shown.size} more")
                }
            }
        }
        item("hint") {
            Text(
                if (u.nearby.isEmpty() && u.nearbyTotal > 0) "None of these are ours."
                else "Not in the list? Check it is on the same Wi-Fi as this phone and Wireless debugging is on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun Label(text: String, live: Boolean = false) = Row(
    Modifier.padding(start = 6.dp, top = 14.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
) {
    if (live) Surface(color = LocalExtra.current.ok, shape = CircleShape, modifier = Modifier.size(8.dp)) {}
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NearbyLabel(u: Ui, vm: EnrollVm) = Row(
    Modifier.fillMaxWidth().padding(start = 6.dp, top = 14.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    val shown = if (u.onlyOurs) "${u.nearby.size}/${u.nearbyTotal}" else "${u.nearbyTotal}"
    Text(
        "ON THIS WI-FI  $shown",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
    )
    if (u.classes.isNotEmpty()) {
        Text("Only ours", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Switch(u.onlyOurs, { vm.setOnlyOurs(it) }, Modifier.padding(start = 6.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceRow(
    r: Dev,
    vm: EnrollVm,
    highlight: Boolean = false,
    action: String,
    onAction: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    ) {
        Row(
            Modifier
                .combinedClickable(onClick = onClick, onLongClick = { vm.say(classWhy(r.cls)) })
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Glyph(r.cls, "")
            Column(Modifier.weight(1f)) {
                Text(r.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // The serial, never the address: it is what is written on the device's label.
                if (r.serial.isNotEmpty()) {
                    Text(
                        r.serial,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                classLabel(r.cls)?.let { (text, tone) -> Chip(text, tone, Modifier.padding(top = 3.dp)) }
            }
            TextButton(onAction) { Text(action) }
        }
    }
}
