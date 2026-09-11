package com.n8n.mobile.studio.runtime

import com.n8n.mobile.studio.core.AppConfig

/** How much memory the device currently has to spare. */
data class MemorySnapshot(
    val totalMb: Long,
    val availMb: Long,
    val lowMemory: Boolean,
    /** `ActivityManager.getMemoryClass()` — the per-app heap ceiling in MiB. */
    val memoryClassMb: Int,
    /** Last `onTrimMemory` level, 0 when unknown. */
    val trimLevel: Int = 0,
) {
    val usedFraction: Float get() = if (totalMb <= 0) 0f else 1f - (availMb.toFloat() / totalMb)
}

enum class MemoryPressure { NORMAL, MODERATE, CRITICAL }

/** What the supervisor should do with the runtime given available memory. */
data class MemoryPlan(
    val pressure: MemoryPressure,
    val allowConcurrentRuntimes: Boolean,
    /** Components that should be stopped to protect the foreground app. */
    val stopComponents: List<EmbeddedComponent>,
    /** Components that must wait for another runtime to become ready first. */
    val deferComponents: List<EmbeddedComponent>,
)

/**
 * Memory policy for low-end Android devices.
 *
 * Phones kill background processes aggressively, so the studio decides *before*
 * spawning whether two Node processes are affordable, how large a V8 heap each
 * one may claim, and which runtime to sacrifice under pressure. n8n keeps
 * priority because it is the primary product surface; OpenCode is stopped first.
 *
 * Pure functions: fully unit tested.
 */
object RuntimeMemoryPolicy {

    const val MIN_AVAIL_MB: Long = 256
    const val MODERATE_FRACTION: Float = 0.15f
    const val CRITICAL_FRACTION: Float = 0.07f

    fun pressure(snapshot: MemorySnapshot): MemoryPressure = when {
        snapshot.lowMemory || snapshot.trimLevel >= TRIM_CRITICAL -> MemoryPressure.CRITICAL
        snapshot.totalMb <= 0 -> MemoryPressure.NORMAL
        snapshot.availMb.toFloat() / snapshot.totalMb.toFloat() <= CRITICAL_FRACTION -> MemoryPressure.CRITICAL
        snapshot.availMb.toFloat() / snapshot.totalMb.toFloat() <= MODERATE_FRACTION -> MemoryPressure.MODERATE
        else -> MemoryPressure.NORMAL
    }

    /**
     * V8 heap budget for a component: never more than half of the free memory
     * minus a reserve for the UI, never below [RuntimePins.MIN_NODE_HEAP_MB].
     */
    fun nodeHeapMb(snapshot: MemorySnapshot, requestedMb: Int): Int {
        val reserve = UI_RESERVE_MB
        val affordable = ((snapshot.availMb - reserve) / 2).coerceAtLeast(0)
        val capped = minOf(requestedMb.toLong(), affordable)
        return capped.coerceAtLeast(RuntimePins.MIN_NODE_HEAP_MB.toLong()).toInt()
    }

    /**
     * Decide whether both runtimes may run together, and who has to wait.
     */
    fun plan(
        snapshot: MemorySnapshot,
        config: AppConfig,
        running: Set<EmbeddedComponent> = emptySet(),
    ): MemoryPlan {
        val pressure = pressure(snapshot)
        val smallDevice = snapshot.memoryClassMb in 1 until config.serialStartMemoryClass
        val tight = snapshot.availMb < MIN_AVAIL_MB * 2
        val allowConcurrent = !smallDevice && pressure != MemoryPressure.CRITICAL && !tight

        val stop = when (pressure) {
            MemoryPressure.NORMAL -> emptyList()
            MemoryPressure.MODERATE -> if (running.contains(EmbeddedComponent.OPENCODE) && running.size > 1) {
                listOf(EmbeddedComponent.OPENCODE)
            } else {
                emptyList()
            }
            MemoryPressure.CRITICAL -> listOf(EmbeddedComponent.OPENCODE)
        }

        val defer = if (allowConcurrent) {
            emptyList()
        } else {
            EmbeddedComponent.entries.filter { it.priority > EmbeddedComponent.N8N.priority }
        }

        return MemoryPlan(
            pressure = pressure,
            allowConcurrentRuntimes = allowConcurrent,
            stopComponents = stop,
            deferComponents = defer,
        )
    }

    /** `onTrimMemory` levels that mean "free everything you can". */
    private const val TRIM_CRITICAL = 80 // TRIM_MEMORY_COMPLETE
    private const val UI_RESERVE_MB = 96L
}
