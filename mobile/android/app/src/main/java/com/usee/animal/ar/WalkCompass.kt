package com.usee.animal.ar

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.GeomagneticField
import com.google.ar.core.Pose
import kotlin.math.atan2
import kotlin.math.hypot

/** Align AR horizontal displacement with magnetic east/north using the same physical phone axis. */
internal class WalkCompass(context: Context) : SensorEventListener {
  private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
  private val matrix = FloatArray(9)
  private var timestamp = 0L
  private var declination = 0.0

  fun setLocation(latitude: Float, longitude: Float) {
    val field = GeomagneticField(latitude, longitude, 0f, System.currentTimeMillis())
    declination = Math.toRadians(field.declination.toDouble())
  }

  fun start() {
    manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
      manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
    }
  }
  fun stop() { manager.unregisterListener(this); timestamp = 0L }
  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
  override fun onSensorChanged(event: SensorEvent) {
    if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) { timestamp = 0L; return }
    SensorManager.getRotationMatrixFromVector(matrix, event.values)
    timestamp = event.timestamp
  }

  fun alignment(pose: Pose): Double? {
    if (timestamp == 0L || android.os.SystemClock.elapsedRealtimeNanos() - timestamp > 500_000_000L) return null
    val right = pose.xAxis
    if (hypot(right[0], right[2]) < 0.6f || hypot(matrix[0], matrix[3]) < 0.6f) return null
    return atan2(matrix[0], matrix[3]).toDouble() + declination - atan2(right[0], -right[2])
  }
}
