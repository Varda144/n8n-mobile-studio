import 'package:uuid/uuid.dart';

const _uuid = Uuid();

enum ExecutionStatus { running, success, error, cancelled, waiting }

class ExecutionStep {
  final String nodeId;
  final String nodeName;
  final String nodeType;
  final ExecutionStatus status;
  final DateTime startedAt;
  final DateTime? finishedAt;
  final dynamic inputData;
  final dynamic outputData;
  final String? error;
  final int durationMs;

  ExecutionStep({
    required this.nodeId,
    required this.nodeName,
    required this.nodeType,
    this.status = ExecutionStatus.running,
    required this.startedAt,
    this.finishedAt,
    this.inputData,
    this.outputData,
    this.error,
    this.durationMs = 0,
  });

  Map<String, dynamic> toJson() => {
        'nodeId': nodeId,
        'nodeName': nodeName,
        'nodeType': nodeType,
        'status': status.name,
        'startedAt': startedAt.toIso8601String(),
        'finishedAt': finishedAt?.toIso8601String(),
        'inputData': inputData,
        'outputData': outputData,
        'error': error,
        'durationMs': durationMs,
      };

  factory ExecutionStep.fromJson(Map<String, dynamic> json) => ExecutionStep(
        nodeId: json['nodeId'],
        nodeName: json['nodeName'],
        nodeType: json['nodeType'] ?? '',
        status: ExecutionStatus.values.firstWhere(
          (e) => e.name == json['status'],
          orElse: () => ExecutionStatus.waiting,
        ),
        startedAt: DateTime.parse(json['startedAt']),
        finishedAt: json['finishedAt'] != null
            ? DateTime.parse(json['finishedAt'])
            : null,
        inputData: json['inputData'],
        outputData: json['outputData'],
        error: json['error'],
        durationMs: json['durationMs'] ?? 0,
      );
}

class Execution {
  final String id;
  final String workflowId;
  final String workflowName;
  ExecutionStatus status;
  final DateTime startedAt;
  DateTime? finishedAt;
  List<ExecutionStep> steps;
  int totalDurationMs;
  final bool retryOf;
  final int retrySuccessId;
  final dynamic data;

  Execution({
    String? id,
    required this.workflowId,
    required this.workflowName,
    this.status = ExecutionStatus.running,
    DateTime? startedAt,
    this.finishedAt,
    List<ExecutionStep>? steps,
    this.totalDurationMs = 0,
    this.retryOf = false,
    this.retrySuccessId = 0,
    this.data,
  })  : id = id ?? _uuid.v4(),
        startedAt = startedAt ?? DateTime.now(),
        steps = steps ?? [];

  int get itemCount => steps.length;
  bool get isRunning => status == ExecutionStatus.running;
  bool get isSuccess => status == ExecutionStatus.success;
  bool get isError => status == ExecutionStatus.error;

  Duration? get duration {
    if (finishedAt == null) return null;
    return finishedAt!.difference(startedAt);
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'workflowId': workflowId,
        'workflowName': workflowName,
        'status': status.name,
        'startedAt': startedAt.toIso8601String(),
        'finishedAt': finishedAt?.toIso8601String(),
        'steps': steps.map((s) => s.toJson()).toList(),
        'totalDurationMs': totalDurationMs,
        'data': data,
      };

  factory Execution.fromJson(Map<String, dynamic> json) => Execution(
        id: json['id'],
        workflowId: json['workflowId'],
        workflowName: json['workflowName'] ?? '',
        status: ExecutionStatus.values.firstWhere(
          (e) => e.name == json['status'],
          orElse: () => ExecutionStatus.waiting,
        ),
        startedAt: DateTime.parse(json['startedAt']),
        finishedAt: json['finishedAt'] != null
            ? DateTime.parse(json['finishedAt'])
            : null,
        steps: (json['steps'] as List<dynamic>?)
                ?.map((s) => ExecutionStep.fromJson(s))
                .toList() ??
            [],
        totalDurationMs: json['totalDurationMs'] ?? 0,
      );
}
