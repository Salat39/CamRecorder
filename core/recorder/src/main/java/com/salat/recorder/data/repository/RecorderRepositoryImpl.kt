package com.salat.recorder.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.os.storage.StorageManager
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import com.salat.carapi.domain.repository.CarApiRepository
import com.salat.commonconst.BROKEN_FILE_DELETE_THRESHOLD_BYTES
import com.salat.commonconst.CAMERA_OUTPUT_TYPE_AVC
import com.salat.commonconst.CAMERA_OUTPUT_TYPE_TS
import com.salat.commonconst.DEFAULT_CAMERA_FPS
import com.salat.commonconst.MAX_CAMERA_COUNT
import com.salat.commonconst.MAX_CAMERA_FPS
import com.salat.commonconst.MAX_VIDEO_HEIGHT
import com.salat.commonconst.MAX_VIDEO_WIDTH
import com.salat.commonconst.MIN_CAMERA_FPS
import com.salat.commonconst.MIN_FREE_SPACE_RATIO
import com.salat.commonconst.PIXELS_576P
import com.salat.commonconst.PIXELS_720P
import com.salat.commonconst.RECORDER_RETRY_DELAY_MS
import com.salat.commonconst.RECORDER_START_ATTEMPTS
import com.salat.commonconst.RECORDS_ROOT_DIRECTORY_NAME
import com.salat.commonconst.STORAGE_CHECK_INITIAL_DELAY_MS
import com.salat.commonconst.STORAGE_CHECK_INTERVAL_MS
import com.salat.commonconst.TARGET_FREE_SPACE_RATIO
import com.salat.commonconst.VIDEO_BITRATE_480P
import com.salat.commonconst.VIDEO_BITRATE_576P
import com.salat.commonconst.VIDEO_BITRATE_720P
import com.salat.commonconst.defaultCameraEnable
import com.salat.commonconst.defaultCameraFps
import com.salat.drivestorage.domain.repository.DriveStorageRepository
import com.salat.preferences.domain.DataStoreRepository
import com.salat.preferences.domain.entity.BoolPref
import com.salat.preferences.domain.entity.CameraDataStoreConfig
import com.salat.preferences.domain.entity.LongPref
import com.salat.recorder.data.components.AvcSegmentedFileWriter
import com.salat.recorder.data.components.GlInputSurfaceBridge
import com.salat.recorder.data.components.SegmentedVideoFileWriter
import com.salat.recorder.data.components.TsSegmentedFileWriter
import com.salat.recorder.data.entity.AliasDescriptor
import com.salat.recorder.data.entity.CameraDescriptor
import com.salat.recorder.data.entity.CameraInputMode
import com.salat.recorder.data.entity.EncoderCandidateConfig
import com.salat.recorder.data.entity.FpsSelection
import com.salat.recorder.data.entity.LayoutMapping
import com.salat.recorder.data.entity.PreferredVideoSize
import com.salat.recorder.data.entity.PreparedEncoder
import com.salat.recorder.data.entity.RecordingLauncherState
import com.salat.recorder.data.entity.RecordingProfile
import com.salat.recorder.data.entity.SegmentGroup
import com.salat.recorder.data.entity.SegmentOutputMode
import com.salat.recorder.data.utils.alignSizeDown
import com.salat.recorder.data.utils.createRecordingSession
import com.salat.recorder.data.utils.deleteEmptyDirectoriesUpwards
import com.salat.recorder.data.utils.segmentGroupKeyOrNull
import com.salat.recorder.data.utils.supportsSurfaceInput
import com.salat.recorder.domain.entity.AvailableCameraFpsRange
import com.salat.recorder.domain.entity.AvailableCameraInfo
import com.salat.recorder.domain.entity.AvailableCameraSize
import com.salat.recorder.domain.repository.RecorderRepository
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class RecorderRepositoryImpl(
    private val context: Context,
    private val scope: CoroutineScope,
    private val carApi: CarApiRepository,
    private val driveStorage: DriveStorageRepository,
    private val dataStore: DataStoreRepository,
) : RecorderRepository {
    private companion object {
        private const val ALIAS_FRONT = "front"
        private const val ALIAS_BACK = "back"
        private const val ALIAS_LEFT = "left"
        private const val ALIAS_RIGHT = "right"

        private const val FILE_ALIAS_FRONT = "F"
        private const val FILE_ALIAS_BACK = "B"
        private const val FILE_ALIAS_LEFT = "L"
        private const val FILE_ALIAS_RIGHT = "R"

        private const val FILE_EXTENSION_AVC = "h264"
        private const val FILE_EXTENSION_TS = "ts"
        private const val VIDEO_MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val KEY_FRAME_INTERVAL_SECONDS = 2 // TODO TEST, base value 1
        private const val ENCODER_DEQUEUE_TIMEOUT_US = 50_000L
        private const val WRITER_IO_BUFFER_BYTES = 256 * 1_024 // 64 * 1_024 -> stable
        private const val SYNC_COMPLETED_SEGMENTS_TO_DISK = false // A dangerous synchronous parameter
        private const val ROLLOVER_STAGGER_STEP_MS = 140L
        private const val MAX_ROLLOVER_STAGGER_MS = 1_500L

        private const val CAMERA_THREAD_NAME = "cam-recorder-thread"
        private const val CAMERA_THREAD_JOIN_TIMEOUT_MS = 1_000L
        private const val FIRST_KEY_FRAME_TIMEOUT_MS = 5_000L
        private const val GL_BRIDGE_THREAD_PREFIX = "cam-recorder-gl"

        private const val CAMERA_CONFIG_DEBOUNCE_MS = 200L
        private const val MIN_SEGMENT_DURATION_MS = 1_000L

        // Keeps encoder size strictly at preferred-or-smaller presets
        private const val ALLOW_CAMERA_SIZE_FALLBACK = false

        private val PREFERRED_FRAME_RATES = intArrayOf(
            // 30,
            // 24,
            20,
            15
        )

        private val PREFERRED_VIDEO_SIZES = listOf(
            PreferredVideoSize(width = 1_280, height = 800),
            PreferredVideoSize(width = 1_280, height = 720),
            PreferredVideoSize(width = 1_024, height = 640),
            PreferredVideoSize(width = 1_024, height = 576),
            PreferredVideoSize(width = 960, height = 540),
            PreferredVideoSize(width = 854, height = 480),
            PreferredVideoSize(width = 640, height = 480)
        )

        private val FILE_DATE_DIRECTORY_FORMATTER = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("d MMMM yyyy", Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                }
            }
        }

        private val FILE_SEGMENT_TIMESTAMP_FORMATTER = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("HH-mm-ss_dd.MM.yy", Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                }
            }
        }

        private val FILE_SEGMENT_TIMESTAMP_PARSER = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("HH-mm-ss_dd.MM.yy", Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                    isLenient = false
                }
            }
        }

        private val LEGACY_FILE_SEGMENT_TIMESTAMP_PARSER = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("yy.MM.dd_HH-mm-ss", Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                    isLenient = false
                }
            }
        }
    }

    private val state = MutableStateFlow(RecordingLauncherState())
    private val started = AtomicBoolean(false)
    private val controlMutex = Mutex()

    @Volatile
    private var desiredRecording = false

    @Volatile
    private var activeEngine: MultiCameraRecordingEngine? = null

    @Volatile
    private var activeConfigSnapshot = RecorderConfigSnapshot()

    private val cameraManager by lazy(LazyThreadSafetyMode.NONE) {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val storageManager by lazy(LazyThreadSafetyMode.NONE) {
        context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    }

    private val videoEncoderInfos by lazy(LazyThreadSafetyMode.NONE) {
        queryVideoEncoderInfos()
    }

    private val RecordingLauncherState.canRecord
        get() = enabled && (ignition || forceStart) && driveConnected

    override fun watchdog() {
        if (!started.compareAndSet(false, true)) return

        scope.launch {
            handlePrefs()
            handleRecording()
            handleRecorderConfigs()
            handleIgnition()
            handleDriveStorage()
        }
    }

    private fun CoroutineScope.handleRecording() = launch(Dispatchers.IO) {
        state.asStateFlow().map { it.canRecord }.distinctUntilChanged().collect { enableRec ->
            desiredRecording = enableRec
            reconcileRecordingState()
        }
    }

    @OptIn(FlowPreview::class)
    private fun CoroutineScope.handleRecorderConfigs() = launch(Dispatchers.IO) {
        observeRecorderConfigSnapshot()
            .debounce(CAMERA_CONFIG_DEBOUNCE_MS)
            .distinctUntilChanged()
            .collect { snapshot ->
                val shouldRestart = snapshot != activeConfigSnapshot
                activeConfigSnapshot = snapshot

                if (shouldRestart) {
                    Timber.i("Camera config snapshot changed, restarting recorder engine")
                    reconcileRecordingState(forceRestart = true)
                }
            }
    }

    private suspend fun reconcileRecordingState(forceRestart: Boolean = false) = controlMutex.withLock {
        if (!desiredRecording) {
            activeEngine?.stop()
            activeEngine = null
            return
        }

        if (!hasEnabledCameras(activeConfigSnapshot)) {
            activeEngine?.stop()
            activeEngine = null
            Timber.i("Recorder engine will stay stopped because all cameras are disabled")
            return
        }

        if (forceRestart) {
            activeEngine?.stop()
            activeEngine = null
        }

        if (activeEngine != null) return

        val engine = MultiCameraRecordingEngine(
            cameraManager = cameraManager,
            driveStorage = driveStorage,
            shouldContinue = { desiredRecording },
            configSnapshot = activeConfigSnapshot,
        )

        try {
            engine.start()
            if (desiredRecording) {
                activeEngine = engine
            } else {
                engine.stop()
                activeEngine = null
            }
        } catch (cancelled: CancellationException) {
            engine.stop()
            activeEngine = null
            throw cancelled
        } catch (throwable: Throwable) {
            Timber.e(throwable, "Failed to start multi-camera recording engine")
            engine.stop()
            activeEngine = null
        }
    }

    private fun observeRecorderConfigSnapshot() = run {
        val (_, openCameraIds) = resolveLayoutAndIdsLikeReferenceProject(cameraManager)
        val defaultCameraConfigs = openCameraIds.associateWith { cameraId ->
            CameraDataStoreConfig(
                cameraId = cameraId,
                enabled = cameraId.defaultCameraEnable,
                fps = cameraId.defaultCameraFps,
                outputType = CAMERA_OUTPUT_TYPE_AVC,
            )
        }

        val cameraConfigsFlow = if (defaultCameraConfigs.isEmpty()) {
            flowOf(emptyMap())
        } else {
            dataStore.getCameraRecordingConfigsFlow(
                defaultConfigsByCameraId = defaultCameraConfigs,
                minFps = MIN_CAMERA_FPS,
                maxFps = MAX_CAMERA_FPS,
            )
        }

        val segmentDurationFlow = dataStore.getLongPrefFlow(LongPref.SegmentDurationMs)
            .map { segmentDurationMs ->
                segmentDurationMs.coerceAtLeast(MIN_SEGMENT_DURATION_MS)
            }
            .distinctUntilChanged()

        combine(cameraConfigsFlow, segmentDurationFlow) { cameraConfigs, segmentDurationMs ->
            buildRecorderConfigSnapshot(
                cameraConfigs = cameraConfigs,
                segmentDurationMs = segmentDurationMs,
            )
        }
    }

    private fun hasEnabledCameras(snapshot: RecorderConfigSnapshot): Boolean {
        val (_, openCameraIds) = resolveLayoutAndIdsLikeReferenceProject(cameraManager)
        return openCameraIds.any { cameraId ->
            snapshot.configFor(cameraId).enabled
        }
    }

    private fun CoroutineScope.handlePrefs() = launch(Dispatchers.IO) {
        dataStore.getBooleanPrefsFlow(
            BoolPref.EnableRecord,
            BoolPref.ForceRecord,
        ).distinctUntilChanged().collect { prefs ->
            state.emit(
                state.value.copy(
                    enabled = prefs[0],
                    forceStart = prefs[1],
                )
            )
        }
    }

    private fun CoroutineScope.handleIgnition() = launch(Dispatchers.IO) {
        carApi.ignitionStateFlow.collect { ignition ->
            state.emit(state.value.copy(ignition = ignition))
        }
    }

    private fun CoroutineScope.handleDriveStorage() = launch(Dispatchers.IO) {
        driveStorage.driveConnectedFlow.collect { driveConnected ->
            state.emit(state.value.copy(driveConnected = driveConnected))
        }
    }

    private inner class MultiCameraRecordingEngine(
        private val cameraManager: CameraManager,
        private val driveStorage: DriveStorageRepository,
        private val shouldContinue: () -> Boolean,
        private val configSnapshot: RecorderConfigSnapshot,
    ) {
        private val engineJob = SupervisorJob()
        private val engineScope = CoroutineScope(engineJob + Dispatchers.IO)
        private val cameraThread = HandlerThread(CAMERA_THREAD_NAME).apply { start() }
        private val cameraHandler = Handler(cameraThread.looper)
        private val stopMutex = Mutex()
        private val cleanupMutex = Mutex()
        private val controllers = mutableListOf<SingleCameraRecorder>()

        suspend fun start() {
            // logCameraCapabilities(cameraManager)

            val descriptors = discoverCameraDescriptors(
                cameraManager = cameraManager,
                configSnapshot = configSnapshot,
            )
            require(descriptors.isNotEmpty()) { "No recordable cameras found" }

            descriptors.forEachIndexed { index, descriptor ->
                val cameraDirectory = driveStorage.prepareCameraDirectory(descriptor.alias)
                val controller = SingleCameraRecorder(
                    cameraManager = cameraManager,
                    descriptor = descriptor,
                    runtimeConfig = configSnapshot.configFor(descriptor.cameraId),
                    cameraDirectory = cameraDirectory,
                    scope = engineScope,
                    cameraHandler = cameraHandler,
                    shouldContinue = shouldContinue,
                    rolloverOffsetMs = calculateRolloverOffsetMs(index),
                    segmentDurationMs = configSnapshot.segmentDurationMs,
                )

                try {
                    controller.start()
                    controllers += controller
                } catch (throwable: Throwable) {
                    Timber.e(throwable, "Failed to start recorder for cameraId=%s", descriptor.cameraId)
                    controller.stop()
                }
            }

            require(controllers.isNotEmpty()) { "No camera recorder started successfully" }

            engineScope.launch {
                runStorageMaintenanceLoop()
            }
        }

        suspend fun stop() = stopMutex.withLock {
            controllers.asReversed().forEach { controller ->
                runCatching { controller.stop() }
                    .onFailure { Timber.w(it, "Failed to stop camera controller") }
            }
            controllers.clear()

            engineJob.cancelAndJoin()
            cameraThread.quitSafely()
            runCatching { cameraThread.join(CAMERA_THREAD_JOIN_TIMEOUT_MS) }
                .onFailure { Timber.w(it, "Failed to join camera thread") }
        }

        private fun calculateRolloverOffsetMs(index: Int): Long {
            val staggerMs = index.coerceAtLeast(0).toLong() * ROLLOVER_STAGGER_STEP_MS
            return minOf(staggerMs, MAX_ROLLOVER_STAGGER_MS)
        }

        private suspend fun runStorageMaintenanceLoop() {
            delay(STORAGE_CHECK_INITIAL_DELAY_MS)

            while (engineScope.isActive && shouldContinue()) {
                runCatching {
                    cleanupIfRequired()
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Timber.w(throwable, "Storage maintenance failed")
                }

                delay(STORAGE_CHECK_INTERVAL_MS)
            }
        }

        private suspend fun cleanupIfRequired() = cleanupMutex.withLock {
            val driveRoot = driveStorage.getRemovableDriveRootOrNull() ?: return
            val totalSpace = driveRoot.totalSpace
            if (totalSpace <= 0L) return

            val freeBytes = driveRoot.getAvailableBytes()
            val freeRatio = freeBytes.toDouble() / totalSpace.toDouble()
            if (freeRatio >= MIN_FREE_SPACE_RATIO) return

            val recordsRoot = File(driveRoot, RECORDS_ROOT_DIRECTORY_NAME)
            if (!recordsRoot.exists() || !recordsRoot.isDirectory) return

            val targetFreeBytes = (totalSpace * TARGET_FREE_SPACE_RATIO).toLong()
            val protectedFiles = controllers.mapNotNullTo(hashSetOf()) { it.getCurrentOutputFileOrNull() }
            val groups = recordsRoot.listSegmentGroupsOrdered(protectedFiles)
            if (groups.isEmpty()) return

            for (group in groups) {
                if (!engineScope.isActive || !shouldContinue()) break
                if (driveRoot.getAvailableBytes() >= targetFreeBytes) break

                group.files.forEach { file ->
                    val parentDirectory = file.parentFile
                    val deleted = runCatching { file.delete() }
                        .onFailure { Timber.w(it, "Failed to delete old record file: %s", file.absolutePath) }
                        .getOrDefault(false)

                    if (deleted && parentDirectory != null) {
                        parentDirectory.deleteEmptyDirectoriesUpwards(stopAt = recordsRoot)
                    }
                }
            }
        }
    }

    private inner class SingleCameraRecorder(
        private val cameraManager: CameraManager,
        private val descriptor: CameraDescriptor,
        private val runtimeConfig: CameraRuntimeConfig,
        private val cameraDirectory: File,
        private val scope: CoroutineScope,
        private val cameraHandler: Handler,
        private val shouldContinue: () -> Boolean,
        private val rolloverOffsetMs: Long,
        private val segmentDurationMs: Long,
    ) {
        private val controlMutex = Mutex()
        private val segmentDurationSeconds = TimeUnit.MILLISECONDS.toSeconds(
            segmentDurationMs.coerceAtLeast(MIN_SEGMENT_DURATION_MS)
        )
        private val fileNameFps = descriptor.frameRate.coerceIn(MIN_CAMERA_FPS, MAX_CAMERA_FPS)

        @Volatile
        private var cameraDevice: CameraDevice? = null

        @Volatile
        private var captureSession: CameraCaptureSession? = null

        @Volatile
        private var encoder: MediaCodec? = null

        @Volatile
        private var encoderInputSurface: Surface? = null

        @Volatile
        private var inputSurfaceBridge: GlInputSurfaceBridge? = null

        @Volatile
        private var firstKeyFrameSignal: CompletableDeferred<Unit>? = null

        @Volatile
        private var segmentWriter: SegmentedVideoFileWriter? = null

        @Volatile
        private var segmentJob: Job? = null

        @Volatile
        private var drainJob: Job? = null

        @Volatile
        private var stopping = false

        suspend fun start() {
            controlMutex.withLock {
                if (stopping) return

                var lastError: Throwable? = null
                val inputModes = listOf(CameraInputMode.GL_BRIDGE) // + CameraInputMode.DIRECT

                repeat(RECORDER_START_ATTEMPTS) { attempt ->
                    inputModes.forEach { inputMode ->
                        try {
                            startPipeline(inputMode)
                            return
                        } catch (cancelled: CancellationException) {
                            cancelBackgroundJobs()
                            cleanupAfterStop(deleteActiveFile = true)
                            throw cancelled
                        } catch (throwable: Throwable) {
                            lastError = throwable
                            Timber.w(
                                throwable,
                                "Failed to start codec pipeline for cameraId=%s attempt=%s mode=%s",
                                descriptor.cameraId,
                                attempt + 1,
                                inputMode,
                            )
                            cancelBackgroundJobs()
                            cleanupAfterStop(deleteActiveFile = true)
                        }
                    }

                    if (attempt < RECORDER_START_ATTEMPTS - 1) {
                        delay(RECORDER_RETRY_DELAY_MS)
                    }
                }

                throw lastError ?: error("Unable to start codec pipeline")
            }
        }

        suspend fun stop() = controlMutex.withLock {
            if (stopping) return
            stopping = true

            val activeSegmentJob = segmentJob
            segmentJob = null
            activeSegmentJob?.cancelAndJoin()

            closeSessionAndCamera()
            runCatching { encoder?.signalEndOfInputStream() }

            val activeDrainJob = drainJob
            drainJob = null
            val drainCompleted = withTimeoutOrNull(300L) {
                activeDrainJob?.join()
                true
            } ?: false
            if (!drainCompleted) {
                activeDrainJob?.cancelAndJoin()
            }

            cleanupAfterStop(deleteActiveFile = false)
        }

        fun getCurrentOutputFileOrNull(): File? = segmentWriter?.getCurrentFileOrNull()

        private suspend fun cancelBackgroundJobs() {
            val activeSegmentJob = segmentJob
            segmentJob = null
            activeSegmentJob?.cancelAndJoin()

            val activeDrainJob = drainJob
            drainJob = null
            activeDrainJob?.cancelAndJoin()
        }

        private suspend fun startPipeline(inputMode: CameraInputMode) {
            val preparedEncoder = buildVideoEncoder(descriptor)
            val preparedWriter = createSegmentWriter(preparedEncoder.config.frameRate)
            var preparedSurface: Surface? = null
            var preparedBridge: GlInputSurfaceBridge? = null
            var published = false

            try {
                val inputSurface = preparedEncoder.codec.createInputSurface()
                preparedSurface = inputSurface
                preparedBridge = if (inputMode == CameraInputMode.GL_BRIDGE) {
                    GlInputSurfaceBridge(
                        cameraWidth = descriptor.videoSize.width,
                        cameraHeight = descriptor.videoSize.height,
                        encoderWidth = preparedEncoder.config.width,
                        encoderHeight = preparedEncoder.config.height,
                        encoderInputSurface = inputSurface,
                        threadName = "$GL_BRIDGE_THREAD_PREFIX-${descriptor.cameraId}",
                        targetFrameRate = preparedEncoder.config.frameRate,
                    )
                } else {
                    null
                }
                val targetSurface = preparedBridge?.inputSurface ?: inputSurface
                val keyFrameSignal = CompletableDeferred<Unit>()

                preparedWriter.requestInitialSegment(createSegmentFile())
                preparedEncoder.codec.start()

                segmentWriter = preparedWriter
                encoder = preparedEncoder.codec
                encoderInputSurface = preparedSurface
                inputSurfaceBridge = preparedBridge
                firstKeyFrameSignal = keyFrameSignal
                published = true

                drainJob = scope.launch(Dispatchers.IO) {
                    drainEncoderLoop(preparedEncoder.codec, preparedWriter, keyFrameSignal)
                }

                cameraDevice = openCameraDevice(descriptor.cameraId)
                captureSession = cameraDevice?.createRecordingSession(listOf(targetSurface), cameraHandler)
                startRepeatingRequest(targetSurface)
                requestKeyFrame(preparedEncoder.codec)
                awaitFirstKeyFrame(keyFrameSignal)

                segmentJob = scope.launch(Dispatchers.IO) {
                    runSegmentLoop(preparedEncoder.codec, preparedWriter)
                }
            } catch (throwable: Throwable) {
                if (!published) {
                    runCatching { preparedBridge?.release() }
                    runCatching { preparedSurface?.release() }
                    runCatching { preparedEncoder.codec.stop() }
                    runCatching { preparedEncoder.codec.release() }
                    runCatching { preparedWriter.close(deleteActiveFile = true) }
                }
                throw throwable
            }
        }

        private suspend fun runSegmentLoop(activeEncoder: MediaCodec, activeWriter: SegmentedVideoFileWriter) {
            var segmentStartedAt = SystemClock.elapsedRealtime()
            var firstRollover = true

            while (scope.isActive && shouldContinue() && !stopping) {
                val targetSegmentDurationMs = if (firstRollover) {
                    segmentDurationMs + rolloverOffsetMs
                } else {
                    segmentDurationMs
                }
                val deadline = segmentStartedAt + targetSegmentDurationMs
                val waitMs = deadline - SystemClock.elapsedRealtime()
                if (waitMs > 0L) {
                    delay(waitMs)
                }

                if (!scope.isActive || !shouldContinue() || stopping) break

                activeWriter.requestRollover(createSegmentFile())
                requestKeyFrame(activeEncoder)
                segmentStartedAt = SystemClock.elapsedRealtime()
                firstRollover = false
            }
        }

        private suspend fun awaitFirstKeyFrame(signal: CompletableDeferred<Unit>) {
            try {
                withTimeout(FIRST_KEY_FRAME_TIMEOUT_MS) {
                    signal.await()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                throw IOException(
                    "Timed out waiting for first key frame for ${descriptor.cameraId}",
                    throwable,
                )
            }
        }

        private suspend fun drainEncoderLoop(
            activeEncoder: MediaCodec,
            activeWriter: SegmentedVideoFileWriter,
            keyFrameSignal: CompletableDeferred<Unit>,
        ) {
            val bufferInfo = MediaCodec.BufferInfo()
            var emptyPollsAfterStop = 0

            while (scope.isActive) {
                val index = try {
                    activeEncoder.dequeueOutputBuffer(bufferInfo, ENCODER_DEQUEUE_TIMEOUT_US)
                } catch (throwable: Throwable) {
                    Timber.w(throwable, "Encoder dequeue failed for cameraId=%s", descriptor.cameraId)
                    if (!keyFrameSignal.isCompleted) {
                        keyFrameSignal.completeExceptionally(throwable)
                    }
                    break
                }

                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (stopping) {
                            emptyPollsAfterStop += 1
                            if (emptyPollsAfterStop >= 3) {
                                break
                            }
                            delay(10)
                        }
                    }

                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        emptyPollsAfterStop = 0
                        activeWriter.onOutputFormatChanged(activeEncoder.outputFormat)
                    }

                    index >= 0 -> {
                        emptyPollsAfterStop = 0
                        val outputBuffer = activeEncoder.getOutputBuffer(index)
                        if (outputBuffer != null) {
                            runCatching {
                                activeWriter.writeSample(outputBuffer, bufferInfo)
                                val isCodecConfig = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                                val isKeyFrame = bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                                if (!isCodecConfig && isKeyFrame &&
                                    bufferInfo.size > 0 && !keyFrameSignal.isCompleted
                                ) {
                                    keyFrameSignal.complete(Unit)
                                }
                            }.onFailure { throwable ->
                                Timber.w(
                                    throwable,
                                    "Failed to write encoded sample for cameraId=%s",
                                    descriptor.cameraId,
                                )
                            }
                        }

                        val endOfStream = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        activeEncoder.releaseOutputBuffer(index, false)
                        if (endOfStream) break
                    }
                }
            }

            if (!keyFrameSignal.isCompleted) {
                keyFrameSignal.completeExceptionally(IOException("Encoder stopped before first key frame"))
            }
        }

        private fun buildVideoEncoder(descriptor: CameraDescriptor): PreparedEncoder {
            val configs = buildEncoderConfigs(descriptor)
            var lastError: Throwable? = null

            configs.forEach { config ->
                var codec: MediaCodec? = null

                try {
                    codec = MediaCodec.createByCodecName(config.codecName)
                    val format = MediaFormat.createVideoFormat(
                        VIDEO_MIME_TYPE,
                        config.width,
                        config.height,
                    ).apply {
                        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                        setInteger(MediaFormat.KEY_BIT_RATE, config.bitRate)
                        setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRate)
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, KEY_FRAME_INTERVAL_SECONDS)
                    }

                    codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    Timber.i(
                        "Using encoder=%s cameraId=%s size=%sx%s fps=%s bitrate=%s",
                        config.codecName,
                        descriptor.cameraId,
                        config.width,
                        config.height,
                        config.frameRate,
                        config.bitRate,
                    )
                    return PreparedEncoder(
                        codec = codec,
                        config = config,
                    )
                } catch (throwable: Throwable) {
                    lastError = throwable
                    Timber.w(
                        throwable,
                        "Encoder rejected codec=%s cameraId=%s size=%sx%s fps=%s bitrate=%s",
                        config.codecName,
                        descriptor.cameraId,
                        config.width,
                        config.height,
                        config.frameRate,
                        config.bitRate,
                    )
                    runCatching { codec?.release() }
                }
            }

            throw lastError ?: error(
                "No compatible AVC encoder configuration found for cameraId=${descriptor.cameraId}",
            )
        }

        private fun requestKeyFrame(activeEncoder: MediaCodec) {
            runCatching {
                activeEncoder.setParameters(
                    Bundle().apply {
                        putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                    }
                )
            }.onFailure {
                Timber.w(it, "Failed to request key frame for cameraId=%s", descriptor.cameraId)
            }
        }

        private fun cleanupAfterStop(deleteActiveFile: Boolean) {
            val activeSession = captureSession
            captureSession = null
            if (activeSession != null) {
                runCatching { activeSession.stopRepeating() }
                runCatching { activeSession.abortCaptures() }
                runCatching { activeSession.close() }
            }

            val activeCamera = cameraDevice
            cameraDevice = null
            runCatching { activeCamera?.close() }

            val activeBridge = inputSurfaceBridge
            inputSurfaceBridge = null
            runCatching { activeBridge?.release() }

            firstKeyFrameSignal?.cancel()
            firstKeyFrameSignal = null

            val activeEncoder = encoder
            encoder = null
            runCatching { activeEncoder?.stop() }
            runCatching { activeEncoder?.release() }

            val activeSurface = encoderInputSurface
            encoderInputSurface = null
            runCatching { activeSurface?.release() }

            val activeWriter = segmentWriter
            segmentWriter = null
            runCatching { activeWriter?.close(deleteActiveFile = deleteActiveFile) }
        }

        private fun createSegmentWriter(actualFrameRate: Int): SegmentedVideoFileWriter {
            return when (runtimeConfig.outputMode) {
                SegmentOutputMode.RAW_AVC -> AvcSegmentedFileWriter(
                    brokenFileDeleteThresholdBytes = BROKEN_FILE_DELETE_THRESHOLD_BYTES,
                    ioBufferBytes = WRITER_IO_BUFFER_BYTES,
                    syncOnSegmentClose = SYNC_COMPLETED_SEGMENTS_TO_DISK,
                    declaredFrameRate = actualFrameRate,
                )

                SegmentOutputMode.MPEG_TS -> TsSegmentedFileWriter(
                    brokenFileDeleteThresholdBytes = BROKEN_FILE_DELETE_THRESHOLD_BYTES,
                    ioBufferBytes = WRITER_IO_BUFFER_BYTES,
                    syncOnSegmentClose = SYNC_COMPLETED_SEGMENTS_TO_DISK,
                )
            }
        }

        private fun segmentFileExtension(): String {
            return recordingFileExtension(runtimeConfig.outputMode)
        }

        private fun createSegmentFile(): File {
            val now = Date()
            val dateDirectoryName = FILE_DATE_DIRECTORY_FORMATTER.get()?.format(now)
                ?: System.currentTimeMillis().toString()
            val fileTimestamp = FILE_SEGMENT_TIMESTAMP_FORMATTER.get()?.format(now)
                ?: System.currentTimeMillis().toString()
            val dateDirectory = File(cameraDirectory, dateDirectoryName)
            val aliasDirectory = File(dateDirectory, descriptor.alias)
            return File(
                aliasDirectory,
                fileTimestamp +
                    "_$segmentDurationSeconds" +
                    "_$fileNameFps" +
                    "_${descriptor.fileAlias}" +
                    ".${segmentFileExtension()}"
            )
        }

        private fun startRepeatingRequest(targetSurface: Surface) {
            val camera = cameraDevice ?: error("Camera device is not available")
            val session = captureSession ?: error("Capture session is not available")

            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(targetSurface)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, descriptor.fpsRange)
            }.build()

            runCatching {
                session.setRepeatingRequest(request, null, cameraHandler)
            }.getOrElse { throwable ->
                throw IOException("Failed to start repeating request for ${descriptor.cameraId}", throwable)
            }
        }

        private fun closeSessionAndCamera() {
            val activeSession = captureSession
            captureSession = null
            if (activeSession != null) {
                runCatching { activeSession.stopRepeating() }
                runCatching { activeSession.abortCaptures() }
                runCatching { activeSession.close() }
            }

            val activeCamera = cameraDevice
            cameraDevice = null
            runCatching { activeCamera?.close() }
        }

        private suspend fun openCameraDevice(cameraId: String): CameraDevice =
            suspendCancellableCoroutine { continuation ->
                val callback = object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        if (continuation.isActive) {
                            continuation.resume(camera)
                        } else {
                            camera.close()
                        }
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        if (continuation.isActive) {
                            continuation.resumeWithException(IOException("Camera disconnected: $cameraId"))
                        } else {
                            scope.launch(Dispatchers.IO) {
                                stop()
                            }
                        }
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        if (continuation.isActive) {
                            continuation.resumeWithException(IOException("Camera error $error for $cameraId"))
                        } else {
                            scope.launch(Dispatchers.IO) {
                                stop()
                            }
                        }
                    }
                }

                try {
                    cameraManager.openCamera(cameraId, callback, cameraHandler)
                } catch (throwable: SecurityException) {
                    continuation.resumeWithException(throwable)
                } catch (throwable: Throwable) {
                    continuation.resumeWithException(throwable)
                }
            }
    }

    private fun buildRecorderConfigSnapshot(
        cameraConfigs: Map<String, CameraDataStoreConfig>,
        segmentDurationMs: Long,
    ): RecorderConfigSnapshot {
        if (cameraConfigs.isEmpty()) {
            return RecorderConfigSnapshot(segmentDurationMs = segmentDurationMs)
        }

        val runtimeConfigs = cameraConfigs.mapValues { (_, config) ->
            CameraRuntimeConfig(
                cameraId = config.cameraId,
                enabled = config.enabled,
                fps = config.fps,
                outputMode = outputModeFromPreference(config.outputType),
            )
        }

        return RecorderConfigSnapshot(
            cameraConfigs = runtimeConfigs,
            segmentDurationMs = segmentDurationMs,
        )
    }

    private fun outputModeFromPreference(outputType: Int) = when (outputType) {
        CAMERA_OUTPUT_TYPE_TS -> SegmentOutputMode.MPEG_TS
        else -> SegmentOutputMode.RAW_AVC
    }

    private fun discoverCameraDescriptors(
        cameraManager: CameraManager,
        configSnapshot: RecorderConfigSnapshot,
    ): List<CameraDescriptor> {
        val (layout, openCameraIds) = resolveLayoutAndIdsLikeReferenceProject(cameraManager)
        if (openCameraIds.isEmpty()) return emptyList()

        val aliasByCameraId = linkedMapOf<String, AliasDescriptor>().apply {
            layout.leftId?.let { putIfAbsent(it, AliasDescriptor(ALIAS_LEFT, FILE_ALIAS_LEFT)) }
            layout.rightId?.let { putIfAbsent(it, AliasDescriptor(ALIAS_RIGHT, FILE_ALIAS_RIGHT)) }
            layout.frontId?.let { putIfAbsent(it, AliasDescriptor(ALIAS_FRONT, FILE_ALIAS_FRONT)) }
            layout.backId?.let { putIfAbsent(it, AliasDescriptor(ALIAS_BACK, FILE_ALIAS_BACK)) }
        }

        Timber.i(
            "Resolved camera layout front=%s back=%s left=%s right=%s openIds=%s",
            layout.frontId,
            layout.backId,
            layout.leftId,
            layout.rightId,
            openCameraIds,
        )

        return openCameraIds.mapNotNull { cameraId ->
            val runtimeConfig = configSnapshot.configFor(cameraId)
            if (!runtimeConfig.enabled) {
                Timber.i("Camera %s is disabled by DataStore config", cameraId)
                return@mapNotNull null
            }

            val alias = aliasByCameraId[cameraId] ?: return@mapNotNull null
            val characteristics = runCatching { cameraManager.getCameraCharacteristics(cameraId) }
                .getOrElse {
                    Timber.w(it, "Unable to read camera characteristics for cameraId=%s", cameraId)
                    return@mapNotNull null
                }

            val configurationMap =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: return@mapNotNull null

            val recordingProfile = resolveRecordingProfile(
                characteristics = characteristics,
                configurationMap = configurationMap,
                requestedFrameRate = runtimeConfig.fps,
            ) ?: return@mapNotNull null

            CameraDescriptor(
                cameraId = cameraId,
                alias = alias.directoryAlias,
                fileAlias = alias.fileAlias,
                videoSize = recordingProfile.videoSize,
                fpsRange = recordingProfile.fpsSelection.range,
                frameRate = recordingProfile.fpsSelection.frameRate,
                videoBitrate = chooseVideoBitrate(recordingProfile.videoSize),
                outputFormat = 0,
                fileExtension = recordingFileExtension(runtimeConfig.outputMode),
            )
        }
    }

    private fun resolveLayoutAndIdsLikeReferenceProject(
        cameraManager: CameraManager
    ): Pair<LayoutMapping, List<String>> {
        val ids = runCatching { cameraManager.cameraIdList.toList() }
            .getOrElse {
                Timber.w(it, "Unable to read cameraIdList")
                return LayoutMapping(null, null, null, null) to emptyList()
            }
        if (ids.isEmpty()) {
            return LayoutMapping(null, null, null, null) to emptyList()
        }

        val desiredCount = minOf(MAX_CAMERA_COUNT, ids.size)
        val selectedIds = selectCameraIdsLikeReferenceProject(cameraManager, desiredCount)
        if (selectedIds.isEmpty()) {
            return LayoutMapping(null, null, null, null) to emptyList()
        }

        val mappingIds = selectedIds.take(MAX_CAMERA_COUNT)
        val layout = mapCameraIdsLikeReferenceProject(mappingIds)
        val openCameraIds = linkedSetOf<String>().apply {
            if (!layout.leftId.isNullOrEmpty()) add(layout.leftId)
            if (!layout.rightId.isNullOrEmpty()) add(layout.rightId)
            if (!layout.frontId.isNullOrEmpty()) add(layout.frontId)
            if (!layout.backId.isNullOrEmpty()) add(layout.backId)
        }.toList()

        return layout to openCameraIds
    }

    private fun selectCameraIdsLikeReferenceProject(cameraManager: CameraManager, desiredCount: Int): List<String> {
        val count = desiredCount.coerceAtLeast(0)
        if (count == 0) return emptyList()

        val allIds = runCatching { cameraManager.cameraIdList.toList() }
            .getOrElse {
                Timber.w(it, "Unable to read cameraIdList")
                return emptyList()
            }
        if (allIds.isEmpty()) return emptyList()

        val usable = allIds.filter { cameraId ->
            isCameraUsableLikeReferenceProject(cameraManager, cameraId)
        }
        val base = usable.ifEmpty { allIds.take(1) }

        if (base.size >= count) return base.take(count)

        val out = ArrayList<String>(count)
        out.addAll(base)
        while (out.size < count) {
            out.add(base.first())
        }
        return out
    }

    private fun isCameraUsableLikeReferenceProject(cameraManager: CameraManager, cameraId: String): Boolean {
        val characteristics = try {
            cameraManager.getCameraCharacteristics(cameraId)
        } catch (throwable: Throwable) {
            Timber.w(throwable, "Unable to read camera characteristics for cameraId=%s", cameraId)
            return false
        }

        val configurationMap =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return false

        val privateSizes = try {
            configurationMap.getOutputSizes(ImageFormat.PRIVATE)
        } catch (_: Throwable) {
            null
        }
        if (!privateSizes.isNullOrEmpty()) return true

        val surfaceSizes = try {
            configurationMap.getOutputSizes(Surface::class.java)
        } catch (_: Throwable) {
            null
        }
        if (!surfaceSizes.isNullOrEmpty()) return true

        val mediaRecorderSizes = try {
            configurationMap.getOutputSizes(MediaRecorder::class.java)
        } catch (_: Throwable) {
            null
        }
        return !mediaRecorderSizes.isNullOrEmpty()
    }

    private fun mapCameraIdsLikeReferenceProject(ids: List<String>): LayoutMapping {
        if (ids.isEmpty()) return LayoutMapping(null, null, null, null)

        return when {
            ids.size >= 4 -> LayoutMapping(
                frontId = ids[2],
                backId = ids[3],
                leftId = ids[0],
                rightId = ids[1],
            )

            ids.size == 3 -> LayoutMapping(
                frontId = ids[0],
                backId = ids[2],
                leftId = ids[0],
                rightId = ids[2],
            )

            ids.size >= 2 -> LayoutMapping(
                frontId = ids[0],
                backId = ids[1],
                leftId = ids[0],
                rightId = ids[1],
            )

            else -> {
                val id = ids[0]
                LayoutMapping(
                    frontId = id,
                    backId = id,
                    leftId = id,
                    rightId = id,
                )
            }
        }
    }

    private fun resolveRecordingProfile(
        characteristics: CameraCharacteristics,
        configurationMap: StreamConfigurationMap,
        requestedFrameRate: Int = DEFAULT_CAMERA_FPS,
    ): RecordingProfile? {
        val sizes = collectVideoSizes(configurationMap)
        if (sizes.isEmpty()) return null

        val orderedSizes = orderVideoSizes(sizes)
        if (orderedSizes.isEmpty()) return null

        val ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.filterNotNull()
            .orEmpty()

        val preferredFrameRates = buildPreferredFrameRates(requestedFrameRate)
        preferredFrameRates.forEach { preferredFps ->
            val fpsSelection = choosePreferredFpsSelection(ranges, preferredFps) ?: return@forEach
            val matchedSize = orderedSizes.firstOrNull { size ->
                hasCompatibleEncoderConfig(size, fpsSelection.frameRate)
            }
            if (matchedSize != null) {
                return RecordingProfile(
                    videoSize = matchedSize,
                    fpsSelection = fpsSelection,
                )
            }
        }

        val fallbackFpsSelection = chooseFallbackFpsSelection(ranges)
        val fallbackSize = orderedSizes.firstOrNull { size ->
            hasCompatibleEncoderConfig(size, fallbackFpsSelection.frameRate)
        } ?: return null

        return RecordingProfile(
            videoSize = fallbackSize,
            fpsSelection = fallbackFpsSelection,
        )
    }

    private fun collectVideoSizes(configurationMap: StreamConfigurationMap): List<Size> {
        val sizes = buildList {
            addAll(runCatching { configurationMap.getOutputSizes(ImageFormat.PRIVATE) }.getOrNull().orEmpty().asList())
            addAll(runCatching { configurationMap.getOutputSizes(Surface::class.java) }.getOrNull().orEmpty().asList())
            addAll(
                runCatching { configurationMap.getOutputSizes(MediaRecorder::class.java) }.getOrNull().orEmpty()
                    .asList()
            )
        }.distinctBy { it.width to it.height }

        if (sizes.isEmpty()) return emptyList()

        return sizes
            .filter { it.width <= MAX_VIDEO_WIDTH && it.height <= MAX_VIDEO_HEIGHT }
            .ifEmpty { sizes }
    }

    private fun orderVideoSizes(sizes: List<Size>): List<Size> {
        if (sizes.isEmpty()) return emptyList()

        val preferredSizes = PREFERRED_VIDEO_SIZES.mapNotNull { preferred ->
            sizes.firstOrNull { size ->
                size.width == preferred.width && size.height == preferred.height
            }
        }

        val fallbackSizes = sizes
            .filterNot { candidate ->
                preferredSizes.any { preferred ->
                    preferred.width == candidate.width && preferred.height == candidate.height
                }
            }
            .sortedByDescending { it.width.toLong() * it.height.toLong() }

        return buildList {
            addAll(preferredSizes)
            addAll(fallbackSizes)
        }.distinctBy { it.width to it.height }
    }

    private fun buildPreferredFrameRates(requestedFrameRate: Int) = buildList {
        add(requestedFrameRate.coerceIn(MIN_CAMERA_FPS, MAX_CAMERA_FPS))
        add(DEFAULT_CAMERA_FPS)
        addAll(PREFERRED_FRAME_RATES.asIterable())
    }.distinct()

    private fun choosePreferredFpsSelection(ranges: List<Range<Int>>, preferredFps: Int): FpsSelection? {
        if (ranges.isEmpty()) return null

        ranges.firstOrNull { it.lower == preferredFps && it.upper == preferredFps }?.let { range ->
            return FpsSelection(
                range = range,
                frameRate = preferredFps,
            )
        }

        ranges
            .filter { it.upper == preferredFps && it.lower <= preferredFps }
            .minByOrNull { range -> preferredFps - range.lower }
            ?.let { range ->
                return FpsSelection(
                    range = range,
                    frameRate = preferredFps,
                )
            }

        ranges
            .filter { it.lower <= preferredFps && it.upper >= preferredFps }
            .minByOrNull { range ->
                (range.upper - range.lower) * 1_000 + abs(range.upper - preferredFps)
            }
            ?.let { range ->
                return FpsSelection(
                    range = range,
                    frameRate = preferredFps,
                )
            }

        return null
    }

    private fun chooseFallbackFpsSelection(ranges: List<Range<Int>>): FpsSelection {
        if (ranges.isEmpty()) {
            return FpsSelection(
                range = Range(DEFAULT_CAMERA_FPS, DEFAULT_CAMERA_FPS),
                frameRate = DEFAULT_CAMERA_FPS,
            )
        }

        ranges.firstOrNull { it.lower == DEFAULT_CAMERA_FPS && it.upper == DEFAULT_CAMERA_FPS }?.let { range ->
            return FpsSelection(
                range = range,
                frameRate = DEFAULT_CAMERA_FPS,
            )
        }

        val fallbackRange = ranges.minByOrNull { abs(it.upper - DEFAULT_CAMERA_FPS) } ?: ranges.first()
        return FpsSelection(
            range = fallbackRange,
            frameRate = fallbackRange.upper.coerceAtMost(DEFAULT_CAMERA_FPS),
        )
    }

    private fun hasCompatibleEncoderConfig(cameraSize: Size, frameRate: Int) = videoEncoderInfos.any { codecInfo ->
        val capabilities = runCatching {
            codecInfo.getCapabilitiesForType(VIDEO_MIME_TYPE)
        }.getOrNull() ?: return@any false

        val videoCapabilities = capabilities.videoCapabilities ?: return@any false
        val widthAlignment = maxOf(1, videoCapabilities.widthAlignment)
        val heightAlignment = maxOf(1, videoCapabilities.heightAlignment)
        val alignedSize = alignSizeDown(
            size = cameraSize,
            widthAlignment = widthAlignment,
            heightAlignment = heightAlignment,
        ) ?: return@any false

        videoCapabilities.areSizeAndRateSupported(
            alignedSize.width,
            alignedSize.height,
            frameRate.toDouble(),
        )
    }

    private fun chooseVideoBitrate(videoSize: Size): Int {
        val pixels = videoSize.width * videoSize.height
        return when {
            pixels >= PIXELS_720P -> VIDEO_BITRATE_720P
            pixels >= PIXELS_576P -> VIDEO_BITRATE_576P
            else -> VIDEO_BITRATE_480P
        }
    }

    private fun recordingFileExtension(outputMode: SegmentOutputMode) = when (outputMode) {
        SegmentOutputMode.RAW_AVC -> FILE_EXTENSION_AVC
        SegmentOutputMode.MPEG_TS -> FILE_EXTENSION_TS
    }

    private fun File.listSegmentGroupsOrdered(protectedFiles: Set<File>): List<SegmentGroup> {
        val files = walkTopDown()
            .maxDepth(3)
            .filter { it.isFile && it !in protectedFiles }
            .filter { it.segmentGroupKeyOrNull() != null }
            .toList()

        if (files.isEmpty()) return emptyList()

        return files
            .groupBy { file -> checkNotNull(file.segmentGroupKeyOrNull()) }
            .map { (key, groupFiles) -> SegmentGroup(key = key, files = groupFiles.sortedBy { it.name }) }
            .sortedBy { group ->
                group.files.asSequence()
                    .mapNotNull { it.segmentInstantOrNull()?.time }
                    .minOrNull()
                    ?: group.files.minOfOrNull { file ->
                        file.lastModified().takeIf { it > 0L } ?: Long.MAX_VALUE
                    }
                    ?: Long.MAX_VALUE
            }
    }

    private fun File.segmentInstantOrNull(): Date? {
        val groupKey = segmentGroupKeyOrNull() ?: return null
        return when (groupKey.getOrNull(2)) {
            '-' -> FILE_SEGMENT_TIMESTAMP_PARSER.get()?.parse(groupKey)
            '.' -> LEGACY_FILE_SEGMENT_TIMESTAMP_PARSER.get()?.parse(groupKey)
            else -> null
        }
    }

    private fun File.getAvailableBytes(): Long {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val allocatableBytes = runCatching {
                val uuid: UUID = storageManager.getUuidForPath(this)
                storageManager.getAllocatableBytes(uuid)
            }.getOrNull()

            if (allocatableBytes != null && allocatableBytes >= 0L) {
                return allocatableBytes
            }
        }

        return usableSpace
    }

    private fun buildEncoderConfigs(descriptor: CameraDescriptor): List<EncoderCandidateConfig> {
        val requestedSizes = buildRequestedEncoderSizes(descriptor.videoSize)
        val requestedFrameRates = listOf(descriptor.frameRate)

        val result = linkedSetOf<EncoderCandidateConfig>()
        for (codecInfo in videoEncoderInfos) {
            val capabilities = runCatching {
                codecInfo.getCapabilitiesForType(VIDEO_MIME_TYPE)
            }.getOrNull() ?: continue

            val videoCapabilities = capabilities.videoCapabilities ?: continue
            val widthAlignment = maxOf(1, videoCapabilities.widthAlignment)
            val heightAlignment = maxOf(1, videoCapabilities.heightAlignment)

            for (requestedSize in requestedSizes) {
                val alignedSize = alignSizeDown(
                    size = requestedSize,
                    widthAlignment = widthAlignment,
                    heightAlignment = heightAlignment,
                ) ?: continue

                val supportedFrameRate = requestedFrameRates.firstOrNull { requestedFrameRate ->
                    videoCapabilities.areSizeAndRateSupported(
                        alignedSize.width,
                        alignedSize.height,
                        requestedFrameRate.toDouble(),
                    )
                } ?: continue

                val targetBitrate = chooseVideoBitrate(alignedSize)
                val clampedBitrate = videoCapabilities.bitrateRange.clamp(targetBitrate)

                result += EncoderCandidateConfig(
                    codecName = codecInfo.name,
                    width = alignedSize.width,
                    height = alignedSize.height,
                    frameRate = supportedFrameRate,
                    bitRate = clampedBitrate,
                )
            }
        }

        return result.toList()
    }

    private fun queryVideoEncoderInfos() = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        .asSequence()
        .filter { it.isEncoder }
        .filter { codecInfo ->
            codecInfo.supportedTypes.any { type -> type.equals(VIDEO_MIME_TYPE, ignoreCase = true) }
        }
        .filter { codecInfo -> codecInfo.supportsSurfaceInput(VIDEO_MIME_TYPE) }
        .sortedWith(
            compareByDescending<MediaCodecInfo> { codecInfo ->
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && codecInfo.isHardwareAccelerated
            }.thenBy { codecInfo ->
                codecInfo.name.startsWith("OMX.google.", ignoreCase = true) ||
                    codecInfo.name.startsWith("c2.android.", ignoreCase = true)
            }.thenBy { codecInfo ->
                codecInfo.name
            }
        )
        .toList()

    private fun buildRequestedEncoderSizes(cameraSize: Size): List<Size> {
        val preferredSizes = PREFERRED_VIDEO_SIZES
            .asSequence()
            .map { Size(it.width, it.height) }
            .filter { preferred ->
                preferred.width <= cameraSize.width && preferred.height <= cameraSize.height
            }
            .toList()

        return buildList {
            add(cameraSize)

            if (ALLOW_CAMERA_SIZE_FALLBACK) {
                addAll(preferredSizes)
            }
        }.distinctBy { it.width to it.height }
    }

    // Collect cameras info
    override suspend fun getAvailableCameraInfos(): List<AvailableCameraInfo> = withContext(Dispatchers.IO) {
        readAvailableCameraInfos(cameraManager)
    }

    private fun readAvailableCameraInfos(cameraManager: CameraManager): List<AvailableCameraInfo> {
        val cameraIds = runCatching { cameraManager.cameraIdList.toList() }
            .getOrElse {
                Timber.w(it, "Unable to read cameraIdList for available camera info")
                return emptyList()
            }

        return cameraIds.mapNotNull { cameraId ->
            runCatching {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val configurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

                val orderedPreviewSizes = configurationMap?.let { orderVideoSizes(collectPreviewSizes(it)) }.orEmpty()
                val orderedVideoSizes = configurationMap?.let { orderVideoSizes(collectVideoSizes(it)) }.orEmpty()
                val recordingProfile = configurationMap?.let {
                    resolveRecordingProfile(
                        characteristics = characteristics,
                        configurationMap = it,
                        requestedFrameRate = DEFAULT_CAMERA_FPS,
                    )
                }
                val activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    ?.toList()
                    .orEmpty()

                val defaultVideoSize = recordingProfile?.videoSize?.toAvailableCameraSize()
                    ?: orderedVideoSizes.firstOrNull()?.toAvailableCameraSize()

                val defaultPreviewSize = recordingProfile?.videoSize
                    ?.takeIf { preferred ->
                        orderedPreviewSizes.any { it.width == preferred.width && it.height == preferred.height }
                    }
                    ?.toAvailableCameraSize()
                    ?: orderedPreviewSizes.firstOrNull()?.toAvailableCameraSize()
                    ?: defaultVideoSize

                AvailableCameraInfo(
                    cameraId = cameraId,
                    lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING),
                    sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION),
                    hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL),
                    isLogicalMultiCamera = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)
                    } else {
                        false
                    },
                    physicalCameraIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        characteristics.physicalCameraIds.toList()
                    } else {
                        emptyList()
                    },
                    activeArraySize = activeArray?.let { AvailableCameraSize(it.width(), it.height()) },
                    defaultPreviewSize = defaultPreviewSize,
                    previewSizes = orderedPreviewSizes.map { it.toAvailableCameraSize() },
                    defaultVideoSize = defaultVideoSize,
                    videoSizes = orderedVideoSizes.map { it.toAvailableCameraSize() },
                    targetFrameRate = recordingProfile?.fpsSelection?.frameRate,
                    targetFpsRange = recordingProfile?.fpsSelection?.range?.toAvailableCameraFpsRange(),
                    capabilities = capabilities,
                )
            }.onFailure { throwable ->
                Timber.w(throwable, "Unable to read available camera info for cameraId=%s", cameraId)
            }.getOrNull()
        }
    }

    private fun collectPreviewSizes(configurationMap: StreamConfigurationMap) = buildList {
        addAll(
            runCatching { configurationMap.getOutputSizes(SurfaceTexture::class.java) }
                .getOrNull()
                .orEmpty()
                .asList()
        )
        addAll(
            runCatching { configurationMap.getOutputSizes(Surface::class.java) }
                .getOrNull()
                .orEmpty()
                .asList()
        )
    }.distinctBy { it.width to it.height }

    private fun Size.toAvailableCameraSize() = AvailableCameraSize(
        width = width,
        height = height,
    )

    private fun Range<Int>.toAvailableCameraFpsRange() = AvailableCameraFpsRange(
        min = lower,
        max = upper,
    )

    override val hasCameraPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
}
