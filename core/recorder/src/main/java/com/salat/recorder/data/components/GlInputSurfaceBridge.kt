package com.salat.recorder.data.components

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import timber.log.Timber

internal class GlInputSurfaceBridge(
    cameraWidth: Int,
    cameraHeight: Int,
    encoderWidth: Int,
    encoderHeight: Int,
    encoderInputSurface: Surface,
    threadName: String,
    targetFrameRate: Int,
) {
    private val bridgeThread = HandlerThread(threadName).apply { start() }
    private val bridgeHandler = Handler(bridgeThread.looper)
    private val surfaceEncoder = SurfaceEncoder(encoderWidth, encoderHeight)
    private val frameAvailable = AtomicBoolean(false)
    private val renderScheduled = AtomicBoolean(false)
    private val targetFrameDurationNs = if (targetFrameRate > 0) 1_000_000_000L / targetFrameRate else 0L

    @Volatile
    private var released = false

    @Volatile
    private var nextFramePresentationTimeNs = Long.MIN_VALUE

    private val inputSurfaceTexture: SurfaceTexture
    val inputSurface: Surface

    init {
        val initLatch = CountDownLatch(1)
        var preparedSurfaceTexture: SurfaceTexture? = null
        var preparedSurface: Surface? = null
        var initError: Throwable? = null

        bridgeHandler.post {
            try {
                surfaceEncoder.initialize(encoderInputSurface)
                val surfaceTexture = SurfaceTexture(surfaceEncoder.textureId).apply {
                    setDefaultBufferSize(cameraWidth, cameraHeight)
                }
                surfaceEncoder.setInputSurfaceTexture(surfaceTexture)
                surfaceTexture.setOnFrameAvailableListener(
                    {
                        frameAvailable.set(true)
                        scheduleRender()
                    },
                    bridgeHandler,
                )
                preparedSurfaceTexture = surfaceTexture
                preparedSurface = Surface(surfaceTexture)
            } catch (throwable: Throwable) {
                initError = throwable
            } finally {
                initLatch.countDown()
            }
        }

        initLatch.await()
        val throwable = initError
        if (throwable != null) {
            bridgeThread.quitSafely()
            throw throwable
        }

        inputSurfaceTexture = checkNotNull(preparedSurfaceTexture)
        inputSurface = checkNotNull(preparedSurface)
    }

    private fun scheduleRender() {
        if (released) return
        if (!renderScheduled.compareAndSet(false, true)) return
        bridgeHandler.post {
            try {
                while (!released) {
                    val hasFrame = frameAvailable.getAndSet(false)
                    if (!hasFrame) break

                    val sourcePresentationTimeNs = surfaceEncoder.latchLatestFrame(System.nanoTime())
                    val targetPresentationTimeNs = resolvePresentationTimeNs(sourcePresentationTimeNs) ?: continue
                    surfaceEncoder.renderFrame(targetPresentationTimeNs)
                }
            } catch (throwable: Throwable) {
                Timber.w(throwable, "GL bridge render failed")
            } finally {
                renderScheduled.set(false)
                if (!released && frameAvailable.get()) {
                    scheduleRender()
                }
            }
        }
    }

    private fun resolvePresentationTimeNs(sourcePresentationTimeNs: Long): Long? {
        if (targetFrameDurationNs <= 0L) {
            return sourcePresentationTimeNs
        }

        if (nextFramePresentationTimeNs == Long.MIN_VALUE) {
            nextFramePresentationTimeNs = sourcePresentationTimeNs + targetFrameDurationNs
            return sourcePresentationTimeNs
        }

        if (sourcePresentationTimeNs < nextFramePresentationTimeNs) {
            return null
        }

        val presentationTimeNs = nextFramePresentationTimeNs
        do {
            nextFramePresentationTimeNs += targetFrameDurationNs
        } while (nextFramePresentationTimeNs <= sourcePresentationTimeNs)

        return presentationTimeNs
    }

    fun release() {
        if (released) return
        released = true

        runCatching {
            inputSurfaceTexture.setOnFrameAvailableListener(null)
        }
        runCatching {
            inputSurface.release()
        }

        bridgeHandler.post {
            runCatching {
                inputSurfaceTexture.release()
            }
            runCatching {
                surfaceEncoder.release()
            }
            bridgeThread.quitSafely()
        }

        runCatching {
            bridgeThread.join(1_000L)
        }
    }

    private class SurfaceEncoder(
        private val width: Int,
        private val height: Int,
    ) {
        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        private var eglConfig: EGLConfig? = null
        private var program: Int = 0

        var textureId: Int = 0
            private set

        private var positionHandle: Int = 0
        private var texCoordHandle: Int = 0
        private var texMatrixHandle: Int = 0
        private var textureHandle: Int = 0

        private val texMatrix = FloatArray(16)
        private var inputSurfaceTexture: SurfaceTexture? = null
        private var released = false

        fun initialize(outputSurface: Surface) {
            initEgl(outputSurface)
            initGl()
            GLES20.glViewport(0, 0, width, height)
        }

        fun setInputSurfaceTexture(surfaceTexture: SurfaceTexture) {
            inputSurfaceTexture = surfaceTexture
        }

        fun latchLatestFrame(fallbackPresentationTimeNs: Long): Long {
            if (released) return fallbackPresentationTimeNs
            val surfaceTexture = inputSurfaceTexture ?: return fallbackPresentationTimeNs
            makeCurrent()
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(texMatrix)
            return surfaceTexture.timestamp.takeIf { it > 0L } ?: fallbackPresentationTimeNs
        }

        fun renderFrame(presentationTimeNs: Long) {
            if (released) return

            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniformMatrix4fv(texMatrixHandle, 1, false, texMatrix, 0)
            GLES20.glUniform1i(textureHandle, 0)

            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, VERTEX_BUFFER)
            GLES20.glEnableVertexAttribArray(texCoordHandle)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, TEX_BUFFER)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(texCoordHandle)

            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeNs)
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        fun release() {
            if (released) return
            released = true

            if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglSurface != EGL14.EGL_NO_SURFACE &&
                eglContext != EGL14.EGL_NO_CONTEXT
            ) {
                runCatching {
                    makeCurrent()
                }
            }

            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
            }

            if (textureId != 0) {
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
                val textures = intArrayOf(textureId)
                GLES20.glDeleteTextures(1, textures, 0)
                textureId = 0
            }

            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    eglDisplay,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT,
                )

                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    eglSurface = EGL14.EGL_NO_SURFACE
                }

                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                    eglContext = EGL14.EGL_NO_CONTEXT
                }

                EGL14.eglTerminate(eglDisplay)
                eglDisplay = EGL14.EGL_NO_DISPLAY
            }

            inputSurfaceTexture = null
        }

        private fun initEgl(outputSurface: Surface) {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val attribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT or EGL14.EGL_WINDOW_BIT,
                EGLExt.EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, num, 0)
            eglConfig = configs[0]

            val contextAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(
                eglDisplay,
                eglConfig,
                EGL14.EGL_NO_CONTEXT,
                contextAttrs,
                0,
            )

            val surfaceAttrs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(
                eglDisplay,
                eglConfig,
                outputSurface,
                surfaceAttrs,
                0,
            )
            makeCurrent()
        }

        private fun initGl() {
            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            texCoordHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
            texMatrixHandle = GLES20.glGetUniformLocation(program, "uTexMatrix")
            textureHandle = GLES20.glGetUniformLocation(program, "sTexture")

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
        }

        private fun createProgram(vertexShaderSource: String, fragmentShaderSource: String): Int {
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)
            val programId = GLES20.glCreateProgram()
            GLES20.glAttachShader(programId, vertexShader)
            GLES20.glAttachShader(programId, fragmentShader)
            GLES20.glLinkProgram(programId)
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return programId
        }

        private fun loadShader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            return shader
        }

        private fun makeCurrent() {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        private companion object {
            private const val VERTEX_SHADER = """
attribute vec2 aPosition;
attribute vec4 aTextureCoord;
varying vec2 vTextureCoord;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vTextureCoord = aTextureCoord.xy;
}
"""

            private const val FRAGMENT_SHADER = """
#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform samplerExternalOES sTexture;
uniform mat4 uTexMatrix;
varying vec2 vTextureCoord;
void main() {
    vec2 finalCoord = (uTexMatrix * vec4(vTextureCoord, 0.0, 1.0)).xy;
    gl_FragColor = texture2D(sTexture, finalCoord);
}
"""

            private val VERTICES = floatArrayOf(
                -1f,
                -1f,
                1f,
                -1f,
                -1f,
                1f,
                1f,
                1f,
            )

            private val TEX_COORDS = floatArrayOf(
                0f,
                0f,
                1f,
                0f,
                0f,
                1f,
                1f,
                1f,
            )

            private val VERTEX_BUFFER: FloatBuffer =
                ByteBuffer.allocateDirect(VERTICES.size * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .apply {
                        put(VERTICES)
                        position(0)
                    }

            private val TEX_BUFFER: FloatBuffer =
                ByteBuffer.allocateDirect(TEX_COORDS.size * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .apply {
                        put(TEX_COORDS)
                        position(0)
                    }
        }
    }
}
