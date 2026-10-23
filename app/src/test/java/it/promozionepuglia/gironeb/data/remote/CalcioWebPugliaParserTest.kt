package it.promozionepuglia.gironeb.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcioWebPugliaParserTest {

    private fun resource(name: String) =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "manca $name" }.readText()

    @Test
    fun parsesStandingsWithHomeAwayAndPenalty() {
        val rows = CalcioWebPugliaParser.parseStandings(resource("classifica_sample.html"))
        assertEquals(16, rows.size)

        val first = rows.first()
        assertEquals("ERC", first.teamKey)
        assertEquals(17, first.total.points)
        assertEquals(7, first.total.played)
        assertEquals(16, first.total.goalsFor)
        assertEquals(0, first.total.goalsAgainst)
        assertEquals(5, first.home.points)
        assertEquals(12, first.away.points)

        val last = rows.last()
        assertEquals("MES", last.teamKey)
        assertEquals("Mesagne", last.sourceName)
        assertEquals(1, last.penalty)
        assertEquals(0, last.total.points)
        assertEquals(20, last.total.goalsAgainst)

        // Tutte le squadre della fonte sono riconosciute dall'anagrafica.
        assertTrue(rows.none { it.teamKey.startsWith("X:") })
    }

    @Test
    fun parsesCalendarRoundsResultsAndScorers() {
        val matches = CalcioWebPugliaParser.parseCalendar(resource("calendario_sample.html"))
        assertEquals((1..8).toList(), matches.map { it.round }.distinct())
        assertEquals(64, matches.size)
        assertTrue(matches.none { it.homeKey.startsWith("X:") || it.awayKey.startsWith("X:") })

        val forfeit = matches.first { it.round == 1 && it.homeKey == "LEV" }
        assertEquals("ORI", forfeit.awayKey)
        assertEquals(0, forfeit.homeGoals)
        assertEquals(3, forfeit.awayGoals)
        assertTrue(forfeit.isForfeit)

        val tricase = matches.first { it.round == 7 && it.homeKey == "TRI" }
        assertEquals("TAV", tricase.awayKey)
        assertEquals("33' Martella, 82' Desiderato", tricase.scorers)
        assertEquals("dom, 18/10/26 - 15:30", tricase.kickoff)

        val upcoming = matches.filter { it.round == 8 }
        assertEquals(8, upcoming.size)
        assertFalse(upcoming.any { it.isPlayed })
        assertNull(upcoming.first().homeGoals)
    }
}
