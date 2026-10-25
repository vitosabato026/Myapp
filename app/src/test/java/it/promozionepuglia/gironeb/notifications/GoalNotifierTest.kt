package it.promozionepuglia.gironeb.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import it.promozionepuglia.gironeb.MainActivity
import it.promozionepuglia.gironeb.data.Goal
import it.promozionepuglia.gironeb.data.GoalAlert
import it.promozionepuglia.gironeb.data.Match
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GoalNotifierTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notifier = GoalNotifier(app)

    private fun alert(h: Int, a: Int, goal: Goal?) =
        GoalAlert(Match(8, "dom, 25/10/26 - 14:30", "MAT", "GRO", h, a, "", isLive = true), goal)

    @Before
    fun setUp() {
        notifier.createChannel()
    }

    @Test
    fun showsOneNotificationPerMatchAndNeverRepeatsAGoal() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val first = alert(1, 0, Goal("12'", "Puzzovio", "MAT", penalty = false, ownGoal = false))
        notifier.notify(listOf(first))
        val n = shadowOf(manager).allNotifications.single()
        assertEquals("GOL! Virtus Matino 1-0 Real V. Grottaglie", n.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals("12' Puzzovio · Virtus Matino", n.extras.getString(NotificationCompat.EXTRA_TEXT))
        assertEquals(GoalNotifier.CHANNEL_ID, n.channelId)
        val intent = shadowOf(n.contentIntent).savedIntent
        assertEquals("partita/8/MAT", intent.getStringExtra(MainActivity.EXTRA_MATCH))

        // Lo stesso gol visto di nuovo (app e controllo in background) non si ripete.
        manager.cancelAll()
        notifier.notify(listOf(first))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())

        // Il gol successivo aggiorna la notifica della partita.
        notifier.notify(listOf(alert(1, 1, null)))
        val second = shadowOf(manager).allNotifications.single()
        assertEquals("GOL! Virtus Matino 1-1 Real V. Grottaglie", second.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals("8ª giornata · marcatore in aggiornamento", second.extras.getString(NotificationCompat.EXTRA_TEXT))
    }

    @Test
    fun staysSilentWithoutPermission() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier.notify(listOf(alert(2, 0, null)))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
