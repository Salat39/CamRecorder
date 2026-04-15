package com.salat.settings.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.salat.commonconst.MAX_CAMERA_COUNT
import com.salat.commonconst.MAX_CAMERA_FPS
import com.salat.commonconst.MIN_CAMERA_FPS
import com.salat.preferences.domain.entity.BoolPref
import com.salat.preferences.domain.entity.CameraDataStoreConfig
import com.salat.preferences.domain.entity.LongPref
import com.salat.preferences.domain.usecases.FlowLongPrefUseCase
import com.salat.preferences.domain.usecases.FlowPrefsUseCase
import com.salat.preferences.domain.usecases.ObserveCameraRecordingConfigsUseCase
import com.salat.preferences.domain.usecases.SaveBoolPrefUseCase
import com.salat.preferences.domain.usecases.SaveCameraRecordingConfigUseCase
import com.salat.preferences.domain.usecases.SaveLongPrefUseCase
import com.salat.recorder.domain.usecases.GetAvailableCamInfoUseCase
import com.salat.recorder.domain.usecases.HasCameraPermissionUseCase
import com.salat.settings.presentation.entity.DisplayCameraSettings
import com.salat.settings.presentation.entity.defaultCameraSettingsConfig
import com.salat.settings.presentation.entity.normalizeCameraSettings
import com.salat.settings.presentation.entity.toDisplayCameraSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import presentation.BaseSyncViewModel
import presentation.mvi.MviAction
import presentation.mvi.MviViewState

