package com.n8n.mobile.studio.runtime.opencode

import com.n8n.mobile.studio.runtime.RuntimePins

/**
 * Version facts for the OpenCode payload.
 *
 * Upstream OpenCode (`anomalyco/opencode`, formerly `sst/opencode`) publishes
 * prebuilt single-file binaries for glibc/musl/darwin/windows — none of which run
 * on Bionic. The payload build therefore bundles the pinned upstream sources for
 * Node with the shims in `runtime/opencode/shims/`, and if that bundle cannot be
 * produced the payload simply stays unpackaged: this app never substitutes a
 * remote service or a stub for the real runtime.
 */
object OpenCodeVersion {
    const val PINNED = RuntimePins.OPENCODE_VERSION

    /** Entry script inside the payload that starts the local server. */
    const val ENTRY = "opencode/server.js"

    const val DEFAULT_PORT = 8765

    /** CLI arguments handed to the bundled server entry. */
    val SERVE_ARGS: List<String> = listOf(
        "serve",
        "--port",
        "{port}",
        "--hostname",
        "127.0.0.1",
    )
}
