package com.n8n.mobile.studio.data.local
import androidx.room.Database
import androidx.room.RoomDatabase
@Database(entities=[WorkflowEntity::class], version=1) abstract class AppDatabase: RoomDatabase(){ abstract fun workflowDao(): WorkflowDao }
