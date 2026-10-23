package it.promozionepuglia.gironeb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.promozionepuglia.gironeb.ui.AppRoot
import it.promozionepuglia.gironeb.ui.AppViewModel
import it.promozionepuglia.gironeb.ui.theme.PromozioneTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels { AppViewModel.factory(application as PromozioneApp) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            PromozioneTheme(themeMode) {
                AppRoot(viewModel)
            }
        }
    }
}
