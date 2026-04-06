package com.aggin.carcost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.aggin.carcost.data.local.settings.SettingsManager
import com.aggin.carcost.presentation.navigation.AppNavigation
import com.aggin.carcost.ui.theme.CarCostTheme

class MainActivity : ComponentActivity() {

    /** Token extracted from carcost://invite?token=... deep link, passed to AppNavigation */
    var pendingInviteToken: String? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Extract invite token from deep link if launched via carcost://invite
        pendingInviteToken = intent?.data
            ?.takeIf { it.scheme == "carcost" && it.host == "invite" }
            ?.getQueryParameter("token")

        setContent {
            val settingsManager = SettingsManager(LocalContext.current)
            val theme by settingsManager.themeFlow.collectAsState(initial = "System")

            CarCostTheme(themeSetting = theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(pendingInviteToken = pendingInviteToken)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // Handle deep link when app is already open (singleTop/singleTask)
        pendingInviteToken = intent.data
            ?.takeIf { it.scheme == "carcost" && it.host == "invite" }
            ?.getQueryParameter("token")
    }
}