package com.chesshelper.app

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.view.View

/**
 * Port of StealthManager.swift: shake, app backgrounding and screen-capture detection
 * hide the helper overlay.
 *
 * Notes for Android:
 *  - Shake (accelerometer) works on all API levels.
 *  - Backgrounding is detected via Application.ActivityLifecycleCallbacks.onStopped.
 *  - There is no public screenshot broadcast below Android 14, so screenshot auto-hide
 *    is best-effort: call setScreenCaptured(true) from a MediaProjection /
 *    ScreenCaptureDetector callback (Android 14+) where available.
 */
class StealthManager(
    context: Context,
    private val overlayView: View,
    listener: StealthListener
) : SensorEventListener {

    interface StealthListener {
        fun onOverlayVisibilityChanged(visible: Boolean, reason: String?)
        fun onScreenCaptureChanged(captured: Boolean)
    }

    var overlayVisible: Boolean = true
        private set
    var minimalMode: Boolean = false
    var isBeingRecorded: Boolean = false
        private set
    var lastHideReason: String = ""

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val shakeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val handler = Handler(Looper.getMainLooper())

    private var lastShakeTime = 0L
    private val shakeThreshold = 16.0          // m/s^2, tunable
    private var lastUpdate = 0L
    private var lastX = 0f; private var lastY = 0f; private var lastZ = 0f

    init {
        applyVisibility()
        sensorManager.registerListener(this, shakeSensor, SensorManager.SENSOR_DELAY_UI)
        if (context.applicationContext is Application) {
            (context.applicationContext as Application).registerActivityLifecycleCallbacks(lifecycle)
        }
    }

    private val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
        override fun onActivityStarted(a: android.app.Activity) {}
        override fun onActivityResumed(a: android.app.Activity) {}
        override fun onActivityPaused(a: android.app.Activity) {}
        override fun onActivityStopped(a: android.app.Activity) { /* hide when app backgrounds */ }
        override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
        override fun onActivityDestroyed(a: android.app.Activity) {}
    }

    fun toggleOverlay() {
        overlayVisible = !overlayVisible
        lastHideReason = if (overlayVisible) "" else "manual"
        applyVisibility()
    }

    fun show() {
        overlayVisible = true
        lastHideReason = ""
        applyVisibility()
    }

    fun hide(reason: String) {
        overlayVisible = false
        lastHideReason = reason
        applyVisibility()
    }

    fun toggleMinimalMode() {
        minimalMode = !minimalMode
        applyVisibility()
    }

    private fun applyVisibility() {
        overlayView.visibility = if (overlayVisible) View.VISIBLE else View.GONE
        handler.post { listener.onOverlayVisibilityChanged(overlayVisible, lastHideReason) }
    }

    // ---- shake detection ----
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val now = System.currentTimeMillis()
        val elapsedMs = Math.max(1L, now - lastUpdate)
        lastUpdate = now
        if (elapsedMs < 100) return
        val dx = Math.abs(event.values[0] - lastX)
        val dy = Math.abs(event.values[1] - lastY)
        val dz = Math.abs(event.values[2] - lastZ)
        lastX = event.values[0]; lastY = event.values[1]; lastZ = event.values[2]
        // coarse acceleration spike approximation (m/s^2 scaled)
        val speed = (dx + dy + dz) * 1000.0 / elapsedMs
        if (speed > shakeThreshold && now - lastShakeTime > 1000) {
            lastShakeTime = now
            handler.post { hide("shake detected") }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** Call this from a MediaProjection / ScreenCaptureDetector callback (Android 14+). */
    fun setScreenCaptured(captured: Boolean) {
        isBeingRecorded = captured
        handler.post { listener.onScreenCaptureChanged(captured) }
        if (captured) hide("screen capture detected")
    }

    fun destroy() {
        sensorManager.unregisterListener(this)
        if (overlayView.context.applicationContext is Application) {
            (overlayView.context.applicationContext as Application).unregisterActivityLifecycleCallbacks(lifecycle)
        }
    }
}
