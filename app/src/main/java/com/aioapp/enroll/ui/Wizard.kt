package com.aioapp.enroll.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aioapp.enroll.adb.STEPS
import com.aioapp.enroll.mdm.RoleOpt
import kotlinx.coroutines.delay

/**
 * Home: one button. Everything else (what was set up today, the account) is a link off it, so a
 * person who has never used the app has exactly one thing to press.
 */
@Composable
fun HomeScreen(u: Ui, vm: EnrollVm) {
    val today = u.log.count { sameDay(it.t) }
    val waiting = u.pairing.size + u.ready.size
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) { Avatar(u) { vm.setTab(Tab.ACCOUNT) } }
        Spacer(Modifier.weight(1f))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(96.dp).align(Alignment.CenterHorizontally),
        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.PhonelinkSetup, null, Modifier.size(48.dp)) } }
        Text(
            "Set up a device",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
        )
        Text(
            "About two minutes. Keep the device and this phone on the same Wi-Fi.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 12.dp, end = 12.dp),
        )
        Spacer(Modifier.weight(1f))
        if (waiting > 0) {
            Text(
                if (waiting == 1) "A device is waiting to be set up." else "$waiting devices are waiting to be set up.",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalExtra.current.ok,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )
        }
        BigButton("Start") { vm.start() }
        TextButton(
            { vm.setTab(Tab.TODAY) },
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
        ) { Text(if (today == 0) "Nothing set up today" else "$today set up today  ›") }
    }
}

/** The wizard: one screen per step, a bar saying how far along it is. */
@Composable
fun WizardScreen(u: Ui, vm: EnrollVm) {
    val step = u.step ?: return
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(
                "Step ${step.ordinal + 1} of ${Step.entries.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { (step.ordinal + 1f) / Step.entries.size },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
        )
        when (step) {
            Step.PREPARE -> Prepare(vm)
            Step.FIND -> Find(u, vm)
            Step.CODE -> Code(u, vm)
            Step.WORK -> Work(u, vm)
        }
    }
}

// ---- 1. get it ready ----

@Composable
private fun Prepare(vm: EnrollVm) = Page(
    "Get the device ready",
    bottom = { BigButton("It's ready") { vm.toFind() } },
) {
    Numbered(1, "Turn it on. If someone has used it before, factory reset it first.")
    Numbered(2, "In its setup, connect it to the same Wi-Fi as this phone.")
    Numbered(3, "Skip the Google account. Don't sign in to anything on it.")
    Text(
        "An account on the device stops it from being set up, so it's quicker to skip it now.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

// ---- 2. Wireless debugging ----

@Composable
private fun Find(u: Ui, vm: EnrollVm) = Page(
    "Turn on Wireless debugging",
    bottom = { TextButton({ vm.setTab(Tab.NEARBY) }, Modifier.fillMaxWidth().padding(bottom = 8.dp)) { Text("Can't find it?") } },
) {
    // A device with AIO firmware connects by itself, no code needed: it only takes one tap.
    u.ready.firstOrNull()?.let { r ->
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Found ${r.name}", style = MaterialTheme.typography.titleMedium)
                Text("It is connected already. No code needed.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                Button({ vm.setUp(r.host) }, Modifier.fillMaxWidth().height(48.dp)) { Text("Set up ${r.name}") }
            }
        }
    }
    Text("On the device:", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 4.dp))
    Numbered(1, "Settings → About → tap Build number 7 times.")
    Numbered(2, "Settings → System → Developer options → turn on Wireless debugging.")
    Numbered(3, "Tap Wireless debugging → Pair device with pairing code.")
    Guide(Modifier.fillMaxWidth().padding(top = 14.dp))
    Looking("Looking for it. This moves on by itself.")
    TypeItIn(vm)
}

/** Some Wi-Fi hides devices from each other's search: then the pairing box is typed in by hand. */
@Composable
private fun TypeItIn(vm: EnrollVm) = TextButton({ vm.manualEntry() }, Modifier.padding(top = 4.dp)) {
    Text("Pairing screen open, but nothing here? Type it in")
}

// ---- 3. the code ----

