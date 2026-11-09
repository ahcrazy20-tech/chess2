package com.chesshelper.app.data

import kotlinx.serialization.Serializable

/**
 * Mirrors the JS payload from reader.js:
 *   { grid, sideToMove, flipped, flippedConfident, lastMoveUCI, plyCount, detectionConfidence }
 *
 * grid = 64-char string row-major from a8 to h1, '.' empty, 'P' white pawn etc.
 * flipped: true if we are playing Black (board orientation is flipped)
 * detectionConfidence: high | medium | low
 */
@Serializable
data class BoardPayload(
    val grid: String,
    val sideToMove: String,       // "w" or "b"
    val flipped: Boolean,
    val flippedConfident: Boolean,
    val lastMoveUCI: String = "",
    val plyCount: Int = -1,
    val detectionConfidence: String = "low",
    val gridString: String? = null // alt name some JS versions use
) {
    // JS sometimes sends "gridString" instead of "grid"
    val effectiveGrid: String get() = grid.ifEmpty { gridString ?: "" }
}

enum class DetectionConfidence { high, medium, low, unknown;
    companion object {
        fun from(s: String) = try { valueOf(s) } catch (_: Exception) { unknown }
    }
}

enum class MoveQuality(val label: String) {
    brilliant("!! Brilliant"),
    best("!  Best"),
    good("   Good"),
    inaccuracy("?! Inaccuracy"),
    mistake("?  Mistake"),
    blunder("?? Blunder"),
    none("");

    companion object {
        fun fromLoss(cpLoss: Int, isBrilliant: Boolean = false): MoveQuality {
            // Thresholds mirrored from iOS EngineViewModel.computeMoveQuality
            return when {
                isBrilliant -> brilliant
                cpLoss < 15 -> best
                cpLoss < 40 -> good
                cpLoss < 90 -> inaccuracy
                cpLoss < 200 -> mistake
                cpLoss >= 200 -> blunder
                else -> none
            }
        }
    }
}
