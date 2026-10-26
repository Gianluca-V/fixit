package com.vespelabs.fixit.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vespelabs.fixit.core.ui.R
import com.vespelabs.fixit.core.ui.theme.FixItTheme

/**
 * Top bar + content for every screen. Root screens of a tab pass `onBack = null` (title only);
 * every other screen passes the callback that `:app` resolves to `goBackFrom(key)`.
 *
 * The bar is painted in `primaryContainer` instead of the default surface: it is the one brand
 * surface that is always on screen, so it is what makes the app recognisable while scrolling.
 *
 * [showBrand] draws the FixIt wordmark before the title, separated by a thin rule. It defaults to
 * "root screens only" (`onBack == null`): on a tab root there is no parent to go back to, so the
 * brand can take the first slot; on a drill-down the screen name is what the user needs to see, and
 * the back arrow already needs its space. Auth screens turn it off explicitly because the sign-in
 * screen shows the wordmark large, in its own content.
 *
 * The bottom and side system insets are not applied here: `:app` owns them (bottom bar or window).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    showBrand: Boolean = onBack == null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showBrand) {
                            FixItWordmark(
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .width(1.dp)
                                    .height(14.dp)
                                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f)),
                            )
                        }
                        Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        contentWindowInsets = WindowInsets(0),
        content = content,
    )
}

@Preview(showBackground = true)
@Composable
private fun ScreenScaffoldPreview() {
    FixItTheme {
        ScreenScaffold(title = "Perfil") { padding ->
            Text("Raíz de tab: muestra la marca", modifier = Modifier.padding(padding))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ScreenScaffoldDetailPreview() {
    FixItTheme {
        ScreenScaffold(title = "Detalle de la solicitud", onBack = {}) { padding ->
            Text("Drill-down: solo el título", modifier = Modifier.padding(padding))
        }
    }
}
