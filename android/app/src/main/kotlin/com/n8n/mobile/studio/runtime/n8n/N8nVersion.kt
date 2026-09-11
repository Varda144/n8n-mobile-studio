package com.n8n.mobile.studio.runtime.n8n

import com.n8n.mobile.studio.runtime.RuntimePins

/**
 * Version facts for the n8n payload.
 *
 * [PINNED] is what the repository builds against; the version actually running on
 * a device always comes from the packaged manifest and the payload's own
 * `install.json`, never from this constant.
 */
object N8nVersion {
    const val PINNED = RuntimePins.N8N_VERSION

    /** Minimum Node major required by n8n 2.x (`engines.node`). */
    const val REQUIRED_NODE_MAJOR = RuntimePins.NODE_MAJOR

    /** Entry script inside the payload, relative to the payload root. */
    const val ENTRY = "n8n/bin/n8n"

    const val DEFAULT_PORT = 5678

    fun isCompatible(nodeVersion: String?): Boolean {
        val major = nodeVersion?.trim()?.removePrefix("v")?.substringBefore('.')?.toIntOrNull()
        return major != null && major >= REQUIRED_NODE_MAJOR
    }
}
