package com.chesshelper.app

data class EngineAnalysis(
    val topMoves: List<TopMove>,
    val bestMove: TopMove?,
    val depth: Int
)
