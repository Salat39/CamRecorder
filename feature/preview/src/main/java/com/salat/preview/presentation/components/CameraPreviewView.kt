package com.salat.preview.presentation.components

import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.salat.preview.presentation.entity.DisplayAvailableCameraSize

@Composable
internal fun CameraPreviewView(
    cameraId: String,
    previewSize: DisplayAvailableCameraSize?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val controller = remember(context, cameraId, previewSize?.width, previewSize?.height) {
        TextureCameraPreviewController(
            context = context.applicationContext,
            cameraId = cameraId,
            previewWidth = previewSize?.width,
            previewHeight = previewSize?.height,
        )
    }

    LaunchedEffect(controller, enabled) {
        controller.setEnabled(enabled)
    }

    DisposableEffect(controller) {
        onDispose {
            controller.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextureView(viewContext).also(controller::attach)
        },
        update = controller::attach,
    )
}
