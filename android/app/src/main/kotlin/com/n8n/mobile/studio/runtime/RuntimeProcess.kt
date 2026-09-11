package com.n8n.mobile.studio.runtime

import com.n8n.mobile.studio.core.AppResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Low-level process launching and liveness checks.
 */
object RuntimeProcess {

    /**
     * Launch a command; optionally tee merged output to [logFile].
     */
    suspend fun launch(
        command: List<String>,
        cwd: File,
        env: Map<String, String> = emptyMap(),
        logFile: File? = null,
    ): Result<Long> = withContext(Dispatchers.IO) {
        AppResult.run {
            val builder = ProcessBuilder(command)
                .directory(cwd)
                .redirectErrorStream(true)
            val processEnv = builder.environment()
            processEnv.putAll(env)
            if (logFile != null) {
                builder.redirectOutput(
                    ProcessBuilder.Redirect.appendTo(
                        logFile.apply { parentFile?.mkdirs() },
                    ),
                )
            }
            builder.start().pid()
        }.toResult()
    }

    fun isAlive(pid: Long): Boolean =
        ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
}