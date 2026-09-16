package com.n8n.mobile.studio.runtime

import android.content.Context
import android.os.Build
import com.n8n.mobile.studio.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.*
import java.util.concurrent.TimeUnit

class DebInstaller(private val ctx: Context) {
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val paths = RuntimePaths(ctx)
    val termuxArch: String get() = when {
        Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "aarch64"
        Build.SUPPORTED_ABIS.contains("armeabi-v7a") -> "arm"
        Build.SUPPORTED_ABIS.contains("x86_64") -> "x86_64"
        else -> "aarch64"
    }
    suspend fun ensureNodeInstalled(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (paths.nodeBin().exists() && paths.nodeBin().canExecute()) {
                val v = runCatching { ProcessBuilder(paths.nodeBin().absolutePath, "--version").start().inputStream.bufferedReader().readText().trim() }.getOrNull()
                Logger.d("Node already installed: $v arch=$termuxArch")
                if (v != null) return@withContext Result.success(Unit)
            }
            Logger.d("Installing Termux Node for arch $termuxArch")
            installFromTermuxRepo()
            paths.nodeBin().setExecutable(true)
            paths.npmBin().setExecutable(true)
            File(paths.usrBin, "npx").setExecutable(true)
            Logger.d("Node install done: ${paths.nodeBin().exists()}")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("Node install failed", e)
            Result.failure(e)
        }
    }

    private fun fetchPackages(): String {
        val url = "https://packages.termux.dev/apt/termux-main/dists/stable/main/binary-$termuxArch/Packages"
        val req = Request.Builder().url(url).header("User-Agent","n8n-mobile/1.0").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Packages fetch failed ${resp.code}")
            return resp.body!!.string()
        }
    }

    private fun parsePackages(raw: String): Map<String, Map<String,String>> {
        val out = mutableMapOf<String, MutableMap<String,String>>()
        var curPkg: String? = null
        var cur = mutableMapOf<String,String>()
        var lastKey: String? = null
        for (line in raw.lines()) {
            if (line.isBlank()) {
                if (curPkg!=null) { out[curPkg]=cur; cur=mutableMapOf() }
                curPkg=null; lastKey=null; continue
            }
            if (line.startsWith(" ")) {
                lastKey?.let { cur[it] = cur[it] + "\n" + line.trim() }
                continue
            }
            val idx = line.indexOf(':')
            if (idx>0) {
                val k = line.substring(0,idx).trim()
                val v = line.substring(idx+1).trim()
                cur[k]=v
                lastKey=k
                if (k=="Package") curPkg=v
            }
        }
        if (curPkg!=null && cur.isNotEmpty()) out[curPkg]=cur
        return out
    }

    private fun findDebUrl(pkg: String, packages: Map<String, Map<String,String>>): String? {
        val meta = packages[pkg] ?: return null
        val filename = meta["Filename"] ?: return null
        return "https://packages.termux.dev/apt/termux-main/$filename"
    }

    private fun installFromTermuxRepo() {
        val raw = fetchPackages()
        val pkgs = parsePackages(raw)
        // install minimal bootstrap set in order
        val toInstall = linkedSetOf<String>()
        // core deps
        val depsOrder = listOf("libandroid-support","libandroid-spawn","libcrypt","zlib","openssl","libnghttp2","libuv","libandroid-exec","termux-tools")
        // Add nodejs last
        for (d in depsOrder) if (pkgs.containsKey(d)) toInstall.add(d)
        toInstall.add("nodejs")
        // also need ca-certificates for npm TLS
        if (pkgs.containsKey("ca-certificates")) toInstall.add("ca-certificates")
        Logger.d("Installing debs: $toInstall")
        for (pkg in toInstall) {
            val url = findDebUrl(pkg, pkgs) ?: continue
            Logger.d("Downloading $pkg from $url")
            downloadAndExtractDeb(url, pkg)
        }
        // create symlink for node if needed
        fixSymlinks()
    }

    private fun downloadAndExtractDeb(url: String, pkg: String) {
        val debFile = File(paths.cache, "$pkg.deb")
        downloadToFile(url, debFile)
        extractDeb(debFile, paths.base)
        debFile.delete()
    }

    private fun downloadToFile(url: String, out: File) {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Download $url failed ${resp.code}")
            FileOutputStream(out).use { o -> resp.body!!.byteStream().copyTo(o) }
        }
    }

    private fun extractDeb(deb: File, destRoot: File) {
        FileInputStream(deb).use { fis ->
            ArArchiveInputStream(fis).use { ar ->
                var entry = ar.nextEntry
                while (entry != null) {
                    if (entry.name.startsWith("data.")) {
                        val dataBytes = ar.readBytes()
                        extractDataTar(dataBytes, destRoot)
                        break
                    }
                    entry = ar.nextEntry
                }
            }
        }
    }

    private fun extractDataTar(bytes: ByteArray, destRoot: File) {
        ByteArrayInputStream(bytes).use { bais ->
            val compIn: InputStream = when {
                bytes.size>2 && bytes[0]==0x1F.toByte() && bytes[1]==0x8B.toByte() -> GzipCompressorInputStream(bais)
                bytes.size>6 && bytes[0]==0xFD.toByte() && bytes[1]==0x37.toByte() -> XZCompressorInputStream(bais)
                bytes.size>2 && bytes[0]==0x42.toByte() && bytes[1]==0x5A.toByte() -> BZip2CompressorInputStream(bais)
                else -> bais
            }
            TarArchiveInputStream(compIn).use { tar ->
                var e = tar.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        val name = e.name.removePrefix("./").removePrefix("/")
                        val outFile = File(destRoot, name)
                        // only allow usr/ and etc/ to avoid overwriting app files
                        if (name.startsWith("usr/") || name.startsWith("etc/")) {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { tar.copyTo(it) }
                            if (e.mode and 0x40 != 0 || name.contains("bin/")) {
                                outFile.setExecutable(true, false)
                            }
                        }
                    }
                    e = tar.nextEntry
                }
            }
        }
    }

    private fun fixSymlinks() {
        // Termux debs may contain symlinks not extracted via TarArchive; ensure node is executable
        val node = paths.nodeBin()
        if (!node.exists()) {
            // fallback: find node elsewhere
            val found = paths.usr.walkTopDown().firstOrNull { it.name=="node" && it.isFile }
            if (found!=null && found!=node) {
                found.copyTo(node, overwrite=true)
                node.setExecutable(true)
            }
        }
    }
}
