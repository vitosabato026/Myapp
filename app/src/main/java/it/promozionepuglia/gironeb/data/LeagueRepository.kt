package it.promozionepuglia.gironeb.data

import it.promozionepuglia.gironeb.data.remote.CalcioWebPugliaParser
import it.promozionepuglia.gironeb.data.remote.SalentoSportParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Scarica i dati dalle fonti e li conserva su disco per l'uso offline. */
class LeagueRepository(
    private val cacheFile: File,
    private val client: OkHttpClient = defaultClient(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun cached(): LeagueSnapshot? = withContext(io) {
        runCatching { json.decodeFromString<LeagueSnapshot>(cacheFile.readText()) }.getOrNull()
    }

    /**
     * Aggiorna classifica, calendario e, se [includeNews], le notizie. Se una fonte non risponde
     * si tengono i suoi dati precedenti; se non c'è nulla da mostrare lancia l'errore.
     */
    suspend fun refresh(previous: LeagueSnapshot?, includeNews: Boolean = true): LeagueSnapshot = withContext(io) {
        coroutineScope {
            val standings = async { runCatching { CalcioWebPugliaParser.parseStandings(get(STANDINGS_URL)) } }
            val calendar = async { runCatching { CalcioWebPugliaParser.parseCalendar(get(CALENDAR_URL)) } }
            val news = if (includeNews) async { runCatching { SalentoSportParser.parsePosts(get(NEWS_URL)) } } else null

            val s = standings.await().getOrNull()?.takeIf { it.isNotEmpty() } ?: previous?.standings
            val c = calendar.await().getOrNull()?.takeIf { it.isNotEmpty() } ?: previous?.matches
            val n = news?.await()?.getOrNull() ?: previous?.news.orEmpty()
            if (s == null || c == null) {
                throw standings.await().exceptionOrNull()
                    ?: calendar.await().exceptionOrNull()
                    ?: IOException("Nessun dato ricevuto dalle fonti")
            }
            LeagueSnapshot(s, c, n, System.currentTimeMillis()).also { snapshot ->
                runCatching { cacheFile.writeText(json.encodeToString(LeagueSnapshot.serializer(), snapshot)) }
            }
        }
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} da $url")
            return response.body?.string() ?: throw IOException("Risposta vuota da $url")
        }
    }

    companion object {
        private const val CWP_BASE = "https://livescore.calciowebpuglia.it/stagioni/26-27/promozione/gironi/girone-b"
        const val STANDINGS_URL = "$CWP_BASE/classifica"
        const val CALENDAR_URL = "$CWP_BASE/calendario"

        /** Categoria "Promozione" (id 6) di SalentoSport. */
        const val NEWS_URL = "https://www.salentosport.net/wp-json/wp/v2/posts" +
            "?categories=6&per_page=30&_fields=id,date,link,title,excerpt,yoast_head_json.og_image"

        const val TUTTOCAMPO_URL = "https://www.tuttocampo.it/Puglia/Promozione/GironeB/Classifica"

        private const val USER_AGENT = "PromozioneB-Android/0.1"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
