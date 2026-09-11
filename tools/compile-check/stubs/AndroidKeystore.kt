@file:Suppress("UNUSED_PARAMETER", "PackageDirectoryMismatch", "unused")
package android.security.keystore

object KeyProperties {
    const val KEY_ALGORITHM_AES = "AES"
    const val KEY_ALGORITHM_RSA = "RSA"
    const val BLOCK_MODE_GCM = "GCM"
    const val ENCRYPTION_PADDING_NONE = "NoPadding"
    const val PURPOSE_ENCRYPT = 1
    const val PURPOSE_DECRYPT = 2
    const val PURPOSE_SIGN = 4
    const val PURPOSE_VERIFY = 8
}

class KeyGenParameterSpec : java.security.spec.AlgorithmParameterSpec {
    class Builder(val alias: String, val purposes: Int) {
        fun setBlockModes(vararg modes: String): Builder = this
        fun setEncryptionPaddings(vararg paddings: String): Builder = this
        fun setKeySize(size: Int): Builder = this
        fun setUserAuthenticationRequired(required: Boolean): Builder = this
        fun setRandomizedEncryptionRequired(required: Boolean): Builder = this
        fun build(): KeyGenParameterSpec = KeyGenParameterSpec()
    }
}
