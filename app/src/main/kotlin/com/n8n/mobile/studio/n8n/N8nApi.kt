package com.n8n.mobile.studio.n8n
import java.net.HttpURLConnection
import java.net.URL
class N8nApi(private val base:String="http://127.0.0.1:5678"){
    fun isRunning():Boolean = try{ val c=URL("$base/healthz").openConnection() as HttpURLConnection; c.connectTimeout=2000; c.connect(); (c.responseCode==200).also{c.disconnect()} }catch(_:Exception){false}
}
