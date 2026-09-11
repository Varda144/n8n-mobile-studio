package com.n8n.mobile.studio.runtime

/**
 * Thread-safe snapshot holder for a single component's status.
 */
class RuntimeState(val component: EmbeddedComponent) {

    private var snapshot: EmbeddedComponentStatus = EmbeddedComponentStatus(component = component)

    @Synchronized
    fun get(): EmbeddedComponentStatus = snapshot

    @Synchronized
    fun update(transform: (EmbeddedComponentStatus) -> EmbeddedComponentStatus) {
        snapshot = transform(snapshot)
    }
}