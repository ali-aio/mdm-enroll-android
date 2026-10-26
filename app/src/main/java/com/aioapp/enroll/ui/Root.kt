package com.aioapp.enroll.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Home is one button; the wizard takes over from there. A device opening its pairing screen
 * jumps straight to the code step, wherever the person is (except the Wi-Fi list, where it shows
 * at the top).
 */
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
    BackHandler(u.tab != Tab.HOME || u.step != null) {
        if (u.tab != Tab.HOME) vm.setTab(Tab.HOME) else vm.back()
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        Box(Modifier.padding(pad)) {
            when {
                u.tab == Tab.NEARBY -> NearbyScreen(u, vm)
                u.step != null -> WizardScreen(u, vm)
                u.tab == Tab.TODAY -> TodayScreen(u, vm)
                u.tab == Tab.ACCOUNT -> AccountScreen(u, vm)
                else -> HomeScreen(u, vm)
            }
        }
    }
}
