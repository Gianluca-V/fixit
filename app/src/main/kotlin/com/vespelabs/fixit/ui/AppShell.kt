package com.vespelabs.fixit.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vespelabs.fixit.navigation.NavigationState
import com.vespelabs.fixit.navigation.TopLevelDestination

/**
 * Bottom bar built from the role's [tabs]; tapping a tab calls `selectTab` (re-tapping the active
 * tab returns to its root). [content] receives the padding that leaves room for the bar.
 */
@Composable
fun AppShell(
    tabs: List<TopLevelDestination>,
    navigationState: NavigationState,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
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