@Composable
private fun Code(u: Ui, vm: EnrollVm) {
    val sh = u.sheet
    if (sh == null) {
        LaunchedEffect(Unit) { vm.toFind() }
        return
    }
    when (sh.phase) {
        Phase.TRYING -> Page("Connecting to ${sh.name}") { Looking("Trying without a code first…") }
        Phase.WAITING -> Page("Open its pairing screen") {
            Text(
                "On ${sh.name}: Wireless debugging → Pair device with pairing code.",
                style = MaterialTheme.typography.bodyLarge,
            )
            sh.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp)) }
            Looking("Waiting for its pairing screen…")
            TypeItIn(vm)
        }
        Phase.MANUAL -> Page(
            "Type in its pairing screen",
            bottom = { BigButton("Connect", enabled = sh.code.length == 6 && sh.typed.isNotBlank()) { vm.submitManual() } },
        ) {
            Text(
                "On the device: Wireless debugging → Pair device with pairing code. Type what its box shows.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AddrField("IP address & Port", sh.typed, "10.32.1.84:37215") { vm.typeAddr(it) }
            Text("Wi-Fi pairing code", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
            CodeBoxes(sh.code, editable = true, autoFocus = false) { vm.typeCode(it) }
            sh.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
        }
        Phase.PORT -> Page(
            "One more number",
            bottom = { BigButton("Connect") { vm.submitPort() } },
        ) {
            Text(
                "Paired. Now close the pairing box on the device. At the top of its Wireless debugging screen is " +
                    "another IP address & Port: type that one.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AddrField("IP address & Port", sh.typed, "10.32.1.84:41233") { vm.typeAddr(it) }
            sh.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 10.dp)) }
        }
        Phase.PAIRING -> if (sh.manual) Page("Connecting to ${sh.host}") { Looking("Pairing…") } else CodePage(u, vm, sh)
        Phase.CODE -> CodePage(u, vm, sh)
        Phase.DONE -> Page("Connected to ${sh.name}") { Looking("Starting to set it up…") }
    }
}

