package com.aioapp.enroll.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Who is signed in, and the way out. Where devices go and what they are is decided on the MDM. */
@Composable
fun AccountScreen(u: Ui, vm: EnrollVm) {
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
        item("t") { BackTitle("Account", { vm.setTab(Tab.HOME) }) }
        item("me") {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp),
                ) {
                    val bm = u.avatar
                    if (bm != null) Image(bm.asImageBitmap(), null, Modifier.clip(CircleShape), contentScale = ContentScale.Crop)
                    else Box(contentAlignment = Alignment.Center) { Text(initials(u.profile), fontSize = 22.sp, fontWeight = FontWeight.SemiBold) }
                }
                Column {
                    Text(u.profile.ifEmpty { u.session?.username.orEmpty() }, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(u.session?.role?.takeIf { it.isNotEmpty() }, u.session?.server?.removePrefix("https://")).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // Nothing to say when it works; only when every device will ask for a code.
        if (!u.fleetKey) {
            item("fk") {
                Text(
                    "Devices will ask for a pairing code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                )
            }
        }
        item("out") {
            OutlinedButton(
                { vm.signOut() },
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp).height(48.dp),
            ) { Text("Sign out") }
        }
    }
}
