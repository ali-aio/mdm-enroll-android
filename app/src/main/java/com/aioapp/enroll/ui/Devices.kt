package com.aioapp.enroll.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The main screen: pairing requests first, then what is connected, then everything else on the
 * Wi-Fi. Rows are keyed by the device, so a scan that re-finds it does not rebuild the list.
 */
@Composable
fun DevicesScreen(u: Ui, vm: EnrollVm) {
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
        item("top") { TopRow(u, vm) }

        if (u.pairing.isNotEmpty()) {
            item("pl") { Label("PAIRING REQUEST", live = true) }
            items(u.pairing, key = { it.key }) { r ->
                DeviceRow(r, u, vm, highlight = true, action = "Enter code", onAction = { vm.openSheet(r) }, onClick = { vm.openSheet(r) })
            }
        }
        if (u.ready.isNotEmpty()) {
            item("rl") { Label("READY TO ENROL") }
            items(u.ready, key = { it.key }) { r ->
                DeviceRow(r, u, vm, action = "Enrol", onAction = { vm.enrolFromList(r) }, onClick = { vm.open(r) })
            }
        }
        if (u.enrolled.isNotEmpty()) {
            item("el") { Label("ENROLLED") }
            items(u.enrolled, key = { it.key }) { r ->
                DeviceRow(r, u, vm, action = "Open", onAction = { vm.open(r) }, onClick = { vm.open(r) })
            }
        }
        if (u.connected.isNotEmpty()) {
            item("cl") { Label("CONNECTED") }
            items(u.connected, key = { it.key }) { r ->
                DeviceRow(r, u, vm, action = null, onAction = {}, onClick = { vm.open(r) })
            }
        }
        item("nl") { NearbyLabel(u, vm) }
        val shown = if (u.showAll) u.nearby else u.nearby.take(SHOWN_AT_FIRST)
        items(shown, key = { it.key }) { r ->
            DeviceRow(r, u, vm, action = "Pair", onAction = { vm.pair(r) }, onClick = { vm.say(classWhy(r.cls)) })
        }
        if (shown.size < u.nearby.size) {
            item("more") {
                TextButton({ vm.showAll() }, Modifier.padding(start = 2.dp, top = 2.dp)) {
                    Text("Show ${u.nearby.size - shown.size} more")
                }
            }
        }
        if (u.nearby.isEmpty() && u.nearbyTotal > 0) {
            item("none") { Hint("None of these are ours.") }
        }
        item("howto") { HowTo() }
    }
}

@Composable
private fun TopRow(u: Ui, vm: EnrollVm) = Row(
    Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text("Devices", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
    if (u.site.name.isNotEmpty()) {
        TextButton({ vm.setTab(Tab.ACCOUNT) }) {
            Icon(Icons.Outlined.Storefront, null, Modifier.size(16.dp))
            Text(" " + u.site.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 130.dp))
        }
    }
    Me(u, vm)
}

@Composable
private fun Me(u: Ui, vm: EnrollVm) = Surface(
    color = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    shape = CircleShape,
    modifier = Modifier.size(32.dp),
    onClick = { vm.setTab(Tab.ACCOUNT) },
) {
    val bm = u.avatar
    if (bm != null) {
        androidx.compose.foundation.Image(bm.asImageBitmap(), "Account", Modifier.clip(CircleShape), contentScale = ContentScale.Crop)
    } else {
        Box(contentAlignment = Alignment.Center) {
            Text(initials(u.profile), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    u: Ui,
    vm: EnrollVm,
    highlight: Boolean = false,
    action: String?,
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
            Glyph(r.cls, u.chosen[r.host].orEmpty())
            Column(Modifier.weight(1f)) {
                Text(r.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    r.host + if (r.serial.isNotEmpty()) " · ${r.serial}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                classLabel(r.cls)?.let { (text, tone) -> Chip(text, tone, Modifier.padding(top = 3.dp)) }
            }
            if (action != null) TextButton(onAction) { Text(action) }
        }
    }
}

@Composable
private fun Hint(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
)

@Composable
private fun HowTo() = Surface(
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = RoundedCornerShape(20.dp),
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
) {
    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Add a device", fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth())
        Text(
            "Put it on this Wi-Fi, then do this on it:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
        )
        Guide()
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = LocalExtra.current.ok, shape = CircleShape, modifier = Modifier.size(8.dp)) {}
            Text(
                "  Watching for pairing screens",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
