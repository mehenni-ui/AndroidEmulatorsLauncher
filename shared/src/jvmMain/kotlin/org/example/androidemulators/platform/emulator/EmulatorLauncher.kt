package org.example.androidemulators.platform.emulator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.androidemulators.platform.process.ProcessExecutor
import org.example.androidemulators.platform.sdk.AndroidSdkLocator
import java.io.File

class EmulatorLauncher(
    private val processExecutor: ProcessExecutor,
    private val sdkLocator: AndroidSdkLocator
) {
    
    private val runningProcesses = mutableMapOf<String, Process>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    suspend fun launch(avdName: String): Result<Unit> {
        val sdkPath = sdkLocator.locateSdk()
            ?: return Result.failure(Exception("Android SDK not found."))
        
        val emulatorPath = sdkLocator.locateEmulator(sdkPath)
            ?: return Result.failure(Exception("Emulator executable not found."))
        
        // Check if already running
        if (isRunning(avdName)) {
            return Result.failure(Exception("Emulator '$avdName' is already running."))
        }
        
        return try {
            withContext(Dispatchers.IO) {
                val process = ProcessBuilder(emulatorPath, "-avd", avdName)
                    .start()
                
                runningProcesses[avdName] = process
                
                // Monitor process and remove from map when it exits
                scope.launch {
                    process.waitFor()
                    runningProcesses.remove(avdName)
                }
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun isRunning(avdName: String): Boolean {
        val process = runningProcesses[avdName]
        return process?.isAlive == true
    }
    
    fun stop(avdName: String): Boolean {
        val process = runningProcesses[avdName] ?: return false
        return if (process.isAlive) {
            process.destroy()
            true
        } else {
            false
        }
    }
}
