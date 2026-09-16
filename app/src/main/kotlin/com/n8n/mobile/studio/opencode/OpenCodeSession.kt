package com.n8n.mobile.studio.opencode
import java.util.UUID
data class OpenCodeSession(val id:String=UUID.randomUUID().toString(), val projectId:String?=null, val createdAt:Long=System.currentTimeMillis(), var isActive:Boolean=true)
