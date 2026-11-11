package it.promozionepuglia.gironeb.data

import it.promozionepuglia.gironeb.data.remote.CalcioWebPugliaParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TablesTest {

    private fun resource(name: String) = requireNotNull(javaClass.classLoader?.getResource(name)).readText()

    private val snapshot by lazy {
        LeagueSnapshot(
            CalcioWebPugliaParser.parseStandings(resource("classifica_sample.html")),
            CalcioWebPugliaParser.parseCalendar(resource("calendario_sample.html")),
            emptyList(), 0,
        )
    }
    private val live by lazy {
        snapshot.copy(matches = CalcioWebPugliaParser.parseCalendar(resource("calendario_live_sample.html")))
    }

    @Test
    fun rebuiltTableMatchesTheSource() {
        // Ricostruita dai risultati, la classifica dopo la 7ª coincide con quella pubblicata.
        for ((i, row) in snapshot.standings.withIndex()) {
            assertEquals(row.teamKey, i + 1, snapshot.positionsByRound(row.teamKey).last().second)
        }
        assertEquals(7, snapshot.positionsByRound("ERC").size)
    }

    @Test
    fun noLiveTableWithoutLiveMatches() {
        assertNull(snapshot.liveTable())
        assertEquals(snapshot.standings.map { it.teamKey }, snapshot.table().map { it.teamKey })
    }

    @Test
    fun liveTableAddsPartialResultsAndKeepsStartingPositions() {
        val table = live.liveTable()!!
        val matino = table.first { it.teamKey == "MAT" }
        // Matino 1-0 Grottaglie in corso: da 10 a 13 punti, dal 9º al 5º posto (davanti a Ginosa con 12).
        assertEquals(13, matino.line.points)
        assertEquals(8, matino.line.played)
        assertEquals(9, matino.previousPosition)
        assertEquals(5, matino.position)
        assertEquals("GRO", matino.live?.awayKey)
        // Pareggio in corso Latiano-Tricase: un punto a testa.
        assertEquals(14, table.first { it.teamKey == "LAT" }.line.points)
        // Chi non gioca ancora resta com'era.
        assertEquals(17, table.first { it.teamKey == "ERC" }.line.points)
    }

    @Test
    fun liveResultIsNotCountedTwiceWhenTheSourceAlreadyHasIt() {
        // La fonte ha già aggiornato la classifica del Matino con il parziale.
        val counted = live.copy(standings = live.standings.map { r ->
            if (r.teamKey == "MAT") r.copy(total = r.total.withResult(1, 0)) else r
        })
        assertEquals(13, counted.liveTable()!!.first { it.teamKey == "MAT" }.line.points)
    }

    @Test
    fun goalTimingAndRecord() {
        val t = snapshot.goalTiming("TAV")
        // Ogni gol delle partite del Taviano finisce in una fascia o tra quelli senza squadra.
        val row = snapshot.standingOf("TAV")!!.total
        assertEquals(row.goalsFor + row.goalsAgainst, t.scored.sum() + t.conceded.sum() + t.unknown)
        // Taviano-Grottaglie 3-4: il 44' subito è nella fascia 31-45'.
        assertTrue(t.conceded[2] >= 1)
        val r = snapshot.teamRecord("ERC")
        // Erchie: 7 partite, una vinta a tavolino; mai un gol subito.
        assertEquals(6, r.played)
        assertEquals(6, r.cleanSheets)
    }

    @Test
    fun halfTimeScoreFromGoalMinutes() {
        val m = snapshot.findMatch(6, "TAV")!!  // 3-4: 10', 17' Tav, 44' Gro | 50', 52', 63' Gro, 87' Tav
        assertEquals(2 to 1, m.halfTimeScore(ScorersCalculator.goals(m)))
        val forfeit = snapshot.findMatch(1, "LEV")!!
        assertNull(forfeit.halfTimeScore(ScorersCalculator.goals(forfeit)))
    }

    @Test
    fun playerGoals() {
        val ruibal = snapshot.goalsOfPlayer("Ruibal", "GRO")
        assertEquals(7, ruibal.first().first.round)  // dalla più recente
        assertEquals(3, ruibal.first { it.first.round == 6 }.second.size)
        val total = ScorersCalculator.compute(snapshot.matches).first { it.name == "Ruibal" }.goals
        assertEquals(total, ruibal.sumOf { it.second.size })
    }
}
