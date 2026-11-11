package it.promozionepuglia.gironeb.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.promozionepuglia.gironeb.data.Match
import it.promozionepuglia.gironeb.data.Teams
import it.promozionepuglia.gironeb.ui.theme.AppTheme

/**
 * Una partita su due righe, come nelle liste di Sofascore: a sinistra orario, minuto o "Fin.",
 * poi le due squadre una sotto l'altra con il punteggio allineato a destra. Chi perde è in grigio,
 * le partite in corso hanno una barra rossa a sinistra e mostrano i marcatori.
 */
@Composable
fun MatchRow(m: Match, onClick: (Match) -> Unit, showRound: Boolean = false) {
    val c = AppTheme.colors
    val homeWon = m.hasScore && m.homeGoals!! > m.awayGoals!!
    val awayWon = m.hasScore && m.awayGoals!! > m.homeGoals!!
    val minute = if (m.isLive) liveMinute(m) else null
    val day = formatKickoff(m.kickoff).substringBefore(" · ").substringAfter(' ')
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable { onClick(m) }) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (m.isLive) c.live else Color.Transparent))
        Column(
            Modifier.width(58.dp).fillMaxHeight().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text(
                when {
                    m.isLive -> minute ?: "LIVE"
                    m.isForfeit -> "Tav."
                    m.isPlayed -> "Fin."
                    else -> kickoffTime(m.kickoff)
                },
                color = if (m.isLive) c.live else c.text2, style = MaterialTheme.typography.labelLarge,
                fontWeight = if (m.isLive) FontWeight.Bold else FontWeight.Medium, maxLines = 1,
            )
            Text(
                when {
                    m.isLive && minute != null -> "LIVE"
                    showRound -> "${m.round}ª g."
                    else -> day
                },
                color = if (m.isLive) c.live else c.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1,
            )
        }
        VerticalDivider(color = c.divider, modifier = Modifier.padding(vertical = 10.dp))
        Column(
            Modifier.weight(1f).padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TeamLine(m.homeKey, m.homeGoals, loser = awayWon && m.isPlayed, live = m.isLive)
            TeamLine(m.awayKey, m.awayGoals, loser = homeWon && m.isPlayed, live = m.isLive)
            if (m.isLive && m.scorers.isNotBlank()) {
                Text(m.scorers, color = c.muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TeamLine(key: String, goals: Int?, loser: Boolean, live: Boolean) {
    val c = AppTheme.colors
    val team = Teams.byKey(key)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TeamBadge(team, 20.dp)
        Text(
            team.name, color = if (loser) c.muted else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (goals != null) {
            Text(
                "$goals", fontWeight = FontWeight.Bold,
                color = when {
                    live -> c.live
                    loser -> c.muted
                    else -> c.text
                },
            )
        }
    }
}

/** Più partite in un'unica scheda, separate da una linea, con un titolo facoltativo. */
@Composable
fun MatchList(matches: List<Match>, onClick: (Match) -> Unit, title: String? = null, showRound: Boolean = false) {
    Card(padding = 0.dp) {
        if (title != null) {
            Text(
                title, color = AppTheme.colors.muted, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 2.dp),
            )
        }
        matches.forEachIndexed { i, m ->
            if (i > 0) HorizontalDivider(color = AppTheme.colors.divider, modifier = Modifier.padding(start = 61.dp))
            MatchRow(m, onClick, showRound)
        }
    }
}
