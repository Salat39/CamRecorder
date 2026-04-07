package com.salat.drivestorage.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.core.content.ContextCompat
import com.salat.commonconst.RECORDS_ROOT_DIRECTORY_NAME
import com.salat.drivestorage.domain.repository.DriveStorageRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class DriveStorageRepositoryImpl(private val context: Context) : DriveStorageRepository {
    private companion object {
        private const val APP_DIR_TO_VOLUME_ROOT_PARENT_STEPS = 4
    }

    override val driveConnectedFlow
        get() = observeAvailability()

    private val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

    private val availabilityRefreshFlow = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override suspend fun fullFileAccessGranted() {
        availabilityRefreshFlow.emit(Unit)
    }

    fun observeAvailability(): Flow<Boolean> = callbackFlow {
        fun emitLatest() {
            launch(Dispatchers.IO) {
                trySend(isAnyRemovableStorageAvailable())
            }
        }

        val availabilityRefreshJob = launch {
            availabilityRefreshFlow.collect {
                emitLatest()
            }
        }

        emitLatest()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val callback = object : StorageManager.StorageVolumeCallback() {
                override fun onStateChanged(volume: StorageVolume) {
                    emitLatest()
                }
            }

            storageManager.registerStorageVolumeCallback(context.mainExecutor, callback)

            awaitClose {
                availabilityRefreshJob.cancel()
                storageManager.unregisterStorageVolumeCallback(callback)
            }
        } else {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    emitLatest()
                }
            }

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_MEDIA_MOUNTED)
                addAction(Intent.ACTION_MEDIA_UNMOUNTED)
                addAction(Intent.ACTION_MEDIA_EJECT)
                addAction(Intent.ACTION_MEDIA_REMOVED)
                addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
                addDataScheme("file")
            }

            registerReceiverCompat(receiver, filter)

            awaitClose {
                availabilityRefreshJob.cancel()
                context.unregisterReceiver(receiver)
            }
        }
    }.distinctUntilChanged()

    override fun getRemovableDriveRootOrNull() = resolveRemovableStorageRoot()
        ?.takeIf { it.exists() && it.isDirectory && it.canRead() && it.canWrite() }

    override fun prepareCameraDirectory(cameraAlias: String): File {
        val driveRoot = getRemovableDriveRootOrNull()
            ?: error("Removable USB drive is not available")

        return File(driveRoot, RECORDS_ROOT_DIRECTORY_NAME).apply {
            if (!exists() && !mkdirs()) error("Unable to create $absolutePath")
        }
    }

    private fun isAnyRemovableStorageAvailable() = resolveRemovableStorageRoot() != null

    private fun resolveApi30RemovableStorageRoot(): File? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        return storageManager.storageVolumes
            .asSequence()
            .filter { it.isMountedAndFileSystemAccessible() }
            .mapNotNull { it.directory }
            .firstOrNull { it.exists() && it.isDirectory && it.canRead() && it.canWrite() }
    }

    private fun resolveLegacyRemovableStorageRoot() = context.getExternalFilesDirs(null)
        .asSequence()
        .filterNotNull()
        .filter { it.isMountedRemovableAppDir() }
        .mapNotNull { it.resolveRemovableRootFromAppDir() }
        .firstOrNull { it.exists() && it.isDirectory && it.canRead() && it.canWrite() }

    private fun resolveRemovableStorageRoot() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        resolveApi30RemovableStorageRoot()
    } else {
        resolveLegacyRemovableStorageRoot()
    }

    @Suppress("ReturnCount")
    private fun StorageVolume.isMountedAndFileSystemAccessible(): Boolean {
        if (!isRemovable) return false
        if (state != Environment.MEDIA_MOUNTED) return false

        val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            directory ?: return false
        } else {
            return false
        }

        return path.exists() && path.isDirectory && path.canRead() && path.canWrite()
    }

    private fun File.isMountedRemovableAppDir() = Environment.isExternalStorageRemovable(this) &&
        Environment.getExternalStorageState(this) == Environment.MEDIA_MOUNTED &&
        exists() &&
        isDirectory &&
        canRead() &&
        canWrite()

    private fun File.resolveRemovableRootFromAppDir(): File? {
        var current: File? = absoluteFile
        repeat(APP_DIR_TO_VOLUME_ROOT_PARENT_STEPS) {
            current = current?.parentFile
        }
        return current
    }

    @Suppress("UnspecifiedRegisterReceiverFlag")
    private fun registerReceiverCompat(receiver: BroadcastReceiver, filter: IntentFilter) {
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }
}
