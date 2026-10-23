package it.promozionepuglia.gironeb

import android.app.Application
import it.promozionepuglia.gironeb.data.LeagueRepository
import it.promozionepuglia.gironeb.data.SettingsStore
import java.io.File

class PromozioneApp : Application() {
    val repository by lazy { LeagueRepository(File(filesDir, "league.json")) }
    val settings by lazy { SettingsStore(this) }
}
