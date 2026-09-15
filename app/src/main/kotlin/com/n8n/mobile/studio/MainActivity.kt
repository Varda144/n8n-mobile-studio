package com.n8n.mobile.studio

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var logText: TextView

    private var n8nProcess: Process? = null
    private var isN8nRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        logText = findViewById(R.id.logText)

        setupWebView()

        startButton.setOnClickListener { startN8n() }
        stopButton.setOnClickListener { stopN8n() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
    }

    private fun startN8n() {
        if (isN8nRunning) return

        appendLog("Starting n8n...")
        statusText.text = "Starting..."
        startButton.isEnabled = false

        Thread {
            try {
                // Install Node.js if needed
                val nodeDir = File(filesDir, "node")
                val nodeBin = File(nodeDir, "bin/node")

                if (!nodeBin.exists()) {
                    appendLog("Installing Node.js...")
                    installNodeJs(nodeDir)
                }

                // Install n8n if needed
                val n8nDir = File(filesDir, "n8n")
                val n8nBin = File(n8nDir, "node_modules/.bin/n8n")

                if (!n8nBin.exists()) {
                    appendLog("Installing n8n...")
                    installN8n(nodeDir, n8nDir)
                }

                // Start n8n
                appendLog("Starting n8n server...")
                startN8nServer(nodeDir, n8nDir)

                runOnUiThread {
                    isN8nRunning = true
                    statusText.text = "Running on port 5678"
                    startButton.isEnabled = false
                    stopButton.isEnabled = true
                    appendLog("n8n started!")
                    loadN8nUI()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Error: ${e.message}"
                    startButton.isEnabled = true
                    appendLog("Error: ${e.message}")
                }
            }
        }.start()
    }

    private fun stopN8n() {
        n8nProcess?.destroyForcibly()
        n8nProcess = null
        isN8nRunning = false
        statusText.text = "Stopped"
        startButton.isEnabled = true
        stopButton.isEnabled = false
        appendLog("n8n stopped")
    }

    private fun installNodeJs(nodeDir: File) {
        nodeDir.mkdirs()
        val url = "https://nodejs.org/dist/v20.11.0/node-v20.11.0-linux-arm64.tar.xz"
        val tempFile = File(cacheDir, "node.tar.xz")

        downloadFile(url, tempFile)

        val process = ProcessBuilder("tar", "-xf", tempFile.absolutePath, "-C", nodeDir.absolutePath, "--strip-components=1")
        process.redirectErrorStream(true)
        process.start().waitFor()

        tempFile.delete()

        File(nodeDir, "bin/node").setExecutable(true)
        File(nodeDir, "bin/npm").setExecutable(true)
    }

    private fun installN8n(nodeDir: File, n8nDir: File) {
        n8nDir.mkdirs()

        File(n8nDir, "package.json").writeText("""
            {
                "name": "n8n-mobile",
                "version": "1.0.0",
                "dependencies": {
                    "n8n": "1.28.0"
                }
            }
        """.trimIndent())

        val processBuilder = ProcessBuilder(
            File(nodeDir, "bin/npm").absolutePath,
            "install",
            "--prefix", n8nDir.absolutePath,
            "--production"
        )
        processBuilder.directory(n8nDir)
        processBuilder.environment()["PATH"] = "${nodeDir.absolutePath}/bin:/system/bin"
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()
        process.waitFor(15, TimeUnit.MINUTES)
    }

    private fun startN8nServer(nodeDir: File, n8nDir: File) {
        val n8nBin = File(n8nDir, "node_modules/.bin/n8n")
        n8nBin.setExecutable(true)

        val processBuilder = ProcessBuilder(
            n8nBin.absolutePath,
            "start",
            "--port", "5678"
        )
        processBuilder.directory(n8nDir)
        processBuilder.environment()["PATH"] = "${nodeDir.absolutePath}/bin:/system/bin"
        processBuilder.environment()["N8N_DATA_FOLDER"] = File(filesDir, "n8n_data").absolutePath
        processBuilder.redirectErrorStream(true)

        n8nProcess = processBuilder.start()

        // Log output
        Thread {
            val reader = BufferedReader(InputStreamReader(n8nProcess!!.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                runOnUiThread { appendLog("n8n: $line") }
            }
        }.start()
    }

    private fun loadN8nUI() {
        Thread {
            var attempts = 0
            while (attempts < 30) {
                try {
                    val url = URL("http://127.0.0.1:5678")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 1000
                    conn.connect()
                    if (conn.responseCode == 200) {
                        runOnUiThread { webView.loadUrl("http://127.0.0.1:5678") }
                        return@Thread
                    }
                } catch (_: Exception) {}
                Thread.sleep(1000)
                attempts++
            }
        }.start()
    }

    private fun downloadFile(url: String, outputFile: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connect()
        val inputStream = connection.inputStream
        val outputStream = FileOutputStream(outputFile)
        inputStream.copyTo(outputStream)
        outputStream.close()
        inputStream.close()
        connection.disconnect()
    }

    private fun appendLog(message: String) {
        logText.append("$message\n")
    }
}
