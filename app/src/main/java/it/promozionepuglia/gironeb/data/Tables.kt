package it.promozionepuglia.gironeb.data

/** Una riga di classifica da mostrare, eventualmente ricalcolata con le partite in corso. */
data class TableEntry(
    val teamKey: String,
    val line: StandingLine,
    val position: Int,
    val penalty: Int,
    /** Posizione prima della giornata in corso; null fuori dalla diretta. */
    val previousPosition: Int? = null,
    /** La partita in corso della squadra, se c'è. */
    val live: Match? = null,
)

/** Aggiunge ([sign] = 1) o toglie ([sign] = -1) un risultato a una riga di classifica. */
internal fun StandingLine.withResult(goalsFor: Int, goalsAgainst: Int, sign: Int = 1): StandingLine {
    val (w, d, l) = when {
        goalsFor > goalsAgainst -> Triple(1, 0, 0)
        goalsFor == goalsAgainst -> Triple(0, 1, 0)
        else -> Triple(0, 0, 1)
    }
    return StandingLine(
        points = points + sign * (3 * w + d),
        played = played + sign,
        won = won + sign * w,
        drawn = drawn + sign * d,
        lost = lost + sign * l,
        goalsFor = this.goalsFor + sign * goalsFor,
        goalsAgainst = this.goalsAgainst + sign * goalsAgainst,
    )
}

/** Gol fatti e subiti della squadra in una partita con risultato. */
internal fun Match.goalsOf(teamKey: String): Pair<Int, Int> =
    if (homeKey == teamKey) homeGoals!! to awayGoals!! else awayGoals!! to homeGoals!!

private val emptyLine = StandingLine(0, 0, 0, 0, 0, 0, 0)

/**
 * Ordina le squadre per punti, differenza reti e gol fatti, come la fonte;
 * a pari merito vale l'ordine della fonte (che applica anche gli scontri diretti).
 */
private fun LeagueSnapshot.rank(lines: Map<String, StandingLine>): List<String> {
    val sourceOrder = standings.withIndex().associate { (i, r) -> r.teamKey to i }
    return lines.keys.sortedWith(
        compareByDescending<String> { lines.getValue(it).points }
            .thenByDescending { lines.getValue(it).goalDiff }
            .thenByDescending { lines.getValue(it).goalsFor }
            .thenBy { sourceOrder[it] ?: Int.MAX_VALUE },
    )
}

/**
 * Classifica in tempo reale: quella della fonte più i risultati parziali delle partite in corso,
 * con la posizione di partenza della giornata. Null se non si gioca.
 *
 * La fonte potrebbe già contare le partite in corso: per ogni squadra lo si capisce dalle partite
 * giocate in classifica rispetto a quelle finite nel calendario, così nessun risultato conta due volte.
 */
fun LeagueSnapshot.liveTable(): List<TableEntry>? {
    val live = liveMatches
    if (live.isEmpty() || standings.isEmpty()) return null
    val liveRounds = live.map { it.round }.toSet()
    val now = mutableMapOf<String, StandingLine>()
    val before = mutableMapOf<String, StandingLine>()
    for (row in standings) {
        val key = row.teamKey
        val finished = matches.count { it.isPlayed && it.involves(key) }
        val alreadyCounted = row.total.played > finished
        var line = row.total
        val liveMatch = live.firstOrNull { it.involves(key) }
        if (liveMatch != null && !alreadyCounted) {
            val (f, a) = liveMatch.goalsOf(key)
            line = line.withResult(f, a)
        }
        now[key] = line
        // Prima della giornata: si tolgono tutti i risultati delle giornate in corso.
        before[key] = matches.filter { it.round in liveRounds && it.hasScore && it.involves(key) }
            .fold(line) { acc, m -> val (f, a) = m.goalsOf(key); acc.withResult(f, a, sign = -1) }
    }
    val previous = rank(before).withIndex().associate { (i, k) -> k to i + 1 }
    val penalties = standings.associate { it.teamKey to it.penalty }
    return rank(now).mapIndexed { i, key ->
        TableEntry(key, now.getValue(key), i + 1, penalties[key] ?: 0, previous[key], live.firstOrNull { it.involves(key) })
    }
}

/** La classifica da mostrare: in diretta quella ricalcolata, altrimenti quella della fonte. */
fun LeagueSnapshot.table(kind: StandingKind = StandingKind.TOTAL): List<TableEntry> {
    if (kind == StandingKind.TOTAL) liveTable()?.let { return it }
    return standings(kind).mapIndexed { i, r -> TableEntry(r.teamKey, r.line(kind), i + 1, r.penalty) }
}

