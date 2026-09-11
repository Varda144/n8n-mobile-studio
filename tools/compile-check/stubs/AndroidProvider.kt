@file:Suppress("UNUSED_PARAMETER", "PackageDirectoryMismatch", "unused")
package android.provider

interface OpenableColumns {
    companion object {
        const val DISPLAY_NAME = "_display_name"
        const val SIZE = "_size"
    }
}

class Settings {
    class Global
    class Secure {
        companion object {
            const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"
        }
    }
}
