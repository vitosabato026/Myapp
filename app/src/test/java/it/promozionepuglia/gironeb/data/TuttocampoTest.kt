package it.promozionepuglia.gironeb.data

import it.promozionepuglia.gironeb.data.remote.CalcioWebPugliaParser
import org.junit.Assert.assertEquals
import org.junit.Test

/** I tre indirizzi verificati su Tuttocampo da cui è ricavata la numerazione. */
class TuttocampoTest {

    private val sample by lazy {
        val html = requireNotNull(javaClass.classLoader?.getResource("calendario_sample.html")).readText()
        LeagueSnapshot(emptyList(), CalcioWebPugliaParser.parseCalendar(html), emptyList(), 0)
    }

    private val base = "https://www.tuttocampo.it/Puglia/Promozione/GironeB"

    @Test
    fun firstLegNumbersFollowHomeTeams() {
        assertEquals(
            "$base/Partita/8.3/latiano-atletico-tricase",
            Tuttocampo.matchUrl(sample, sample.findMatch(8, "LAT")!!),
        )
        assertEquals(
            "$base/Partita/8.7/virtus-erchie-san-vito",
            Tuttocampo.matchUrl(sample, sample.findMatch(8, "ERC")!!),
        )
    }

    @Test
    fun secondLegKeepsTheFirstLegNumber() {
        // 9ª giornata (dal calendario della fonte), giocata a campi invertiti nella 24ª.
        val firstLeg = listOf("TRI" to "MAT", "MES" to "LEV", "TRE" to "RUF", "LOC" to "SAV",
            "MAN" to "LAT", "SVI" to "TAV", "ORI" to "GIN", "GRO" to "ERC")
        val round24 = firstLeg.map { (h, a) -> Match(24, "dom, 28/02/27 - 16:00", a, h, null, null, "") }
        val s = LeagueSnapshot(emptyList(), round24, emptyList(), 0)
        assertEquals("$base/Partita/24.6/ginosa-sporting-oria", Tuttocampo.matchUrl(s, s.findMatch(24, "GIN")!!))
    }

    @Test
    fun roundPage() {
        assertEquals("$base/Giornata8", Tuttocampo.roundUrl(8))
    }
}
