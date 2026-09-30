package org.example.androidemulators

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.androidemulators.domain.model.Emulator
import org.example.androidemulators.domain.model.EmulatorState
import org.example.androidemulators.platform.emulator.EmulatorLauncher
import org.example.androidemulators.platform.emulator.EmulatorRepository
import org.example.androidemulators.platform.process.ProcessExecutorImpl
import org.example.androidemulators.platform.sdk.AndroidSdkLocator

// Android Studio Darcula theme colors
private val darkBackground = Color(0xFF2B2B2B)
private val darkSurface = Color(0xFF3C3F41)
private val darkPrimary = Color(0xFF4B6EAF)
private val darkOnPrimary = Color(0xFFFFFFFF)
private val darkSecondary = Color(0xFF7B1FA2)
private val darkText = Color(0xFFA9B7C6)
private val darkTextSecondary = Color(0xFF808080)
private val darkBorder = Color(0xFF4E5254)
private val darkError = Color(0xFFE74C3C)

private val androidStudioDarkColorScheme = darkColorScheme(
    primary = darkPrimary,
    onPrimary = darkOnPrimary,
    primaryContainer = darkSurface,
    onPrimaryContainer = darkText,
    secondary = darkSecondary,
    background = darkBackground,
    onBackground = darkText,
    surface = darkSurface,
    onSurface = darkText,
    error = darkError,
    onError = Color.White
)

sealed class UiState {
    data object Loading : UiState()
    data class Success(val emulators: List<Emulator>) : UiState()
    data class Error(val message: String) : UiState()
    data object Empty : UiState()
}

@Composable
actual fun App() {
    var uiState by remember { mutableStateOf<UiState>(UiState.Loading) }
    val coroutineScope = rememberCoroutineScope()
    
    val processExecutor = remember { ProcessExecutorImpl() }
    val sdkLocator = remember { AndroidSdkLocator() }
    val emulatorRepository = remember { EmulatorRepository(processExecutor, sdkLocator) }
    val emulatorLauncher = remember { EmulatorLauncher(processExecutor, sdkLocator) }
    
    fun loadEmulators() {
        coroutineScope.launch {
            uiState = UiState.Loading
            val result = emulatorRepository.discoverAvds()
            result.fold(
                onSuccess = { emulators ->
                    uiState = if (emulators.isEmpty()) UiState.Empty else UiState.Success(emulators)
                },
                onFailure = { error ->
                    uiState = UiState.Error(error.message ?: "Unknown error")
                }
            )
        }
    }
    
    fun launchEmulator(emulator: Emulator) {
        coroutineScope.launch {
            val currentList = (uiState as? UiState.Success)?.emulators ?: emptyList()
            uiState = UiState.Success(currentList.map { 
                if (it.name == emulator.name) it.copy(state = EmulatorState.Starting) 
                else it 
            })
            
            val result = emulatorLauncher.launch(emulator.name)
            result.fold(
                onSuccess = {
                    uiState = UiState.Success(currentList.map { 
                        if (it.name == emulator.name) it.copy(state = EmulatorState.Running) 
                        else it 
                    })
                },
                onFailure = { error ->
                    uiState = UiState.Success(currentList.map { 
                        if (it.name == emulator.name) it.copy(state = EmulatorState.Error(error.message ?: "Failed to launch")) 
                        else it 
                    })
                }
            )
        }
    }
    
    LaunchedEffect(Unit) {
        loadEmulators()
    }
    
    MaterialTheme(
        colorScheme = androidStudioDarkColorScheme,
        typography = Typography()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidStudioDarkColorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Android Emulator Launcher",
                        style = MaterialTheme.typography.headlineMedium,
                        color = darkText
                    )
                    Button(
                        onClick = { loadEmulators() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = darkPrimary,
                            contentColor = darkOnPrimary
                        )
                    ) {
                        Text("Refresh")
                    }
                }
                
                // Content
                when (val state = uiState) {
                    is UiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Success -> {
                        EmulatorList(
                            emulators = state.emulators,
                            onLaunchClick = { launchEmulator(it) }
                        )
                    }
                    is UiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Error",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    is UiState.Empty -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No Emulators Found",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Make sure Android SDK is installed and AVDs are configured.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmulatorList(
    emulators: List<Emulator>,
    onLaunchClick: (Emulator) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(emulators) { emulator ->
            EmulatorItem(
                emulator = emulator,
                onLaunchClick = onLaunchClick
            )
        }
    }
}

@Composable
fun EmulatorItem(
    emulator: Emulator,
    onLaunchClick: (Emulator) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = darkSurface,
            contentColor = darkText
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, darkBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = emulator.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = darkText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = getStateText(emulator.state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = getStateColor(emulator.state)
                    )
                }
                Button(
                    onClick = { onLaunchClick(emulator) },
                    enabled = emulator.state == EmulatorState.Stopped || emulator.state is EmulatorState.Error,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = darkPrimary,
                        contentColor = darkOnPrimary,
                        disabledContainerColor = darkBorder,
                        disabledContentColor = darkTextSecondary
                    )
                ) {
                    Text(
                        when (emulator.state) {
                            EmulatorState.Starting -> "Starting..."
                            EmulatorState.Running -> "Running"
                            else -> "Launch"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun getStateText(state: EmulatorState): String {
    return when (state) {
        EmulatorState.Stopped -> "Stopped"
        EmulatorState.Starting -> "Starting..."
        EmulatorState.Running -> "Running"
        is EmulatorState.Error -> "Error: ${state.message}"
    }
}

@Composable
private fun getStateColor(state: EmulatorState): androidx.compose.ui.graphics.Color {
    return when (state) {
        EmulatorState.Stopped -> darkTextSecondary
        EmulatorState.Starting -> darkPrimary
        EmulatorState.Running -> darkPrimary
        is EmulatorState.Error -> darkError
    }
}
