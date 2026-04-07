package com.salat.recorder.data.entity

data class RecordingLauncherState(
    val enabled: Boolean = false,
    val forceStart: Boolean = false, // without ignition
    val ignition: Boolean = false,
    val driveConnected: Boolean = false,
)
