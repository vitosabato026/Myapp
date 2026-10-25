package it.promozionepuglia.gironeb.data

import java.text.Normalizer

data class Team(
    val key: String,
    /** Nome mostrato in classifica e nelle liste. */
    val name: String,
    /** Nome ufficiale della società, mostrato nella pagina squadra. */
    val fullName: String,
    /** Colore del badge con la sigla (ARGB). */
    val color: Long,
    val facebook: String? = null,
    val instagram: String? = null,
    /** Nomi con cui le fonti indicano la squadra. */
    val aliases: List<String> = emptyList(),
)

/**
 * Anagrafica del Girone B di Promozione Puglia 2026/27.
 * I profili social sono stati trovati con ricerca web (Sporting Oria: forniti dall'utente).
 */
object Teams {
    val all: List<Team> = listOf(
        Team("ERC", "Virtus Erchie", "Virtus Erchie", 0xFF21618C,
            facebook = "https://www.facebook.com/p/Virtus-Erchie-61551476596919/",
            aliases = listOf("V. Erchie", "Erchie")),
        Team("ORI", "Sporting Oria", "ASD Sporting Oria", 0xFFB03A2E,
            facebook = "https://www.facebook.com/p/Asd-Sporting-ORIA-61579176719705/",
            instagram = "https://www.instagram.com/asdsportingoria/",
            aliases = listOf("Sp. Oria", "Oria")),
        Team("LAT", "Latiano", "US Latiano 1955", 0xFFAF601A,
            facebook = "https://www.facebook.com/US.Latiano/",
            instagram = "https://www.instagram.com/uslatiano1955/"),
        Team("GRO", "Real V. Grottaglie", "Real Virtus Grottaglie", 0xFF7D3C98,
            facebook = "https://www.facebook.com/realvirtusgrottaglie/",
            aliases = listOf("R. V. Grottaglie", "RV Grottaglie", "R.V. Grottaglie", "Grottaglie")),
        Team("GIN", "Ginosa", "ASD Ginosa Calcio", 0xFF2E8B57,
            facebook = "https://www.facebook.com/asdginosacalcio/",
            instagram = "https://www.instagram.com/asdginosacalcio/"),
        Team("TAV", "F. Taviano", "Football Taviano", 0xFFA93226,
            facebook = "https://www.facebook.com/footballtaviano/",
            instagram = "https://www.instagram.com/asdfootballtaviano/",
            aliases = listOf("Taviano", "Football Taviano")),
        Team("TRI", "Atletico Tricase", "ASD Atletico Tricase", 0xFF1F6FB2,
            facebook = "https://www.facebook.com/p/ASD-Atletico-Tricase-100057364273735/",
            instagram = "https://www.instagram.com/asdatleticotricase/",
            aliases = listOf("A. Tricase", "Atl. Tricase", "Tricase")),
        Team("TRE", "Trepuzzi", "SSD Trepuzzi Calcio", 0xFFC27C0E,
            facebook = "https://www.facebook.com/p/SSD-Trepuzzi-Calcio-61561764222585/",
            instagram = "https://www.instagram.com/trepuzzicalcio/",
            aliases = listOf("Trepuzzi Calcio")),
        Team("MAT", "Virtus Matino", "Polisportiva Virtus Matino", 0xFF117A65,
            facebook = "https://www.facebook.com/p/Polisportiva-Virtus-Matino-100064112465771/",
            instagram = "https://www.instagram.com/virtusmatino1967/",
            aliases = listOf("V. Matino", "Matino")),
        Team("SAV", "Sava", "ASD Sava Calcio", 0xFF884EA0,
            facebook = "https://www.facebook.com/p/ASD-Sava-Calcio-100071193434455/"),
        Team("RUF", "Ruffano", "ASD Ruffano Calcio", 0xFFB9770E,
            facebook = "https://www.facebook.com/p/ASD-Ruffano-Calcio-100057487297818/",
            instagram = "https://www.instagram.com/asd_ruffano_calcio/"),
        Team("MAN", "Manduria", "UG Manduria Sport", 0xFFC0392B,
            facebook = "https://www.facebook.com/UGManduriaSport/"),
        Team("LOC", "V. Locorotondo", "AC Virtus Locorotondo 1948", 0xFF1A5276,
            facebook = "https://www.facebook.com/acvirtuslocorotondo1948/",
            instagram = "https://www.instagram.com/acvirtuslocorotondo1948/",
            aliases = listOf("Locorotondo", "Virtus Locorotondo")),
        Team("SVI", "San Vito", "US San Vito 1923", 0xFF196F3D,
            facebook = "https://www.facebook.com/ussanvito1923/",
            aliases = listOf("S. Vito")),
        Team("LEV", "Leverano", "ASD Leverano Football", 0xFFCA6F1E,
            facebook = "https://www.facebook.com/asdleveranofootball/",
            instagram = "https://www.instagram.com/leveranofootball/"),
        Team("MES", "Mesagne", "Mesagne Calcio 2020", 0xFF2471A3,
            facebook = "https://www.facebook.com/Mesagnecalcio2020/",
            instagram = "https://www.instagram.com/mesagnecalcio2020/"),
    )

    private val byKey: Map<String, Team> = all.associateBy { it.key }
    private val byAlias: Map<String, Team> = buildMap {
        for (team in all) {
            put(normalize(team.name), team)
            put(normalize(team.fullName), team)
            team.aliases.forEach { put(normalize(it), team) }
        }
    }

    /** Restituisce la chiave della squadra; i nomi sconosciuti diventano una chiave derivata dal nome. */
    fun keyFor(sourceName: String): String =
        byAlias[normalize(sourceName)]?.key ?: "X:" + sourceName.trim()

    /** La squadra per chiave; per squadre fuori anagrafica crea una voce con il nome della fonte. */
    fun byKey(key: String): Team = byKey[key] ?: run {
        val name = key.removePrefix("X:")
        Team(key, name, name, 0xFF5A6B82)
    }

    /**
     * Se il testo nomina la squadra con uno dei suoi nomi. Le forme abbreviate ("V. Erchie")
     * contano anche con la sola parte significativa ("Erchie"), purché di almeno 4 lettere.
     */
    fun mentionedIn(key: String, text: String): Boolean {
        val team = byKey(key)
        val haystack = " " + normalize(text) + " "
        val names = listOf(team.name, team.fullName) + team.aliases
        return names.any { name ->
            val n = normalize(name)
            val core = n.split(' ').lastOrNull { it.length >= 4 && it !in COMMON_WORDS }
            haystack.contains(" $n ") || (core != null && haystack.contains(" $core "))
        }
    }

    /** Parole dei nomi che non bastano da sole a identificare una squadra. */
    private val COMMON_WORDS = setOf("calcio", "virtus", "sporting", "football", "real", "atletico", "sport", "1955", "1923", "1948", "1967", "2020")

    /** Sigla di tre lettere per il badge. */
    fun abbreviation(team: Team): String =
        if (byKey.containsKey(team.key)) team.key
        else normalize(team.name).filter { it.isLetter() }.take(3).uppercase()

    internal fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}
