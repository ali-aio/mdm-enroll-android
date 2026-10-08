package com.aioapp.enroll.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.aioapp.enroll.store.LogEntry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What this phone enrolled today, and whether each one is online now. */
@Composable
fun TodayScreen(u: Ui, vm: EnrollVm) {
    val ctx = LocalContext.current
    val today = u.log.filter { sameDay(it.t) }
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
        item("t") {
            Text("Today", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp))
        }
        if (today.isEmpty()) {
            item("none") {
                Text(
                    "Nothing enrolled from this phone today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, top = 12.dp),
                )
            }
        }
        items(today, key = { it.serial + it.t }) { e -> Entry(e, u) }
        if (today.isNotEmpty()) {
            item("acts") {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ copy(ctx, today.joinToString("\n") { it.serial }); vm.say("${today.size} serials copied.") }, Modifier.weight(1f)) {
                        Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp))
                        Text(" Copy serials")
                    }
                    OutlinedButton({ shareCsv(ctx, today) }, Modifier.weight(1f)) {
                        Icon(Icons.Outlined.FileDownload, null, Modifier.size(16.dp))
                        Text(" Export CSV")
                    }
                }
            }
        }
    }
}

@Composable
private fun Entry(e: LogEntry, u: Ui) = Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Glyph(u.classes[e.serial], e.cls)
        Column(Modifier.weight(1f)) {
            Text(e.name.ifEmpty { "Device" }, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(clock(e.t), e.serial.takeIf { it.isNotEmpty() }, e.cls.takeIf { it.isNotEmpty() }, e.restaurant.takeIf { it.isNotEmpty() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        onlineLabel(u.statuses[e.serial])?.let { (text, tone) -> Chip(text, tone) }
    }
}

private fun sameDay(t: Long): Boolean {
    val f = SimpleDateFormat("yyyyMMdd", Locale.US)
    return f.format(Date(t)) == f.format(Date())
}

private fun clock(t: Long) = SimpleDateFormat("HH:mm", Locale.US).format(Date(t))

private fun copy(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("serials", text))
}

/** A CSV in the cache, handed to whatever the person wants to send it with. */
private fun shareCsv(ctx: Context, log: List<LogEntry>) {
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    val f = File(dir, "aio-enroll-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.csv")
    val iso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    f.writeText(
        buildString {
            appendLine("time,device,serial,class,restaurant,enrolled_by")
            log.forEach { e ->
                appendLine(listOf(iso.format(Date(e.t)), e.name, e.serial, e.cls, e.restaurant, e.by).joinToString(",") { csv(it) })
            }
        }
    )
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { ctx.startActivity(Intent.createChooser(send, "Export CSV")) }
}

private fun csv(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
