package it.promozionepuglia.gironeb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import it.promozionepuglia.gironeb.data.LeagueRepository
import it.promozionepuglia.gironeb.data.NewsItem
import it.promozionepuglia.gironeb.ui.components.Card
import it.promozionepuglia.gironeb.ui.components.formatNewsDate
import it.promozionepuglia.gironeb.ui.components.openUrl
import it.promozionepuglia.gironeb.ui.theme.AppTheme

@Composable
fun NewsScreen(news: List<NewsItem>) {
    val c = AppTheme.colors
    val context = LocalContext.current

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.clickable { openUrl(context, LeagueRepository.TUTTOCAMPO_URL) }, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("TUTTOCAMPO.IT", color = c.accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("Apri il Girone B su Tuttocampo", fontWeight = FontWeight.Medium)
                    }
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = c.muted)
                }
            }
        }
        if (news.isEmpty()) item {
            Text("Nessuna notizia disponibile al momento.", color = c.muted)
        }
        items(news, key = { it.source + it.id }) { n -> NewsRow(n) { openUrl(context, n.url) } }
    }
}

@Composable
private fun NewsRow(n: NewsItem, onClick: () -> Unit) {
    val c = AppTheme.colors
    Card(Modifier.clickable(onClick = onClick), padding = 12.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(
                model = n.imageUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)).background(c.line),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(n.source.uppercase(), color = c.accent2, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(n.title, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(formatNewsDate(n.date), color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
