import 'package:uuid/uuid.dart';

const _uuid = Uuid();

enum WorkflowStatus { active, inactive, draft }
enum TriggerType { manual, webhook, cron, event }

class WorkflowNode {
  final String id;
  String type;
  String name;
  Map<String, dynamic> parameters;
  double x, y;
  Map<String, dynamic> credentials;
  String? notes;

  WorkflowNode({
    String? id,
    this.type = 'action',
    required this.name,
    Map<String, dynamic>? parameters,
    this.x = 0,
    this.y = 0,
    Map<String, dynamic>? credentials,
    this.notes,
  })  : id = id ?? _uuid.v4(),
        parameters = parameters ?? {},
        credentials = credentials ?? {};

  Map<String, dynamic> toJson() => {
        'id': id,
        'type': type,
        'name': name,
        'parameters': parameters,
        'x': x,
        'y': y,
        'credentials': credentials,
        'notes': notes,
      };

  factory WorkflowNode.fromJson(Map<String, dynamic> json) => WorkflowNode(
        id: json['id'],
        type: json['type'] ?? 'action',
        name: json['name'] ?? 'Node',
        parameters: Map<String, dynamic>.from(json['parameters'] ?? {}),
        x: (json['x'] ?? 0).toDouble(),
        y: (json['y'] ?? 0).toDouble(),
        credentials: Map<String, dynamic>.from(json['credentials'] ?? {}),
        notes: json['notes'],
      );
}

class WorkflowConnection {
  final String id;
  final String sourceNodeId;
  final int sourceOutput;
  final String targetNodeId;
  final int targetInput;

  WorkflowConnection({
    String? id,
    required this.sourceNodeId,
    this.sourceOutput = 0,
    required this.targetNodeId,
    this.targetInput = 0,
  }) : id = id ?? _uuid.v4();

  Map<String, dynamic> toJson() => {
        'id': id,
        'sourceNodeId': sourceNodeId,
        'sourceOutput': sourceOutput,
        'targetNodeId': targetNodeId,
        'targetInput': targetInput,
      };

  factory WorkflowConnection.fromJson(Map<String, dynamic> json) =>
      WorkflowConnection(
        id: json['id'],
        sourceNodeId: json['sourceNodeId'],
        sourceOutput: json['sourceOutput'] ?? 0,
        targetNodeId: json['targetNodeId'],
        targetInput: json['targetInput'] ?? 0,
      );
}

class Workflow {
  final String id;
  String name;
  String description;
  WorkflowStatus status;
  TriggerType triggerType;
  String triggerCron;
  List<WorkflowNode> nodes;
  List<WorkflowConnection> connections;
  DateTime createdAt;
  DateTime updatedAt;
  String? tags;
  int executionCount;
  bool shared;

  Workflow({
    String? id,
    required this.name,
    this.description = '',
    this.status = WorkflowStatus.draft,
    this.triggerType = TriggerType.manual,
    this.triggerCron = '',
    List<WorkflowNode>? nodes,
    List<WorkflowConnection>? connections,
    DateTime? createdAt,
    DateTime? updatedAt,
    this.tags,
    this.executionCount = 0,
    this.shared = false,
  })  : id = id ?? _uuid.v4(),
        nodes = nodes ?? [],
        connections = connections ?? [],
        createdAt = createdAt ?? DateTime.now(),
        updatedAt = updatedAt ?? DateTime.now();

  Workflow copyWith({
    String? name,
    String? description,
    WorkflowStatus? status,
    TriggerType? triggerType,
    String? triggerCron,
    List<WorkflowNode>? nodes,
    List<WorkflowConnection>? connections,
    String? tags,
    bool? shared,
  }) {
    return Workflow(
      id: id,
      name: name ?? this.name,
      description: description ?? this.description,
      status: status ?? this.status,
      triggerType: triggerType ?? this.triggerType,
      triggerCron: triggerCron ?? this.triggerCron,
      nodes: nodes ?? this.nodes,
      connections: connections ?? this.connections,
      createdAt: createdAt,
      updatedAt: DateTime.now(),
      tags: tags ?? this.tags,
      executionCount: executionCount,
      shared: shared ?? this.shared,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'description': description,
        'status': status.name,
        'triggerType': triggerType.name,
        'triggerCron': triggerCron,
        'nodes': nodes.map((n) => n.toJson()).toList(),
        'connections': connections.map((c) => c.toJson()).toList(),
        'createdAt': createdAt.toIso8601String(),
        'updatedAt': updatedAt.toIso8601String(),
        'tags': tags,
        'executionCount': executionCount,
        'shared': shared,
      };

  factory Workflow.fromJson(Map<String, dynamic> json) => Workflow(
        id: json['id'],
        name: json['name'] ?? 'Untitled',
        description: json['description'] ?? '',
        status: WorkflowStatus.values.firstWhere(
          (e) => e.name == json['status'],
          orElse: () => WorkflowStatus.draft,
        ),
        triggerType: TriggerType.values.firstWhere(
          (e) => e.name == json['triggerType'],
          orElse: () => TriggerType.manual,
        ),
        triggerCron: json['triggerCron'] ?? '',
        nodes: (json['nodes'] as List<dynamic>?)
                ?.map((n) => WorkflowNode.fromJson(n))
                .toList() ??
            [],
        connections: (json['connections'] as List<dynamic>?)
                ?.map((c) => WorkflowConnection.fromJson(c))
                .toList() ??
            [],
        createdAt: DateTime.parse(json['createdAt']),
        updatedAt: DateTime.parse(json['updatedAt']),
        tags: json['tags'],
        executionCount: json['executionCount'] ?? 0,
        shared: json['shared'] ?? false,
      );
}
