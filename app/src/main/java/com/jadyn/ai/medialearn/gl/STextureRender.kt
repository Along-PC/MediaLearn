package com.jadyn.ai.medialearn.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import com.jadyn.ai.medialearn.codec.checkGlError
import com.jadyn.ai.medialearn.codec.checkLocation
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 *@version:
 *@FileDescription:
 *@Author:jing
 *@Since:2019/2/12
 *@ChangeList:
 */
class STextureRender {

    private val FLOAT_SIZE_BYTES = 4
    private val TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES
    private val TRIANGLE_VERTICES_DATA_POS_OFFSET = 0
    private val TRIANGLE_VERTICES_DATA_UV_OFFSET = 3

    private val VERTEX_SHADER = "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uSTMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTextureCoord;\n" +
            "varying vec2 vTextureCoord;\n" +
            "void main() {\n" +
            "gl_Position = uMVPMatrix * aPosition;\n" +
            "vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
            "}\n"

    private val FRAGMENT_SHADER = "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +      // highp here doesn't seem to matter
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "void main() {\n" +
            "gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
            "}\n"

    private val TAG = "STextureRender"

    private val mTriangleVerticesData = floatArrayOf(
            // X, Y, Z, U, V
            -1.0f, -1.0f, 0f, 0f, 0f, 1.0f, -1.0f, 0f, 1f, 0f, -1.0f, 1.0f, 0f, 0f, 1f, 1.0f, 1.0f, 0f, 1f, 1f)

    private val mTriangleVertices: FloatBuffer

    private val mMVPMatrix = FloatArray(16)
    private val mSTMatrix = FloatArray(16)

    private var mProgram: Int = 0
    var textureId = -12345
        private set
    private var muMVPMatrixHandle: Int = 0
    private var muSTMatrixHandle: Int = 0
    private var maPositionHandle: Int = 0
    private var maTextureHandle: Int = 0

    init {
        mTriangleVertices = ByteBuffer.allocateDirect(
                mTriangleVerticesData.size * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
        mTriangleVertices.put(mTriangleVerticesData).position(0)

        Matrix.setIdentityM(mSTMatrix, 0)
    }

    fun drawFrame(st: SurfaceTexture) {
        checkGlError("onDrawFrame start")
        st.getTransformMatrix(mSTMatrix)

        // (optional) clear to green so we can see if we're failing to set pixels
        GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(mProgram)
        checkGlError("glUseProgram")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET)
        GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices)
        checkGlError("glVertexAttribPointer maPosition")
        GLES20.glEnableVertexAttribArray(maPositionHandle)
        checkGlError("glEnableVertexAttribArray maPositionHandle")

        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET)
        GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices)
        checkGlError("glVertexAttribPointer maTextureHandle")
        GLES20.glEnableVertexAttribArray(maTextureHandle)
        checkGlError("glEnableVertexAttribArray maTextureHandle")

        Matrix.setIdentityM(mMVPMatrix, 0)
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0)
        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")

        // IMPORTANT: on some devices, if you are sharing the external texture between two
        // contexts, one context may not see updates to the texture unless you un-bind and
        // re-bind it.  If you're not using shared EGL contexts, you don't need to bind
        // texture 0 here.
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    fun surfaceCreated() {
        mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        Log.d(TAG, "surface created :$mProgram ")
        if (mProgram == 0) {
            throw RuntimeException("failed creating program")
        }
        maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition")
        checkLocation(maPositionHandle, "aPosition")
        maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord")
        checkLocation(maTextureHandle, "aTextureCoord")

        muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")
        checkLocation(muMVPMatrixHandle, "uMVPMatrix")
        muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix")
        checkLocation(muSTMatrixHandle, "uSTMatrix")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)

        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        checkGlError("glBindTexture mTextureID")

        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE)
        checkGlError("glTexParameter")
    }

    /**
     * Replaces the fragment shader.  Pass in null to reset to default.
     */
    fun changeFragmentShader(fragmentShader: String?) {
        var fragmentShader = fragmentShader
        if (fragmentShader == null) {
            fragmentShader = FRAGMENT_SHADER
        }
        GLES20.glDeleteProgram(mProgram)
        mProgram = createProgram(VERTEX_SHADER, fragmentShader)
        if (mProgram == 0) {
            throw RuntimeException("failed creating program")
        }
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        var shader = GLES20.glCreateShader(shaderType)
        checkGlError("glCreateShader type=$shaderType")
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader $shaderType:")
            Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader))
            GLES20.glDeleteShader(shader)
            shader = 0
        }
        return shader
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        Log.d(TAG, "createProgram vertexShader: $vertexShader ")
        if (vertexShader == 0) {
            return 0
        }
        val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        Log.d(TAG, "createProgram vertexShader: $pixelShader ")
        if (pixelShader == 0) {
            return 0
        }

        var program = GLES20.glCreateProgram()
        if (program == 0) {
            Log.e(TAG, "Could not create program")
        }
        GLES20.glAttachShader(program, vertexShader)
        checkGlError("glAttachShader")
        GLES20.glAttachShader(program, pixelShader)
        checkGlError("glAttachShader")
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: ")
            Log.e(TAG, GLES20.glGetProgramInfoLog(program))
            GLES20.glDeleteProgram(program)
            program = 0
        }
        return program
    }
}