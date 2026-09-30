package org.example.androidemulators.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.example.androidemulators.domain.model.Emulator
import org.example.androidemulators.domain.model.EmulatorState
import org.example.androidemulators.platform.emulator.EmulatorLauncher
import org.example.androidemulators.platform.emulator.EmulatorRepository
import org.example.androidemulators.platform.process.ProcessExecutorImpl
import org.example.androidemulators.platform.sdk.AndroidSdkLocator

sealed class UiState {
    data object Loading : UiState()
    data class Success(val emulators: List<Emulator>) : UiState()
    data class Error(val message: String) : UiState()
    data object Empty : UiState()
}

class EmulatorViewModel : ViewModel() {
    
    private val processExecutor by lazy { ProcessExecutorImpl() }
    private val sdkLocator by lazy { AndroidSdkLocator() }
    private val emulatorRepository by lazy { EmulatorRepository(processExecutor, sdkLocator) }
    private val emulatorLauncher by lazy { EmulatorLauncher(processExecutor, sdkLocator) }
    
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    fun loadEmulators() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            
            val result = emulatorRepository.discoverAvds()
            
            result.fold(
                onSuccess = { emulators ->
                    if (emulators.isEmpty()) {
                        _uiState.value = UiState.Empty
                    } else {
                        _uiState.value = UiState.Success(emulators)
                    }
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "Unknown error")
                }
            )
        }
    }
    
    fun launchEmulator(emulator: Emulator) {
        viewModelScope.launch {
            val currentList = (_uiState.value as? UiState.Success)?.emulators ?: emptyList()
            
            // Update state to Starting
            val updatedList = currentList.map { 
                if (it.name == emulator.name) it.copy(state = EmulatorState.Starting) 
                else it 
            }
            _uiState.value = UiState.Success(updatedList)
            
            val result = emulatorLauncher.launch(emulator.name)
            
            result.fold(
                onSuccess = {
                    // Update state to Running
                    val finalList = currentList.map { 
                        if (it.name == emulator.name) it.copy(state = EmulatorState.Running) 
                        else it 
                    }
                    _uiState.value = UiState.Success(finalList)
                },
                onFailure = { error ->
                    // Update state to Error
                    val finalList = currentList.map { 
                        if (it.name == emulator.name) it.copy(state = EmulatorState.Error(error.message ?: "Failed to launch")) 
                        else it 
                    }
                    _uiState.value = UiState.Success(finalList)
                }
            )
        }
    }
}
