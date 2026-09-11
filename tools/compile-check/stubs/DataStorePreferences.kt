@file:Suppress("UNUSED_PARAMETER", "PackageDirectoryMismatch", "unused")

package androidx.datastore.preferences

import android.content.Context
import androidx.datastore.preferences.core.DataStore
import androidx.datastore.preferences.core.InMemoryDataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Offline check stub for `preferencesDataStore { }` (see DataStore.kt). Each
 * named store is a process-wide singleton, matching the real delegate's contract.
 */
private val stores = HashMap<String, DataStore<Preferences>>()

fun preferencesDataStore(
    name: String,
    produceFile: (() -> File)? = null,
): ReadOnlyProperty<Context, DataStore<Preferences>> =
    object : ReadOnlyProperty<Context, DataStore<Preferences>> {
        override fun getValue(thisRef: Context, property: KProperty<*>): DataStore<Preferences> =
            stores.getOrPut(name) { InMemoryDataStore(Preferences()) }
    }
