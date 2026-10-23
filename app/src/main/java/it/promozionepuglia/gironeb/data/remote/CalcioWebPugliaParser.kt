package it.promozionepuglia.gironeb.data.remote

import it.promozionepuglia.gironeb.data.Match
import it.promozionepuglia.gironeb.data.StandingLine
import it.promozionepuglia.gironeb.data.StandingRow
import it.promozionepuglia.gironeb.data.Teams
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Legge le pagine di livescore.calciowebpuglia.it, la stessa fonte che SalentoSport
 * usa nelle sue dirette del girone.
 */
object CalcioWebPugliaParser {

    private val roundHeader = Regex("""Giornata\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val score = Regex("""^(\d+)\s*-\s*(\d+)$""")
    private val number = Regex("""\d+""")

    /** Pagina "calendario": una tabella per giornata, con risultati e marcatori. */
    fun parseCalendar(html: String): List<Match> {
        val doc = Jsoup.parse(html)
        val matches = mutableListOf<Match>()
        for (table in doc.select("table.partite")) {
            val round = roundHeader.find(table.select("thead").text())
                ?.groupValues?.get(1)?.toInt() ?: continue
            val rows = table.select("tbody > tr")
            for ((i, row) in rows.withIndex()) {
                if (!row.hasClass("top")) continue
                val cells = row.select("> td")
                if (cells.size < 4) continue
                val next = rows.getOrNull(i + 1)?.takeIf { it.hasClass("marcatori") }
                val result = score.find(cells[2].cleanText())
                matches += Match(
                    round = round,
                    kickoff = cells[0].selectFirst("[title]")?.attr("title")?.trim().orEmpty(),
                    homeKey = Teams.keyFor(cells[1].cleanText()),
                    awayKey = Teams.keyFor(cells[3].cleanText()),
                    homeGoals = result?.groupValues?.get(1)?.toInt(),
                    awayGoals = result?.groupValues?.get(2)?.toInt(),
                    scorers = next?.cleanText().orEmpty(),
                )
            }
        }
        return matches
    }

    /** Pagina "classifica": per ogni squadra 7 colonne (PT G V N P Gf Gs) per totale, casa e trasferta. */
    fun parseStandings(html: String): List<StandingRow> {
        val doc = Jsoup.parse(html)
        return doc.select("table tbody tr").mapNotNull { row ->
            val cells = row.select("> td")
            if (cells.size < 23) return@mapNotNull null
            val nameCell = cells[1]
            val penalty = nameCell.selectFirst("span.label")?.text()
                ?.let { number.find(it)?.value?.toInt() } ?: 0
            val name = nameCell.clone().apply { select("span").remove() }.cleanText()
            val values = cells.drop(2).map { number.find(it.text())?.value?.toInt() ?: 0 }
            fun line(offset: Int) = StandingLine(
                points = values[offset], played = values[offset + 1], won = values[offset + 2],
                drawn = values[offset + 3], lost = values[offset + 4],
                goalsFor = values[offset + 5], goalsAgainst = values[offset + 6],
            )
            StandingRow(
                teamKey = Teams.keyFor(name),
                sourceName = name,
                penalty = penalty,
                total = line(0),
                home = line(7),
                away = line(14),
            )
        }
    }

    private fun Element.cleanText(): String =
        text().replace(' ', ' ').replace(Regex("\\s+"), " ").trim()
}
