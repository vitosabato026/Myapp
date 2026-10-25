package it.promozionepuglia.gironeb.ui

import androidx.test.core.app.ApplicationProvider
import it.promozionepuglia.gironeb.data.LeagueRepository
import it.promozionepuglia.gironeb.data.ROME
import it.promozionepuglia.gironeb.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDateTime
import java.time.ZonedDateTime

/** Aggiornamento all'apertura e in diretta, con le fonti simulate e il tempo virtuale. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val requests = mutableListOf<String>()
    private var calendar = "calendario_live_sample.html"
    private var now: ZonedDateTime = at(15, 0)

    private fun at(h: Int, m: Int): ZonedDateTime = LocalDateTime.of(2026, 10, 25, h, m).atZone(ROME)

    private fun resource(name: String) = requireNotNull(javaClass.classLoader?.getResource(name)).readText()

    private val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val path = chain.request().url.encodedPath
        val source = path.substringAfterLast('/')
        requests += source
        val body = when (source) {
            "classifica" -> resource("classifica_sample.html")
            "calendario" -> resource(calendar)
            else -> "[]"
        }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body.toResponseBody()).build()
    }).build()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Esegue [block] e ferma sempre gli aggiornamenti, altrimenti runTest aspetterebbe all'infinito. */
    private fun TestScope.withViewModel(block: TestScope.(AppViewModel) -> Unit) {
        val vm = AppViewModel(
            LeagueRepository(File.createTempFile("league", ".json").apply { deleteOnExit() }, client, dispatcher),
            SettingsStore(ApplicationProvider.getApplicationContext()),
            clock = { now },
        )
        try {
            block(vm)
        } finally {
            vm.onBackground()
        }
    }

    @Test
    fun refreshesOnOpenAndFollowsLiveMatches() = runTest(dispatcher) {
        withViewModel { vm ->
            vm.onForeground()
            runCurrent()
            assertEquals(listOf("calendario", "classifica", "posts"), requests.sorted())
            assertEquals(2, vm.state.value.snapshot!!.liveMatches.size)

            // In diretta: ogni 30 secondi classifica e calendario, senza notizie.
            requests.clear()
            advanceTimeBy(29_000)
            runCurrent()
            assertTrue(requests.isEmpty())
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(listOf("calendario", "classifica"), requests.sorted())

            // Partite finite e nient'altro in programma: gli aggiornamenti automatici si fermano.
            calendar = "calendario_sample.html"
            now = at(20, 30)
            requests.clear()
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(2, requests.size)
            assertTrue(vm.state.value.snapshot!!.liveMatches.isEmpty())
            requests.clear()
            advanceTimeBy(3_600_000)
            runCurrent()
            assertTrue(requests.isEmpty())
        }
    }

    @Test
    fun refreshesEveryTimeTheAppIsReopenedAndStopsInBackground() = runTest(dispatcher) {
        withViewModel { vm ->
            vm.onForeground()
            runCurrent()
            vm.onBackground()
            requests.clear()
            advanceTimeBy(600_000)
            runCurrent()
            assertTrue("nessun aggiornamento in background", requests.isEmpty())

            vm.onForeground()
            runCurrent()
            assertEquals(listOf("calendario", "classifica", "posts"), requests.sorted())
        }
    }
}
