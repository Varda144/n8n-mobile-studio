package com.n8n.mobile.studio.domain.models

import kotlinx.serialization.Serializable

/** A configured n8n endpoint (cloud, self-hosted, staging, production…). */
@Serializable
data class N8nInstance(
    val id: String,
    val name: String,
    val baseUrl: String,
    val environment: String = "development",
)

/** A single node on the workflow graph canvas. */
data class GraphNode(
    val id: String,
    val name: String,
    val type: String,
    val x: Float,
    val y: Float,
)

/** A directed edge between two [GraphNode]s. */
data class GraphConnection(
    val from: String,
    val to: String,
)

/** In-memory graph backing the workflow editor canvas. */
data class WorkflowGraph(
    val nodes: List<GraphNode> = emptyList(),
    val connections: List<GraphConnection> = emptyList(),
)