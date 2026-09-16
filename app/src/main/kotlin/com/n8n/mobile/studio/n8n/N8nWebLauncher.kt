package com.n8n.mobile.studio.n8n
import android.content.Context
import android.content.Intent
import android.net.Uri
class N8nWebLauncher(private val ctx:Context){
    fun open(url:String){ val i=Intent(Intent.ACTION_VIEW, Uri.parse(url)); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i) }
}
