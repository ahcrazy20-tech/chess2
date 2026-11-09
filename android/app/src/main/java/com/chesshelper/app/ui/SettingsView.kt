package com.chesshelper.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.collectAsState

private val Context.dataStore by preferencesDataStore(name = "chess_helper_prefs")

/**
 * SettingsView — Android port of Sources/SettingsView.swift
 * S26 Ultra: we expose higher defaults for depth/threads because the SoC can handle it.
 */
@Composable
fun SettingsView(stealth: StealthManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // DataStore-backed prefs — mirror iOS @AppStorage keys
    val engineDepthKey = intPreferencesKey("engineDepth")
    val movetimeKey = intPreferencesKey("engineMovetime")
    val multiPVKey = intPreferencesKey("multiPV")
    val autoHideBgKey = booleanPreferencesKey("autoHideBackground")
    val autoHideRecKey = booleanPreferencesKey("autoHideRecording")
    val autoHideShotKey = booleanPreferencesKey("autoHideScreenshot")
    val minimalModeKey = booleanPreferencesKey("useMinimalMode")

    val prefsFlow = remember { context.dataStore.data }
    val prefs by prefsFlow.collectAsState(initial = null)

    var depth by remember { mutableStateOf(20) }
    var movetime by remember { mutableStateOf(2000) }
    var multiPV by remember { mutableStateOf(3) }

    LaunchedEffect(prefs) {
        prefs?.let {
            depth = it[engineDepthKey] ?: 20
            movetime = it[movetimeKey] ?: 2000
            multiPV = it[multiPVKey] ?: 3
        }
    }

    fun save(block: suspend () -> Unit) { scope.launch { block() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        // Engine
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Engine — Stockfish 16 (local)", style = MaterialTheme.typography.titleMedium)
                SettingStepper(label = "Search depth", value = "$depth", onMinus = {
                    depth = (depth - 1).coerceIn(10, 25)
                    save { context.dataStore.edit { it[engineDepthKey] = depth } }
                }, onPlus = {
                    depth = (depth + 1).coerceIn(10, 25)
                    save { context.dataStore.edit { it[engineDepthKey] = depth } }
                })
                SettingStepper(label = "Max think time", value = "$movetime ms", onMinus = {
                    movetime = (movetime - 250).coerceIn(500, 5000)
                    save { context.dataStore.edit { it[movetimeKey] = movetime } }
                }, onPlus = {
                    movetime = (movetime + 250).coerceIn(500, 5000)
                    save { context.dataStore.edit { it[movetimeKey] = movetime } }
                })
                SettingStepper(label = "Candidate moves", value = "$multiPV", onMinus = {
                    multiPV = (multiPV - 1).coerceIn(1, 5)
                    save { context.dataStore.edit { it[multiPVKey] = multiPV } }
                }, onPlus = {
                    multiPV = (multiPV + 1).coerceIn(1, 5)
                    save { context.dataStore.edit { it[multiPVKey] = multiPV } }
                })
                Text("Higher depth = stronger but slower. Depth 20 ≈ ~3400 Elo. S26 Ultra can comfortably run depth 22–24.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text("Threads: 6 • Hash: 128 MB — tuned for Snapdragon 8 Elite / Exynos 2600 + 12 GB RAM.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }

        // Stealth — Panic Gestures
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Stealth — Panic Gestures", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Vibration, contentDescription = null)
                    Text("Shake device to hide overlay")
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.TouchApp, contentDescription = null)
                    Text("3-finger tap to hide overlay")
                }
                Text("Both gestures are always active. S Pen button + tap also hides on S26 Ultra.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }

        // Stealth — Auto-hide
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Stealth — Auto-hide", style = MaterialTheme.typography.titleMedium)
                var autoHideBg by remember { mutableStateOf(true) }
                var autoHideRec by remember { mutableStateOf(true) }
                var autoHideShot by remember { mutableStateOf(true) }
                var useMinimal by remember { mutableStateOf(false) }
                LaunchedEffect(prefs) {
                    prefs?.let {
                        autoHideBg = it[autoHideBgKey] ?: true
                        autoHideRec = it[autoHideRecKey] ?: true
                        autoHideShot = it[autoHideShotKey] ?: true
                        useMinimal = it[minimalModeKey] ?: false
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("When app goes to background"); Switch(checked = autoHideBg, onCheckedChange = { autoHideBg = it; save{ context.dataStore.edit{ s -> s[autoHideBgKey]=it } } })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("When screen is being recorded"); Switch(checked = autoHideRec, onCheckedChange = { autoHideRec = it; save{ context.dataStore.edit{ s -> s[autoHideRecKey]=it } } })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("When screenshot is taken"); Switch(checked = autoHideShot, onCheckedChange = { autoHideShot = it; save{ context.dataStore.edit{ s -> s[autoHideShotKey]=it } } })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Use minimal mode (tiny dot)"); Switch(checked = useMinimal, onCheckedChange = {
                        useMinimal = it
                        stealth.let { s -> if (it) { if (!s.minimalMode) s.toggleMinimalMode() } else { if (s.minimalMode) s.toggleMinimalMode() } }
                        save{ context.dataStore.edit{ s -> s[minimalModeKey]=it } }
                    })
                }
            }
        }

        // Status
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Status", style = MaterialTheme.typography.titleMedium)
                Text("Overlay visible: ${if (stealth.overlayVisible) "Yes" else "No"}")
                Text("Screen captured: ${if (stealth.isBeingRecorded) "Yes" else "No"}")
                if (stealth.lastHideReason.isNotEmpty()) Text("Last hide reason: ${stealth.lastHideReason}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { stealth.show() }) { Text("Force show overlay") }
                    Button(onClick = { stealth.hide("manual") }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Force hide") }
                }
            }
        }

        // Privacy
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2ECC71).copy(alpha = 0.12f))) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF2ECC71))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Zero external requests", style = MaterialTheme.typography.titleSmall)
                    Text("Analysis runs 100% on-device. Nothing sent to any server — same as iOS build.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }

        // About + S26 Ultra notes
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("About", style = MaterialTheme.typography.titleMedium)
                Text("Version: 2.0.0 (20)")
                Text("Engine: Stockfish 16 (bundled)")
                Text("Build: Android — S26 Ultra optimized")
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text("S26 Ultra optimizations", style = MaterialTheme.typography.titleSmall)
                Text("• 6.9\" 3120×1440 LTPO 120 Hz, edge-to-edge, display cutout aware\n• Snapdragon 8 Elite: 6 threads / 128 MB hash, stays cool under sustained analyze\n• One UI 7 adaptive layout + DeX + S Pen hover supported\n• Hardware-accelerated WebView, 1440p board stays crisp", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingStepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onMinus, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) { Text("−") }
            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(min = 72.dp))
            OutlinedButton(onClick = onPlus, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) { Text("+") }
        }
    }
}
