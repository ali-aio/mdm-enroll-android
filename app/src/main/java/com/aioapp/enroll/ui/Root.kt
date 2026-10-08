package com.aioapp.enroll.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun Root(vm: EnrollVm) {
    val u by vm.ui.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.resume()
        onPauseOrDispose { vm.pause() }
    }
    if (u.booting) return
    if (u.session == null) {
        SignIn(u, vm)
        return
    }

    val snack = remember { SnackbarHostState() }
    LaunchedEffect(u.banner) {
        u.banner?.let {
            snack.showSnackbar(it)
            vm.dismissBanner()
        }
    }
    BackHandler(u.openHost != null) { vm.back() }

    Scaffold(
        bottomBar = { if (u.openHost == null) NavBar(u.tab) { vm.setTab(it) } },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when {
                u.openHost != null -> DeviceScreen(u, vm)
                u.tab == Tab.TODAY -> TodayScreen(u, vm)
                u.tab == Tab.ACCOUNT -> AccountScreen(u, vm)
                else -> DevicesScreen(u, vm)
            }
        }
    }
    if (u.sheet != null) PairSheet(u, vm)
}

@Composable
private fun NavBar(tab: Tab, onTab: (Tab) -> Unit) = NavigationBar {
    NavigationBarItem(tab == Tab.DEVICES, { onTab(Tab.DEVICES) },
        icon = { Icon(Icons.Outlined.Smartphone, null) }, label = { Text("Devices") })
    NavigationBarItem(tab == Tab.TODAY, { onTab(Tab.TODAY) },
        icon = { Icon(Icons.Outlined.Schedule, null) }, label = { Text("Today") })
    NavigationBarItem(tab == Tab.ACCOUNT, { onTab(Tab.ACCOUNT) },
        icon = { Icon(Icons.Outlined.Person, null) }, label = { Text("Account") })
}

/**
 * The code sheet. It opens by itself when a device's pairing dialog appears, and closes by itself
 * when that dialog goes away — pairing was cancelled on the device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairSheet(u: Ui, vm: EnrollVm) {
    val sh = u.sheet ?: return
    val state = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = { vm.closeSheet() }, sheetState = state) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text(
                when (sh.phase) {
                    Phase.TRYING -> "Connecting to ${sh.name}"
                    Phase.DONE -> if (sh.noCode) "Connected — no code needed" else "Paired and connected"
                    else -> "Pair with ${sh.name}"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                sh.host + if (sh.serial.isNotEmpty()) " · ${sh.serial}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            classLabel(sh.cls)?.let { (text, tone) ->
                if (sh.cls?.kind != "other") Chip(text, tone, Modifier.padding(top = 8.dp))
            }

            when (sh.phase) {
                Phase.TRYING -> Waiting("Trying without a code first…")
                Phase.WAITING -> {
                    sh.error?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                    Text(
                        "On the device: Developer options → Wireless debugging → Pair device with pairing code.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    Waiting("Waiting for its pairing screen…")
                }
                Phase.CODE, Phase.PAIRING -> {
                    CodeBoxes(sh.code, sh.phase == Phase.CODE) { vm.typeCode(it) }
                    Text(
                        "Type the 6 digits ${sh.name} shows.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    )
                    sh.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    Button(
                        onClick = { vm.submitCode() },
                        enabled = sh.phase == Phase.CODE && sh.code.length == 6,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (sh.phase == Phase.PAIRING) "Pairing…" else "Pair") }
                }
                Phase.DONE -> Waiting("Reading the device…")
            }
        }
    }
}

@Composable
private fun Waiting(text: String) = Row(
    Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
) {
    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CodeBoxes(code: String, editable: Boolean, onCode: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(editable) { if (editable) runCatching { focus.requestFocus() } }
    BasicTextField(
        value = code,
        onValueChange = onCode,
        enabled = editable,
        singleLine = true,
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { }),
        modifier = Modifier.focusRequester(focus),
        decorationBox = {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                repeat(6) { i ->
                    val here = i == code.length && editable
                    Surface(
                        color = Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp,
                            if (here) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        ),
                        modifier = Modifier.size(width = 40.dp, height = 50.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                code.getOrNull(i)?.toString() ?: "",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        },
    )
}
