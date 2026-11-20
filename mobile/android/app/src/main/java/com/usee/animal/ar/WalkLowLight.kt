package com.usee.animal.ar

import android.util.Log
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.ResourceExhaustedException
import kotlin.math.hypot

internal const val LOW_LIGHT_LEAD_METERS = 0.4f

internal class WalkLowLight {
  private var darkSamples = 0
  private val obstacleTimes = ArrayDeque<Long>()
  var detected = false
    private set
  var shortRange = false
    private set

  fun observeLight(meanLuma: Int?, trackingNeedsLight: Boolean) {
    if (meanLuma != null) darkSamples = if (meanLuma <= 40) darkSamples + 1 else 0
    // Keep the flash on for this walk so its own light cannot switch it off again.
    detected = detected || trackingNeedsLight || darkSamples >= 3
  }

  fun observeObstacle(timeNanos: Long) {
    if (!detected || shortRange) return
    if (obstacleTimes.lastOrNull()?.let { timeNanos - it < 150_000_000L } == true) return
    while (obstacleTimes.firstOrNull()?.let { timeNanos - it > 2_000_000_000L } == true) {
      obstacleTimes.removeFirst()
    }
    obstacleTimes.addLast(timeNanos)
    shortRange = obstacleTimes.size >= 3
  }

  fun nearbyFloor(x: Float, z: Float, cameraX: Float, cameraZ: Float, userGroundY: Float): Float? =
    userGroundY.takeIf { shortRange && hypot(x - cameraX, z - cameraZ) <= LOW_LIGHT_LEAD_METERS + 0.0001f }
}

internal class WalkTorch(private val light: WalkLowLight) {
  private var lastSample = 0L
  private var nextEnableAttempt = 0L
  private var enabled = false

  fun update(session: Session, frame: Frame, now: Long) {
    if (!light.detected) {
      val needsLight = frame.camera.trackingFailureReason == TrackingFailureReason.INSUFFICIENT_LIGHT
      val luma = if (!needsLight && frame.timestamp > 0L &&
        (lastSample == 0L || frame.timestamp - lastSample >= 200_000_000L)) {
        lastSample = frame.timestamp
        sampleLuma(frame)
      } else null
      light.observeLight(luma, needsLight)
    }
    if (light.detected && !enabled && now >= nextEnableAttempt) {
      nextEnableAttempt = now + 1_000_000_000L
      enabled = configure(session, Config.FlashMode.TORCH)
    }
  }

  fun turnOff(session: Session) {
    if (enabled && configure(session, Config.FlashMode.OFF)) enabled = false
    lastSample = 0L
    nextEnableAttempt = 0L
  }

  private fun configure(session: Session, mode: Config.FlashMode): Boolean = try {
    session.configure(session.config.apply { flashMode = mode })
    true
  } catch (error: RuntimeException) {
    Log.w("ArWalkTorch", "Cannot set AR camera flash to $mode", error)
    false
  }

  private fun sampleLuma(frame: Frame): Int? = try {
    frame.acquireCameraImage().use { image ->
      val plane = image.planes[0]
      val pixels = plane.buffer
      val base = pixels.position()
      var total = 0
      for (row in 0 until 8) for (column in 0 until 8) {
        val y = (2 * row + 1) * image.height / 16
        val x = (2 * column + 1) * image.width / 16
        total += pixels.get(base + y * plane.rowStride + x * plane.pixelStride).toInt() and 0xff
      }
      total / 64
    }
  } catch (_: NotYetAvailableException) {
    null
  } catch (_: ResourceExhaustedException) {
    null
  }
}
