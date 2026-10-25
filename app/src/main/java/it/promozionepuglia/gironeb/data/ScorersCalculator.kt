package it.promozionepuglia.gironeb.data

/** Un gol del tabellino. [teamKey] è null quando non si riesce a capire chi ha segnato. */
data class Goal(
    /** Come pubblicato, es. "45+3'". */
    val minute: String,
    val scorer: String,
    val teamKey: String?,
    val penalty: Boolean,
    val ownGoal: Boolean,
) {
    /** Per ordinare: 45+3' viene dopo 45' e prima di 46'. */
    val sortKey: Int get() {
        val (base, extra) = Regex("""(\d+)(?:\+(\d+))?""").find(minute)?.destructured ?: return Int.MAX_VALUE
        return base.toInt() * 100 + (extra.toIntOrNull() ?: 0)
    }
}

/**
 * Ricava i gol e la classifica marcatori dai tabellini, che hanno forme come:
 * "33' Martella, 82' Desiderato", "6' e 62' Secondo", "4', 20' e 55' Diaz (Man)",
 * "60' rig. e 64' Abatelillo", "46' Secka (G), 65' Richella rig. (S)".
 * La sigla tra parentesi indica la squadra quando hanno segnato entrambe.
 */
object ScorersCalculator {

    private val minute = Regex("""\d+(?:\+\d+)?\s*['’]""")
    private val teamTag = Regex("""\(([^)]+)\)""")
    private val penaltyMark = Regex("""\brig\.?""")

    /** Classifica marcatori: autogol e gare a tavolino esclusi, gol delle partite in corso inclusi. */
    fun compute(matches: List<Match>): List<Scorer> {
        val goals = linkedMapOf<Pair<String, String>, IntArray>() // (nome, squadra) -> [gol, rigori]
        for (m in matches) {
            for (g in goals(m)) {
                if (g.ownGoal) continue
                val acc = goals.getOrPut(g.scorer to (g.teamKey ?: continue)) { IntArray(2) }
                acc[0] += 1
                if (g.penalty) acc[1] += 1
            }
        }
        return goals.map { (k, v) -> Scorer(k.first, k.second, v[0], v[1]) }
            .sortedWith(compareByDescending<Scorer> { it.goals }.thenBy { it.penalties }.thenBy { it.name })
    }

    /** I gol di una partita in ordine di minuto. */
    fun goals(m: Match): List<Goal> {
        if (!m.hasScore || m.isForfeit || m.scorers.isBlank()) return emptyList()
        val result = mutableListOf<Goal>()
        // Minuti senza nome ("4', 20' e 55' Diaz"): valgono per il primo nome che segue.
        val pending = mutableListOf<Pair<String, Boolean>>()
        for (raw in m.scorers.split(',')) {
            val part = raw.trim()
            if (part.isEmpty()) continue
            val tag = teamTag.find(part)?.groupValues?.get(1)?.trim()
            val minutes = minute.findAll(part).toList()
            val penaltyMinutes = penaltyMark.findAll(part).map { mark ->
                // "60' rig." segna il minuto che precede; "Richella rig." l'unico minuto della parte.
                minutes.lastOrNull { it.range.last < mark.range.first } ?: minutes.firstOrNull()
            }.toSet()
            minutes.forEach { pending += it.value.replace(Regex("""\s+"""), "").replace('’', '\'') to (it in penaltyMinutes) }
            val name = part
                .replace(teamTag, " ")
                .replace(minute, " ")
                .replace(penaltyMark, " ")
                .replace(Regex("""(^|\s)e(\s|$)"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
            if (name.isEmpty()) continue
            val ownGoal = name.startsWith("aut", ignoreCase = true)
            val teamKey = when {
                tag != null -> resolveTag(tag, m)
                m.awayGoals == 0 -> m.homeKey
                m.homeGoals == 0 -> m.awayKey
                else -> null
            }
            pending.forEach { (min, pen) -> result += Goal(min, name, teamKey, pen, ownGoal) }
            pending.clear()
        }
        return result.sortedBy { it.sortKey }
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
