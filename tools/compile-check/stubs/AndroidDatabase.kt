@file:Suppress("UNUSED_PARAMETER", "PackageDirectoryMismatch", "unused")
package android.database

interface Cursor : AutoCloseable {
    fun moveToFirst(): Boolean
    fun moveToNext(): Boolean
    fun getColumnIndex(columnName: String): Int
    fun getString(columnIndex: Int): String?
    fun getLong(columnIndex: Int): Long
    fun getInt(columnIndex: Int): Int
    fun isNull(columnIndex: Int): Boolean
    fun getCount(): Int
    override fun close()
    fun isClosed(): Boolean
}
