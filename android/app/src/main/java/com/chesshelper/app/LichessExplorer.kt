package com.chesshelper.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Port of ChessAPI.swift's opening lookup — the free Lichess Opening Explorer
 * (https://explorer.lichess.ovh). Used for the "opening name + popular moves"
 * rows in the helper overlay. Falls back silently to the offline OpeningBook.
 */
object LichessExplorer {

    data class PopularMove(
        val san: String,
        val white: Int,
        val draws: Int,
        val black: Int
    ) {
        val totalGames: Int get() = white + draws + black
        val winRate: Double get() = if (totalGames == 0) 0.0 else white * 100.0 / totalGames
    }

    data class OpeningInfo(
        val name: String,
        val eco: String,
        val topMoves: List<PopularMove>
    )

    /** Query the explorer for [fen]. Returns null when offline or unknown. */
    fun lookup(fen: String): OpeningInfo? {
        return try {
            val qs = StringBuilder()
            qs.append("fen=").append(URLEncoder.encode(fen, "UTF-8"))
            qs.append("&moves=5&topGames=0&recentGames=0")
            qs.append("&speeds=blitz,rapid,classical")
            qs.append("&ratings=1600,1800,2000,2200,2500")
            val conn = URL("https://explorer.lichess.ovh/lichess?$qs").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("User-Agent", "ChessHelperApp/2.1")
                if (conn.responseCode !in 200..299) return null
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                parse(body)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parse(body: String): OpeningInfo? {
        val json = JSONObject(body)
        val opening = json.optJSONObject("opening")
        val name = opening?.optString("name") ?: ""
        val eco = opening?.optString("eco") ?: ""
        val movesArr = json.optJSONArray("moves") ?: JSONArray()
        val top = ArrayList<PopularMove>()
        for (i in 0 until minOf(movesArr.length(), 5)) {
            val m = movesArr.optJSONObject(i) ?: continue
            top.add(
                PopularMove(
                    san = m.optString("san"),
                    white = m.optInt("white"),
                    draws = m.optInt("draws"),
                    black = m.optInt("black")
                )
            )
        }
        if (name.isEmpty() && top.isEmpty()) return null
        return OpeningInfo(name, eco, top)
    }
}
