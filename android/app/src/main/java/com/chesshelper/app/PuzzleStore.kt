package com.chesshelper.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Port of PuzzlesView.swift's "Puzzle Rating Tracker": puzzle attempts are
 * logged locally (SharedPreferences holds a JSON array) and summarized by
 * theme. Zero network involvement.
 */
object PuzzleStore {

    data class PuzzleAttempt(
        val dateISO: String,
        val theme: String,
        val rating: Int,
        val solved: Boolean,
        val seconds: Int
    )

    val THEMES = listOf(
        "fork", "pin", "skewer", "discovered attack", "double attack",
        "mate in 1", "mate in 2", "mate in 3", "back rank", "zugzwang",
        "sacrifice", "deflection", "attraction", "endgame", "other"
    )

    // ---- serialization ----

    fun encode(attempts: List<PuzzleAttempt>): String {
        val arr = JSONArray()
        for (a in attempts) {
            val o = JSONObject()
            o.put("date", a.dateISO)
            o.put("theme", a.theme)
            o.put("rating", a.rating)
            o.put("solved", a.solved)
            o.put("seconds", a.seconds)
            arr.put(o)
        }
        return arr.toString()
    }

    fun decode(json: String): List<PuzzleAttempt> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val out = ArrayList<PuzzleAttempt>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    PuzzleAttempt(
                        dateISO = o.optString("date"),
                        theme = o.optString("theme"),
                        rating = o.optInt("rating"),
                        solved = o.optBoolean("solved"),
                        seconds = o.optInt("seconds")
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---- summary stats ----

    data class ThemeStat(val theme: String, val count: Int, val solved: Int) {
        val pct: Double get() = if (count == 0) 0.0 else solved * 100.0 / count
    }

    fun byTheme(attempts: List<PuzzleAttempt>): List<ThemeStat> {
        return attempts.groupBy { it.theme }
            .map { (theme, list) -> ThemeStat(theme, list.size, list.count { it.solved }) }
            .sortedWith(Cmp.byDesc { it.count })
    }

    fun accuracy(attempts: List<PuzzleAttempt>): Double {
        if (attempts.isEmpty()) return 0.0
        return attempts.count { it.solved } * 100.0 / attempts.size
    }

    fun avgRating(attempts: List<PuzzleAttempt>): Int {
        if (attempts.isEmpty()) return 0
        return attempts.sumOf { it.rating } / attempts.size
    }

    fun nowISO(): String {
        // ISO-8601 without pulling in java.time (minSdk 24 safe).
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date())
    }
}
