package com.usee.animal.ar

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class DepthGridTest {
  @Test fun missingStaleAndIsolatedSamplesAreUnknown() {
    val grid = DepthGrid(100)
    assertEquals(FloorEvidence.UNKNOWN, grid.evidence(0f, 0f, 0f, 100))
    grid.add(0f, 0.3f, 0f)
    assertEquals(FloorEvidence.UNKNOWN, grid.evidence(0f, 0f, 0f, 100))
    repeat(3) { grid.add(0f, 0f, 0f) }
    assertEquals(FloorEvidence.CLEAR, grid.evidence(0f, 0f, 0f, 100))
    assertEquals(FloorEvidence.UNKNOWN, grid.evidence(0f, 0f, 0f, 600_000_101))
  }

  @Test fun bodyObstacleWinsOverFloorAndDropIsBlocked() {
    val grid = DepthGrid(0)
    repeat(3) { grid.add(0f, 0f, 0f); grid.add(0f, 0.2f, 0f) }
    assertEquals(FloorEvidence.OBSTACLE, grid.evidence(0f, 0f, 0f, 0))
    val drop = DepthGrid(0)
    repeat(3) { drop.add(-0.01f, -0.2f, -0.01f) }
    assertEquals(FloorEvidence.OBSTACLE, drop.evidence(-0.01f, 0f, -0.01f, 0))
  }

  @Test fun littleEndianPaddedRowsAndPoseProduceWorldFloor() {
    val bytes = ByteBuffer.allocate(40)
    bytes.position(2)
    for (v in 0..1) for (u in 0..2) {
      val index = 2 + v * 16 + u * 4
      bytes.put(index, 0xe8.toByte()); bytes.put(index + 1, 3)
    }
    val pose = floatArrayOf(1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,1f,0f, 2f,0f,3f,1f)
    val grid = DepthGrid.read(bytes, 3, 2, 16, 4, 100f, 100f, 0f, 0f, pose, 0)
    assertEquals(FloorEvidence.CLEAR, grid.evidence(2.01f, 0f, 2f, 0))
    assertEquals(FloorEvidence.UNKNOWN, grid.evidence(0f, 0f, -1f, 0))
    assertEquals(2, bytes.position())
  }
}
