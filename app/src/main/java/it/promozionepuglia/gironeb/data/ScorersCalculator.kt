package it.promozionepuglia.gironeb.data

/**
 * Ricava la classifica marcatori dai tabellini, che hanno forme come:
 * "33' Martella, 82' Desiderato", "6' e 62' Secondo", "4', 20' e 55' Diaz (Man)",
 * "60' rig. e 64' Abatelillo", "46' Secka (G), 65' Richella rig. (S)".
 * La sigla tra parentesi indica la squadra quando hanno segnato entrambe.
 */
object ScorersCalculator {

    private val minute = Regex("""\d+(?:\+\d+)?\s*['’]""")
    private val teamTag = Regex("""\(([^)]+)\)""")

    fun compute(matches: List<Match>): List<Scorer> {
        val goals = linkedMapOf<Pair<String, String>, IntArray>() // (nome, squadra) -> [gol, rigori]
        for (m in matches) {
            if (!m.isPlayed || m.isForfeit || m.scorers.isBlank()) continue
            var pendingGoals = 0
            var pendingPens = 0
            for (raw in m.scorers.split(',')) {
                val part = raw.trim()
                if (part.isEmpty()) continue
                val n = minute.findAll(part).count()
                val pens = Regex("""\brig\.?""").findAll(part).count()
                val tag = teamTag.find(part)?.groupValues?.get(1)?.trim()
                val name = part
                    .replace(teamTag, " ")
                    .replace(minute, " ")
                    .replace(Regex("""\brig\.?"""), " ")
                    .replace(Regex("""(^|\s)e(\s|$)"""), " ")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
                if (name.isEmpty()) {
                    pendingGoals += n
                    pendingPens += pens
                    continue
                }
                val total = n + pendingGoals
                val totalPens = pens + pendingPens
                pendingGoals = 0
                pendingPens = 0
                if (name.startsWith("aut", ignoreCase = true) || total == 0) continue
                val teamKey = when {
                    tag != null -> resolveTag(tag, m)
                    m.awayGoals == 0 -> m.homeKey
                    m.homeGoals == 0 -> m.awayKey
                    else -> null
                } ?: continue
                val acc = goals.getOrPut(name to teamKey) { IntArray(2) }
                acc[0] += total
                acc[1] += totalPens
            }
        }
        return goals.map { (k, v) -> Scorer(k.first, k.second, v[0], v[1]) }
            .sortedWith(compareByDescending<Scorer> { it.goals }.thenBy { it.penalties }.thenBy { it.name })
    }

    /** Abbina sigle come "Ta", "VM", "Man", "O" a una delle due squadre in campo. */
    internal fun resolveTag(tag: String, match: Match): String? {
        val t = Teams.normalize(tag).replace(" ", "")
        if (t.isEmpty()) return null
        val candidates = listOf(match.homeKey, match.awayKey).filter { key ->
            val team = Teams.byKey(key)
            (listOf(team.name, team.fullName) + team.aliases).any { name ->
                val words = Teams.normalize(name).split(' ').filter { it.isNotEmpty() }
                words.any { it.startsWith(t) } || words.joinToString("") { it.take(1) } == t
            }
        }
        return candidates.singleOrNull()
    }
}
