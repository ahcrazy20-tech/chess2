package com.chesshelper.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesshelper.app.data.EngineViewModel
import com.chesshelper.app.data.MoveQuality
import com.chesshelper.app.data.TopMove

/**
 * HelperOverlay — Android Compose port of Sources/HelperOverlay.swift
 *
 * Shows above the WebView (ZStack/Bottom alignment). Draggable? For now anchored bottom with
 * expand/collapse. On S26 Ultra's tall screen we cap maxHeight and use bottom sheet feel.
 *
 * Stealth:
 *  - Minimal dot (14dp) when stealth.minimalMode
 *  - Hide button + tap target to reveal
 *  - 3-finger tap handled via pointerInput counting touches
 */

@Composable
fun HelperOverlay(
    engine: EngineViewModel,
    stealth: StealthManager,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        if (!stealth.overlayVisible) {
            // Invisible tap target to reveal — matches iOS small circle
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                    .clickable { stealth.show() },
                contentAlignment = Alignment.Center
            ) { }
        } else if (stealth.minimalMode) {
            // Minimal dot — green if my turn else gray
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (engine.isMyTurn) Color(0xFF2ECC71) else Color.Gray)
                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                    .clickable { stealth.toggleMinimalMode() }
            )
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E).copy(alpha = 0.94f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HeaderRow(
                        engine = engine,
                        stealth = stealth,
                        expanded = expanded,
                        onToggleExpand = { expanded = !expanded }
                    )
                    AnimatedVisibility(visible = expanded) {
                        Column {
                            ColorOverrideRow(engine)
                            TopMovesSection(engine)
                            LastMoveSection(engine)
                            OpeningSection(engine)
                            ErrorSection(engine)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(
    engine: EngineViewModel,
    stealth: StealthManager,
    expanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (engine.enabled) Color(0xFF2ECC71) else Color.Gray)
        )
        Text("Chess Helper", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (engine.isMyTurn) "●" else "⏳", fontSize = 10.sp, color = if (engine.isMyTurn) Color(0xFF2ECC71) else Color.Gray)
            Text(if (engine.isMyTurn) "Your turn" else "Waiting", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = if (engine.isMyTurn) Color(0xFF2ECC71) else Color.Gray)
        }

        if (engine.isThinking) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFF2ECC71))
        }

        Spacer(Modifier.weight(1f))

        if (engine.confidence.name != "high") {
            Text("⚠", fontSize = 12.sp, color = Color.Yellow)
        }

        IconButton(onClick = { stealth.toggleMinimalMode() }, modifier = Modifier.size(28.dp)) {
            Icon(if (stealth.minimalMode) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Minimal", tint = Color.White, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = { stealth.hide("manual") }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Hide", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        }

        Switch(
            checked = engine.enabled,
            onCheckedChange = { engine.enabled = it },
            modifier = Modifier.graphicsLayer(scaleX = 0.8f, scaleY = 0.8f)
        )

        IconButton(onClick = onToggleExpand, modifier = Modifier.size(28.dp)) {
            Text(if (expanded) "⌄" else "⌃", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ColorOverrideRow(engine: EngineViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("I'm playing:", fontSize = 11.sp, color = Color.Gray)
        ColorButton(active = engine.effectivePlayerColor == "w", label = "White", symbol = "♔") { engine.setManualColor("w") }
        ColorButton(active = engine.effectivePlayerColor == "b", label = "Black", symbol = "♚") { engine.setManualColor("b") }
        if (engine.manualPlayerColor != null) {
            AssistChip(
                onClick = { engine.setManualColor(null) },
                label = { Text("Auto", fontSize = 11.sp) },
                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF2196F3).copy(alpha = 0.25f), labelColor = Color(0xFF2196F3))
            )
        }
        Spacer(Modifier.weight(1f))
        if (engine.plyCount >= 0) {
            Text("ply ${engine.plyCount}", fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun ColorButton(active: Boolean, label: String, symbol: String, onClick: () -> Unit) {
    val bg = if (active) Color(0xFF2ECC71).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.10f)
    val fg = if (active) Color(0xFF2ECC71) else Color.White.copy(alpha = 0.85f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(symbol, fontSize = 12.sp)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
        }
    }
}

@Composable
private fun TopMovesSection(engine: EngineViewModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = Color.White.copy(alpha = 0.15f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (engine.isMyTurn) "Top moves for you" else "Opponent's best replies",
                fontSize = 11.sp, color = Color.Gray
            )
            Text("depth ${engine.depth}", fontSize = 10.sp, color = Color.Gray)
        }

        if (engine.topMoves.isEmpty()) {
            Text("—", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        } else {
            engine.topMoves.forEachIndexed { idx, move ->
                TopMoveRow(rank = idx + 1, move = move, isBest = idx == 0)
            }
        }

        // Win chance bar — my win chance
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .padding(horizontal = 14.dp)
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.6f))
        ) {
            val pct = (engine.myWinChance / 100.0).toFloat().coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(pct)
                    .background(Color.White)
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun TopMoveRow(rank: Int, move: TopMove, isBest: Boolean) {
    val color = when (rank) {
        1 -> Color(0xFF2ECC71)
        2 -> Color(0xFFFFEB3B)
        3 -> Color(0xFFFF9800)
        else -> Color.White.copy(alpha = 0.7f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isBest) color.copy(alpha = 0.10f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("#$rank", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.width(22.dp))
        Text(move.san.ifEmpty { move.uci }, fontSize = if (isBest) 18.sp else 14.sp, fontWeight = FontWeight.Bold, color = color)
        Text(move.uci, fontSize = 10.sp, color = Color.Gray)
        Spacer(Modifier.weight(1f))
        Text(move.evalString, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun LastMoveSection(engine: EngineViewModel) {
    if (engine.lastMoveQuality != MoveQuality.none && engine.lastMoveUCI.isNotEmpty()) {
        val q = engine.lastMoveQuality
        val qColor = when (q) {
            MoveQuality.brilliant -> Color.Cyan
            MoveQuality.best -> Color(0xFF2ECC71)
            MoveQuality.good -> Color(0xFF2ECC71).copy(alpha = 0.7f)
            MoveQuality.inaccuracy -> Color.Yellow
            MoveQuality.mistake -> Color(0xFFFF9800)
            MoveQuality.blunder -> Color.Red
            MoveQuality.none -> Color.Transparent
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(if (engine.lastMoveWasMine) "You:" else "Opp:", fontSize = 11.sp, color = Color.Gray)
            Text(engine.lastMoveUCI, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(qColor.copy(alpha = 0.25f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(q.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = qColor)
            }
            if (engine.lastMoveCentipawnLoss >= 30) {
                Text("-${engine.lastMoveCentipawnLoss}cp", fontSize = 10.sp, color = Color(0xFFFF9800))
            }
        }
    }
}

@Composable
private fun OpeningSection(engine: EngineViewModel) {
    if (engine.openingName.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("♞", fontSize = 11.sp, color = Color(0xFFFF9800))
            if (engine.openingECO.isNotEmpty()) {
                Text(engine.openingECO, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
            }
            Text(engine.openingName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f), maxLines = 2)
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ErrorSection(engine: EngineViewModel) {
    if (engine.errorMessage.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("⚠", fontSize = 11.sp, color = Color.Red)
            Text(engine.errorMessage, fontSize = 11.sp, color = Color.Red.copy(alpha = 0.9f))
        }
    }
}


