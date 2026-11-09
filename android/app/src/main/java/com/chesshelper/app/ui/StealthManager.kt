package com.chesshelper.app.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sqrt

/**
 * StealthManager — Android port of Sources/StealthManager.swift
 *
 * iOS hid overlay on:
 *  - Shake gesture
 *  - 3-finger tap
 *  - Screen recording / mirroring
 *  - Screenshot
 *  - App backgrounding
 *
 * Android equivalents:
 *  - Shake: accelerometer listener (high-pass filter)
 *  - 3-finger tap: handled in Compose overlay via pointerInput (see HelperOverlay)
 *  - Screen recording: MediaProjection callback + FLAG_SECURE if needed (we detect via callback)
 *  - Screenshot: ContentObserver on MediaStore.Images (optional) + FileObserver
 *  - App background: Lifecycle observer (we expose onPause hook)
 *
 * On S26 Ultra, the 3-finger tap maps nicely to S Pen button + touch if available.
 */
class StealthManager(context: Context) : ViewModel(), SensorEventListener {

    var overlayVisible by mutableStateOf(true)
        private set
    var minimalMode by mutableStateOf(false)
        private set
    var isBeingRecorded by mutableStateOf(false)
        private set
    var lastHideReason by mutableStateOf("")
        private set

    private val appContext = context.applicationContext
    private var sensorManager: SensorManager? = null
    private var accelSensor: Sensor? = null

    // Shake detection params — tuned for S26 Ultra's accelerometer (sensitive)
    private var lastShakeMs = 0L
    private var lastAccel = 9.81f
    private var shakeCount = 0

    init {
        initShakeDetection()
    }

    private fun initShakeDetection() {
        try {
            sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            accelSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        } catch (_: Exception) {}
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
        val accel = sqrt((x*x + y*y + z*z).toDouble()).toFloat()
        val delta = kotlin.math.abs(accel - lastAccel)
        lastAccel = accel
        // Threshold ~12 m/s² delta is a firm shake on S26 Ultra
        if (delta > 12f) {
            val now = System.currentTimeMillis()
            if (now - lastShakeMs > 500) {
                shakeCount++
                lastShakeMs = now
                if (shakeCount >= 2) {
                    shakeCount = 0
                    toggleOverlay()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun toggleOverlay() {
        overlayVisible = !overlayVisible
        lastHideReason = if (!overlayVisible) "shake / 3-finger" else ""
    }

    fun hide(reason: String) {
        overlayVisible = false
        lastHideReason = reason
    }

    fun show() {
        overlayVisible = true
        lastHideReason = ""
    }

    fun toggleMinimalMode() {
        minimalMode = !minimalMode
    }

    fun onAppBackgrounded() {
        // Mirrors iOS autoHideBackground preference — hide when app leaves foreground
        hide("app backgrounded")
    }

    fun onScreenCaptured() {
        isBeingRecorded = true
        hide("screen recording detected")
    }

    override fun onCleared() {
        super.onCleared()
        try { sensorManager?.unregisterListener(this) } catch (_: Exception) {}
    }
}
