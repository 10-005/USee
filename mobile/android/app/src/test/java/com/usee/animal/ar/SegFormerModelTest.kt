package com.usee.animal.ar

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class SegFormerModelTest {
  @Test fun bundledModelRunsWithExpectedInputOutputAndLabelContract() {
    val model = File("src/main/assets/models/segformer/model.onnx")
    val bytes = model.readBytes()
    val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    assertEquals("3a89102115fe3c16230502437b894844ba50cde6f7c800f9884e87c360bcbfc9", hash)
    val environment = OrtEnvironment.getEnvironment()
    OrtSession.SessionOptions().use { options ->
      options.setIntraOpNumThreads(2)
      environment.createSession(bytes, options).use { session ->
        assertTrue(session.inputNames.contains("pixel_values"))
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(FloatArray(3 * 512 * 512)), longArrayOf(1, 3, 512, 512)).use { input ->
          session.run(mapOf("pixel_values" to input)).use { result ->
            val output = result[0] as OnnxTensor
            assertArrayEquals(longArrayOf(1, 150, 128, 128), output.info.shape)
            assertEquals(128 * 128, SemanticFloorMask.decode(output.floatBuffer, 150, 128, 128).size)
          }
        }
      }
    }
  }
}
