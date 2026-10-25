package it.promozionepuglia.gironeb

import android.app.Application
import it.promozionepuglia.gironeb.data.GoalDetector
import it.promozionepuglia.gironeb.data.LeagueRepository
import it.promozionepuglia.gironeb.data.ROME
import it.promozionepuglia.gironeb.data.SettingsStore
import it.promozionepuglia.gironeb.notifications.GoalNotifier
import it.promozionepuglia.gironeb.notifications.LiveUpdates
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.ZonedDateTime

class PromozioneApp : Application() {
    val settings by lazy { SettingsStore(this) }
    val notifier by lazy { GoalNotifier(this) }
    val repository by lazy {
        LeagueRepository(File(filesDir, "league.json"), onUpdate = { previous, current ->
            val alerts = settings.goalAlerts.first()
            notifier.notify(
                GoalDetector.newGoals(previous, current, ZonedDateTime.now(ROME)).filter { alerts.covers(it.match) },
            )
        })
    }

    override fun onCreate() {
        super.onCreate()
        notifier.createChannel()
        // Nei test Robolectric WorkManager non è inizializzato.
        runCatching { LiveUpdates.ensurePeriodicCheck(this) }
    }
}