@Composable
private fun CodePage(u: Ui, vm: EnrollVm, sh: Sheet) = Page(
        "Type the code",
        bottom = {
            BigButton(if (sh.phase == Phase.PAIRING) "Connecting…" else "Connect", enabled = sh.phase == Phase.CODE && sh.code.length == 6) { vm.submitCode() }
        },
    ) {
        Text(
            "${sh.name} shows a 6-digit code. Type it here.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Two devices asking at once look alike here; the address on their screens tells them apart.
        val asking = u.pairing.size + if (u.pairing.none { it.host == sh.host }) 1 else 0
        if (asking > 1) {
            Surface(
                color = LocalExtra.current.warnContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text(
                    "$asking devices are asking. Use the one whose screen shows ${sh.host} under IP address. The other is next.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        CodeBoxes(sh.code, sh.phase == Phase.CODE) { c ->
            vm.typeCode(c)
            if (c.filter { it.isDigit() }.length == 6) vm.submitCode() // the sixth digit is the go
        }
        sh.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
    }

// ---- 4. setting up, and done ----

@Composable
private fun Work(u: Ui, vm: EnrollVm) {
    val conn = u.wizConn
    if (conn == null) {
        Page("Lost the device", bottom = { BigButton("Start again") { vm.another() } }) {
            Text("It stopped answering. Check it is still on and on the Wi-Fi.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    val probe = conn.probe
    val job = u.job?.takeIf { it.host == conn.host }
    val blocked = probe.blockedReason
    val ask = u.roleQuestion(conn)
    val picked = u.rolePick[conn.serial]?.let { k -> u.roleOpts[probe.product]?.firstOrNull { it.key == k } }

    // Re-read it every few seconds while it can't be set up, so the list ticks off as it is fixed
    // and setting up starts by itself once it is clean.
    LaunchedEffect(conn.host, blocked, job == null) {
        while (blocked != null && job == null) {
            delay(3_000)
            vm.refreshProbe(conn.host)
        }
    }

    when {
        job?.outcome != null -> {
            val out = job.outcome
            Result(
                ok = true,
                title = "${conn.name} is set up",
                text = (if (out.live) "It is online on the MDM." else "It will show up on the MDM in a minute.") +
                    (if (picked != null) " It is ${withArticle(picked.label)}; its venue is set there." else " Its type and venue are set there.") +
                    when {
                        out.closed -> " Developer options are off now, and it is restarting."
                        out.live -> " Turn off Developer options on it: Settings → System → Developer options."
                        else -> ""
                    },
                bottom = { Finish(vm) },
            ) { OpenOnMdm(u, out.serial) }
        }
        job?.error != null -> Result(
            ok = false,
            title = "Couldn't finish",
            text = job.error,
            bottom = {
                BigButton("Try again") { vm.enrol(conn.host) }
                TextButton({ vm.another() }, Modifier.fillMaxWidth().padding(bottom = 8.dp)) { Text("Start over") }
            },
        )
        job != null -> Page("Setting up ${conn.name}") {
            Text(phase(job.step), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            LinearProgressIndicator(
                progress = { (job.step + 1f) / STEPS.size },
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp).height(10.dp).clip(RoundedCornerShape(5.dp)),
            )
            Text(
                "Takes about a minute. Keep both on the Wi-Fi and leave the device alone until it's done.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        probe.owner.ours -> Result(
            ok = true,
            title = "${conn.name} is already set up",
            text = "It is on the MDM. Nothing to do here.",
            bottom = { Finish(vm) },
        ) { OpenOnMdm(u, probe.serial) }
        blocked != null -> Page("${conn.name} needs a reset first") {
            Fix(probe.accounts.isEmpty(), "No accounts on it", "Remove: " + probe.accounts.joinToString(", "))
            Fix(!probe.owner.set || probe.owner.ours, "No other management app", "Another management app: ${probe.owner.pkg}")
            Fix(probe.users <= 1, "One user only", "A second user or work profile is on it")
            Text(
                "A factory reset fixes all of these. Skip the Google account during setup, then come back: " +
                    "this screen carries on by itself once it's clear.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        ask != null -> RoleAsk(conn.name, ask) { vm.pickRole(conn.serial, it) }
        else -> Page("Setting up ${conn.name}") { Looking("Starting…") }
    }
}

/**
 * A model that can be more than one thing (the Galaxy Tab A9+ is a POS or a kitchen display,
 * 29 Oct 2026): the person holding it says which, before anything is installed. Its app and
 * lock on the MDM follow from this.
 */
@Composable
private fun RoleAsk(name: String, opts: List<RoleOpt>, onPick: (String) -> Unit) {
    var sel by remember(name, opts) { mutableStateOf<String?>(null) }
    val title = if (opts.size == 2) "Is it ${withArticle(opts[0].label)} or ${withArticle(opts[1].label)}?" else "What will it be?"
    Page(title, bottom = {
        val chosen = opts.firstOrNull { it.key == sel }
        BigButton(if (chosen != null) "Set it up as ${withArticle(chosen.label)}" else "Pick one", enabled = chosen != null) { chosen?.let { onPick(it.key) } }
    }) {
        Text(
            "$name can be any of these. Pick what it will do at the venue: the app it runs and its lock follow from that.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 14.dp),
        )
        opts.forEach { o ->
            val on = o.key == sel
            Surface(
                onClick = { sel = o.key },
                shape = RoundedCornerShape(16.dp),
                color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(iconFor(o.key), null, Modifier.size(28.dp))
                    Text(o.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (on) Icon(Icons.Filled.Check, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** "a POS terminal", "an MPOS": the role labels as they read in a sentence. */
private fun withArticle(label: String) = (if (label.firstOrNull()?.lowercaseChar() in setOf('a', 'e', 'i', 'o', 'u') || label.startsWith("MPOS")) "an " else "a ") + label

/** The eight steps the enroller takes, as the three things a person can picture. */
private fun phase(step: Int) = when {
    step <= 1 -> "Connecting"
    step <= 5 -> "Installing the AIO app"
    step <= 7 -> "Checking in with the MDM"
    else -> "Turning off Developer options"
}

@Composable
private fun Finish(vm: EnrollVm) {
    BigButton("Set up another") { vm.another() }
    TextButton({ vm.finish() }, Modifier.fillMaxWidth().padding(bottom = 8.dp)) { Text("I'm done") }
}

@Composable
private fun OpenOnMdm(u: Ui, serial: String) {
    val ctx = LocalContext.current
    val server = u.session?.server ?: return
    if (serial.isEmpty()) return
    TextButton({
        runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("$server/devices/$serial"))) }
    }) {
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp))
        Text("  Open it on the MDM")
    }
}

// ---- building blocks ----

/** A wizard page: a big title, the content, and its buttons kept at the bottom. */
@Composable
private fun Page(title: String, bottom: @Composable ColumnScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) =
    // imePadding: the keyboard pushes the buttons up instead of covering them.
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 22.dp, bottom = 14.dp))
            content()
            Spacer(Modifier.height(16.dp))
        }
        bottom()
    }

/** The end of a device: a big tick or a big warning, and what next. */
@Composable
private fun Result(ok: Boolean, title: String, text: String, bottom: @Composable ColumnScope.() -> Unit, extra: @Composable () -> Unit = {}) =
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        val x = LocalExtra.current
        Surface(
            color = if (ok) x.ok else MaterialTheme.colorScheme.error,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(84.dp),
        ) { Box(contentAlignment = Alignment.Center) { Icon(if (ok) Icons.Filled.Check else Icons.Outlined.ErrorOutline, null, Modifier.size(44.dp)) } }
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        extra()
        Spacer(Modifier.weight(1f))
        bottom()
    }

@Composable
private fun BigButton(text: String, enabled: Boolean = true, onClick: () -> Unit) = Button(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(56.dp),
) { Text(text, fontSize = 17.sp) }

@Composable
private fun Numbered(n: Int, text: String) = Row(
    Modifier.fillMaxWidth().padding(vertical = 7.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = CircleShape, modifier = Modifier.size(26.dp)) {
        Box(contentAlignment = Alignment.Center) { Text("$n", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    }
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 2.dp))
}

/** One thing that has to be true before it can be set up: ticked, or saying what is in the way. */
@Composable
private fun Fix(ok: Boolean, okText: String, badText: String) = Row(
    Modifier.fillMaxWidth().padding(vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    val x = LocalExtra.current
    Surface(color = if (ok) x.okContainer else MaterialTheme.colorScheme.errorContainer, shape = CircleShape, modifier = Modifier.size(26.dp)) {
        Box(contentAlignment = Alignment.Center) {
            if (ok) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = x.ok)
            else Text("!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
    Text(if (ok) okText else badText, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Looking(text: String) = Row(
    Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CodeBoxes(code: String, editable: Boolean, autoFocus: Boolean = true, onCode: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(editable) { if (editable && autoFocus) runCatching { focus.requestFocus() } }
    BasicTextField(
        value = code,
        onValueChange = onCode,
        enabled = editable,
        singleLine = true,
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.focusRequester(focus),
        decorationBox = {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                repeat(6) { i ->
                    val here = i == code.length && editable
                    Surface(
                        color = Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(2.dp, if (here) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        modifier = Modifier.size(width = 46.dp, height = 58.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(code.getOrNull(i)?.toString() ?: "", fontSize = 26.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        },
    )
}

/** An address typed off a device's screen. A space works as well as the colon, which hides behind ?123. */
@Composable
private fun AddrField(label: String, value: String, example: String, onValue: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    val moves = LocalFocusManager.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        placeholder = { Text(example) },
        supportingText = { Text("A space instead of the colon works too.") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { moves.moveFocus(FocusDirection.Down) }),
        textStyle = MaterialTheme.typography.titleLarge,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).focusRequester(focus),
    )
}

/** Who is signed in, as a round picture; it opens the account. */
@Composable
fun Avatar(u: Ui, onClick: () -> Unit) = Surface(
    color = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    shape = CircleShape,
    modifier = Modifier.size(36.dp),
    onClick = onClick,
) {
    val bm = u.avatar
    if (bm != null) {
        androidx.compose.foundation.Image(bm.asImageBitmap(), "Account", Modifier.clip(CircleShape), contentScale = ContentScale.Crop)
    } else {
        Box(contentAlignment = Alignment.Center) { Text(initials(u.profile), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

/** A plain back arrow and a title, for the screens off home. */
@Composable
fun BackTitle(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) = Row(
    Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
    trailing()
}

@Composable
fun SectionLabel(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 4.dp),
)
