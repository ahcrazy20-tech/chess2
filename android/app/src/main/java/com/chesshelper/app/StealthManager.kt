package com.chesshelper.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View

/**
 * Port of StealthManager.swift: shake, 3-finger tap, app backgrounding and
 * screen-capture detection hide the helper overlay.
 *
 * Android notes:
 *  - Shake (accelerometer) works on all API levels.
 *  - Backgrounding is detected via ActivityLifecycleCallbacks.onActivityStopped
 *    and MainActivity.onPause (per the autoHideBackground preference).
 *  - There is no public screenshot broadcast on Android; screenshot auto-hide is
 *    wired through [setScreenCaptured] where a detector is available (e.g. the
 *    MediaProjection callback on Android 14+).
 */
class StealthManager(
    context: Context,
    private val overlayView: View,
    private val listener: StealthListener
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
        private set

    // Preference-driven behavior (synced from Settings in MainActivity.onResume).
    var autoHideBackground: Boolean = true
    var autoHideRecording: Boolean = true
    var autoHideScreenshot: Boolean = true

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val shakeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val handler = Handler(Looper.getMainLooper())

    private var lastShakeTime = 0L
    private val shakeThreshold = 16.0
    private var lastUpdate = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    private val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(a: Activity, b: Bundle?) {}
        override fun onActivityStarted(a: Activity) {}
        override fun onActivityResumed(a: Activity) {}
        override fun onActivityPaused(a: Activity) {}
        override fun onActivityStopped(a: Activity) {
            if (autoHideBackground) hide("app backgrounded")
        }
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        override fun onActivityDestroyed(a: Activity) {}
    }

    init {
        applyVisibility()
        shakeSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        val app = context.applicationContext
        if (app is Application) {
            app.registerActivityLifecycleCallbacks(lifecycle)
        }
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
        val visible = overlayVisible
        handler.post {
            overlayView.visibility = if (visible) View.VISIBLE else View.GONE
            listener.onOverlayVisibilityChanged(visible, lastHideReason)
        }
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
        lastX = event.values[0]
        lastY = event.values[1]
        lastZ = event.values[2]
        val speed = (dx + dy + dz) * 1000.0 / elapsedMs
        if (speed > shakeThreshold && now - lastShakeTime > 1000) {
            lastShakeTime = now
            handler.post { hide("shake detected") }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** Call this from a MediaProjection / ScreenCaptureDetector callback. */
    fun setScreenCaptured(captured: Boolean) {
        isBeingRecorded = captured
        handler.post { listener.onScreenCaptureChanged(captured) }
        if (captured && autoHideRecording) hide("screen capture detected")
    }

    /** Call this where a screenshot detector is available. */
    fun onScreenshotDetected() {
        if (autoHideScreenshot) hide("screenshot taken")
    }

    fun destroy() {
        sensorManager.unregisterListener(this)
        val app = overlayView.context.applicationContext
        if (app is Application) {
            app.unregisterActivityLifecycleCallbacks(lifecycle)
        }
    }
}
