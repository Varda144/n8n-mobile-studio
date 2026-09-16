package com.n8n.mobile.studio.runtime
import android.content.Context
import java.io.File
class RuntimePaths(ctx:Context){
    val base: File = ctx.filesDir
    val usr: File = File(base,"usr").apply{mkdirs()}
    val usrBin: File = File(usr,"bin")
    val usrLib: File = File(usr,"lib")
    val n8nDir: File = File(base,"n8n").apply{mkdirs()}
    val opencodeDir: File = File(base,"opencode").apply{mkdirs()}
    val n8nData: File = File(base,"n8n_data").apply{mkdirs()}
    val opencodeData: File = File(base,"opencode_data").apply{mkdirs()}
    val cache: File = ctx.cacheDir
    val tmp: File = File(base,"tmp").apply{mkdirs()}
    fun nodeBin(): File = File(usrBin,"node")
    fun npmBin(): File = File(usrBin,"npm")
    fun npxBin(): File = File(usrBin,"npx")
}
