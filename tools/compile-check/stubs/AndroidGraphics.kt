@file:Suppress("UNUSED_PARAMETER", "PackageDirectoryMismatch", "unused")
package android.graphics.drawable

import android.content.Context

class Icon {
    companion object {
        fun createWithResource(context: Context?, resId: Int): Icon = Icon()
        fun createWithAdaptiveBitmap(bitmap: Any?): Icon = Icon()
    }
}
