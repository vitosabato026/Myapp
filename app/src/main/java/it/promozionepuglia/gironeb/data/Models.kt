package it.promozionepuglia.gironeb.data

import kotlinx.serialization.Serializable

/** Una riga di classifica (totale, casa o trasferta). */
@Serializable
data class StandingLine(
    val points: Int,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
) {
    val goalDiff: Int get() = goalsFor - goalsAgainst
}

@Serializable
data class StandingRow(
    val teamKey: String,
    val sourceName: String,
    /** Punti di penalizzazione (già sottratti dai punti totali dalla fonte). */
    val penalty: Int,
    val total: StandingLine,
    val home: StandingLine,
    val away: StandingLine,
)

@Serializable
data class Match(
    val round: Int,
    /** Testo data/ora così come pubblicato, es. "dom, 25/10/26 - 17:00". */
    val kickoff: String,
    val homeKey: String,
    val awayKey: String,
    val homeGoals: Int?,
    val awayGoals: Int?,
    /** Tabellino grezzo, es. "33' Martella, 82' Desiderato" oppure "(a tavolino)". */
    val scorers: String,
) {
    val isPlayed: Boolean get() = homeGoals != null && awayGoals != null
    val isForfeit: Boolean get() = scorers.contains("tavolino", ignoreCase = true)
}

@Serializable
data class Scorer(
    val name: String,
    val teamKey: String,
    val goals: Int,
    val penalties: Int,
)

@Serializable
data class NewsItem(
    val id: Long,
    val title: String,
    val excerpt: String,
    val url: String,
    val imageUrl: String?,
    /** Data ISO-8601 locale, es. "2026-10-19T14:30:00". */
    val date: String,
    val source: String,
)

/** Tutto ciò che l'app mostra, salvato anche su disco per l'uso offline. */
@Serializable
data class LeagueSnapshot(
    val standings: List<StandingRow>,
    val matches: List<Match>,
    val news: List<NewsItem>,
    val updatedAtMillis: Long,
) {
    /** Ultima giornata con almeno una partita giocata. */
    val lastPlayedRound: Int
        get() = matches.filter { it.isPlayed }.maxOfOrNull { it.round } ?: 1

    /** Prima giornata con partite ancora da giocare (o l'ultima, a campionato finito). */
    val nextRound: Int
        get() = matches.filter { !it.isPlayed }.minOfOrNull { it.round } ?: lastPlayedRound

    val rounds: List<Int> get() = matches.map { it.round }.distinct().sorted()
}
