package it.promozionepuglia.gironeb.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import it.promozionepuglia.gironeb.data.LeagueRepository
import it.promozionepuglia.gironeb.data.LeagueSnapshot
import it.promozionepuglia.gironeb.data.SettingsStore
import it.promozionepuglia.gironeb.data.ThemeMode
import it.promozionepuglia.gironeb.data.remote.CalcioWebPugliaParser
import it.promozionepuglia.gironeb.ui.theme.PromozioneTheme
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/** Navigazione tra le schede con l'app completa, offline sui dati di esempio. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w392dp-h800dp-xxhdpi")
class NavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun resource(name: String) = requireNotNull(javaClass.classLoader?.getResource(name)).readText()

    private fun startApp() {
        val snapshot = LeagueSnapshot(
            standings = CalcioWebPugliaParser.parseStandings(resource("classifica_sample.html")),
            matches = CalcioWebPugliaParser.parseCalendar(resource("calendario_sample.html")),
            news = emptyList(),
            updatedAtMillis = 0,
        )
        val cache = File.createTempFile("league", ".json").apply {
            deleteOnExit()
            writeText(Json.encodeToString(LeagueSnapshot.serializer(), snapshot))
        }
        val offline = OkHttpClient.Builder().addInterceptor(Interceptor { throw IOException("offline") }).build()
        val viewModel = AppViewModel(
            LeagueRepository(cache, offline),
            SettingsStore(ApplicationProvider.getApplicationContext()),
        )
        compose.setContent { PromozioneTheme(ThemeMode.LIGHT) { AppRoot(viewModel) } }
        compose.waitForIdle()
    }

    private fun isHomeShown() =
        compose.onAllNodesWithText("Tutte le partite della giornata").fetchSemanticsNodes().isNotEmpty()

    private fun clickTab(label: String) {
        compose.onAllNodesWithText(label).filter(hasClickAction()).onLast().performClick()
        compose.waitForIdle()
    }

    @Test
    fun homeTabReturnsHomeFromEveryTab() {
        startApp()
        assertTrue(isHomeShown())
        for (tab in listOf("Classifica", "Partite", "Gol", "Notizie")) {
            clickTab(tab)
            assertFalse("$tab non dovrebbe mostrare la Home", isHomeShown())
            clickTab("Home")
            assertTrue("Home non mostrata dopo $tab", isHomeShown())
        }
    }

    @Test
    fun homeTabReturnsHomeAfterHomeShortcuts() {
        startApp()
        compose.onNodeWithText("Completa").performClick()
        compose.waitForIdle()
        assertFalse(isHomeShown())
        clickTab("Home")
        assertTrue(isHomeShown())

        compose.onNodeWithText("Tutte le partite della giornata").performClick()
        compose.waitForIdle()
        clickTab("Home")
        assertTrue(isHomeShown())
    }
}
