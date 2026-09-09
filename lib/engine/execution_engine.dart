import 'dart:async';
import 'workflow_data.dart';
import 'node_registry.dart';
import 'expression_engine.dart';
import 'package:uuid/uuid.dart';

const _uuid = Uuid();

/// Callback for execution progress updates
typedef ProgressCallback = void Function(String nodeName, ExecutionStatus status, int step, int total);

/// The core execution engine - implements n8n's stack-based graph traversal
class ExecutionEngine {
  final Map<String, dynamic> _environment;
  ProgressCallback? _onProgress;

  ExecutionEngine({
    Map<String, dynamic> environment = const {},
  })  : _environment = environment {
    NodeRegistry.initialize();
  }

  set onProgress(ProgressCallback? callback) => _onProgress = callback;

  /// Execute a workflow definition
  Future<WorkflowExecutionResult> execute(
    WorkflowDefinition workflow, {
    String? startNodeName,
    Map<String, dynamic> triggerData = const {},
  }) async {
    final executionId = _uuid.v4();
    final startedAt = DateTime.now();
    final nodeResults = <String, List<NodeExecutionData>>{};
    final stepResults = <NodeExecutionResult>[];

    try {
      // 1. Find start node
      String startName;
      if (startNodeName != null) {
        startName = startNodeName;
      } else {
        final triggers = workflow.getTriggerNodes();
        if (triggers.isEmpty && workflow.nodes.isNotEmpty) {
          startName = workflow.nodes.first.name;
        } else if (triggers.isNotEmpty) {
          startName = triggers.first;
        } else {
          throw Exception('No start node found in workflow');
        }
      }

      // 2. Build adjacency list for execution order
      final executionOrder = _computeExecutionOrder(workflow, startName);
      final totalSteps = executionOrder.length;

      // 3. Execute nodes in order (stack-based traversal)
      for (int i = 0; i < executionOrder.length; i++) {
        final nodeName = executionOrder[i];
        final nodeDef = workflow.findNode(nodeName);
        if (nodeDef == null) continue;

        _onProgress?.call(nodeName, ExecutionStatus.running, i + 1, totalSteps);

        // Gather input data from connected upstream nodes
        final inputItems = _gatherInputs(nodeName, workflow, nodeResults, triggerData);

        // Resolve parameters with expressions
        final exprContext = <String, dynamic>{
          '_inputItems': inputItems.map((d) => d.json).toList(),
          '_env': _environment,
          '_parameters': nodeDef.parameters,
        };
        if (inputItems.isNotEmpty) {
          exprContext['_currentItem'] = inputItems.first.json;
        }
        // Add previous node results
        nodeResults.forEach((name, results) {
          exprContext['_nodeResults_$name'] = results.map((r) => r.json).toList();
        });

        final resolvedParams = ExpressionEngine.resolveParameters(
          nodeDef.parameters,
          exprContext,
        );

        // Get node executor
        final executor = NodeRegistry.get(nodeDef.type);
        if (executor == null) {
          // Unknown node type - pass through
          nodeResults[nodeName] = inputItems;
          stepResults.add(NodeExecutionResult(
            nodeName: nodeName,
            nodeType: nodeDef.type,
            output: inputItems,
            status: ExecutionStatus.success,
            startedAt: DateTime.now(),
            finishedAt: DateTime.now(),
          ));
          _onProgress?.call(nodeName, ExecutionStatus.success, i + 1, totalSteps);
          continue;
        }

        // Execute the node
        final nodeStartedAt = DateTime.now();
        final context = ExecutionContext(
          node: nodeDef,
          inputData: inputItems,
          allNodeResults: nodeResults,
          workflowEnv: _environment,
          resolvedParameters: resolvedParams,
        );

        try {
          final output = await executor.execute(context);
          nodeResults[nodeName] = output;
          final duration = DateTime.now().difference(nodeStartedAt).inMilliseconds;

          stepResults.add(NodeExecutionResult(
            nodeName: nodeName,
            nodeType: nodeDef.type,
            output: output,
            status: ExecutionStatus.success,
            startedAt: nodeStartedAt,
            finishedAt: DateTime.now(),
            durationMs: duration,
            inputData: inputItems.isNotEmpty ? inputItems.first.json : null,
          ));

          _onProgress?.call(nodeName, ExecutionStatus.success, i + 1, totalSteps);
        } catch (e) {
          final duration = DateTime.now().difference(nodeStartedAt).inMilliseconds;

          stepResults.add(NodeExecutionResult(
            nodeName: nodeName,
            nodeType: nodeDef.type,
            output: [],
            status: ExecutionStatus.error,
            error: e.toString(),
            startedAt: nodeStartedAt,
            finishedAt: DateTime.now(),
            durationMs: duration,
          ));

          // Check if node should continue on fail
          final continueOnFail = nodeDef.settings?['continueOnFail'] == true ||
              nodeDef.settings?['continue_on_fail'] == true;
          if (!continueOnFail) {
            _onProgress?.call(nodeName, ExecutionStatus.error, i + 1, totalSteps);
            return WorkflowExecutionResult(
              executionId: executionId,
              workflowId: workflow.id,
              workflowName: workflow.name,
              status: ExecutionStatus.error,
              nodeResults: nodeResults,
              stepResults: stepResults,
              startedAt: startedAt,
              finishedAt: DateTime.now(),
              totalDurationMs: DateTime.now().difference(startedAt).inMilliseconds,
              error: 'Node "$nodeName" failed: ${e.toString()}',
            );
          }

          // Continue with empty output
          nodeResults[nodeName] = [NodeExecutionData(json: {'error': e.toString()})];
          _onProgress?.call(nodeName, ExecutionStatus.error, i + 1, totalSteps);
        }
      }

      final finishedAt = DateTime.now();
      return WorkflowExecutionResult(
        executionId: executionId,
        workflowId: workflow.id,
        workflowName: workflow.name,
        status: ExecutionStatus.success,
        nodeResults: nodeResults,
        stepResults: stepResults,
        startedAt: startedAt,
        finishedAt: finishedAt,
        totalDurationMs: finishedAt.difference(startedAt).inMilliseconds,
      );
    } catch (e) {
      return WorkflowExecutionResult(
        executionId: executionId,
        workflowId: workflow.id,
        workflowName: workflow.name,
        status: ExecutionStatus.error,
        nodeResults: nodeResults,
        stepResults: stepResults,
        startedAt: startedAt,
        finishedAt: DateTime.now(),
        totalDurationMs: DateTime.now().difference(startedAt).inMilliseconds,
        error: e.toString(),
      );
    }
  }

