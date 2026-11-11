package it.promozionepuglia.gironeb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.promozionepuglia.gironeb.data.LeagueSnapshot
import it.promozionepuglia.gironeb.data.Match
import it.promozionepuglia.gironeb.ui.components.MatchList
import it.promozionepuglia.gironeb.ui.components.formatKickoff
import it.promozionepuglia.gironeb.ui.theme.AppTheme

@Composable
fun MatchesScreen(snapshot: LeagueSnapshot, onMatchClick: (Match) -> Unit = {}) {
    val c = AppTheme.colors
    val rounds = snapshot.rounds
    var round by rememberSaveable {
        mutableIntStateOf(snapshot.liveMatches.firstOrNull()?.round ?: snapshot.lastPlayedRound)
    }
    val matches = snapshot.matches.filter { it.round == round }
    // Una scheda per giorno, come le liste di Sofascore.
    val byDay = matches.groupBy { formatKickoff(it.kickoff).substringBefore(" · ") }

    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp)).background(c.surface).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { round-- }, enabled = round > (rounds.firstOrNull() ?: 1)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Giornata precedente")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${round}ª giornata", fontWeight = FontWeight.Bold)
                Text(byDay.keys.joinToString(" / "), color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { round++ }, enabled = round < (rounds.lastOrNull() ?: 1)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Giornata successiva")
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            byDay.forEach { (day, list) ->
                item(key = day) { MatchList(list, onMatchClick, title = day.uppercase()) }
            }
            if (matches.any { it.isLive }) item { LiveFootnote() }
        }
    }
}

/** Spiega come si aggiornano le partite in corso. */
@Composable
fun LiveFootnote() {
    Text(
        "Risultati e marcatori in diretta si aggiornano da soli ogni 30 secondi. " +
            "Il minuto è stimato dall'orario d'inizio: la fonte non lo pubblica.",
        color = AppTheme.colors.muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}
