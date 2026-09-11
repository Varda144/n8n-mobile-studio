package com.n8n.mobile.studio.runtime

import com.n8n.mobile.studio.core.AppResult
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Low-level process launching and liveness checks.
 *
 * Android's [java.lang.Process] has no `pid()`/`ProcessHandle` (Java 9 APIs),
 * so launched processes are tracked in-process under a generated id instead.
 */
object RuntimeProcess {

    private val processes = ConcurrentHashMap<Long, Process>()
    private val counter = AtomicLong(1)

    /**
     * Launch a command; optionally tee merged output to [logFile]. Returns a
     * generated id usable with [isAlive].
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
            val process = builder.start()
            val id = counter.getAndIncrement()
            processes[id] = process
            id
        }.toResult()
    }

    fun isAlive(id: Long): Boolean = processes[id]?.isAlive() ?: false
}