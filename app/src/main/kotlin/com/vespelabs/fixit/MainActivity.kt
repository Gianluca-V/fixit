package com.vespelabs.fixit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vespelabs.fixit.core.data.push.PushKeys
import com.vespelabs.fixit.core.ui.theme.FixItTheme
import com.vespelabs.fixit.core.ui.theme.isDark
import com.vespelabs.fixit.navigation.FixItApp
import com.vespelabs.fixit.push.PendingPushEvent
import com.vespelabs.fixit.ui.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

/** `singleTop` in the manifest: a notice tapped while the activity is on top arrives through [onNewIntent]. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val pendingPushEvent = PendingPushEvent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreation (rotation) must not reapply the notice that started the activity (design 14b.6).
        pendingPushEvent.onCreate(
            restored = savedInstanceState != null,
            type = intent.getStringExtra(PushKeys.TYPE),
            requestId = intent.getStringExtra(PushKeys.REQUEST_ID),
        )
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.mode.collectAsStateWithLifecycle()
            val pushEvent by pendingPushEvent.event.collectAsStateWithLifecycle()
            FixItTheme(darkTheme = themeMode.isDark()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    FixItApp(pushEvent = pushEvent, onPushEventConsumed = pendingPushEvent::consume)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingPushEvent.onNewIntent(intent.getStringExtra(PushKeys.TYPE), intent.getStringExtra(PushKeys.REQUEST_ID))
    }
}
