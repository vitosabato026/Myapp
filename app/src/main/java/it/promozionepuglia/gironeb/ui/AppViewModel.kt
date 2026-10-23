package it.promozionepuglia.gironeb.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.promozionepuglia.gironeb.PromozioneApp
import it.promozionepuglia.gironeb.data.LeagueRepository
import it.promozionepuglia.gironeb.data.LeagueSnapshot
import it.promozionepuglia.gironeb.data.Scorer
import it.promozionepuglia.gironeb.data.ScorersCalculator
import it.promozionepuglia.gironeb.data.SettingsStore
import it.promozionepuglia.gironeb.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val snapshot: LeagueSnapshot? = null,
    val scorers: List<Scorer> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class AppViewModel(
    private val repository: LeagueRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState(loading = true))
    val state: StateFlow<UiState> = _state.asStateFlow()

    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    init {
        viewModelScope.launch {
            repository.cached()?.let { cached -> _state.update { it.withSnapshot(cached) } }
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.refresh(_state.value.snapshot) }
                .onSuccess { snap -> _state.update { it.withSnapshot(snap).copy(loading = false) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, error = "Impossibile aggiornare i dati (${e.message ?: "errore di rete"})")
                    }
                }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    private fun UiState.withSnapshot(s: LeagueSnapshot) =
        copy(snapshot = s, scorers = ScorersCalculator.compute(s.matches))

    companion object {
        fun factory(app: PromozioneApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(app.repository, app.settings) }
        }
    }
}
