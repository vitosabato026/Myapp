package it.promozionepuglia.gironeb

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.promozionepuglia.gironeb.ui.AppRoot
import it.promozionepuglia.gironeb.ui.AppViewModel
import it.promozionepuglia.gironeb.ui.theme.PromozioneTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels { AppViewModel.factory(application as PromozioneApp) }

    /** Partita da aprire dopo il tocco su una notifica di gol. */
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pendingRoute = intent.getStringExtra(EXTRA_MATCH)
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            PromozioneTheme(themeMode) {
                AppRoot(viewModel, pendingRoute) { pendingRoute = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_MATCH)?.let { pendingRoute = it }
    }

    companion object {
        const val EXTRA_MATCH = "match"
    }
}
