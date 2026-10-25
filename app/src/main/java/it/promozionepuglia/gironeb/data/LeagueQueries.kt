package it.promozionepuglia.gironeb.data

enum class StandingKind { TOTAL, HOME, AWAY }

fun StandingRow.line(kind: StandingKind): StandingLine = when (kind) {
    StandingKind.TOTAL -> total
    StandingKind.HOME -> home
    StandingKind.AWAY -> away
}

/** Classifica ordinata: totale nell'ordine della fonte, casa/trasferta per punti, differenza reti, gol fatti. */
fun LeagueSnapshot.standings(kind: StandingKind): List<StandingRow> =
    if (kind == StandingKind.TOTAL) standings
    else standings.sortedWith(
        compareByDescending<StandingRow> { it.line(kind).points }
            .thenByDescending { it.line(kind).goalDiff }
            .thenByDescending { it.line(kind).goalsFor },
    )

fun LeagueSnapshot.position(teamKey: String): Int =
    standings.indexOfFirst { it.teamKey == teamKey } + 1

fun LeagueSnapshot.standingOf(teamKey: String): StandingRow? =
    standings.firstOrNull { it.teamKey == teamKey }

fun Match.involves(teamKey: String) = homeKey == teamKey || awayKey == teamKey

/** 'V', 'N' o 'P' dal punto di vista della squadra. */
fun Match.resultFor(teamKey: String): Char? {
    val h = homeGoals ?: return null
    val a = awayGoals ?: return null
    val (mine, other) = if (homeKey == teamKey) h to a else a to h
    return when {
        mine > other -> 'V'
        mine == other -> 'N'
        else -> 'P'
    }
}

/** Partite giocate dalla squadra, dalla più recente. */
fun LeagueSnapshot.playedBy(teamKey: String): List<Match> =
    matches.filter { it.isPlayed && it.involves(teamKey) }.sortedByDescending { it.round }

fun LeagueSnapshot.form(teamKey: String, count: Int = 5): List<Char> =
    playedBy(teamKey).take(count).mapNotNull { it.resultFor(teamKey) }.reversed()

fun LeagueSnapshot.nextMatchOf(teamKey: String): Match? =
    matches.filter { !it.isPlayed && it.involves(teamKey) }.minByOrNull { it.round }

/** La sfida della giornata tra le squadre meglio piazzate. */
fun LeagueSnapshot.bigMatch(round: Int): Match? =
    matches.filter { it.round == round }.minByOrNull {
        val ph = position(it.homeKey).takeIf { p -> p > 0 } ?: 99
        val pa = position(it.awayKey).takeIf { p -> p > 0 } ?: 99
        ph + pa
    }

data class LeagueStats(
    val totalGoals: Int,
    val matchesPlayed: Int,
    val bestAttack: StandingRow?,
    val bestDefense: StandingRow?,
) {
    val averagePerMatch: Double get() = if (matchesPlayed == 0) 0.0 else totalGoals.toDouble() / matchesPlayed
}

/** Statistiche gol: le gare vinte a tavolino non contano come gol segnati. */
fun LeagueSnapshot.stats(): LeagueStats {
    val played = matches.filter { it.isPlayed && !it.isForfeit }
    return LeagueStats(
        totalGoals = played.sumOf { (it.homeGoals ?: 0) + (it.awayGoals ?: 0) },
        matchesPlayed = played.size,
        bestAttack = standings.maxByOrNull { it.total.goalsFor },
        bestDefense = standings.minByOrNull { it.total.goalsAgainst },
    )
}

fun LeagueSnapshot.findMatch(round: Int, homeKey: String): Match? =
    matches.firstOrNull { it.round == round && it.homeKey == homeKey }

/** L'altra sfida tra le stesse squadre (andata o ritorno). */
fun LeagueSnapshot.otherLeg(m: Match): Match? =
    matches.firstOrNull { it.homeKey == m.awayKey && it.awayKey == m.homeKey }

/**
 * Notizie che nominano entrambe le squadre, uscite nei 4 giorni prima o dopo la partita
 * (così i riepiloghi di altre giornate non contano), dalla più recente.
 */
fun LeagueSnapshot.newsAbout(m: Match, limit: Int = 3): List<NewsItem> {
    val kickoff = m.kickoffAt()?.toLocalDateTime() ?: return emptyList()
    return news.filter { item ->
        val date = runCatching { java.time.LocalDateTime.parse(item.date) }.getOrNull() ?: return@filter false
        val text = item.title + " " + item.excerpt
        date.isAfter(kickoff.minusDays(4)) && date.isBefore(kickoff.plusDays(4)) &&
            Teams.mentionedIn(m.homeKey, text) && Teams.mentionedIn(m.awayKey, text)
    }.sortedByDescending { it.date }.take(limit)
}
