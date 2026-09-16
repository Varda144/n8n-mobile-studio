package com.n8n.mobile.studio.runtime

import android.content.Context
import com.n8n.mobile.studio.core.AppConfig
import com.n8n.mobile.studio.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class NodeRuntime(private val ctx: Context) {
    private val paths = RuntimePaths(ctx)
    private val debInstaller = DebInstaller(ctx)

    suspend fun ensureReady(): Result<Unit> = debInstaller.ensureNodeInstalled()

    fun nodeBin(): File = paths.nodeBin()
    fun npmBin(): File = paths.npmBin()
    fun npxBin(): File = paths.npxBin()

    fun env(): Map<String,String> {
        val usr = paths.usr.absolutePath
        return mapOf(
            "PREFIX" to usr,
            "HOME" to paths.base.absolutePath,
            "TMPDIR" to paths.tmp.absolutePath,
            "LD_LIBRARY_PATH" to "${paths.usrLib.absolutePath}:${System.getenv("LD_LIBRARY_PATH") ?: ""}",
            "PATH" to "${paths.usrBin.absolutePath}:/system/bin:/system/xbin",
            "TERM" to "xterm-256color",
            "LANG" to "en_US.UTF-8"
        )
    }

    suspend fun npmInstall(targetDir: File, pkg: String, version: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            targetDir.mkdirs()
            val pkgJson = File(targetDir, "package.json")
            if (!pkgJson.exists()) {
                pkgJson.writeText("""{"name":"${targetDir.name}","private":true,"dependencies":{"$pkg":"$version"}}""")
            }
            val npm = npmBin()
            if (!npm.exists()) return@withContext Result.failure(IllegalStateException("npm not found"))
            npm.setExecutable(true)
            val pb = ProcessBuilder(npm.absolutePath, "install", "--prefix", targetDir.absolutePath, "--no-audit","--no-fund","--loglevel","error")
            pb.directory(targetDir)
            pb.environment().putAll(env())
            pb.redirectErrorStream(true)
            Logger.d("npm install $pkg@$version in ${targetDir.name}")
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader().readText()
            val ok = proc.waitFor(20, TimeUnit.MINUTES)
            if (!ok || proc.exitValue()!=0) {
                Logger.e("npm install failed: $out")
                return@withContext Result.failure(RuntimeException("npm install failed: ${out.take(500)}"))
            }
            Logger.d("npm install ok $pkg")
            Result.success(Unit)
        } catch(e:Exception){ Logger.e("npmInstall error",e); Result.failure(e) }
    }

    fun runNode(args: List<String>, wd: File, extraEnv: Map<String,String> = emptyMap()): Process {
        val pb = ProcessBuilder(listOf(nodeBin().absolutePath) + args)
        pb.directory(wd)
        val env = pb.environment()
        env.putAll(env())
        env.putAll(extraEnv)
        pb.redirectErrorStream(true)
        return pb.start()
    }

    fun runNpmBin(binName: String, args: List<String>, wd: File, extraEnv: Map<String,String> = emptyMap()): Process {
        val bin = File(paths.usrBin, binName)
        bin.setExecutable(true)
        val target = if (bin.exists()) bin else {
            // fallback to npx
            return runNode(listOf(npxBin().absolutePath, binName) + args, wd, extraEnv)
        }
        val pb = ProcessBuilder(listOf(target.absolutePath) + args)
        pb.directory(wd)
        val env = pb.environment()
        env.putAll(env())
        env.putAll(extraEnv)
        pb.redirectErrorStream(true)
        return pb.start()
    }
}