  /// Compute topological execution order from the workflow graph
  List<String> _computeExecutionOrder(
      WorkflowDefinition workflow, String startNode) {
    final visited = <String>{};
    final order = <String>[];
    final stack = [startNode];

    while (stack.isNotEmpty) {
      final nodeName = stack.last;
      if (visited.contains(nodeName)) {
        stack.removeLast();
        continue;
      }

      final downstream = workflow.getDownstreamNodes(nodeName);
      final allDownstreamVisited =
          downstream.every((d) => visited.contains(d));

      if (allDownstreamVisited || downstream.isEmpty) {
        visited.add(nodeName);
        order.add(nodeName);
        stack.removeLast();
      } else {
        // Push unvisited downstream nodes
        for (final d in downstream.reversed) {
          if (!visited.contains(d) && !stack.contains(d)) {
            stack.add(d);
          }
        }
      }
    }

    return order;
  }

  /// Gather input data from all connected upstream nodes
  List<NodeExecutionData> _gatherInputs(
    String nodeName,
    WorkflowDefinition workflow,
    Map<String, List<NodeExecutionData>> nodeResults,
    Map<String, dynamic> triggerData,
  ) {
    final inputs = <NodeExecutionData>[];

    // Find all nodes that connect TO this node
    workflow.connections.forEach((sourceName, types) {
      types.forEach((typeName, outputs) {
        for (final output in outputs) {
          if (output['node'] == nodeName) {
            final sourceOutput = nodeResults[sourceName];
            if (sourceOutput != null) {
              inputs.addAll(sourceOutput);
            }
          }
        }
      });
    });

    // If no inputs found (first node), use trigger data
    if (inputs.isEmpty && triggerData.isNotEmpty) {
      inputs.add(NodeExecutionData(json: triggerData));
    } else if (inputs.isEmpty) {
      inputs.add(NodeExecutionData(json: {}));
    }

    return inputs;
  }

  /// List all available node types
  List<String> getAvailableNodeTypes() => NodeRegistry.registeredTypes;

  /// Get description for a node type
  String? getNodeDescription(String type) =>
      NodeRegistry.get(type)?.description;
}

/// Helper to convert our workflow model to WorkflowDefinition
class WorkflowConverter {
  static WorkflowDefinition fromModels(
    String id,
    String name,
    List<dynamic> nodes,
    List<dynamic> connections,
  ) {
    final nodeDefs = nodes.map((n) {
      return WorkflowNodeDef(
        name: n.name,
        type: n.type,
        parameters: Map<String, dynamic>.from(n.parameters ?? {}),
        credentials: Map<String, dynamic>.from(n.credentials ?? {}),
      );
    }).toList();

    final connMap = <String, Map<String, List<Map<String, dynamic>>>>{};
    for (final conn in connections) {
      final sourceName = conn.sourceNodeId;
      if (!connMap.containsKey(sourceName)) {
        connMap[sourceName] = {'main': []};
      }
      connMap[sourceName]!['main']!.add({
        'node': conn.targetNodeId,
        'type': 'main',
        'index': conn.targetInput,
      });
    }

    return WorkflowDefinition(
      id: id,
      name: name,
      nodes: nodeDefs,
      connections: connMap,
    );
  }
}
