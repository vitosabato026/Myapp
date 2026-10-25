package it.promozionepuglia.gironeb.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.promozionepuglia.gironeb.PromozioneApp
import it.promozionepuglia.gironeb.data.GoalAlerts
import it.promozionepuglia.gironeb.data.LeagueSnapshot
import it.promozionepuglia.gironeb.data.LivePolling
import it.promozionepuglia.gironeb.data.ROME
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Aggiornamenti con l'app chiusa, solo per le notifiche dei gol: una catena di controlli
 * che durante le partite si ripete ogni minuto e tra una giornata e l'altra aspetta il prossimo
 * calcio d'inizio, più un controllo ogni 12 ore per gli spostamenti di orario.
 */
object LiveUpdates {
    private const val LIVE = "live-updates"
    private const val PERIODIC = "calendar-check"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /**
     * Programma il prossimo controllo. [fromWorker]: chiamato dal controllo in corso, che va
     * accodato a sé stesso invece di essere sostituito (e quindi cancellato).
     */
    fun scheduleNext(context: Context, snapshot: LeagueSnapshot, fromWorker: Boolean = false) {
        val work = WorkManager.getInstance(context)
        val delay = LivePolling.backgroundDelayMillis(snapshot, ZonedDateTime.now(ROME))
        if (delay == null) {
            if (!fromWorker) work.cancelUniqueWork(LIVE)
            return
        }
        val request = OneTimeWorkRequestBuilder<LiveUpdatesWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(network)
            .build()
        val policy = if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        work.enqueueUniqueWork(LIVE, policy, request)
    }

    /** L'app è aperta e aggiorna da sé. */
    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(LIVE)
    }

    fun ensurePeriodicCheck(context: Context) {
        val request = PeriodicWorkRequestBuilder<CalendarCheckWorker>(12, TimeUnit.HOURS).setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

/** Un controllo della catena: aggiorna (le notifiche partono dal repository) e programma il successivo. */
class LiveUpdatesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PromozioneApp
        if (app.settings.goalAlerts.first() == GoalAlerts.Off) return Result.success()
        val previous = app.repository.cached()
        val snapshot = runCatching { app.repository.refresh(previous, includeNews = false) }.getOrNull() ?: previous
        snapshot?.let { LiveUpdates.scheduleNext(applicationContext, it, fromWorker = true) }
        return Result.success()
    }
}

/** Ogni 12 ore: aggiorna il calendario e riprogramma la catena, nel caso gli orari siano cambiati. */
class CalendarCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PromozioneApp
        if (app.settings.goalAlerts.first() == GoalAlerts.Off) return Result.success()
        val previous = app.repository.cached()
        val snapshot = runCatching { app.repository.refresh(previous) }.getOrNull() ?: return Result.retry()
        LiveUpdates.scheduleNext(applicationContext, snapshot)
        return Result.success()
    }
}
