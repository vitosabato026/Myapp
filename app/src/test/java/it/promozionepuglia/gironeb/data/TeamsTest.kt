package it.promozionepuglia.gironeb.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamsTest {
    @Test
    fun recognisesTeamsInNewsTitles() {
        val title = "PROMOZIONE/B – Erchie e Sporting Oria in vetta a braccetto: colpo esterno del Matino, tris Grottaglie"
        assertTrue(Teams.mentionedIn("ERC", title))
        assertTrue(Teams.mentionedIn("ORI", title))
        assertTrue(Teams.mentionedIn("MAT", title))
        assertTrue(Teams.mentionedIn("GRO", title))
        assertFalse(Teams.mentionedIn("LAT", title))
        // "Virtus" o "Calcio" da soli non bastano.
        assertFalse(Teams.mentionedIn("LOC", "Virtus Francavilla Calcio, il comunicato"))
    }

    @Test
    fun matchNewsMustBeAroundTheMatchDate() {
        val m = Match(7, "dom, 18/10/26 - 15:30", "MAT", "GRO", null, null, "")
        fun news(date: String) = NewsItem(1, "Colpo esterno del Matino, tris Grottaglie", "", "", null, date, "SalentoSport")
        val s = LeagueSnapshot(emptyList(), listOf(m), listOf(news("2026-10-19T14:30:00")), 0)
        assertTrue(s.newsAbout(m).isNotEmpty())
        val week = s.copy(matches = listOf(m.copy(round = 8, kickoff = "dom, 25/10/26 - 14:30")))
        assertTrue(week.newsAbout(week.matches.single()).isEmpty())
    }
}
