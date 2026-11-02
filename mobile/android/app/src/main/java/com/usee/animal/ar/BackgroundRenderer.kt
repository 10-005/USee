package com.usee.animal.ar

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class BackgroundRenderer {
  var textureId: Int = -1
    private set

  private var program = 0
  private var positionAttrib = 0
  private var texCoordAttrib = 0
  private var textureUniform = 0
  private val quadCoords: FloatBuffer = directBuffer(QUAD_COORDS)
  private val quadTexCoords: FloatBuffer = directBuffer(FloatArray(QUAD_COORDS.size))

  fun createOnGlThread() {
    val textures = IntArray(1)
    GLES20.glGenTextures(1, textures, 0)
    textureId = textures[0]
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
    GLES20.glTexParameteri(
      GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
      GLES20.GL_TEXTURE_MIN_FILTER,
      GLES20.GL_LINEAR,
    )
    GLES20.glTexParameteri(
      GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
      GLES20.GL_TEXTURE_MAG_FILTER,
      GLES20.GL_LINEAR,
    )
    GLES20.glTexParameteri(
      GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
      GLES20.GL_TEXTURE_WRAP_S,
      GLES20.GL_CLAMP_TO_EDGE,
    )
    GLES20.glTexParameteri(
      GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
      GLES20.GL_TEXTURE_WRAP_T,
      GLES20.GL_CLAMP_TO_EDGE,
    )

    val vertexShader =
      loadShader(
        GLES20.GL_VERTEX_SHADER,
        """
        #version 300 es
        in vec2 a_Position;
        in vec2 a_TexCoord;
        out vec2 v_TexCoord;
        void main() {
          gl_Position = vec4(a_Position, 0.0, 1.0);
          v_TexCoord = a_TexCoord;
        }
        """.trimIndent(),
      )
    val fragmentShader =
      loadShader(
        GLES20.GL_FRAGMENT_SHADER,
        """
        #version 300 es
        #extension GL_OES_EGL_image_external_essl3 : require
        precision mediump float;
        uniform samplerExternalOES sTexture;
        in vec2 v_TexCoord;
        out vec4 fragColor;
        void main() {
          fragColor = texture(sTexture, v_TexCoord);
        }
        """.trimIndent(),
      )

    program = GLES20.glCreateProgram()
    GLES20.glAttachShader(program, vertexShader)
    GLES20.glAttachShader(program, fragmentShader)
    GLES20.glLinkProgram(program)
    val linkStatus = IntArray(1)
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
    if (linkStatus[0] == 0) {
      val log = GLES20.glGetProgramInfoLog(program)
      throw IllegalStateException("카메라 배경 셰이더를 링크하지 못했습니다. $log")
    }
    positionAttrib = GLES20.glGetAttribLocation(program, "a_Position")
    texCoordAttrib = GLES20.glGetAttribLocation(program, "a_TexCoord")
    textureUniform = GLES20.glGetUniformLocation(program, "sTexture")
  }

  fun draw(frame: Frame) {
    if (frame.timestamp == 0L) {
      return
    }
    quadCoords.position(0)
    quadTexCoords.position(0)
    frame.transformCoordinates2d(
      Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
      quadCoords,
      Coordinates2d.TEXTURE_NORMALIZED,
      quadTexCoords,
    )
    quadCoords.position(0)
    quadTexCoords.position(0)

    GLES20.glDisable(GLES20.GL_DEPTH_TEST)
    GLES20.glDepthMask(false)
    GLES20.glUseProgram(program)
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
    GLES20.glUniform1i(textureUniform, 0)
    GLES20.glVertexAttribPointer(positionAttrib, 2, GLES20.GL_FLOAT, false, 0, quadCoords)
    GLES20.glVertexAttribPointer(texCoordAttrib, 2, GLES20.GL_FLOAT, false, 0, quadTexCoords)
    GLES20.glEnableVertexAttribArray(positionAttrib)
    GLES20.glEnableVertexAttribArray(texCoordAttrib)
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    GLES20.glDisableVertexAttribArray(positionAttrib)
    GLES20.glDisableVertexAttribArray(texCoordAttrib)
    GLES20.glDepthMask(true)
  }

  private fun directBuffer(values: FloatArray): FloatBuffer {
    val buffer =
      ByteBuffer.allocateDirect(values.size * FLOAT_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    buffer.put(values)
    buffer.position(0)
    return buffer
  }

  private fun loadShader(type: Int, code: String): Int {
    val shader = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, code)
    GLES20.glCompileShader(shader)
    val status = IntArray(1)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
    if (status[0] == 0) {
      val log = GLES20.glGetShaderInfoLog(shader)
      GLES20.glDeleteShader(shader)
      throw IllegalStateException("셰이더 컴파일에 실패했습니다. $log")
    }
    return shader
  }

  private companion object {
    const val FLOAT_BYTES = 4
    val QUAD_COORDS =
      floatArrayOf(
        -1f,
        -1f,
        1f,
        -1f,
        -1f,
        1f,
        1f,
        1f,
      )
  }
}
