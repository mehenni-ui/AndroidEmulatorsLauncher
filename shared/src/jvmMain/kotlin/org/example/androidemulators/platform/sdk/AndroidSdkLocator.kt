package org.example.androidemulators.platform.sdk

import java.io.File

class AndroidSdkLocator {
    
    fun locateSdk(): String? {
        // Check environment variables first
        System.getenv("ANDROID_HOME")?.let { return validateSdkPath(it) }
        System.getenv("ANDROID_SDK_ROOT")?.let { return validateSdkPath(it) }
        
        // Check standard macOS location
        val standardPath = System.getProperty("user.home") + "/Library/Android/sdk"
        validateSdkPath(standardPath)?.let { return it }
        
        // Check Android Studio default location
        val androidStudioPath = System.getProperty("user.home") + "/Android/Sdk"
        validateSdkPath(androidStudioPath)?.let { return it }
        
        return null
    }
    
    fun locateEmulator(sdkPath: String): String? {
        val emulatorPath = File(sdkPath, "emulator/emulator")
        return if (emulatorPath.exists()) emulatorPath.absolutePath else null
    }
    
    fun locateAdb(sdkPath: String): String? {
        val adbPath = File(sdkPath, "platform-tools/adb")
        return if (adbPath.exists()) adbPath.absolutePath else null
    }

    fun locateAvdManager(sdkPath: String): String? {
        val commandLineToolsDirectory = File(sdkPath, "cmdline-tools")
        val candidates = buildList {
            add(File(commandLineToolsDirectory, "latest/bin/avdmanager"))
            commandLineToolsDirectory.listFiles()
                ?.filter { it.isDirectory }
                ?.forEach { add(File(it, "bin/avdmanager")) }
            add(File(sdkPath, "tools/bin/avdmanager"))
        }
        return candidates.firstOrNull { it.isFile && it.canExecute() }?.absolutePath
    }
    
    private fun validateSdkPath(path: String): String? {
        val sdkDir = File(path)
        return if (sdkDir.exists() && sdkDir.isDirectory) {
            sdkDir.absolutePath
        } else {
            null
        }
    }
}
