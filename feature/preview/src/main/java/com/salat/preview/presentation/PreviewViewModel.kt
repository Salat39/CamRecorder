package com.salat.preview.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.salat.carapi.domain.usecases.GetIgnitionUseCase
import com.salat.drivestorage.domain.usecases.DriveConnectedUseCase
import com.salat.drivestorage.domain.usecases.FullFileAccessGrantedUseCase
import com.salat.preferences.domain.entity.BoolPref
import com.salat.preferences.domain.usecases.FlowPrefsUseCase
import com.salat.preferences.domain.usecases.SaveBoolPrefUseCase
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
    private val fullFileAccessGrantedUseCase: FullFileAccessGrantedUseCase,
    private val flowPrefsUseCase: FlowPrefsUseCase,
    private val getIgnitionUseCase: GetIgnitionUseCase,
    private val saveBoolPrefUseCase: SaveBoolPrefUseCase
) : BaseSyncViewModel<PreviewViewModel.ViewState, PreviewViewModel.Action>(ViewState()) {

    init {
        viewModelScope.launch {
            handleAS()
            handleIgnition()
            handleDriveConnected()
            handlePrefs()
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

    override fun onReduceState(viewAction: Action): ViewState = when (viewAction) {
        is Action.SetAccessibilityServiceEnabled -> state.value.copy(accessibilityServiceEnabled = viewAction.enabled)

        is Action.SetInitPrefs -> state.value.copy(
            enableRecord = viewAction.enabled,
            forceRecord = viewAction.force
        )

        is Action.SetIgnition -> state.value.copy(ignition = viewAction.ignition)

        is Action.SetDriveConnected -> state.value.copy(driveConnected = viewAction.connected)

        is Action.SetEnableRec -> {
            viewModelScope.launch(Dispatchers.IO) {
                saveBoolPrefUseCase.execute(BoolPref.EnableRecord, viewAction.enable)
            }
            state.value
        }

        is Action.SetForceRecord -> {
            viewModelScope.launch(Dispatchers.IO) {
                saveBoolPrefUseCase.execute(BoolPref.ForceRecord, viewAction.enable)
            }
            state.value
        }

        Action.RefreshDriveConnected -> {
            viewModelScope.launch { fullFileAccessGrantedUseCase.execute() }
            state.value
        }
    }

    @Immutable
    data class ViewState(
        val accessibilityServiceEnabled: Boolean = false,
        val enableRecord: Boolean? = null,
        val forceRecord: Boolean? = null,
        val ignition: Boolean = false,
        val driveConnected: Boolean = false
    ) : MviViewState

    sealed class Action : MviAction {
        data class SetAccessibilityServiceEnabled(val enabled: Boolean) : Action()
        data class SetInitPrefs(val enabled: Boolean, val force: Boolean) : Action()
        data class SetIgnition(val ignition: Boolean) : Action()
        data class SetDriveConnected(val connected: Boolean) : Action()
        data class SetEnableRec(val enable: Boolean) : Action()
        data class SetForceRecord(val enable: Boolean) : Action()
        object RefreshDriveConnected : Action()
    }
}
