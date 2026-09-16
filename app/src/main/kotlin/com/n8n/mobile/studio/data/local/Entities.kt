package com.n8n.mobile.studio.data.local
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName="workflows") data class WorkflowEntity(@PrimaryKey val id:String, val name:String, val active:Boolean, val updatedAt:Long=System.currentTimeMillis())
