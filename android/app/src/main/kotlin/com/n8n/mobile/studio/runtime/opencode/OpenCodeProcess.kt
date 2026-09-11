package com.n8n.mobile.studio.runtime.opencode

import android.content.Context
import com.n8n.mobile.studio.runtime.RuntimePaths
import com.n8n.mobile.studio.runtime.RuntimeProcess
import java.io.File

class OpenCodeProcess(private val config: OpenCodeConfig = OpenCodeConfig()) {
    suspend fun launch(context: Context): Result<Long> {
        val executable = File(RuntimePaths.bin(context), OpenCodeVersion.REQUIRED_EXECUTABLE)
        if (!executable.exists()) {
            return Result.failure(
                IllegalStateException("OpenCode runtime not packaged yet: expected ${executable.absolutePath}"),
            )
        }
        val root = File(RuntimePaths.data(context), config.rootFolder)
        if (!root.exists() && !root.mkdirs()) {
            return Result.failure(IllegalStateException("Failed to create ${root.absolutePath}"))
        }
        return RuntimeProcess.launch(
            command = listOf(executable.absolutePath),
            cwd = root,
            env = mapOf(
                "OPENCODE_PORT" to config.port.toString(),
                "OPENCODE_HOST" to config.host,
            ),
            logFile = File(RuntimePaths.logs(context), "opencode.log"),
        )
    }
}