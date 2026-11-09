package com.chesshelper.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesshelper.app.data.EngineViewModel

/**
 * PlayView — chess.com WebView + overlay + nav toolbar, port of Sources/PlayView.swift
 */
@Composable
fun PlayView(
    engine: EngineViewModel,
    router: AppRouter,
    stealth: StealthManager
) {
    var showDisclaimer by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            NavBar(router)
            Box(modifier = Modifier.weight(1f)) {
                ChessWebView(engine = engine, router = router, stealth = stealth)
                // Overlay anchored bottom
                HelperOverlay(
                    engine = engine,
                    stealth = stealth,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        if (showDisclaimer) {
            DisclaimerCard(
                onDismiss = { showDisclaimer = false },
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }
    }
}

@Composable
private fun NavBar(router: AppRouter) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NavButton(icon = Icons.Filled.Home, label = "Home") { router.openInPlay("https://www.chess.com/") }
        NavButton(icon = Icons.Filled.Public, label = "Online") { router.openInPlay("https://www.chess.com/play/online/new") }
        NavButton(icon = Icons.Filled.Computer, label = "Bots") { router.openInPlay("https://www.chess.com/play/computer") }
        NavButton(icon = Icons.Filled.Extension, label = "Puzzles") { router.openInPlay("https://www.chess.com/puzzles") }
        NavButton(icon = Icons.Filled.ShowChart, label = "Analysis") { router.openInPlay("https://www.chess.com/analysis") }
    }
}

@Composable
private fun RowScope.NavButton(icon: ImageVector, label: String, action: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = action)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.9f))
    }
}

@Composable
private fun DisclaimerCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.widthIn(max = 420.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E).copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 20.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("⚠️", fontSize = 40.sp)
            Text("Fair-Play Notice", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
            Text(
                "Using engine assistance during live online games violates Chess.com's Terms of Service and will get your account banned.\n\nUse this helper for:\n• Post-game review (Review tab)\n• Openings study (Openings tab)\n• Puzzles (Puzzles tab)\n• Playing the Computer bots",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("I understand", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}
