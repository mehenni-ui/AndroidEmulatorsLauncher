package org.example.androidemulators.platform.emulator

import org.example.androidemulators.platform.process.ProcessExecutor
import org.example.androidemulators.platform.sdk.AndroidSdkLocator
import java.io.File

data class DeviceProfile(
    val id: String,
    val name: String
)

data class SystemImage(
    val packageName: String,
    val displayName: String
)

data class AvdCreationOptions(
    val deviceProfiles: List<DeviceProfile>,
    val systemImages: List<SystemImage>
)

class AvdCreator(
    private val processExecutor: ProcessExecutor,
    private val sdkLocator: AndroidSdkLocator
) {
    suspend fun loadOptions(): Result<AvdCreationOptions> {
        val sdkPath = sdkLocator.locateSdk()
            ?: return Result.failure(Exception("Android SDK not found. Please set ANDROID_HOME or ANDROID_SDK_ROOT."))
        val avdManagerPath = sdkLocator.locateAvdManager(sdkPath)
            ?: return Result.failure(Exception("avdmanager was not found. Install Android SDK Command-line Tools."))

        return try {
            val deviceResult = processExecutor.execute(avdManagerPath, listOf("list", "device"))
            if (deviceResult.exitCode != 0) {
                return Result.failure(Exception(deviceResult.stderr.ifBlank { "Unable to load Android device profiles." }))
            }

            val devices = parseDeviceProfiles(deviceResult.stdout)
            val images = findInstalledSystemImages(sdkPath)
            when {
                devices.isEmpty() -> Result.failure(Exception("No Android device profiles were found."))
                images.isEmpty() -> Result.failure(Exception("No Android system images are installed."))
                else -> Result.success(AvdCreationOptions(devices, images))
            }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    suspend fun createAvd(
        name: String,
        device: DeviceProfile,
        systemImage: SystemImage
    ): Result<Unit> {
        val sdkPath = sdkLocator.locateSdk()
            ?: return Result.failure(Exception("Android SDK not found."))
        val avdManagerPath = sdkLocator.locateAvdManager(sdkPath)
            ?: return Result.failure(Exception("avdmanager was not found. Install Android SDK Command-line Tools."))

        return try {
            val result = processExecutor.execute(
                command = avdManagerPath,
                args = listOf("create", "avd", "--force", "--name", name, "--package", systemImage.packageName, "--device", device.id),
                stdin = "no\n"
            )
            if (result.exitCode == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(result.stderr.ifBlank { result.stdout.ifBlank { "Failed to create the emulator." } }))
            }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun parseDeviceProfiles(output: String): List<DeviceProfile> {
        val idPattern = Regex("^id:\\s*\\d+\\s+or\\s+\"([^\"]+)\"")
        val namePattern = Regex("^Name:\\s*(.+)$")
        var currentId: String? = null
        val profiles = mutableListOf<DeviceProfile>()

        output.lineSequence().forEach { line ->
            val trimmed = line.trim()
            idPattern.find(trimmed)?.let { match -> currentId = match.groupValues[1] }
            namePattern.find(trimmed)?.let { match ->
                currentId?.let { id ->
                    profiles += DeviceProfile(id = id, name = match.groupValues[1])
                    currentId = null
                }
            }
        }
        return profiles.distinctBy { it.id }.sortedBy { it.name }
    }

    private fun findInstalledSystemImages(sdkPath: String): List<SystemImage> {
        val systemImagesDirectory = File(sdkPath, "system-images")
        if (!systemImagesDirectory.isDirectory) return emptyList()

        return systemImagesDirectory.walkTopDown()
            .maxDepth(4)
            .filter { it.isDirectory && it.parentFile?.parentFile?.parentFile == systemImagesDirectory }
            .map { imageDirectory ->
                val relativePath = imageDirectory.relativeTo(systemImagesDirectory).invariantSeparatorsPath
                val parts = relativePath.split('/')
                SystemImage(
                    packageName = "system-images;${parts.joinToString(";")}",
                    displayName = parts.joinToString(" • ") { part ->
                        if (part.startsWith("android-")) "Android ${part.removePrefix("android-")}" else part.replace('_', ' ')
                    }
                )
            }
            .sortedBy { it.displayName }
            .toList()
    }
}
