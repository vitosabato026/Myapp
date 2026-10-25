package it.promozionepuglia.gironeb.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.promozionepuglia.gironeb.PromozioneApp
import it.promozionepuglia.gironeb.data.LeagueRepository
import it.promozionepuglia.gironeb.data.LeagueSnapshot
import it.promozionepuglia.gironeb.data.LivePolling
import it.promozionepuglia.gironeb.data.ROME
import it.promozionepuglia.gironeb.data.Scorer
import it.promozionepuglia.gironeb.data.ScorersCalculator
import it.promozionepuglia.gironeb.data.SettingsStore
import it.promozionepuglia.gironeb.data.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime

data class UiState(
    val snapshot: LeagueSnapshot? = null,
    val scorers: List<Scorer> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class AppViewModel(
    private val repository: LeagueRepository,
    private val settings: SettingsStore,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now(ROME) },
) : ViewModel() {

    private val _state = MutableStateFlow(UiState(loading = true))
    val state: StateFlow<UiState> = _state.asStateFlow()

    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    /** I dati salvati, letti una volta sola prima del primo aggiornamento. */
    private val cacheLoaded = viewModelScope.async {
        repository.cached()?.let { cached -> _state.update { it.withSnapshot(cached) } }
    }
    private val refreshMutex = Mutex()
    private var foregroundJob: Job? = null

    /**
     * L'app è in primo piano: aggiorna subito e, finché ci sono partite in corso
     * o in arrivo, continua ad aggiornare da sola.
     */
    fun onForeground() {
        foregroundJob?.cancel()
        foregroundJob = viewModelScope.launch {
            update(full = true, showProgress = true)
            while (true) {
                val wait = _state.value.snapshot?.let { LivePolling.nextDelayMillis(it, clock()) } ?: break
                delay(wait)
                update(full = false, showProgress = false)
            }
        }
    }

    /** L'app non è più visibile: niente aggiornamenti in background. */
    fun onBackground() {
        foregroundJob?.cancel()
        foregroundJob = null
    }

    /** Trascina per aggiornare. */
    fun refresh() {
        viewModelScope.launch { update(full = true, showProgress = true) }
    }

    /**
     * [full] scarica anche le notizie; gli aggiornamenti in diretta non le riscaricano
     * e non mostrano l'indicatore, per non disturbare ogni 30 secondi.
     */
    private suspend fun update(full: Boolean, showProgress: Boolean) {
        cacheLoaded.await()
        refreshMutex.withLock {
            if (showProgress) _state.update { it.copy(loading = true) }
            runCatching { repository.refresh(_state.value.snapshot, includeNews = full) }
                .onSuccess { snap -> _state.update { it.withSnapshot(snap).copy(loading = false, error = null) } }
                .onFailure { e ->
                    if (e is CancellationException) throw e
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
