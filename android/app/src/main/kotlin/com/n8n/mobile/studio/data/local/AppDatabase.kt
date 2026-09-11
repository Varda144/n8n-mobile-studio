package com.n8n.mobile.studio.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [InstanceEntity::class, WorkflowEntity::class, ExecutionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun instances(): InstanceDao
    abstract fun workflows(): WorkflowDao
    abstract fun executions(): ExecutionDao
}
