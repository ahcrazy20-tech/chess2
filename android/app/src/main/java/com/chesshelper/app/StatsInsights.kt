package com.chesshelper.app

/**
 * Port of StatsView.swift's "Personal Weakness Detector": categorizes recent
 * chess.com games and generates human-readable coaching insights.
 */
object StatsInsights {

    data class GameStats(
        val asWhite: Boolean,
        val win: Boolean,
        val draw: Boolean,
        val plyCount: Int,
        val timeClass: String
    ) {
        val phaseAtEnd: String
            get() = when {
                plyCount < 20 -> "opening"
                plyCount < 60 -> "middlegame"
                else -> "endgame"
            }
    }

    /** Build [GameStats] rows from raw game summaries (needs PGN replay for length). */
    fun collect(games: List<ChessComApi.GameSummary>, me: String): List<GameStats> {
        val myName = me.trim().lowercase()
        return games.mapNotNull { g ->
            val asWhite = g.white.lowercase() == myName
            val asBlack = g.black.lowercase() == myName
            if (!asWhite && !asBlack) return@mapNotNull null
            val win = (asWhite && g.result == "1-0") || (!asWhite && g.result == "0-1")
            val draw = g.result == "1/2-1/2"
            GameStats(
                asWhite = asWhite,
                win = win,
                draw = draw,
                plyCount = PgnReader.plyCount(g.pgn),
                timeClass = g.timeClass
            )
        }
    }

    fun generateInsights(games: List<GameStats>): List<String> {
        val out = ArrayList<String>()
        if (games.isEmpty()) return listOf("No games to analyze")

        val total = games.size
        val wins = games.count { it.win }
        val draws = games.count { it.draw }
        val losses = total - wins - draws
        val winRate = wins * 100.0 / total
        out.add("Overall: ${wins}W / ${losses}L / ${draws}D — win rate ${winRate.toInt()}%")

        // Color performance.
        val whiteGames = games.filter { it.asWhite }
        val blackGames = games.filter { !it.asWhite }
        if (whiteGames.isNotEmpty() && blackGames.isNotEmpty()) {
            val wWinRate = whiteGames.count { it.win } * 100.0 / whiteGames.size
            val bWinRate = blackGames.count { it.win } * 100.0 / blackGames.size
            val diff = wWinRate - bWinRate
            if (kotlin.math.abs(diff) > 15) {
                if (diff > 0) {
                    out.add(
                        "♔ You're much stronger with White (${wWinRate.toInt()}%) than " +
                            "Black (${bWinRate.toInt()}%). Study a solid Black repertoire."
                    )
                } else {
                    out.add(
                        "♚ You're stronger with Black (${bWinRate.toInt()}%) than " +
                            "White (${wWinRate.toInt()}%). Sharpen your White opening."
                    )
                }
            }
        }

        // Phase where losses end.
        val allLosses = games.filter { !it.win && !it.draw }
        if (allLosses.isNotEmpty()) {
            val byPhase = allLosses.groupBy { it.phaseAtEnd }
            val worst = byPhase.entries.maxByOrNull { it.value.size }
            if (worst != null) {
                val pct = worst.value.size * 100.0 / allLosses.size
                if (pct >= 50) {
                    out.add(
                        "⚠️ ${pct.toInt()}% of your losses end in the ${worst.key} — " +
                            "that's your weakest phase."
                    )
                    when (worst.key) {
                        "opening" -> out.add(
                            "→ Fix: Use the Openings tab to lock down 1 white + 1 black repertoire."
                        )
                        "middlegame" -> out.add(
                            "→ Fix: Do tactics daily — Puzzles tab → Lichess training."
                        )
                        "endgame" -> out.add(
                            "→ Fix: Study basic K+P and rook endings. " +
                                "Daniel Naroditsky's endgame videos are a great start."
                        )
                    }
                }
            }
        }

        // Time-class performance.
        for ((tc, gs) in games.groupBy { it.timeClass }) {
            if (gs.size < 5 || tc.isEmpty()) continue
            val wr = gs.count { it.win } * 100.0 / gs.size
            if (wr < 40) {
                out.add(
                    "🕒 In $tc you win only ${wr.toInt()}%. " +
                        "Consider slower time controls for practice."
                )
            } else if (wr > 65) {
                out.add("🏆 You dominate $tc at ${wr.toInt()}% — your best time class.")
            }
        }

        // Quick losses (getting mated early).
        if (allLosses.size >= 5) {
            val quick = allLosses.count { it.plyCount < 24 }
            if (quick * 1.0 / allLosses.size > 0.3) {
                out.add(
                    "💥 $quick/${allLosses.size} losses ended in under 12 moves — " +
                        "you're getting mated early. Slow down on moves 1–10."
                )
            }
        }

        return out
    }
}
