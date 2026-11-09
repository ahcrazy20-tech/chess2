package com.chesshelper.app

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Foreground service placeholder so the local engine / stealth behavior can stay
 * alive while the app is visible. Start/stop it from MainActivity as needed.
 */
class StealthService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
}
