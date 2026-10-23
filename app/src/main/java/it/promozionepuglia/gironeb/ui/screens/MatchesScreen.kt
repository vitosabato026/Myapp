package it.promozionepuglia.gironeb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.promozionepuglia.gironeb.data.LeagueSnapshot
import it.promozionepuglia.gironeb.data.Match
import it.promozionepuglia.gironeb.data.Teams
import it.promozionepuglia.gironeb.ui.components.Card
import it.promozionepuglia.gironeb.ui.components.TeamBadge
import it.promozionepuglia.gironeb.ui.components.formatKickoff
import it.promozionepuglia.gironeb.ui.components.kickoffTime
import it.promozionepuglia.gironeb.ui.theme.AppTheme

@Composable
fun MatchesScreen(snapshot: LeagueSnapshot, onTeamClick: (String) -> Unit) {
    val c = AppTheme.colors
    val rounds = snapshot.rounds
    var round by rememberSaveable { mutableIntStateOf(snapshot.lastPlayedRound) }
    val matches = snapshot.matches.filter { it.round == round }
    val dates = matches.map { formatKickoff(it.kickoff).substringBefore(" · ") }.distinct()

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
                Text(dates.joinToString(" / "), color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { round++ }, enabled = round < (rounds.lastOrNull() ?: 1)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Giornata successiva")
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(matches, key = { it.homeKey + it.awayKey + it.round }) { MatchCard(it, onTeamClick) }
        }
    }
}

@Composable
fun MatchCard(m: Match, onTeamClick: (String) -> Unit) {
    val c = AppTheme.colors
    val home = Teams.byKey(m.homeKey)
    val away = Teams.byKey(m.awayKey)
    val homeWon = m.isPlayed && m.homeGoals!! > m.awayGoals!!
    val awayWon = m.isPlayed && m.awayGoals!! > m.homeGoals!!
    Card(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clickable { onTeamClick(m.homeKey) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TeamBadge(home)
                Text(home.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (homeWon) FontWeight.Bold else FontWeight.Normal)
            }
            Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (m.isPlayed) "${m.homeGoals} - ${m.awayGoals}" else kickoffTime(m.kickoff),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                )
                Text(
                    if (m.isPlayed) "FINALE" else formatKickoff(m.kickoff).substringBefore(" · ").uppercase(),
                    color = c.muted, style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(
                Modifier.weight(1f).clickable { onTeamClick(m.awayKey) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                Text(away.name, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                    fontWeight = if (awayWon) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f, fill = false))
                TeamBadge(away)
            }
        }
        if (m.scorers.isNotBlank()) {
            Text(
                m.scorers, color = c.muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}
