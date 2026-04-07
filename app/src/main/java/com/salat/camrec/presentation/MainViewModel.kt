package com.salat.camrec.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {
    private val _splashScreenState = MutableStateFlow(true)
    val splashScreenState = _splashScreenState.asStateFlow()

    init {
        viewModelScope.launch {
            // Disabling splashscreen
            _splashScreenState.value = false
        }
    }
}
