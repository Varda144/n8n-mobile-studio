package com.n8n.mobile.studio.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.EmbeddedComponentStatus
import com.n8n.mobile.studio.runtime.EmbeddedProcessState
import com.n8n.mobile.studio.runtime.service.RuntimeClient

/**
 * Ambient handle on the runtime service.
 *
 * Provided once at the top of the composition so every screen (dashboard, hub,
 * settings) observes the same live state instead of each one polling.
 */
val LocalRuntimeClient = staticCompositionLocalOf<RuntimeClient?> { null }

/** Convenience: status of one component from the ambient client. */
fun RuntimeClient?.statusOf(component: EmbeddedComponent): EmbeddedComponentStatus =
    this?.statuses?.value?.get(component) ?: EmbeddedComponentStatus(component)

fun EmbeddedComponentStatus.isBusy(): Boolean =
    state == EmbeddedProcessState.STARTING || state == EmbeddedProcessState.STOPPING

/** Short, human state label used across screens. */
fun EmbeddedProcessState.label(): String = when (this) {
    EmbeddedProcessState.NOT_INSTALLED -> "NOT INSTALLED"
    EmbeddedProcessState.STOPPED -> "STOPPED"
    EmbeddedProcessState.STARTING -> "STARTING"
    EmbeddedProcessState.RUNNING -> "RUNNING"
    EmbeddedProcessState.DEGRADED -> "DEGRADED"
    EmbeddedProcessState.STOPPING -> "STOPPING"
    EmbeddedProcessState.FAILED -> "FAILED"
}
