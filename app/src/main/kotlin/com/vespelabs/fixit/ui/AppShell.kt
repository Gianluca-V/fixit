package com.vespelabs.fixit.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.vespelabs.fixit.navigation.NavigationState
import com.vespelabs.fixit.navigation.TopLevelDestination

/**
 * Bottom bar built from the role's [tabs]; tapping a tab calls `selectTab` (re-tapping the active
 * tab returns to its root). [content] receives the padding that leaves room for the bar.
 * Only signed-in experiences show it, so the notification permission is asked here and never on authentication.
 */
@Composable
fun AppShell(
    tabs: List<TopLevelDestination>,
    navigationState: NavigationState,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    RequestNotificationPermission()
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == navigationState.currentTab,
                        onClick = { navigationState.selectTab(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
        // Screens draw their own top bar and handle the status bar inset.
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}

/**
 * Asks for POST_NOTIFICATIONS once per signed-in shell (design 14b.5): only on Android 13+ and when not granted.
 * The answer is ignored: a denial shows nothing and blocks nothing, the app just posts no notices.
 */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!asked && !granted) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
