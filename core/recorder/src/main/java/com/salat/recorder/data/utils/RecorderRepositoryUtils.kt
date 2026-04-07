package com.salat.recorder.data.utils

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.media.MediaCodecInfo
import android.os.Handler
import android.util.Size
import android.view.Surface
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber

internal fun alignSizeDown(size: Size, widthAlignment: Int, heightAlignment: Int): Size? {
    val alignedWidth = alignDown(size.width, widthAlignment)
    val alignedHeight = alignDown(size.height, heightAlignment)
    if (alignedWidth <= 0 || alignedHeight <= 0) return null
    return Size(alignedWidth, alignedHeight)
}

internal fun alignDown(value: Int, alignment: Int): Int {
    if (alignment <= 1) return value
    return value - (value % alignment)
}

internal fun MediaCodecInfo.supportsSurfaceInput(mimeType: String): Boolean {
    val capabilities = runCatching { getCapabilitiesForType(mimeType) }.getOrNull() ?: return false
    return capabilities.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
}

internal fun File.segmentGroupKeyOrNull(): String? {
    val fileName = name.substringBeforeLast('.')
    return when {
        fileName.matches(Regex("\\d{2}-\\d{2}-\\d{2}_\\d{2}\\.\\d{2}\\.\\d{2}_[A-Za-z]+")) -> {
            fileName.substringBeforeLast('_')
        }

        fileName.matches(Regex("\\d{2}\\.\\d{2}\\.\\d{2}_\\d{2}-\\d{2}-\\d{2}_[A-Za-z]+")) -> {
            fileName.substringBeforeLast('_')
        }

        else -> null
    }
}

internal fun File.deleteEmptyDirectoriesUpwards(stopAt: File) {
    var current: File? = this
    val stopPath = stopAt.absoluteFile.path

    while (current != null && current.absoluteFile.path != stopPath) {
        if (!current.exists() || !current.isDirectory) break
        if (!current.list().isNullOrEmpty()) break

        val deleted = runCatching { current.delete() }
            .onFailure { Timber.w(it, "Failed to delete empty directory: %s", current.absolutePath) }
            .getOrDefault(false)
        if (!deleted) break

        current = current.parentFile
    }
}

internal suspend fun CameraDevice.createRecordingSession(
    outputs: List<Surface>,
    handler: Handler,
): CameraCaptureSession = suspendCancellableCoroutine { continuation ->
    @Suppress("DEPRECATION")
    createCaptureSession(
        outputs,
        object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                if (continuation.isActive) {
                    continuation.resume(session)
                } else {
                    session.close()
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                session.close()
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        IOException("Failed to configure capture session for $id"),
                    )
                }
            }
        },
        handler,
    )
}
