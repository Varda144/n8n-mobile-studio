package com.n8n.mobile.studio.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "instances")
data class InstanceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val environment: String,
    val lastSyncEpochMs: Long?
)

@Entity(tableName = "workflows")
data class WorkflowEntity(
    @PrimaryKey val id: String,
    val instanceId: String,
    val name: String,
    val active: Boolean,
    val updatedAt: String?,
    val json: String
)

@Entity(tableName = "executions")
data class ExecutionEntity(
    @PrimaryKey val id: String,
    val instanceId: String,
    val workflowName: String?,
    val status: String,
    val startedAt: String?,
    val stoppedAt: String?
)
