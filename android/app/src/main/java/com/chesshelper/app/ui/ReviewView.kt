package com.chesshelper.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * ReviewView — port of iOS ReviewView (shows recent games via chess.com).
 * On Android we reuse the router to open chess.com review URLs inside Play tab,
 * plus provide quick links.
 */
@Composable
fun ReviewView(router: AppRouter) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Review", style = MaterialTheme.typography.headlineSmall)
        }
        Text("Analyze your games without risking your account. All review opens inside the Play tab's WebView.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)

        val items = listOf(
            "My Games" to "https://www.chess.com/games/archive",
            "Game Review" to "https://www.chess.com/analysis",
            "Daily Review" to "https://www.chess.com/analysis/daily",
            "Lessons" to "https://www.chess.com/lessons",
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { (label, url) ->
                Card(modifier = Modifier.fillMaxWidth().clickable { router.openInPlay(url) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        Text("Open →", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun OpeningsView(router: AppRouter) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Openings", style = MaterialTheme.typography.headlineSmall)
        Text("Study your repertoire. Tap to open the explorer on chess.com.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        val items = listOf(
            "Opening Explorer" to "https://www.chess.com/openings",
            "My Openings (White)" to "https://www.chess.com/openings/1.e4",
            "Sicilian Defense" to "https://www.chess.com/openings/Sicilian-Defense",
            "Queen's Gambit" to "https://www.chess.com/openings/Queens-Gambit",
            "King's Indian" to "https://www.chess.com/openings/Kings-Indian-Defense",
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { (label, url) ->
                Card(modifier = Modifier.fillMaxWidth().clickable { router.openInPlay(url) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(label)
                        Text("Open →", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2ECC71).copy(alpha = 0.12f))) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Offline Opening Book", style = MaterialTheme.typography.titleSmall)
                Text("Your overlay also shows ECO + name offline (no network) using the bundled 30-opening book — same as iOS.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

@Composable
fun PuzzlesView(router: AppRouter) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Puzzles", style = MaterialTheme.typography.headlineSmall)
        val items = listOf(
            "Daily Puzzle" to "https://www.chess.com/puzzles",
            "Puzzle Rush" to "https://www.chess.com/puzzles/rush",
            "Puzzle Battle" to "https://www.chess.com/puzzles/battle",
            "Learn Puzzles" to "https://www.chess.com/puzzles/learning",
            "Lichess Training (free tactics)" to "https://lichess.org/training",
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { (label, url) ->
                Card(modifier = Modifier.fillMaxWidth().clickable { router.openInPlay(url) }) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label); Text("Open →", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun StatsView(router: AppRouter) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Stats", style = MaterialTheme.typography.headlineSmall)
        Text("View your chess.com stats and insights.", color = Color.Gray)
        val items = listOf(
            "My Stats" to "https://www.chess.com/stats",
            "Leaderboard" to "https://www.chess.com/leaderboard",
            "My Archive Insights" to "https://www.chess.com/games/archive",
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { (label, url) ->
                Card(modifier = Modifier.fillMaxWidth().clickable { router.openInPlay(url) }) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label); Text("Open →", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        // Local stat placeholder — real PGN analysis would go here (see PGN.swift)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Local insights (offline)", style = MaterialTheme.typography.titleSmall)
                Text("Import a PGN in the future to get color performance, worst phase, and time-class win-rate — same logic as iOS PGN insights.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}
