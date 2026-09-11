package com.n8n.mobile.studio.runtime

/**
 * Pinned runtime versions and payload identities.
 *
 * These values are the *fallback* documentation of what the project targets;
 * the authoritative, per-build values always come from the packaged
 * `assets/runtime/manifest.json` ([RuntimeManifest]). Keeping both means a
 * payload built by `scripts/prepare-runtime.sh` can never silently drift from
 * what the APK claims to embed.
 */
object RuntimePins {

    /** Manifest schema this app understands. */
    const val SCHEMA_VERSION = 2

    /** Node.js major line required by n8n 2.x (`engines.node >= 24`). */
    const val NODE_MAJOR = 24
    const val NODE_VERSION = "24.9.0"
    const val NODE_DIST_OS = "android"

    const val N8N_VERSION = "2.38.7"
    const val OPENCODE_VERSION = "1.18.30"

    /** Shared-library names produced by the Android Node cross-build. */
    const val NODE_LAUNCHER_LIB = "libnoderun.so"
    const val NODE_CORE_LIB = "libnode.so"

    /**
     * ABIs the runtime build produces. `arm64-v8a` is the primary target;
     * 32-bit and emulator ABIs are buildable but not shipped by default because
     * n8n's memory profile is a poor fit for 32-bit address spaces.
     */
    val SUPPORTED_ABIS: List<String> = listOf("arm64-v8a", "x86_64")
    val PRIMARY_ABI: String = "arm64-v8a"

    /** Payload archive name inside `assets/runtime/<component>/`. */
    const val PAYLOAD_ARCHIVE = "payload.zip"

    /** Marker file written next to an installed payload. */
    const val INSTALL_MARKER = "install.json"

    /** Default per-component V8 heap cap handed to Node via NODE_OPTIONS. */
    const val DEFAULT_NODE_HEAP_MB = 512
    const val MIN_NODE_HEAP_MB = 128
}
