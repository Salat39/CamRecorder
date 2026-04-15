package com.salat.preview.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.salat.carapi.domain.usecases.GetIgnitionUseCase
import com.salat.commonconst.MAX_CAMERA_COUNT
import com.salat.drivestorage.domain.usecases.DriveConnectedUseCase
import com.salat.drivestorage.domain.usecases.FullFileAccessGrantedUseCase
import com.salat.preferences.domain.entity.BoolPref
import com.salat.preferences.domain.usecases.FlowPrefsUseCase
import com.salat.preferences.domain.usecases.ObserveAllCamerasDiableUseCase
import com.salat.preferences.domain.usecases.SaveBoolPrefUseCase
import com.salat.preview.presentation.entity.DisplayAvailableCamera
import com.salat.preview.presentation.mappers.toDisplay
import com.salat.recorder.domain.usecases.GetAvailableCamInfoUseCase
import com.salat.recorder.domain.usecases.HasCameraPermissionUseCase
import com.salat.sharedevents.domain.usecases.CheckAccessibilityServiceEnabledUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import presentation.BaseSyncViewModel
import presentation.mvi.MviAction
import presentation.mvi.MviViewState

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val checkAccessibilityServiceEnabledUseCase: CheckAccessibilityServiceEnabledUseCase,
    private val driveConnectedUseCase: DriveConnectedUseCase,
    private val getAvailableCamInfoUseCase: GetAvailableCamInfoUseCase,
    private val hasCameraPermissionUseCase: HasCameraPermissionUseCase,
    private val fullFileAccessGrantedUseCase: FullFileAccessGrantedUseCase,
    private val flowPrefsUseCase: FlowPrefsUseCase,
    private val getIgnitionUseCase: GetIgnitionUseCase,
    private val saveBoolPrefUseCase: SaveBoolPrefUseCase,
    private val observeAllCamerasDiableUseCase: ObserveAllCamerasDiableUseCase
) : BaseSyncViewModel<PreviewViewModel.ViewState, PreviewViewModel.Action>(ViewState()) {

    init {
        viewModelScope.launch {
            handleAS()
            handleIgnition()
            handleDriveConnected()
            handlePrefs()
            handleAvailableCameraConfigs()
            handleAvailableCamerasInfo()
        }
    }

    private fun CoroutineScope.handleAS() = launch {
        checkAccessibilityServiceEnabledUseCase.flow.collect { enabled ->
            sendAction(Action.SetAccessibilityServiceEnabled(enabled))
        }
    }

    private fun CoroutineScope.handleIgnition() = launch {
        getIgnitionUseCase.flow.collect { ignition ->
            sendAction(Action.SetIgnition(ignition))
        }
    }

    private fun CoroutineScope.handleDriveConnected() = launch {
        driveConnectedUseCase.flow.collect { connected ->
            sendAction(Action.SetDriveConnected(connected))
        }
    }

    private fun CoroutineScope.handlePrefs() = launch(Dispatchers.IO) {
        flowPrefsUseCase.execute(
            BoolPref.EnableRecord,
            BoolPref.ForceRecord
        ).distinctUntilChanged()
            .collect { prefs ->
                sendAction(
                    Action.SetInitPrefs(
                        enabled = prefs[0] as Boolean,
                        force = prefs[1] as Boolean
                    )
                )
            }
    }

    private fun CoroutineScope.handleAvailableCameraConfigs() = launch(Dispatchers.IO) {
        observeAllCamerasDiableUseCase.execute()
            .collect { isAllCamerasDiable -> sendAction(Action.SetAllCamerasDiable(isAllCamerasDiable)) }
    }

    private fun CoroutineScope.handleAvailableCamerasInfo() = launch(Dispatchers.IO) {
        if (!hasCameraPermissionUseCase.check) {
            sendAction(Action.SetCamerasInfo(emptyList()))
            return@launch
        }

        val cameras = getAvailableCamInfoUseCase.execute()
        // Available cameras filter
        val filtered = cameras.take(MAX_CAMERA_COUNT).toDisplay()
        sendAction(Action.SetCamerasInfo(filtered))
    }

    override fun onReduceState(viewAction: Action): ViewState = when (viewAction) {
        is Action.SetAccessibilityServiceEnabled -> state.value.copy(accessibilityServiceEnabled = viewAction.enabled)

        is Action.SetInitPrefs -> state.value.copy(
            enableRecord = viewAction.enabled,
            forceRecord = viewAction.force
        )

        is Action.SetIgnition -> state.value.copy(ignition = viewAction.ignition)

        is Action.SetDriveConnected -> state.value.copy(driveConnected = viewAction.connected)

        is Action.SetAllCamerasDiable -> state.value.copy(allCamerasDiable = viewAction.value)

        is Action.SetEnableRec -> {
            viewModelScope.launch(Dispatchers.IO) {
                saveBoolPrefUseCase.execute(BoolPref.EnableRecord, viewAction.enable)
            }
            state.value
        }

        is Action.SetCamerasInfo -> state.value.copy(cameras = viewAction.cameras)

        Action.RefreshDriveConnected -> {
            viewModelScope.launch { fullFileAccessGrantedUseCase.execute() }
            state.value
        }

        Action.GetCamerasInfo -> {
            viewModelScope.launch { handleAvailableCamerasInfo() }
            state.value
        }

        Action.LaunchRec -> {
            viewModelScope.launch(Dispatchers.IO) {
                saveBoolPrefUseCase.execute(BoolPref.EnableRecord, true)
            }
            state.value.copy(
                cameras = state.value.cameras.map { it.copy(showPreview = false) }
            )
        }

        is Action.ToggleCameraPreview -> state.value.let { st ->
            st.copy(
                cameras = st.cameras.map { cam ->
                    if (cam.cameraId == viewAction.id) {
                        cam.copy(showPreview = !cam.showPreview)
                    } else cam
                }
            )
        }

        is Action.ToggleCameraInfo -> state.value.let { st ->
            st.copy(
                cameras = st.cameras.map { cam ->
                    if (cam.cameraId == viewAction.id) {
                        cam.copy(showInfo = !cam.showInfo)
                    } else cam
                }
            )
        }
    }

    @Immutable
    data class ViewState(
        val accessibilityServiceEnabled: Boolean = false,
        val enableRecord: Boolean? = null,
        val forceRecord: Boolean? = null,
        val ignition: Boolean = false,
        val driveConnected: Boolean = false,
        val cameras: List<DisplayAvailableCamera> = emptyList(),
        val allCamerasDiable: Boolean = false
    ) : MviViewState

    sealed class Action : MviAction {
        data class SetAccessibilityServiceEnabled(val enabled: Boolean) : Action()
        data class SetInitPrefs(val enabled: Boolean, val force: Boolean) : Action()
        data class SetIgnition(val ignition: Boolean) : Action()
        data class SetDriveConnected(val connected: Boolean) : Action()
        data class SetAllCamerasDiable(val value: Boolean) : Action()
        data class SetEnableRec(val enable: Boolean) : Action()
        data class SetCamerasInfo(val cameras: List<DisplayAvailableCamera>) : Action()
        data class ToggleCameraPreview(val id: String) : Action()
        data class ToggleCameraInfo(val id: String) : Action()
        object RefreshDriveConnected : Action()
        object GetCamerasInfo : Action()
        object LaunchRec : Action()
    }
}
