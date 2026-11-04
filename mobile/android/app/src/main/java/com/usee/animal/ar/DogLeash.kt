package com.usee.animal.ar

import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.colorOf
import io.github.sceneview.node.CylinderNode

/** World-space rope; its hand endpoint is unprojected from the viewport's bottom center. */
internal class DogLeash(private val view: ARSceneView) : AutoCloseable {
  private val curve = LeashCurve()
  private val material = view.materialLoader.createColorInstance(
    colorOf(r = 0.025f, g = 0.18f, b = 0.65f, a = 1f),
    metallic = 0f, roughness = 0.7f, reflectance = 0.25f,
  )
  private val segments = List(24) {
    CylinderNode(view.engine, radius = 0.004f, height = 1f, sideCount = 8, materialInstance = material).apply {
      isVisible = false
      isShadowCaster = false
      isShadowReceiver = true
      view.addChildNode(this)
    }
  }
  private var previousTime = 0L
  private var latestPoints: List<LeashPoint> = emptyList()

  fun hide() {
    segments.forEach { it.isVisible = false }
    previousTime = 0L
    latestPoints = emptyList()
    curve.reset()
  }

  fun update(collar: LeashPoint, hand: LeashPoint, time: Long): Float {
    val dt = if (previousTime == 0L) 0f else (time - previousTime) / 1_000_000_000f
    previousTime = time
    val points = curve.points(collar, hand, dt, segments.size)
    latestPoints = points
    segments.forEachIndexed { index, segment ->
      segment.transformManager.setTransform(segment.transformInstance, LeashCurve.segmentTransform(points[index], points[index + 1]))
      segment.isVisible = true
    }
    return points.zipWithNext().sumOf { (a, b) -> (b - a).length().toDouble() }.toFloat()
  }

  fun hitTest(x: Float, y: Float, viewMatrix: FloatArray, projection: FloatArray, width: Int, height: Int, tolerance: Float): Boolean {
    if (width <= 0 || height <= 0) return false
    val camera = FloatArray(4)
    val clip = FloatArray(4)
    fun project(point: LeashPoint): Pair<Float, Float>? {
      android.opengl.Matrix.multiplyMV(camera, 0, viewMatrix, 0, floatArrayOf(point.x, point.y, point.z, 1f), 0)
      android.opengl.Matrix.multiplyMV(clip, 0, projection, 0, camera, 0)
      if (clip[3] <= 0.05f) return null
      return (clip[0] / clip[3] + 1f) * width / 2f to (1f - clip[1] / clip[3]) * height / 2f
    }
    val screen = latestPoints.map { project(it) }
    return (0 until screen.size - 1).any { index ->
      val a = screen[index]
      val b = screen[index + 1]
      a != null && b != null && LeashInteraction.nearSegment(x, y, a.first, a.second, b.first, b.second, tolerance)
    }
  }

  override fun close() {
    segments.forEach { view.removeChildNode(it); it.destroy() }
    // Material lifetime belongs to SceneView's material loader.
  }
}
