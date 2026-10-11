package com.vespelabs.fixit.core.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.vespelabs.fixit.core.ui.R
import com.vespelabs.fixit.core.ui.theme.FixItTheme

/**
 * Top bar + content for every screen. Root screens of a tab pass `onBack = null` (title only);
 * every other screen passes the callback that `:app` resolves to `goBackFrom(key)`.
 *
 * The bottom and side system insets are not applied here: `:app` owns them (bottom bar or window).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    }
                },
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
        ScreenScaffold(title = "Detalle", onBack = {}) { padding ->
            Text("Contenido", modifier = Modifier.padding(padding))
        }
    }
}
