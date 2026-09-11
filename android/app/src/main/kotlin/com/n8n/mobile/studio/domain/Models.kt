package com.n8n.mobile.studio.domain

import kotlinx.serialization.Serializable

@Serializable
data class N8nInstance(
    val id: String,
    val name: String,
    val baseUrl: String,
    val environment: String = "development",
    val connected: Boolean = false,
    val lastSyncEpochMs: Long? = null
)

@Serializable
data class WorkflowSummary(
    val id: String,
    val name: String,
    val active: Boolean = false,
    val updatedAt: String? = null,
    val tags: List<String> = emptyList()
)

@Serializable
data class ExecutionSummary(
    val id: String,
    val workflowName: String? = null,
    val status: String,
    val startedAt: String? = null,
    val stoppedAt: String? = null,
    val mode: String? = null
)

@Serializable
data class GraphNode(
    val id: String,
    val name: String,
    val type: String,
    val x: Float,
    val y: Float,
    val parametersJson: String = "{}"
)

@Serializable
data class GraphConnection(val from: String, val to: String)

@Serializable
data class WorkflowGraph(
    val nodes: List<GraphNode> = emptyList(),
    val connections: List<GraphConnection> = emptyList()
)
