package org.example.androidemulators

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.example.androidemulators.platform.emulator.AvdCreator
import org.example.androidemulators.platform.emulator.AvdCreationOptions
import org.example.androidemulators.platform.emulator.DeviceProfile
import org.example.androidemulators.platform.emulator.SystemImage
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

private sealed interface CreationUiState {
    data object Idle : CreationUiState
    data object Loading : CreationUiState
    data class Ready(val options: AvdCreationOptions) : CreationUiState
    data object Creating : CreationUiState
    data class Error(val message: String) : CreationUiState
}

@Composable
actual fun App() {
    var uiState by remember { mutableStateOf<UiState>(UiState.Loading) }
    val coroutineScope = rememberCoroutineScope()
    
    val processExecutor = remember { ProcessExecutorImpl() }
    val sdkLocator = remember { AndroidSdkLocator() }
    val emulatorRepository = remember { EmulatorRepository(processExecutor, sdkLocator) }
    val emulatorLauncher = remember { EmulatorLauncher(processExecutor, sdkLocator) }
    val avdCreator = remember { AvdCreator(processExecutor, sdkLocator) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var creationUiState by remember { mutableStateOf<CreationUiState>(CreationUiState.Idle) }
    
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

    fun openCreateDialog() {
        showCreateDialog = true
        creationUiState = CreationUiState.Loading
        coroutineScope.launch {
            avdCreator.loadOptions().fold(
                onSuccess = { options -> creationUiState = CreationUiState.Ready(options) },
                onFailure = { error -> creationUiState = CreationUiState.Error(error.message ?: "Unable to load emulator options.") }
            )
        }
    }

    fun createEmulator(name: String, device: DeviceProfile, image: SystemImage) {
        if (!name.matches(Regex("[A-Za-z0-9_.-]+"))) {
            creationUiState = CreationUiState.Error("Use only letters, numbers, periods, underscores, or hyphens in the emulator name.")
            return
        }
        creationUiState = CreationUiState.Creating
        coroutineScope.launch {
            avdCreator.createAvd(name, device, image).fold(
                onSuccess = {
                    showCreateDialog = false
                    creationUiState = CreationUiState.Idle
                    loadEmulators()
                },
                onFailure = { error -> creationUiState = CreationUiState.Error(error.message ?: "Unable to create the emulator.") }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { openCreateDialog() }) {
                            Text("Create Emulator")
                        }
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

    if (showCreateDialog) {
        CreateEmulatorDialog(
            state = creationUiState,
            onDismiss = {
                if (creationUiState !is CreationUiState.Creating) {
                    showCreateDialog = false
                    creationUiState = CreationUiState.Idle
                }
            },
            onCreate = ::createEmulator
        )
    }
}

@Composable
private fun CreateEmulatorDialog(
    state: CreationUiState,
    onDismiss: () -> Unit,
    onCreate: (String, DeviceProfile, SystemImage) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Android Emulator") },
        text = {
            when (state) {
                CreationUiState.Idle, CreationUiState.Loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Loading device profiles and installed system images…")
                    }
                }
                CreationUiState.Creating -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Creating emulator…")
                    }
                }
                is CreationUiState.Error -> Text(state.message)
                is CreationUiState.Ready -> CreateEmulatorForm(state.options, onCreate)
            }
        },
        confirmButton = {
            if (state is CreationUiState.Error) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (state !is CreationUiState.Creating) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun CreateEmulatorForm(
    options: AvdCreationOptions,
    onCreate: (String, DeviceProfile, SystemImage) -> Unit
) {
    var selectedDevice by remember(options) { mutableStateOf(options.deviceProfiles.first()) }
    var selectedImage by remember(options) { mutableStateOf(options.systemImages.first()) }
    var avdName by remember(options) { mutableStateOf(suggestedAvdName(selectedDevice, selectedImage)) }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Choose a device profile and one of the Android system images already installed on this Mac.")
        OptionSelector(
            label = "Device",
            selectedLabel = selectedDevice.name,
            options = options.deviceProfiles,
            optionLabel = { it.name },
            onSelected = {
                selectedDevice = it
                avdName = suggestedAvdName(selectedDevice, selectedImage)
            }
        )
        OptionSelector(
            label = "Android version",
            selectedLabel = selectedImage.displayName,
            options = options.systemImages,
            optionLabel = { it.displayName },
            onSelected = {
                selectedImage = it
                avdName = suggestedAvdName(selectedDevice, selectedImage)
            }
        )
        OutlinedTextField(
            value = avdName,
            onValueChange = { avdName = it },
            label = { Text("Emulator name") },
            supportingText = { Text("Generated automatically; you can change it.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onCreate(avdName.trim(), selectedDevice, selectedImage) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = darkPrimary, contentColor = darkOnPrimary)
        ) {
            Text("Create Emulator")
        }
    }
}

@Composable
private fun <T> OptionSelector(
    label: String,
    selectedLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedLabel, modifier = Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun suggestedAvdName(device: DeviceProfile, image: SystemImage): String {
    val api = image.packageName.split(';').getOrNull(1)?.removePrefix("android-") ?: "API"
    return "${device.id.replace(Regex("[^A-Za-z0-9_.-]"), "_")}_API_$api"
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
