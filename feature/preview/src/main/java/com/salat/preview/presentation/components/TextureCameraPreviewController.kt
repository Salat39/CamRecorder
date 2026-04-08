package com.salat.preview.presentation.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat
import java.io.IOException
import timber.log.Timber

internal class TextureCameraPreviewController(
    private val context: Context,
    private val cameraId: String,
    private val previewWidth: Int?,
    private val previewHeight: Int?,
) {
    private val cameraManager by lazy(LazyThreadSafetyMode.NONE) {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val cameraThread = HandlerThread("preview-$cameraId").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    @Volatile
    private var attachedView: TextureView? = null

    @Volatile
    private var previewSurfaceTexture: SurfaceTexture? = null

    @Volatile
    private var previewSurface: Surface? = null

    @Volatile
    private var cameraDevice: CameraDevice? = null

    @Volatile
    private var captureSession: CameraCaptureSession? = null

    @Volatile
    private var enabled = false

    @Volatile
    private var released = false

    @Volatile
    private var sessionGeneration = 0L

    private val listener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            handleSurfaceAvailable(surface, width, height)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            handleSurfaceAvailable(surface, width, height)
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            handleSurfaceDestroyed(surface)
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }

    fun attach(textureView: TextureView) {
        if (released) return
        if (attachedView === textureView) {
            if (textureView.surfaceTextureListener !== listener) {
                textureView.surfaceTextureListener = listener
            }
            if (textureView.isAvailable) {
                textureView.surfaceTexture?.let { surfaceTexture ->
                    handleSurfaceAvailable(surfaceTexture, textureView.width, textureView.height)
                }
            }
            return
        }

        attachedView = textureView
        textureView.surfaceTextureListener = listener
        if (textureView.isAvailable) {
            textureView.surfaceTexture?.let { surfaceTexture ->
                handleSurfaceAvailable(surfaceTexture, textureView.width, textureView.height)
            }
        }
    }

    fun setEnabled(value: Boolean) {
        if (released) return
        enabled = value
        cameraHandler.post {
            if (released) return@post
            if (enabled) {
                openIfReady()
            } else {
                closeCameraSession()
            }
        }
    }

    fun release() {
        if (released) return
        released = true
        attachedView?.surfaceTextureListener = null
        attachedView = null
        cameraHandler.post {
            closeCameraSession()
            previewSurfaceTexture = null
        }
        runCatching {
            cameraThread.quitSafely()
            cameraThread.join(1_000L)
        }
    }

    private fun handleSurfaceAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        if (released) return
        val targetWidth = previewWidth ?: width.coerceAtLeast(1)
        val targetHeight = previewHeight ?: height.coerceAtLeast(1)
        surfaceTexture.setDefaultBufferSize(targetWidth, targetHeight)
        previewSurfaceTexture = surfaceTexture
        cameraHandler.post {
            if (released) return@post
            openIfReady()
        }
    }

    private fun handleSurfaceDestroyed(surfaceTexture: SurfaceTexture) {
        if (previewSurfaceTexture === surfaceTexture) {
            previewSurfaceTexture = null
        }
        cameraHandler.post {
            closeCameraSession()
        }
    }

    private fun openIfReady() {
        if (!enabled || released) return
        if (cameraDevice != null && captureSession != null) return
        val surfaceTexture = previewSurfaceTexture ?: return
        if (!hasCameraPermission()) return

        val surface = previewSurface ?: Surface(surfaceTexture).also { previewSurface = it }
        openCamera(surface)
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(surface: Surface) {
        if (cameraDevice != null) {
            createSession(surface)
            return
        }

        val generation = ++sessionGeneration
        runCatching {
            cameraManager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        if (released || !enabled || generation != sessionGeneration) {
                            camera.close()
                            return
                        }
                        cameraDevice = camera
                        createSession(surface)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        if (cameraDevice === camera) {
                            cameraDevice = null
                        }
                        closeSessionOnly()
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        Timber.w("Preview openCamera error=%s cameraId=%s", error, cameraId)
                        camera.close()
                        if (cameraDevice === camera) {
                            cameraDevice = null
                        }
                        closeSessionOnly()
                    }
                },
                cameraHandler,
            )
        }.onFailure { throwable ->
            Timber.w(throwable, "Preview openCamera failed cameraId=%s", cameraId)
        }
    }

    @Suppress("DEPRECATION")
    private fun createSession(surface: Surface) {
        val camera = cameraDevice ?: return
        val generation = ++sessionGeneration
        closeSessionOnly()

        runCatching {
            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (released || !enabled || generation != sessionGeneration) {
                            session.close()
                            return
                        }
                        captureSession = session
                        startRepeating(camera, session, surface)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Timber.w("Preview session configure failed cameraId=%s", cameraId)
                        session.close()
                        if (captureSession === session) {
                            captureSession = null
                        }
                    }
                },
                cameraHandler,
            )
        }.onFailure { throwable ->
            Timber.w(throwable, "Preview createCaptureSession failed cameraId=%s", cameraId)
        }
    }

    private fun startRepeating(camera: CameraDevice, session: CameraCaptureSession, surface: Surface) {
        val request = runCatching {
            camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            }.build()
        }.getOrElse { throwable ->
            throw IOException("Failed to build preview request for $cameraId", throwable)
        }

        runCatching {
            session.setRepeatingRequest(request, null, cameraHandler)
        }.onFailure { throwable ->
            Timber.w(throwable, "Preview repeating request failed cameraId=%s", cameraId)
            closeCameraSession()
        }
    }

    private fun closeCameraSession() {
        closeSessionOnly()
        val activeCamera = cameraDevice
        cameraDevice = null
        runCatching { activeCamera?.close() }
        val activeSurface = previewSurface
        previewSurface = null
        runCatching { activeSurface?.release() }
    }

    private fun closeSessionOnly() {
        val activeSession = captureSession
        captureSession = null
        if (activeSession != null) {
            runCatching { activeSession.stopRepeating() }
            runCatching { activeSession.abortCaptures() }
            runCatching { activeSession.close() }
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
