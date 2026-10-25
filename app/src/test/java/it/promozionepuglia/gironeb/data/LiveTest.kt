package it.promozionepuglia.gironeb.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
