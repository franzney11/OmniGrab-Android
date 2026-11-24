package com.franzney.omnigrab

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.franzney.omnigrab.ui.OmniGrabScreen
import com.franzney.omnigrab.ui.OmniGrabViewModel
import com.franzney.omnigrab.ui.theme.OmniGrabTheme

class MainActivity : ComponentActivity() {
    private val viewModel: OmniGrabViewModel by viewModels()

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        handleIncomingShareIntent(intent)

        setContent {
            OmniGrabTheme {
                OmniGrabScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            val urlMatch = Regex("""https?://\S+""").find(sharedText)?.value
            if (!urlMatch.isNullOrBlank()) {
                viewModel.onUrlChanged(urlMatch)
                viewModel.analyze(urlMatch)
            }
        }
    }
}
