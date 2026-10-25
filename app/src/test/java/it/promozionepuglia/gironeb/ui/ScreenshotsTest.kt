package it.promozionepuglia.gironeb.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.drawToBitmap
import it.promozionepuglia.gironeb.data.LeagueSnapshot
import it.promozionepuglia.gironeb.data.NewsItem
import it.promozionepuglia.gironeb.data.ScorersCalculator
import it.promozionepuglia.gironeb.data.ThemeMode
import it.promozionepuglia.gironeb.data.remote.CalcioWebPugliaParser
import it.promozionepuglia.gironeb.ui.screens.HomeScreen
import it.promozionepuglia.gironeb.ui.screens.MatchScreen
import it.promozionepuglia.gironeb.ui.screens.MatchesScreen
import it.promozionepuglia.gironeb.ui.screens.NewsScreen
import it.promozionepuglia.gironeb.ui.screens.ScorersScreen
import it.promozionepuglia.gironeb.ui.screens.StandingsScreen
import it.promozionepuglia.gironeb.ui.screens.TeamScreen
import it.promozionepuglia.gironeb.ui.theme.AppTheme
import it.promozionepuglia.gironeb.ui.theme.PromozioneTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Disegna le schermate con i dati di esempio in test/resources e le salva in
 * app/build/screenshots, per controllarle senza emulatore.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h800dp-xxhdpi")
class ScreenshotsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun resource(name: String) = requireNotNull(javaClass.classLoader?.getResource(name)).readText()

    private val snapshot by lazy {
        LeagueSnapshot(
            standings = CalcioWebPugliaParser.parseStandings(resource("classifica_sample.html")),
            matches = CalcioWebPugliaParser.parseCalendar(resource("calendario_sample.html")),
            news = listOf(
                NewsItem(1, "ECCELLENZA, PROMOZIONE/B – Il Giudice sportivo, gare del 18.10.2026", "", "https://www.salentosport.net/", null, "2026-10-21T16:45:00", "SalentoSport"),
                NewsItem(2, "PROMOZIONE/B – Erchie e Sporting Oria in vetta a braccetto: colpo esterno del Matino, tris Grottaglie. I tabellini", "", "https://www.salentosport.net/", null, "2026-10-19T14:30:00", "SalentoSport"),
                NewsItem(3, "PROMOZIONE/B – Classifica marcatori alla 6ª giornata: un terzetto domina la scena", "", "https://www.salentosport.net/", null, "2026-10-14T12:00:00", "SalentoSport"),
            ),
            updatedAtMillis = 1_792_740_000_000,
        )
    }
    private val scorers by lazy { ScorersCalculator.compute(snapshot.matches) }

    /** Gli stessi dati con due partite dell'8ª giornata in corso. */
    private val liveSnapshot by lazy {
        snapshot.copy(matches = CalcioWebPugliaParser.parseCalendar(resource("calendario_live_sample.html")))
    }

    @Test
    fun scorersMatchPublishedTable() {
        // SalentoSport alla 6ª: Secka, Castrì e Carrino a 6; Secka segna ancora alla 7ª.
        val top = scorers.first()
        assertEquals("Secka", top.name)
        assertEquals(7, top.goals)
        assertEquals(6, scorers.first { it.name == "Carrino" }.goals)
    }

    @Test fun home() = shoot("home") { HomeScreen(snapshot, scorers, null, {}, {}, {}, {}, {}) }
    @Test fun homeLive() = shoot("home_live") { HomeScreen(liveSnapshot, scorers, null, {}, {}, {}, {}, {}) }
    @Test fun matchesLive() = shoot("partite_live") { MatchesScreen(liveSnapshot) {} }
    @Test fun matchDetail() = shoot("partita") { MatchScreen(snapshot, 6, "TAV", {}, {}) }
    @Test fun matchDetailLive() = shoot("partita_live") { MatchScreen(liveSnapshot, 8, "MAT", {}, {}) }
    @Test fun standings() = shoot("classifica") { StandingsScreen(snapshot) {} }
    @Test fun matches() = shoot("partite") { MatchesScreen(snapshot) {} }
    @Test fun goals() = shoot("gol") { ScorersScreen(snapshot, scorers) {} }
    @Test fun news() = shoot("notizie") { NewsScreen(snapshot.news) }
    @Test fun teamErchie() = shoot("squadra_erchie") { TeamScreen(snapshot, scorers, "ERC") {} }
    @Test fun teamOria() = shoot("squadra_oria") { TeamScreen(snapshot, scorers, "ORI") {} }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        var mode by mutableStateOf(ThemeMode.LIGHT)
        compose.setContent {
            PromozioneTheme(mode) {
                Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) { content() }
            }
        }
        for (m in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            mode = m
            compose.waitForIdle()
            val bitmap = compose.activity.window.decorView.drawToBitmap()
            val dir = File("build/screenshots").apply { mkdirs() }
            File(dir, "${name}_${m.name.lowercase()}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
