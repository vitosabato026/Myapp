package it.promozionepuglia.gironeb.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZonedDateTime

class LiveTest {

    private fun match(kickoff: String, goals: Int? = null, live: Boolean = false) =
        Match(8, kickoff, "MAT", "GRO", goals, goals, "", isLive = live)

    private fun at(h: Int, m: Int): ZonedDateTime = LocalDateTime.of(2026, 10, 25, h, m).atZone(ROME)

    private fun snapshot(vararg matches: Match) = LeagueSnapshot(emptyList(), matches.toList(), emptyList(), 0)

    @Test
    fun parsesKickoffInItalianTime() {
        assertEquals(at(14, 30), match("dom, 25/10/26 - 14:30").kickoffAt())
        assertNull(match("da definire").kickoffAt())
    }

    @Test
    fun estimatesMinuteWithHalfTimeBreak() {
        val m = match("dom, 25/10/26 - 14:30", 0, live = true)
        assertNull(m.estimatedMinute(at(14, 25)))
        assertEquals("1'", m.estimatedMinute(at(14, 30)))
        assertEquals("37'", m.estimatedMinute(at(15, 6)))
        assertEquals("45'+", m.estimatedMinute(at(15, 16)))
        assertEquals("Intervallo", m.estimatedMinute(at(15, 25)))
        assertEquals("46'", m.estimatedMinute(at(15, 33)))
        assertEquals("90'", m.estimatedMinute(at(16, 17)))
        assertEquals("90'+", m.estimatedMinute(at(16, 20)))
    }

    @Test
    fun pollsEvery30SecondsWhileAMatchIsLive() {
        val s = snapshot(match("dom, 25/10/26 - 14:30", 1, live = true))
        assertEquals(LivePolling.LIVE_MILLIS, LivePolling.nextDelayMillis(s, at(15, 0)))
    }

    @Test
    fun pollsEveryMinuteAroundKickoff() {
        val s = snapshot(match("dom, 25/10/26 - 14:30"))
        assertEquals(LivePolling.KICKOFF_MILLIS, LivePolling.nextDelayMillis(s, at(14, 15)))
        assertEquals(LivePolling.KICKOFF_MILLIS, LivePolling.nextDelayMillis(s, at(15, 0)))
    }

    @Test
    fun waitsForTheNextKickoffWindow() {
        val s = snapshot(match("dom, 25/10/26 - 14:30"))
        // 20 minuti prima delle 14:30 sono le 14:10: dalle 13:55 mancano 15 minuti.
        assertEquals(15 * 60_000L, LivePolling.nextDelayMillis(s, at(13, 55)))
        assertEquals(LivePolling.MAX_WAIT_MILLIS, LivePolling.nextDelayMillis(s, at(10, 50)))
    }

    @Test
    fun stopsWhenNothingIsHappening() {
        // Giornata finita.
        assertNull(LivePolling.nextDelayMillis(snapshot(match("dom, 25/10/26 - 14:30", 2)), at(16, 30)))
        // Prossima partita fra una settimana.
        assertNull(LivePolling.nextDelayMillis(snapshot(match("dom, 01/11/26 - 14:30")), at(18, 0)))
        // Partita mai aggiornata dalla fonte (es. rinviata): dopo 3 ore si smette.
        assertNull(LivePolling.nextDelayMillis(snapshot(match("dom, 25/10/26 - 14:30")), at(17, 31)))
    }

    @Test
    fun backgroundChecksAtMostEveryMinuteAndWaitsForNextRound() {
        val live = snapshot(match("dom, 25/10/26 - 14:30", 1, live = true))
        assertEquals(LivePolling.BACKGROUND_LIVE_MILLIS, LivePolling.backgroundDelayMillis(live, at(15, 0)))
        // Prossima giornata fra una settimana: si aspetta fino a 20 minuti prima del calcio d'inizio.
        val nextWeek = snapshot(match("dom, 01/11/26 - 14:30"))
        val expected = java.time.Duration.between(at(18, 0), LocalDateTime.of(2026, 11, 1, 14, 10).atZone(ROME)).toMillis()
        assertEquals(expected, LivePolling.backgroundDelayMillis(nextWeek, at(18, 0)))
        assertNull(LivePolling.backgroundDelayMillis(snapshot(match("dom, 25/10/26 - 14:30", 2)), at(18, 0)))
    }

    private fun scored(h: Int, a: Int, scorers: String, live: Boolean = true) =
        Match(8, "dom, 25/10/26 - 14:30", "MAT", "GRO", h, a, scorers, isLive = live)

    @Test
    fun detectsNewGoalsWithScorer() {
        val before = snapshot(scored(0, 0, ""))
        val after = snapshot(scored(1, 0, "12' Puzzovio"))
        val alerts = GoalDetector.newGoals(before, after, at(14, 45))
        assertEquals(1, alerts.size)
        assertEquals("Puzzovio", alerts.single().goal?.scorer)
        assertEquals("MAT", alerts.single().goal?.teamKey)
        assertEquals("8-MAT-GRO-1-0", alerts.single().key)
    }

    @Test
    fun scorerIsOmittedWhileTheScoresheetLagsBehind() {
        val before = snapshot(scored(1, 0, "12' Puzzovio"))
        val after = snapshot(scored(1, 1, "12' Puzzovio"))
        assertNull(GoalDetector.newGoals(before, after, at(15, 10)).single().goal)
    }

    @Test
    fun finalGoalEnteredTogetherWithTheFinalWhistleIsNotified() {
        val before = snapshot(scored(1, 0, "12' Puzzovio"))
        val after = snapshot(scored(2, 0, "12' Puzzovio, 90' Garcia", live = false))
        assertEquals(1, GoalDetector.newGoals(before, after, at(16, 20)).size)
    }

    @Test
    fun noAlertsForFirstLoadAnnulledGoalsOrOldResults() {
        val after = snapshot(scored(1, 0, "12' Puzzovio"))
        assertTrue(GoalDetector.newGoals(null, after, at(14, 45)).isEmpty())
        // Gol annullato.
        assertTrue(GoalDetector.newGoals(after, snapshot(scored(0, 0, "")), at(14, 50)).isEmpty())
        // Risultato di una partita non seguita in diretta (app aperta il giorno dopo).
        val unplayed = snapshot(Match(8, "dom, 25/10/26 - 14:30", "MAT", "GRO", null, null, ""))
        assertTrue(GoalDetector.newGoals(unplayed, snapshot(scored(2, 1, "", live = false)), at(14, 45).plusDays(1)).isEmpty())
        // Partita ancora "in diretta" per la fonte ma iniziata da più di 4 ore.
        assertTrue(GoalDetector.newGoals(after, snapshot(scored(2, 0, "")), at(19, 0)).isEmpty())
    }

    @Test
    fun teamFilterForAlerts() {
        val m = scored(1, 0, "")
        assertTrue(GoalAlerts.All.covers(m))
        assertTrue(GoalAlerts.Team("GRO").covers(m))
        assertFalse(GoalAlerts.Team("ORI").covers(m))
        assertFalse(GoalAlerts.Off.covers(m))
    }
}
