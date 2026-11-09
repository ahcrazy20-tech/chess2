package com.chesshelper.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * Port of Sources/AppRouter.swift
 * Shared navigation so any tab can push a URL to the Play tab's WebView.
 */
class AppRouter : ViewModel() {
    var selectedTab by mutableIntStateOf(0)
        private set

    var chessUrl by mutableStateOf("https://www.chess.com/")
        private set

    var navigateTrigger by mutableIntStateOf(0)
        private set

    fun openInPlay(urlString: String) {
        // Basic validation
        if (urlString.startsWith("http")) {
            chessUrl = urlString
            navigateTrigger += 1
            selectedTab = 0
        }
    }

    fun selectTab(index: Int) {
        selectedTab = index.coerceIn(0, 5)
    }
}
