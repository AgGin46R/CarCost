package com.aggin.carcost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.aggin.carcost.data.local.settings.SettingsManager
import com.aggin.carcost.data.update.AppUpdateManager
import com.aggin.carcost.data.update.VersionInfo
import com.aggin.carcost.presentation.navigation.AppNavigation
import com.aggin.carcost.ui.theme.CarCostTheme
import kotlinx.coroutines.launch

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
            var pendingUpdate by remember { mutableStateOf<VersionInfo?>(null) }
            val updateManager = remember { AppUpdateManager(this) }

            // Check for update once on start
            LaunchedEffect(Unit) {
                pendingUpdate = updateManager.checkForUpdate()
            }

            CarCostTheme(themeSetting = theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(pendingInviteToken = pendingInviteToken)

                    pendingUpdate?.let { info ->
                        AlertDialog(
                            onDismissRequest = { pendingUpdate = null },
                            title = { Text("Доступно обновление ${info.versionName}") },
                            text = {
                                Text(
                                    buildString {
                                        if (info.releaseNotes.isNotBlank()) {
                                            appendLine(info.releaseNotes)
                                            appendLine()
                                        }
                                        append("Обновление будет загружено в фоне и установлено автоматически.")
                                    }
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    updateManager.downloadAndInstall(info.apkUrl)
                                    pendingUpdate = null
                                }) { Text("Обновить") }
                            },
                            dismissButton = {
                                TextButton(onClick = { pendingUpdate = null }) {
                                    Text("Позже")
                                }
                            }
                        )
                    }
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
