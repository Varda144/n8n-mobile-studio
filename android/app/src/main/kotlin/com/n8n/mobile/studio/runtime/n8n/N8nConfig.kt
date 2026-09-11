package com.n8n.mobile.studio.runtime.n8n

data class N8nConfig(
    val port: Int = 5678,
    val host: String = "127.0.0.1",
    val userFolder: String = "n8n-home",
    val maxMemoryMb: Int = 1024,
) {
    val endpoint: String get() = "http://$host:$port"
}