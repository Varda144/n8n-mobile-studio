package com.n8n.mobile.studio.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for both components' runtime status.
 *
 * The foreground service owns one instance; the GUI and the terminal observe
 * the same [statuses] flow, so what the user sees in the app is exactly what the
 * supervisor is doing.
 */
class RuntimeState(
    initial: List<EmbeddedComponentStatus> = EmbeddedComponent.entries.map {
        EmbeddedComponentStatus(component = it)
    },
) {

    private val guard = Any()
    private val _statuses = MutableStateFlow(initial.associateBy { it.component })

    val statuses: StateFlow<Map<EmbeddedComponent, EmbeddedComponentStatus>> = _statuses.asStateFlow()

    fun status(component: EmbeddedComponent): EmbeddedComponentStatus =
        _statuses.value[component] ?: EmbeddedComponentStatus(component)

    fun snapshot(): List<EmbeddedComponentStatus> =
        EmbeddedComponent.entries.map { status(it) }

    fun set(status: EmbeddedComponentStatus) {
        synchronized(guard) {
            _statuses.value = _statuses.value + (status.component to status)
        }
    }

    fun update(
        component: EmbeddedComponent,
        transform: (EmbeddedComponentStatus) -> EmbeddedComponentStatus,
    ) {
        synchronized(guard) {
            val current = _statuses.value[component] ?: EmbeddedComponentStatus(component)
            _statuses.value = _statuses.value + (component to transform(current))
        }
    }

    fun anyActive(): Boolean = _statuses.value.values.any { it.state.isActive }

    fun activeComponents(): Set<EmbeddedComponent> =
        _statuses.value.filterValues { it.state.isActive }.keys
}
