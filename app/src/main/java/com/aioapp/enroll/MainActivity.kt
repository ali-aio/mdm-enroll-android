package com.aioapp.enroll

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aioapp.enroll.ui.AioTheme
import com.aioapp.enroll.ui.EnrollVm
import com.aioapp.enroll.ui.Root

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            AioTheme {
                Root(viewModel(factory = EnrollVm.factory(application)))
            }
        }
    }
}
