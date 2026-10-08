package com.aioapp.enroll.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import kotlinx.coroutines.delay

/** The scenes the little phone plays, in order, the same ones the desktop app shows. */
private enum class Scene(val caption: String, val ms: Long) {
    ABOUT("Settings → About phone → tap “Build number” seven times", 4_600),
    WD("Developer options → turn on Wireless debugging", 3_400),
    PAIR("Wireless debugging → Pair device with pairing code", 4_200),
}

/**
 * A phone that shows what to do on the *other* device, looping through the three screens. The
 * desktop app does the same thing in `ui/guide.js`; there is no cable here, so the USB scenes
 * are left out.
 */
@Composable
fun Guide(modifier: Modifier = Modifier) {
    var i by remember { mutableIntStateOf(0) }
    val scene = Scene.entries[i % Scene.entries.size]
    LaunchedEffect(i) { delay(scene.ms); i++ }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.width(168.dp).height(272.dp).clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(7.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(258.dp),
            ) {
                AnimatedContent(scene, label = "scene", transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(250)) }) { s ->
                    Column(Modifier.padding(9.dp)) {
                        when (s) {
                            Scene.ABOUT -> About()
                            Scene.WD -> Toggles()
                            Scene.PAIR -> PairDialog()
                        }
                    }
                }
            }
        }
        Text(
            scene.caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
        )
    }
}

@Composable
private fun Head(text: String) = Text(
    text,
    fontSize = 11.sp,
    fontWeight = FontWeight.Medium,
    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
)

@Composable
private fun Li(label: String, value: String = "", hl: Boolean = false, trailing: @Composable () -> Unit = {}) = Surface(
    color = if (hl) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = if (hl) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    shape = RoundedCornerShape(7.dp),
    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
) {
    Row(Modifier.padding(horizontal = 7.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 9.5.sp, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, fontSize = 9.5.sp, fontWeight = FontWeight.Medium)
        trailing()
    }
}

/** Tapping Build number seven times, counting as it goes. */
@Composable
private fun About() {
    var taps by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (taps < 7) { delay(430); taps++ } }
    Head("About phone")
    Li("Device name", "Phone")
    Li("Android version", "15")
    Li("Build number", if (taps >= 7) "✓" else "$taps/7", hl = true) {
        val size by animateDpAsState(if (taps % 2 == 0) 9.dp else 13.dp, tween(180), label = "tap")
        Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.padding(start = 6.dp).size(size)) {}
    }
    Li("Legal info", "›")
    if (taps >= 7) Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) { Text("You are now a developer!", fontSize = 9.sp, modifier = Modifier.padding(6.dp)) }
}

/** Wireless debugging flicking on by itself. */
@Composable
private fun Toggles() {
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(900); on = true }
    Head("Developer options")
    Li("Stay awake") { Switchlet(false) }
    Li("USB debugging") { Switchlet(false) }
    Li("Wireless debugging", hl = true) { Switchlet(on) }
}

@Composable
private fun Switchlet(on: Boolean) {
    val x by animateDpAsState(if (on) 10.dp else 0.dp, tween(320), label = "knob")
    val a by animateFloatAsState(if (on) 1f else 0.35f, tween(320), label = "track")
    Surface(
        color = if (on) MaterialTheme.colorScheme.primary.copy(alpha = a) else MaterialTheme.colorScheme.outline.copy(alpha = a),
        shape = CircleShape,
        modifier = Modifier.width(24.dp).height(14.dp),
    ) {
        Box(Modifier.padding(2.dp)) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = CircleShape, modifier = Modifier.padding(start = x).size(10.dp)) {}
        }
    }
}

/** The dialog to read the code off. */
@Composable
private fun PairDialog() {
    Head("Wireless debugging")
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Pair with device", fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth())
            Text("Wi-Fi pairing code", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 3.dp))
            Text(
                "482 913",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 5.dp),
            )
            Text("IP address & Port", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
            Text("192.168.1.20:37215", fontSize = 9.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth())
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
        Text(
            "The app watches for this screen and opens itself",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
