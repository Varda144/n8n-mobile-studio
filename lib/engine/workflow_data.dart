import 'dart:convert';
import 'package:uuid/uuid.dart';

const _uuid = Uuid();

/// Data item flowing between nodes - matches n8n's INodeExecutionData
class NodeExecutionData {
  final Map<String, dynamic> json;
  final Map<String, dynamic>? binary;
  final int? itemIndex;
  final int? inputIndex;

  NodeExecutionData({
    required this.json,
    this.binary,
    this.itemIndex,
    this.inputIndex,
  });

  factory NodeExecutionData.fromJson(Map<String, dynamic> json) {
    return NodeExecutionData(
      json: json['json'] ?? {},
      binary: json['binary'],
      itemIndex: json['itemIndex'],
      inputIndex: json['inputIndex'],
    );
  }

  Map<String, dynamic> toJson() => {
        'json': json,
        if (binary != null) 'binary': binary,
        if (itemIndex != null) 'itemIndex': itemIndex,
        if (inputIndex != null) 'inputIndex': inputIndex,
      };

  NodeExecutionData copyWith({Map<String, dynamic>? json}) {
    return NodeExecutionData(
      json: json ?? this.json,
      binary: binary,
      itemIndex: itemIndex,
      inputIndex: inputIndex,
    );
  }
}

/// Connection between nodes
class NodeConnection {
  final String sourceNode;
  final String targetNode;
  final int sourceOutput;
  final int targetInput;

  NodeConnection({
    required this.sourceNode,
    required this.targetNode,
    this.sourceOutput = 0,
    this.targetInput = 0,
  });
}

/// Execution result for a single node
class NodeExecutionResult {
  final String nodeName;
  final String nodeType;
  final List<NodeExecutionData> output;
  final ExecutionStatus status;
  final String? error;
  final int durationMs;
  final DateTime startedAt;
  final DateTime finishedAt;
  final Map<String, dynamic>? inputData;

  NodeExecutionResult({
    required this.nodeName,
    required this.nodeType,
    required this.output,
    this.status = ExecutionStatus.success,
    this.error,
    this.durationMs = 0,
    required this.startedAt,
    required this.finishedAt,
    this.inputData,
  });
}

enum ExecutionStatus { pending, running, success, error, waiting, cancelled }

/// Complete workflow execution result
class WorkflowExecutionResult {
  final String executionId;
  final String workflowId;
  final String workflowName;
  final ExecutionStatus status;
  final Map<String, List<NodeExecutionData>> nodeResults;
  final List<NodeExecutionResult> stepResults;
  final DateTime startedAt;
  final DateTime? finishedAt;
  final int totalDurationMs;
  final String? error;

  WorkflowExecutionResult({
    required this.executionId,
    required this.workflowId,
    required this.workflowName,
    required this.status,
    required this.nodeResults,
    required this.stepResults,
    required this.startedAt,
    this.finishedAt,
    this.totalDurationMs = 0,
    this.error,
  });
}

/// Workflow definition matching n8n format
class WorkflowDefinition {
  final String id;
  final String name;
  final List<WorkflowNodeDef> nodes;
  final Map<String, Map<String, List<Map<String, dynamic>>>> connections;
  final Map<String, dynamic>? settings;

  WorkflowDefinition({
    required this.id,
    required this.name,
    required this.nodes,
    required this.connections,
    this.settings,
  });

  factory WorkflowDefinition.fromJson(Map<String, dynamic> json) {
    return WorkflowDefinition(
      id: json['id'] ?? _uuid.v4(),
      name: json['name'] ?? 'Untitled',
      nodes: (json['nodes'] as List?)
              ?.map((n) => WorkflowNodeDef.fromJson(n))
              .toList() ??
          [],
      connections: _parseConnections(json['connections']),
      settings: json['settings'],
    );
  }

  static Map<String, Map<String, List<Map<String, dynamic>>>> _parseConnections(
      dynamic conn) {
    if (conn == null) return {};
    final result = <String, Map<String, List<Map<String, dynamic>>>>{};
    if (conn is Map) {
      conn.forEach((sourceName, types) {
        if (types is Map) {
          final typeMap = <String, List<Map<String, dynamic>>>{};
          types.forEach((typeName, outputs) {
            if (outputs is List) {
              final outputList = <Map<String, dynamic>>[];
              for (final output in outputs) {
                if (output is List) {
                  for (final target in output) {
                    if (target is Map) {
                      outputList.add(Map<String, dynamic>.from(target));
                    }
                  }
                }
              }
              typeMap[typeName] = outputList;
            }
          });
          result[sourceName] = typeMap;
        }
      });
    }
    return result;
  }

  List<String> getDownstreamNodes(String nodeName) {
    final downstream = <String>[];
    final conn = connections[nodeName];
    if (conn != null) {
      conn.forEach((type, outputs) {
        for (final output in outputs) {
          final target = output['node'] as String?;
          if (target != null && !downstream.contains(target)) {
            downstream.add(target);
          }
        }
      });
    }
    return downstream;
  }

  WorkflowNodeDef? findNode(String name) {
    try {
      return nodes.firstWhere((n) => n.name == name);
    } catch (_) {
      return null;
    }
  }

  List<String> getTriggerNodes() {
    return nodes
        .where((n) =>
            n.type.contains('trigger') ||
            n.type.contains('Trigger') ||
            n.type == 'manual' ||
            n.type == 'webhook' ||
            n.type == 'cron')
        .map((n) => n.name)
        .toList();
  }
}

class WorkflowNodeDef {
  final String name;
  final String type;
  final Map<String, dynamic> parameters;
  final Map<String, dynamic> credentials;
  final String? notes;
  final Map<String, dynamic>? settings;

  WorkflowNodeDef({
    required this.name,
    required this.type,
    this.parameters = const {},
    this.credentials = const {},
    this.notes,
    this.settings,
  });

  factory WorkflowNodeDef.fromJson(Map<String, dynamic> json) {
    return WorkflowNodeDef(
      name: json['name'] ?? '',
      type: json['type'] ?? '',
      parameters: Map<String, dynamic>.from(json['parameters'] ?? {}),
      credentials: Map<String, dynamic>.from(json['credentials'] ?? {}),
      notes: json['notes'],
      settings: json['settings'] != null
          ? Map<String, dynamic>.from(json['settings'])
          : null,
    );
  }
}