/**
 * Posizione della squadra dopo ogni giornata giocata, ricostruita dai risultati del calendario.
 * Le penalizzazioni si applicano a tutte le giornate (la fonte non dice da quando valgono).
 */
fun LeagueSnapshot.positionsByRound(teamKey: String): List<Pair<Int, Int>> {
    val penalties = standings.associate { it.teamKey to it.penalty }
    val teams = standings.map { it.teamKey }
    if (teamKey !in teams) return emptyList()
    val lines = teams.associateWith { emptyLine.copy(points = -(penalties[it] ?: 0)) }.toMutableMap()
    val result = mutableListOf<Pair<Int, Int>>()
    for (round in 1..lastPlayedRound) {
        for (m in matches.filter { it.round == round && it.isPlayed }) {
            for (key in listOf(m.homeKey, m.awayKey)) {
                val line = lines[key] ?: continue
                val (f, a) = m.goalsOf(key)
                lines[key] = line.withResult(f, a)
            }
        }
        result += round to rank(lines).indexOf(teamKey) + 1
    }
    return result
}

/** Fasce di minuti per "quando segna e quando subisce". */
val GOAL_BANDS = listOf("1-15'", "16-30'", "31-45'", "46-60'", "61-75'", "76-90'")

/** Fascia di un gol: i recuperi contano nella fascia che chiudono (45+2' è nel primo tempo). */
private fun Goal.band(): Int? {
    val base = Regex("""\d+""").find(minute)?.value?.toInt() ?: return null
    return ((base - 1).coerceIn(0, 89)) / 15
}

data class GoalTiming(val scored: IntArray, val conceded: IntArray, val unknown: Int)

/** Gol fatti e subiti per fascia di minuti, dai tabellini delle partite finite. */
fun LeagueSnapshot.goalTiming(teamKey: String): GoalTiming {
    val scored = IntArray(GOAL_BANDS.size)
    val conceded = IntArray(GOAL_BANDS.size)
    var unknown = 0
    for (m in matches) {
        if (!m.isPlayed || !m.involves(teamKey)) continue
        for (g in ScorersCalculator.goals(m)) {
            val band = g.band()
            when {
                band == null || g.teamKey == null -> unknown++
                g.teamKey == teamKey -> scored[band]++
                else -> conceded[band]++
            }
        }
    }
    return GoalTiming(scored, conceded, unknown)
}

data class TeamRecord(val cleanSheets: Int, val failedToScore: Int, val played: Int)

/** Partite senza gol subiti e senza gol segnati (escluse le gare a tavolino). */
fun LeagueSnapshot.teamRecord(teamKey: String): TeamRecord {
    val played = matches.filter { it.isPlayed && !it.isForfeit && it.involves(teamKey) }
    return TeamRecord(
        cleanSheets = played.count { it.goalsOf(teamKey).second == 0 },
        failedToScore = played.count { it.goalsOf(teamKey).first == 0 },
        played = played.size,
    )
}

/** Tutte le partite della squadra, in ordine di giornata. */
fun LeagueSnapshot.matchesOf(teamKey: String): List<Match> =
    matches.filter { it.involves(teamKey) }.sortedBy { it.round }

/** I gol di un giocatore, partita per partita (dalla più recente). */
fun LeagueSnapshot.goalsOfPlayer(name: String, teamKey: String): List<Pair<Match, List<Goal>>> =
    matches.mapNotNull { m ->
        val goals = ScorersCalculator.goals(m).filter { it.scorer == name && it.teamKey == teamKey && !it.ownGoal }
        if (goals.isEmpty()) null else m to goals
    }.sortedByDescending { it.first.round }

/**
 * Risultato all'intervallo ricavato dai minuti dei gol. Null se il tabellino non è completo
 * o se di un gol del primo tempo non si sa la squadra.
 */
fun Match.halfTimeScore(goals: List<Goal>): Pair<Int, Int>? {
    if (!hasScore || isForfeit || goals.size != homeGoals!! + awayGoals!!) return null
    val firstHalf = goals.filter { it.sortKey < 4600 }
    if (firstHalf.any { it.teamKey == null }) return null
    return firstHalf.count { it.teamKey == homeKey } to firstHalf.count { it.teamKey == awayKey }
}
