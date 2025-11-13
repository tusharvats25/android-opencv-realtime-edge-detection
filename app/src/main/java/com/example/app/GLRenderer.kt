package com.example.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLRenderer(val context: Context) : GLSurfaceView.Renderer {
    private var textureId = -1
    private var frameWidth = 0
    private var frameHeight = 0
    @Volatile private var pixelBuffer: ByteBuffer? = null

    private val vertexCoords = floatArrayOf(
        -1f,  1f,
        -1f, -1f,
         1f,  1f,
         1f, -1f
    )
    private val texCoords = floatArrayOf(
        0f, 0f,
        0f, 1f,
        1f, 0f,
        1f, 1f
    )
    private lateinit var vtxBuf: FloatBuffer
    private lateinit var texBuf: FloatBuffer
    private var program = 0
    private var positionHandle = 0
    private var texHandle = 0
    private var samplerHandle = 0

    init {
        vtxBuf = ByteBuffer.allocateDirect(vertexCoords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vtxBuf.put(vertexCoords).position(0)
        texBuf = ByteBuffer.allocateDirect(texCoords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        texBuf.put(texCoords).position(0)
    }

    fun updateFrame(rgba: ByteArray, w: Int, h: Int) {
        frameWidth = w
        frameHeight = h
        val bb = ByteBuffer.allocateDirect(rgba.size)
        bb.put(rgba)
        bb.position(0)
        pixelBuffer = bb
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val vs = """attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main(){ gl_Position = aPosition; vTexCoord = aTexCoord; }"""
        val fs = """precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            void main(){ gl_FragColor = texture2D(sTexture, vTexCoord); }"""
        program = createProgram(vs, fs)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        samplerHandle = GLES20.glGetUniformLocation(program, "sTexture")
        textureId = genTexture()
        GLES20.glClearColor(0f,0f,0f,1f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0,0,width,height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val buf = pixelBuffer ?: return
        if (frameWidth == 0 || frameHeight == 0) return

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        // upload pixels
        buf.position(0)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, frameWidth, frameHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)

        vtxBuf.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vtxBuf)

        texBuf.position(0)
        GLES20.glEnableVertexAttribArray(texHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, texBuf)

        GLES20.glUniform1i(samplerHandle, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texHandle)
    }

    fun saveCurrentFrame() {
        val buf = pixelBuffer ?: return
        if (frameWidth == 0 || frameHeight == 0) return
        try {
            val arr = ByteArray(buf.capacity())
            buf.position(0)
            buf.get(arr)
            val bmp = Bitmap.createBitmap(frameWidth, frameHeight, Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(ByteBuffer.wrap(arr))
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val f = File(dir, "processed_sample.png")
            val fos = FileOutputStream(f)
            bmp.compress(Bitmap.CompressFormat.PNG, 90, fos)
            fos.flush()
            fos.close()
            Log.d("GLRenderer","Saved frame to: ${f.absolutePath}")
        } catch (e: Exception) {
            Log.e("GLRenderer","save error: ${e.message}")
        }
    }

    private fun genTexture(): Int {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return tex[0]
    }

    private fun loadShader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        return s
    }

    private fun createProgram(vsSrc: String, fsSrc: String): Int {
        val v = loadShader(GLES20.GL_VERTEX_SHADER, vsSrc)
        val f = loadShader(GLES20.GL_FRAGMENT_SHADER, fsSrc)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        return p
    }
}
