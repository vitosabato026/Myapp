package it.promozionepuglia.gironeb.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {

    private val store = SettingsStore(ApplicationProvider.getApplicationContext())

    @Test
    fun favoriteTeamAlsoNarrowsGoalAlertsUnlessChosenByHand() = runBlocking {
        // Notifiche mai toccate: seguendo una squadra arrivano solo i suoi gol.
        store.setFavoriteTeam("ORI")
        assertEquals("ORI", store.favoriteTeam.first())
        assertEquals(GoalAlerts.Team("ORI"), store.goalAlerts.first())

        // Scelta fatta a mano: cambiare squadra seguita non la tocca.
        store.setGoalAlerts(GoalAlerts.All)
        store.setFavoriteTeam("LAT")
        assertEquals(GoalAlerts.All, store.goalAlerts.first())

        store.setFavoriteTeam(null)
        assertEquals(null, store.favoriteTeam.first())
    }
}
