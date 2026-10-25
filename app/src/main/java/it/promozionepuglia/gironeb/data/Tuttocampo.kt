package it.promozionepuglia.gironeb.data

/**
 * Collegamenti a Tuttocampo, che l'app non legge (è protetto da un controllo anti-bot)
 * ma apre nel browser.
 */
object Tuttocampo {
    private const val BASE = "https://www.tuttocampo.it/Puglia/Promozione/GironeB"

    /** Giornate del girone d'andata: con 16 squadre, 15. */
    private val firstLegRounds get() = Teams.all.size - 1

    fun roundUrl(round: Int) = "$BASE/Giornata$round"

    /**
     * Pagina della partita, "Partita/{giornata}.{numero}/{casa}-{ospite}". Tuttocampo numera le
     * partite di ogni giornata d'andata in ordine alfabetico della squadra di casa, e al ritorno
     * ogni partita tiene il numero dell'andata (quindi ordine della squadra in trasferta).
     * Ricavato da 8.3 Latiano-Atletico Tricase, 8.7 Virtus Erchie-San Vito, 24.6 Ginosa-Sporting Oria.
     * Conta solo il numero: il resto dell'indirizzo è ignorato dal sito.
     */
    fun matchUrl(snapshot: LeagueSnapshot, m: Match): String? {
        val firstLeg = m.round <= firstLegRounds
        val ordered = snapshot.matches.filter { it.round == m.round }
            .sortedBy { Teams.normalize(Teams.byKey(if (firstLeg) it.homeKey else it.awayKey).tuttocampoName) }
        val number = ordered.indexOf(m) + 1
        if (number == 0) return null
        val slug = listOf(m.homeKey, m.awayKey).joinToString("-") { Teams.normalize(Teams.byKey(it).tuttocampoName).replace(' ', '-') }
        return "$BASE/Partita/${m.round}.$number/$slug"
    }
}
