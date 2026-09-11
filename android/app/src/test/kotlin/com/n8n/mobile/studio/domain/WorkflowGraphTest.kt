package com.n8n.mobile.studio.domain.models

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkflowGraphTest {
    @Test fun graphRetainsNodesAndConnections() {
        val graph = WorkflowGraph(
            nodes = listOf(GraphNode("a", "Webhook", "n8n-nodes-base.webhook", 10f, 20f)),
            connections = listOf(GraphConnection("a", "b"))
        )
        assertEquals(1, graph.nodes.size)
        assertEquals("a", graph.connections.first().from)
        assertEquals("b", graph.connections.first().to)
    }
}
