package com.n8n.mobile.studio.data.api
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
class N8nApiClient(private val baseUrl:String="http://127.0.0.1:5678"){
    private val client = OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).build()
    fun health():Boolean = try{ val req=Request.Builder().url("$baseUrl/healthz").build(); client.newCall(req).execute().use{it.isSuccessful} }catch(_:Exception){false}
}
