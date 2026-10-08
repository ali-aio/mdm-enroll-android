package com.aioapp.enroll.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aioapp.enroll.store.Account

/**
 * Sign in with an MDM dashboard account. Saved accounts come first — one with a saved password
 * signs in on a single tap; "Add account" shows the form. No server field: live is the default.
 */
@Composable
fun SignIn(u: Ui, vm: EnrollVm) {
    var form by remember(u.accounts.isEmpty()) { mutableStateOf(u.accounts.isEmpty()) }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("AIO Enroll", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text(
                "Sign in with your MDM dashboard account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 28.dp),
            )
            if (form) Form(u, vm, back = if (u.accounts.isEmpty()) null else { { form = false } })
            else Pickers(u, vm) { form = true }
            u.signInError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun Pickers(u: Ui, vm: EnrollVm, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
    ) {
        u.accounts.take(4).forEach { a -> Avatar(a, u.signingIn == a.username) { pick(a, u, vm, onAdd) } }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape,
                modifier = Modifier.size(56.dp),
                onClick = onAdd,
            ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, "Add account") } }
            Text("Add", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
        }
    }
    Text(
        "Tap an account to sign in. One without a saved password asks for it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
    )
}

/** A saved password signs in straight away; without one, the form opens with the name filled in. */
private fun pick(a: Account, u: Ui, vm: EnrollVm, onAdd: () -> Unit) {
    if (u.signingIn != null) return
    if (a.saved) vm.signInSaved(a) else onAdd()
}

@Composable
private fun Avatar(a: Account, busy: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(56.dp),
            onClick = onClick,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text(initials(a.name.ifEmpty { a.username }), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            a.name.ifEmpty { a.username },
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun Form(u: Ui, vm: EnrollVm, back: (() -> Unit)?) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var save by remember { mutableStateOf(true) }
    var show by remember { mutableStateOf(false) }
    val busy = u.signingIn != null
    val go = { if (!busy) vm.signIn(user, pass, save) }

    OutlinedTextField(
        value = user,
        onValueChange = { user = it; vm.clearSignInError() },
        label = { Text("Email") },
        singleLine = true,
        enabled = !busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = pass,
        onValueChange = { pass = it; vm.clearSignInError() },
        label = { Text("Password") },
        singleLine = true,
        enabled = !busy,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { go() }),
        trailingIcon = {
            IconButton({ show = !show }) {
                Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (show) "Hide" else "Show")
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Save my password", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(save, { save = it }, enabled = !busy)
    }
    Button(
        onClick = go,
        enabled = !busy && user.isNotBlank() && pass.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(50.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text("Sign in")
    }
    if (back != null) TextButton(back, Modifier.fillMaxWidth()) { Text("Back to accounts") }
}

fun initials(name: String): String = name.trim().split(Regex("[\\s@._-]+")).filter { it.isNotEmpty() }
    .take(2).map { it.first().uppercaseChar() }.joinToString("").ifEmpty { "?" }
