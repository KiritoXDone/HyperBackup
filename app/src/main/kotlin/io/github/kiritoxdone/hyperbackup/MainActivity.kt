package io.github.kiritoxdone.hyperbackup

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kiritoxdone.hyperbackup.feature.AppNavigation
import io.github.kiritoxdone.hyperbackup.ui.HyperTheme

class MainActivity : ComponentActivity() {
    private val model by viewModels<AppViewModel>()
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            HyperTheme(state.theme) { AppNavigation(model, state, intent.getStringExtra("taskId")) }
        }
    }
}
