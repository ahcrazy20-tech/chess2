package com.chesshelper.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Port of ChessComAPI.swift — the public, no-auth chess.com data API.
 * https://www.chess.com/news/view/published-data-api
 */
object ChessComApi {

    data class GameSummary(
        val white: String,
        val black: String,
        val result: String,        // "1-0" / "0-1" / "1/2-1/2"
        val timeClass: String,     // "blitz" / "rapid" / …
        val endTimeMillis: Long,
        val pgn: String
    ) {
        fun title(): String = "$white vs $black  •  $result  •  $timeClass"
    }

    private const val USER_AGENT = "ChessHelperApp/2.1 (open-source study tool)"

    private fun fetch(urlStr: String, timeoutMs: Int): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code !in 200..299) throw RuntimeException("chess.com returned HTTP $code")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** All monthly archive URLs for [username] (chronological order). */
    fun archives(username: String): List<String> {
        val u = username.trim().lowercase()
        if (u.isEmpty()) return emptyList()
        val body = fetch("https://api.chess.com/pub/player/$u/games/archives", 15000)
        val json = JSONObject(body)
        val arr = json.optJSONArray("archives") ?: JSONArray()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optString(i)
            if (s.isNotEmpty()) out.add(s)
        }
        return out
    }

    /** Games from one monthly archive. */
    fun games(archiveUrl: String): List<GameSummary> {
        val body = fetch(archiveUrl, 20000)
        val json = JSONObject(body)
        val arr = json.optJSONArray("games") ?: JSONArray()
        val out = ArrayList<GameSummary>(arr.length())
        for (i in 0 until arr.length()) {
            val g = arr.optJSONObject(i) ?: continue
            val white = g.optJSONObject("white")
            val black = g.optJSONObject("black")
            val wResult = white?.optString("result") ?: ""
            val bResult = black?.optString("result") ?: ""
            val result = when {
                wResult == "win" -> "1-0"
                bResult == "win" -> "0-1"
                else -> "1/2-1/2"
            }
            out.add(
                GameSummary(
                    white = white?.optString("username") ?: "?",
                    black = black?.optString("username") ?: "?",
                    result = result,
                    timeClass = g.optString("time_class"),
                    endTimeMillis = g.optLong("end_time") * 1000L,
                    pgn = g.optString("pgn")
                )
            )
        }
        return out
    }

    /** The 20 most recent games of [username], newest first. */
    fun recentGames(username: String, limit: Int = 20): List<GameSummary> {
        val all = archives(username)
        val last = all.lastOrNull() ?: return emptyList()
        return games(last).sortedByDescending { it.endTimeMillis }.take(limit)
    }
}
