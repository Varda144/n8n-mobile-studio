package com.n8n.mobile.studio.runtime.opencode

data class OpenCodeConfig(
    val port: Int = 8765,
    val host: String = "127.0.0.1",
    val rootFolder: String = "opencode-home",
    val maxMemoryMb: Int = 1024,
) {
    val endpoint: String get() = "http://$host:$port"
}