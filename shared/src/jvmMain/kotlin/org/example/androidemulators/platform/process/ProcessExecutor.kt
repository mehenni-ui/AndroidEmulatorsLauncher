package org.example.androidemulators.platform.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader

data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

interface ProcessExecutor {
    suspend fun execute(
        command: String,
        args: List<String>,
        workingDirectory: String? = null,
        stdin: String? = null
    ): ProcessResult
}

class ProcessExecutorImpl : ProcessExecutor {
    override suspend fun execute(
        command: String,
        args: List<String>,
        workingDirectory: String?,
        stdin: String?
    ): ProcessResult = withContext(Dispatchers.IO) {
        val processBuilder = ProcessBuilder(listOf(command) + args)
        workingDirectory?.let { processBuilder.directory(java.io.File(it)) }
        
        val process = processBuilder.start()
        stdin?.let { input ->
            process.outputStream.bufferedWriter().use { writer ->
                writer.write(input)
            }
        } ?: process.outputStream.close()
        
        val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
        val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
        
        val exitCode = process.waitFor()
        
        ProcessResult(exitCode, stdout, stderr)
    }
}
