@file:Suppress("UNUSED_PARAMETER", "PackageDirectoryMismatch")

package android.content

import java.io.File
import java.io.InputStream

/** Offline check stub: android.content subset (see AndroidFramework.kt). */
open class Context {

    open fun getApplicationContext(): Context = this
    val packageName: String = "com.n8n.mobile.studio"
    open val filesDir: File = File("/tmp/n8n-studio-check/files")
    open val cacheDir: File = File("/tmp/n8n-studio-check/cache")
    open val noBackupFilesDir: File = File("/tmp/n8n-studio-check/no-backup")
    open val assets: android.content.res.AssetManager = android.content.res.AssetManager()
    open val packageManager: android.content.pm.PackageManager = android.content.pm.PackageManager()
    open val contentResolver: android.content.ContentResolver = android.content.ContentResolver()
    val applicationInfo: android.content.pm.ApplicationInfo = android.content.pm.ApplicationInfo()

    fun getSystemService(name: String): Any? =
        when (name) {
            ACTIVITY_SERVICE -> android.app.ActivityManager()
            else -> null
        }

    fun <T> getSystemService(serviceClass: Class<T>): T = getSystemService(serviceClass.simpleName.lowercase()) as T

    fun checkSelfPermission(permission: String): Int = 0
    fun getString(resId: Int): String = ""
    fun getString(resId: Int, vararg formatArgs: Any?): String = ""
    fun getSharedPreferences(name: String, mode: Int): SharedPreferences = SharedPreferences()
    fun startService(intent: Intent?): Any? = null
    fun startForegroundService(intent: Intent?): Any? = null
    fun stopService(intent: Intent?): Boolean = true
    fun startActivity(intent: Intent?) {}
    fun bindService(intent: Intent?, conn: ServiceConnection, flags: Int): Boolean = true
    fun unbindService(conn: ServiceConnection) {}
    fun createDeviceProtectedStorageContext(): Context = this
    fun getExternalFilesDir(type: String?): File? = null

    companion object {
        const val MODE_PRIVATE = 0
        const val BIND_AUTO_CREATE = 1
        const val BIND_NOT_FOREGROUND = 4
        const val CONNECTIVITY_SERVICE = "connectivity"
        const val ACTIVITY_SERVICE = "activity"
        const val NOTIFICATION_SERVICE = "notification"
    }
}

open class ContextWrapper(private var base: Context = Context()) : Context() {
    override fun getApplicationContext(): Context = this
    open fun attachBaseContext(base: Context) {
        this.base = base
    }
}

class SharedPreferences {
    fun getString(key: String, defValue: String?): String? = defValue
    fun getInt(key: String, defValue: Int): Int = defValue
    fun getLong(key: Long, defValue: Long): Long = defValue
    fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
    fun contains(key: String): Boolean = false
    fun edit(): Editor = Editor()
    class Editor {
        fun putString(key: String, value: String?): Editor = this
        fun putInt(key: String, value: Int): Editor = this
        fun putLong(key: String, value: Long): Editor = this
        fun putBoolean(key: String, value: Boolean): Editor = this
        fun remove(key: String): Editor = this
        fun clear(): Editor = this
        fun apply() {}
        fun commit(): Boolean = true
    }
}

class Intent {
    var action: String? = null
    var data: android.net.Uri? = null
    var component: ComponentName? = null
    val extras: Bundle = Bundle()
    val flags: Int = 0

    constructor()
    constructor(action: String?)
    constructor(context: Context?, cls: Class<*>?)
    constructor(action: String?, uri: android.net.Uri?)

    fun setAction(action: String?): Intent = apply { this.action = action }
    fun addFlags(flags: Int): Intent = this
    fun setFlags(flags: Int): Intent = this
    fun setClass(context: Context?, cls: Class<*>?): Intent = this
    fun setPackage(packageName: String?): Intent = this
    fun setData(uri: android.net.Uri?): Intent = apply { this.data = uri }
    fun putExtra(name: String, value: String?): Intent = apply { extras.strings[name] = value }
    fun putExtra(name: String, value: Int): Intent = apply { extras.ints[name] = value }
    fun putExtra(name: String, value: Long): Intent = apply { extras.longs[name] = value }
    fun putExtra(name: String, value: Boolean): Intent = apply { extras.booleans[name] = value }
    fun putExtras(bundle: Bundle): Intent = this
    fun getStringExtra(name: String): String? = extras.strings[name]
    fun getIntExtra(name: String, defValue: Int): Int = extras.ints[name] ?: defValue
    fun getLongExtra(name: String, defValue: Long): Long = extras.longs[name] ?: defValue
    fun getBooleanExtra(name: String, defValue: Boolean): Boolean = extras.booleans[name] ?: defValue
    fun hasExtra(name: String): Boolean =
        extras.strings.containsKey(name) || extras.ints.containsKey(name) || extras.longs.containsKey(name) ||
            extras.booleans.containsKey(name)
    fun removeExtra(name: String) {
        extras.strings.remove(name)
        extras.ints.remove(name)
    }
    fun setDataAndType(uri: android.net.Uri?, type: String?): Intent = this
    fun getParcelableExtra(name: String): Any? = null

    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_MAIN = "android.intent.action.MAIN"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val ACTION_OPEN_DOCUMENT = "android.intent.action.OPEN_DOCUMENT"
        const val ACTION_GET_CONTENT = "android.intent.action.GET_CONTENT"
        const val CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"
        const val FLAG_ACTIVITY_NEW_TASK = 268435456
        const val FLAG_ACTIVITY_SINGLE_TOP = 536870912
        const val FLAG_GRANT_READ_URI_PERMISSION = 1
    }
}

class Bundle {
    val strings = HashMap<String, String?>()
    val ints = HashMap<String, Int>()
    val longs = HashMap<String, Long>()
    val booleans = HashMap<String, Boolean>()
}

interface ServiceConnection {
    fun onServiceConnected(name: ComponentName?, service: android.os.IBinder?)
    fun onServiceDisconnected(name: ComponentName?)
    fun onBindingDied(name: ComponentName?) {}
    fun onNullBinding(name: ComponentName?) {}
}

class ComponentName(val packageName: String, val className: String)

class ContentResolver {
    fun openInputStream(uri: android.net.Uri): InputStream = InputStream.nullInputStream()
    fun query(
        uri: android.net.Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): android.database.Cursor? = null
    fun takePersistableUriPermission(uri: android.net.Uri, flags: Int) {}
    fun getType(uri: android.net.Uri): String? = null
}

class ComponentCallbacks2

class ActivityNotFoundException : RuntimeException("no activity found")
