@file:Suppress("UNUSED_PARAMETER", "PackageDirectoryMismatch", "unused")

package androidx.datastore.preferences.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Offline check stub for the androidx DataStore preferences surface used by
 * `data/preferences`: values live in memory, which is enough to type-check — and
 * exercise — the settings layer without the Android framework. Never shipped.
 */
class Preferences(private val values: Map<Key<*>, Any?> = emptyMap()) {

    class Key<T>(val name: String) {
        override fun equals(other: Any?): Boolean = other is Key<*> && other.name == name
        override fun hashCode(): Int = name.hashCode()
        override fun toString(): String = name
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: Key<T>): T? = values[key] as T?

    operator fun contains(key: Key<*>): Boolean = values.containsKey(key)

    fun asMap(): Map<Key<*>, Any?> = values
}

class MutablePreferences internal constructor(private val map: MutableMap<Preferences.Key<*>, Any?>) {

    operator fun <T> set(key: Preferences.Key<T>, value: T) {
        map[key] = value
    }

    fun <T> remove(key: Preferences.Key<T>) {
        map.remove(key)
    }
}

fun booleanPreferencesKey(name: String): Preferences.Key<Boolean> = Preferences.Key(name)

fun intPreferencesKey(name: String): Preferences.Key<Int> = Preferences.Key(name)

fun longPreferencesKey(name: String): Preferences.Key<Long> = Preferences.Key(name)

fun stringPreferencesKey(name: String): Preferences.Key<String> = Preferences.Key(name)

fun doublePreferencesKey(name: String): Preferences.Key<Double> = Preferences.Key(name)

interface DataStore<T> {
    val data: Flow<T>

    suspend fun updateData(transform: suspend (T) -> T): T
}

class InMemoryDataStore<T>(initial: T) : DataStore<T> {
    private val state = MutableStateFlow(initial)

    override val data: Flow<T> get() = state.asStateFlow()

    override suspend fun updateData(transform: suspend (T) -> T): T {
        val updated = transform(state.value)
        state.update { updated }
        return updated
    }
}

suspend fun DataStore<Preferences>.edit(transform: suspend (MutablePreferences) -> Unit): Preferences =
    updateData { current ->
        val map = current.asMap().toMutableMap()
        transform(MutablePreferences(map))
        Preferences(map)
    }
