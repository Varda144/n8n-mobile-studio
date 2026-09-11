package com.n8n.mobile.studio.runtime.n8n

import android.content.Context
import com.n8n.mobile.studio.runtime.RuntimePaths
import com.n8n.mobile.studio.runtime.RuntimeProcess
import java.io.File

class N8nProcess(private val config: N8nConfig = N8nConfig()) {
    suspend fun launch(context: Context): Result<Long> {
        val executable = File(RuntimePaths.bin(context), N8nVersion.REQUIRED_EXECUTABLE)
        if (!executable.exists()) {
            return Result.failure(
                IllegalStateException("n8n runtime not packaged yet: expected ${executable.absolutePath}"),
            )
        }
        val storage = N8nStorage(context, config)
        storage.ensure().getOrThrow()
        return RuntimeProcess.launch(
            command = listOf(executable.absolutePath, "start", "--tunnel=false"),
            cwd = storage.root(),
            env = mapOf(
                "N8N_PORT" to config.port.toString(),
                "N8N_HOST" to config.host,
            ),
            logFile = File(RuntimePaths.logs(context), "n8n.log"),
        )
    }
}