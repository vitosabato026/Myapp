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

    @Test
    fun listsGoalsByMinuteWithTeamAndPenalties() {
        val g = ScorersCalculator.goals(
            match("TAV", "GRO", 3, 4, "10' Quarta (T), 17' Castrì (T), 44' rig., 52' e 63' Ruibal (G), 50' Secondo (G), 87' Gennari (T)"),
        )
        assertEquals(listOf("10'", "17'", "44'", "50'", "52'", "63'", "87'"), g.map { it.minute })
        assertEquals(listOf("TAV", "TAV", "GRO", "GRO", "GRO", "GRO", "TAV"), g.map { it.teamKey })
        assertEquals(listOf("44'"), g.filter { it.penalty }.map { it.minute })
        assertEquals("Ruibal", g[2].scorer)
    }

    @Test
    fun penaltyAfterNameAndStoppageTime() {
        val g = ScorersCalculator.goals(
            match("ORI", "SAV", 2, 1, "12' Gomis (O), 90' Del Gaudio (S), 90+4' Houma (O)"),
        )
        assertEquals(listOf("12'", "90'", "90+4'"), g.map { it.minute })
        val r = ScorersCalculator.goals(match("GIN", "SAV", 1, 2, "46' Secka (G), 52' Galeandro (S), 65' Richella rig. (S)"))
        assertTrue(r.last().penalty)
        assertEquals("SAV", r.last().teamKey)
        val two = ScorersCalculator.goals(match("LAT", "MES", 8, 0, "60' rig. e 64' Abatelillo"))
        assertEquals(listOf(true, false), two.map { it.penalty })
    }

    @Test
    fun ownGoalsAreListedButNotCredited() {
        val m = match("ERC", "MES", 2, 0, "10' Perez, 70' autorete Rossi")
        val g = ScorersCalculator.goals(m)
        assertEquals(2, g.size)
        assertTrue(g.last().ownGoal)
        assertEquals(listOf("Perez"), ScorersCalculator.compute(listOf(m)).map { it.name })
    }
}
