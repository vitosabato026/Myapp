package it.promozionepuglia.gironeb.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScorersCalculatorTest {

    private fun match(home: String, away: String, h: Int, a: Int, scorers: String) =
        Match(1, "", home, away, h, a, scorers)

    private fun goals(list: List<Scorer>, name: String, team: String) =
        list.firstOrNull { it.name == name && it.teamKey == team }

    @Test
    fun creditsGoalsToTheOnlyTeamThatScored() {
        val s = ScorersCalculator.compute(listOf(match("GRO", "SVI", 3, 0, "6' e 62' Secondo, 80' Ruibal")))
        assertEquals(2, goals(s, "Secondo", "GRO")?.goals)
        assertEquals(1, goals(s, "Ruibal", "GRO")?.goals)
    }

    @Test
    fun usesTeamTagsWhenBothTeamsScored() {
        val s = ScorersCalculator.compute(
            listOf(match("GIN", "SAV", 1, 2, "46' Secka (G), 52' Galeandro (S), 65' Richella rig. (S)")),
        )
        assertEquals(1, goals(s, "Secka", "GIN")?.goals)
        assertEquals(1, goals(s, "Galeandro", "SAV")?.goals)
        assertEquals(1, goals(s, "Richella", "SAV")?.penalties)
    }

    @Test
    fun handlesMinutesSplitAcrossCommasAndPenaltyBeforeName() {
        val s = ScorersCalculator.compute(
            listOf(
                match("MAT", "MAN", 3, 3, "4', 20' e 55' Diaz (Man), 15' e 30' Carrino (VM), 43' Almirall (VM)"),
                match("TAV", "GRO", 3, 4, "10' Quarta (T), 17' Castrì (T), 44' rig., 52' e 63' Ruibal (G), 50' Secondo (G), 87' Gennari (T)"),
            ),
        )
        assertEquals(3, goals(s, "Diaz", "MAN")?.goals)
        assertEquals(2, goals(s, "Carrino", "MAT")?.goals)
        assertEquals(3, goals(s, "Ruibal", "GRO")?.goals)
        assertEquals(1, goals(s, "Ruibal", "GRO")?.penalties)
        assertEquals(1, goals(s, "Castrì", "TAV")?.goals)
    }

    @Test
    fun ignoresForfeitsAndUnplayedMatches() {
        val s = ScorersCalculator.compute(
            listOf(
                match("LEV", "ORI", 0, 3, "(a tavolino)"),
                Match(8, "dom, 25/10/26 - 17:00", "TAV", "ORI", null, null, ""),
            ),
        )
        assertTrue(s.isEmpty())
    }

    @Test
    fun ambiguousTagIsNotCredited() {
        val m = match("LEV", "LOC", 3, 2, "")
        assertNull(ScorersCalculator.resolveTag("L", m))
        assertEquals("LEV", ScorersCalculator.resolveTag("Le", m))
        assertEquals("LOC", ScorersCalculator.resolveTag("VL", m))
    }
}