private const val SETTINGS_DEBOUNCE_MS = 500L
internal const val MIN_SEGMENT_DURATION_MS = 60_000L
internal const val MAX_SEGMENT_DURATION_MS = 600_000L
internal const val SEGMENT_DURATION_STEP_MS = 60_000L

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getAvailableCamInfoUseCase: GetAvailableCamInfoUseCase,
    private val hasCameraPermissionUseCase: HasCameraPermissionUseCase,
    private val observeCameraRecordingConfigsUseCase: ObserveCameraRecordingConfigsUseCase,
    private val saveCameraRecordingConfigUseCase: SaveCameraRecordingConfigUseCase,
    private val flowPrefsUseCase: FlowPrefsUseCase,
    private val flowLongPrefUseCase: FlowLongPrefUseCase,
    private val saveLongPrefUseCase: SaveLongPrefUseCase,
    private val saveBoolPrefUseCase: SaveBoolPrefUseCase
) : BaseSyncViewModel<SettingsViewModel.ViewState, SettingsViewModel.Action>(ViewState()) {
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observeCameraConfigsJob: Job? = null
    private var observeSegmentDurationJob: Job? = null
    private val saveCameraConfigJobs = mutableMapOf<String, Job>()
    private var saveSegmentDurationJob: Job? = null
    private var availableCameraIds: List<String> = emptyList()
    private var persistedCameraConfigs: Map<String, CameraDataStoreConfig> = emptyMap()
    private val pendingCameraConfigs = mutableMapOf<String, CameraDataStoreConfig>()
    private var isCameraConfigsLoaded = false
    private var persistedSegmentDurationMs: Long = normalizeSegmentDuration(LongPref.SegmentDurationMs.default)
    private var pendingSegmentDurationMs: Long? = null

    init {
        viewModelScope.launch {
            refreshScreenState()
            handlePrefs()
        }
    }

    private fun CoroutineScope.handlePrefs() = launch(Dispatchers.IO) {
        flowPrefsUseCase.execute(BoolPref.ForceRecord)
            .distinctUntilChanged()
            .collect { prefs -> sendAction(Action.ApplyPrefs(force = prefs[0] as Boolean)) }
    }

    private fun CoroutineScope.refreshScreenState() = launch(Dispatchers.IO) {
        observeSegmentDuration()

        val granted = withContext(Dispatchers.IO) {
            hasCameraPermissionUseCase.check
        }

        sendAction(Action.SetCameraPermission(granted))

        if (!granted) {
            clearCameraState()
            sendAction(Action.SetCameraSettings(emptyList()))
            return@launch
        }

        val cameraIds = withContext(Dispatchers.IO) {
            getAvailableCamInfoUseCase.execute()
                .take(MAX_CAMERA_COUNT)
                .map { it.cameraId }
        }

        val validCameraIds = cameraIds.toSet()
        availableCameraIds = cameraIds
        persistedCameraConfigs = persistedCameraConfigs.filterKeys(validCameraIds::contains)
        pendingCameraConfigs.keys.retainAll(validCameraIds)
        saveCameraConfigJobs.keys.toList()
            .filterNot(validCameraIds::contains)
            .forEach { cameraId -> saveCameraConfigJobs.remove(cameraId)?.cancel() }

        isCameraConfigsLoaded = false
        sendAction(Action.SetCameraSettings(buildCameraSettingsItems()))
        observeCameraConfigs(cameraIds)
    }

    private fun observeSegmentDuration() {
        if (observeSegmentDurationJob != null) return

        observeSegmentDurationJob = viewModelScope.launch {
            flowLongPrefUseCase.execute(LongPref.SegmentDurationMs).collect { value ->
                val normalizedValue = normalizeSegmentDuration(value)
                persistedSegmentDurationMs = normalizedValue
                if (pendingSegmentDurationMs == normalizedValue) {
                    pendingSegmentDurationMs = null
                }
                sendAction(Action.SetSegmentDurationMs(currentSegmentDurationMs()))
            }
        }
    }

    private fun observeCameraConfigs(cameraIds: List<String>) {
        observeCameraConfigsJob?.cancel()

        if (cameraIds.isEmpty()) {
            isCameraConfigsLoaded = true
            persistedCameraConfigs = emptyMap()
            pendingCameraConfigs.clear()
            sendAction(Action.SetCameraSettings(emptyList()))
            return
        }

        isCameraConfigsLoaded = false
        val defaultConfigsByCameraId = cameraIds.associateWith(::defaultCameraSettingsConfig)

        observeCameraConfigsJob = viewModelScope.launch {
            observeCameraRecordingConfigsUseCase.execute(
                defaultConfigsByCameraId = defaultConfigsByCameraId,
                minFps = MIN_CAMERA_FPS,
                maxFps = MAX_CAMERA_FPS
            ).collect { configs ->
                isCameraConfigsLoaded = true
                persistedCameraConfigs = configs
                pendingCameraConfigs.entries.removeAll { (cameraId, config) ->
                    configs[cameraId] == config
                }
                sendAction(Action.SetCameraSettings(buildCameraSettingsItems()))
            }
        }
    }

    private fun updateCameraConfig(
        cameraId: String,
        transform: (CameraDataStoreConfig) -> CameraDataStoreConfig,
    ): ViewState {
        val currentConfig = pendingCameraConfigs[cameraId]
            ?: persistedCameraConfigs[cameraId].takeIf { isCameraConfigsLoaded }
            ?: defaultCameraSettingsConfig(cameraId)

        val updatedConfig = transform(currentConfig).normalizeCameraSettings()
        pendingCameraConfigs[cameraId] = updatedConfig
        scheduleCameraConfigSave(cameraId, updatedConfig)
        return state.value.copy(cameras = buildCameraSettingsItems())
    }

    private fun updateSegmentDuration(value: Long): ViewState {
        val normalizedValue = normalizeSegmentDuration(value)
        pendingSegmentDurationMs = normalizedValue
        scheduleSegmentDurationSave(normalizedValue)
        return state.value.copy(segmentDurationMs = normalizedValue)
    }

    private fun scheduleCameraConfigSave(cameraId: String, config: CameraDataStoreConfig) {
        saveCameraConfigJobs.remove(cameraId)?.cancel()
        saveCameraConfigJobs[cameraId] = saveScope.launch {
            delay(SETTINGS_DEBOUNCE_MS)
            saveCameraRecordingConfigUseCase.execute(config)
        }
    }

    private fun scheduleSegmentDurationSave(value: Long) {
        saveSegmentDurationJob?.cancel()
        saveSegmentDurationJob = saveScope.launch {
            delay(SETTINGS_DEBOUNCE_MS)
            saveLongPrefUseCase.execute(LongPref.SegmentDurationMs, value)
        }
    }

    private fun flushPendingChangesAsync() {
        flushPendingCameraConfigsAsync()
        flushPendingSegmentDurationAsync()
    }

    private fun flushPendingCameraConfigsAsync() {
        val configsToSave = pendingCameraConfigs.values
            .map(CameraDataStoreConfig::normalizeCameraSettings)
            .distinctBy(CameraDataStoreConfig::cameraId)

        if (configsToSave.isEmpty()) return

        saveCameraConfigJobs.values.forEach(Job::cancel)
        saveCameraConfigJobs.clear()

        saveScope.launch {
            configsToSave.forEach { config ->
                saveCameraRecordingConfigUseCase.execute(config)
            }
        }
    }

    private fun flushPendingSegmentDurationAsync() {
        val valueToSave = pendingSegmentDurationMs ?: return

        saveSegmentDurationJob?.cancel()
        saveSegmentDurationJob = null

        saveScope.launch {
            saveLongPrefUseCase.execute(LongPref.SegmentDurationMs, valueToSave)
        }
    }

    private fun flushPendingChangesBlocking() {
        val configsToSave = pendingCameraConfigs.values
            .map(CameraDataStoreConfig::normalizeCameraSettings)
            .distinctBy(CameraDataStoreConfig::cameraId)
        val segmentValueToSave = pendingSegmentDurationMs

        saveCameraConfigJobs.values.forEach(Job::cancel)
        saveCameraConfigJobs.clear()
        saveSegmentDurationJob?.cancel()
        saveSegmentDurationJob = null

        if (configsToSave.isEmpty() && segmentValueToSave == null) return

        runBlocking(Dispatchers.IO) {
            configsToSave.forEach { config ->
                saveCameraRecordingConfigUseCase.execute(config)
            }
            segmentValueToSave?.let {
                saveLongPrefUseCase.execute(LongPref.SegmentDurationMs, it)
            }
        }
    }

    private fun currentSegmentDurationMs() = pendingSegmentDurationMs ?: persistedSegmentDurationMs

    private fun buildCameraSettingsItems() = availableCameraIds.map { cameraId ->
        val defaultConfig = defaultCameraSettingsConfig(cameraId)
        val pendingConfig = pendingCameraConfigs[cameraId]
        val persistedConfig = persistedCameraConfigs[cameraId].takeIf { isCameraConfigsLoaded }
        val config = (pendingConfig ?: persistedConfig ?: defaultConfig).normalizeCameraSettings()

        config.toDisplayCameraSettings(
            enabledOverride = pendingConfig?.enabled
                ?: persistedConfig?.enabled
                ?: if (isCameraConfigsLoaded) defaultConfig.enabled else null
        )
    }

    private fun clearCameraState() {
        observeCameraConfigsJob?.cancel()
        observeCameraConfigsJob = null
        saveCameraConfigJobs.values.forEach(Job::cancel)
        saveCameraConfigJobs.clear()
        availableCameraIds = emptyList()
        persistedCameraConfigs = emptyMap()
        pendingCameraConfigs.clear()
        isCameraConfigsLoaded = false
    }

    override fun onCleared() {
        flushPendingChangesBlocking()
        observeSegmentDurationJob?.cancel()
        saveScope.cancel()
        clearCameraState()
        super.onCleared()
    }

    override fun onReduceState(viewAction: Action): ViewState = when (viewAction) {
        Action.FlushPendingChanges -> {
            flushPendingChangesAsync()
            state.value
        }

        is Action.SetForceRecord -> {
            viewModelScope.launch(Dispatchers.IO) {
                saveBoolPrefUseCase.execute(BoolPref.ForceRecord, viewAction.enable)
            }
            state.value
        }

        is Action.ApplyPrefs -> state.value.copy(
            forceRecord = viewAction.force
        )

        is Action.SetCameraPermission -> state.value.copy(hasCameraPermission = viewAction.granted)

        is Action.SetCameraSettings -> state.value.copy(cameras = viewAction.cameras)

        is Action.SetSegmentDurationMs -> state.value.copy(segmentDurationMs = viewAction.value)

        is Action.ChangeCameraEnabled -> updateCameraConfig(viewAction.cameraId) { current ->
            current.copy(enabled = viewAction.enabled)
        }

        is Action.ChangeCameraOutputType -> updateCameraConfig(viewAction.cameraId) { current ->
            current.copy(outputType = viewAction.outputType)
        }

        is Action.ChangeCameraFps -> updateCameraConfig(viewAction.cameraId) { current ->
            current.copy(fps = viewAction.fps)
        }

        is Action.ChangeSegmentDurationMs -> updateSegmentDuration(viewAction.value)
    }

    @Immutable
    data class ViewState(
        val hasCameraPermission: Boolean? = null,
        val forceRecord: Boolean? = null,
        val cameras: List<DisplayCameraSettings> = emptyList(),
        val segmentDurationMs: Long = normalizeSegmentDuration(LongPref.SegmentDurationMs.default),
    ) : MviViewState

    sealed class Action : MviAction {
        data object FlushPendingChanges : Action()
        data class ApplyPrefs(val force: Boolean) : Action()
        data class SetCameraPermission(val granted: Boolean) : Action()
        data class SetForceRecord(val enable: Boolean) : Action()
        data class SetCameraSettings(val cameras: List<DisplayCameraSettings>) : Action()
        data class SetSegmentDurationMs(val value: Long) : Action()
        data class ChangeCameraEnabled(val cameraId: String, val enabled: Boolean) : Action()
        data class ChangeCameraOutputType(val cameraId: String, val outputType: Int) : Action()
        data class ChangeCameraFps(val cameraId: String, val fps: Int) : Action()
        data class ChangeSegmentDurationMs(val value: Long) : Action()
    }
}

private fun normalizeSegmentDuration(value: Long): Long {
    val clampedValue = value.coerceIn(MIN_SEGMENT_DURATION_MS, MAX_SEGMENT_DURATION_MS)
    val offset = clampedValue - MIN_SEGMENT_DURATION_MS
    val snappedOffset = ((offset + SEGMENT_DURATION_STEP_MS / 2) / SEGMENT_DURATION_STEP_MS) * SEGMENT_DURATION_STEP_MS
    return (MIN_SEGMENT_DURATION_MS + snappedOffset).coerceIn(MIN_SEGMENT_DURATION_MS, MAX_SEGMENT_DURATION_MS)
}
