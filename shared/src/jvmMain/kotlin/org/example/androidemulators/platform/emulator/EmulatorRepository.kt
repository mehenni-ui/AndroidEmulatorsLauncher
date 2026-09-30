package org.example.androidemulators.platform.emulator

import org.example.androidemulators.domain.model.Emulator
import org.example.androidemulators.domain.model.EmulatorState
import org.example.androidemulators.platform.process.ProcessExecutor
import org.example.androidemulators.platform.sdk.AndroidSdkLocator

class EmulatorRepository(
    private val processExecutor: ProcessExecutor,
    private val sdkLocator: AndroidSdkLocator
) {
    
    suspend fun discoverAvds(): Result<List<Emulator>> {
        val sdkPath = sdkLocator.locateSdk()
            ?: return Result.failure(Exception("Android SDK not found. Please set ANDROID_HOME or ANDROID_SDK_ROOT environment variable."))
        
        val emulatorPath = sdkLocator.locateEmulator(sdkPath)
            ?: return Result.failure(Exception("Emulator executable not found in Android SDK at: $sdkPath"))
        
        return try {
            val result = processExecutor.execute(
                command = emulatorPath,
                args = listOf("-list-avds")
            )
            
            if (result.exitCode == 0) {
                val avdNames = result.stdout
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                
                val emulators = avdNames.map { name ->
                    Emulator(name = name, state = EmulatorState.Stopped)
                }
                
                Result.success(emulators)
            } else {
                Result.failure(Exception("Failed to list AVDs: ${result.stderr}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
