package it.promozionepuglia.gironeb.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import it.promozionepuglia.gironeb.MainActivity
import it.promozionepuglia.gironeb.R
import it.promozionepuglia.gironeb.data.GoalAlert
import it.promozionepuglia.gironeb.data.Teams

/** Mostra una notifica per ogni nuovo gol, una sola volta per risultato. */
class GoalNotifier(private val context: Context) {

    private val prefs = context.getSharedPreferences("goal_notifications", Context.MODE_PRIVATE)

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Gol in diretta", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Un avviso a ogni gol delle partite del girone" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    @Synchronized
    fun notify(alerts: List<GoalAlert>) {
        if (alerts.isEmpty() || !canNotify()) return
        val sent = prefs.getString(SENT_KEY, "").orEmpty().split('|').filter { it.isNotEmpty() }.toMutableList()
        for (alert in alerts) {
            if (alert.key in sent) continue
            show(alert)
            sent += alert.key
        }
        prefs.edit().putString(SENT_KEY, sent.takeLast(200).joinToString("|")).apply()
    }

    @Suppress("MissingPermission") // controllato da canNotify()
    private fun show(alert: GoalAlert) {
        val m = alert.match
        val home = Teams.byKey(m.homeKey).name
        val away = Teams.byKey(m.awayKey).name
        val goal = alert.goal
        val text = when {
            goal == null -> "${m.round}ª giornata · marcatore in aggiornamento"
            goal.ownGoal -> "${goal.minute} autogol"
            else -> buildString {
                append("${goal.minute} ${goal.scorer}")
                if (goal.penalty) append(" (rigore)")
                goal.teamKey?.let { append(" · ${Teams.byKey(it).name}") }
            }
        }
        val open = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_MATCH, matchRoute(m.round, m.homeKey))
        val tap = PendingIntent.getActivity(
            context, matchId(m.round, m.homeKey), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goal)
            .setContentTitle("GOL! $home ${m.homeGoals}-${m.awayGoals} $away")
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        // Una notifica per partita: il gol successivo aggiorna la stessa.
        NotificationManagerCompat.from(context).notify(matchId(m.round, m.homeKey), notification)
    }

    companion object {
        const val CHANNEL_ID = "goals"
        private const val SENT_KEY = "sent"

        /** Destinazione "partita/{giornata}/{squadra di casa}". */
        fun matchRoute(round: Int, homeKey: String) = "partita/$round/$homeKey"

        private fun matchId(round: Int, homeKey: String) = matchRoute(round, homeKey).hashCode()
    }
}
