import 'package:flutter/foundation.dart';
import '../models/execution_model.dart';
import '../models/workflow_model.dart';

class ExecutionProvider extends ChangeNotifier {
  final List<Execution> _executions = [];
  List<Execution> get executions => List.unmodifiable(_executions);

  ExecutionProvider() {
    _seedData();
  }

  void _seedData() {
    final now = DateTime.now();
    _executions.addAll([
      Execution(
        workflowId: '1', workflowName: 'Email Automation',
        status: ExecutionStatus.success,
        startedAt: now.subtract(const Duration(minutes: 12)),
        finishedAt: now.subtract(const Duration(minutes: 11, seconds: 45)),
        totalDurationMs: 15000,
        steps: [
          ExecutionStep(nodeId: '1', nodeName: 'Email Trigger', nodeType: 'trigger', status: ExecutionStatus.success, startedAt: now.subtract(const Duration(minutes: 12)), finishedAt: now.subtract(const Duration(minutes: 12)), durationMs: 200, outputData: {'from': 'user@test.com'}),
          ExecutionStep(nodeId: '2', nodeName: 'AI Classify', nodeType: 'action', status: ExecutionStatus.success, startedAt: now.subtract(const Duration(minutes: 12)), finishedAt: now.subtract(const Duration(minutes: 11, seconds: 50)), durationMs: 8000, outputData: {'category': 'support'}),
          ExecutionStep(nodeId: '3', nodeName: 'Send Reply', nodeType: 'action', status: ExecutionStatus.success, startedAt: now.subtract(const Duration(minutes: 11, seconds: 50)), finishedAt: now.subtract(const Duration(minutes: 11, seconds: 45)), durationMs: 5000, outputData: {'sent': true}),
        ],
      ),
      Execution(
        workflowId: '2', workflowName: 'Data Sync Pipeline',
        status: ExecutionStatus.error,
        startedAt: now.subtract(const Duration(hours: 2)),
        finishedAt: now.subtract(const Duration(hours: 2)),
        totalDurationMs: 3200,
        steps: [
          ExecutionStep(nodeId: '1', nodeName: 'Cron Trigger', nodeType: 'trigger', status: ExecutionStatus.success, startedAt: now.subtract(const Duration(hours: 2)), finishedAt: now.subtract(const Duration(hours: 2)), durationMs: 100),
          ExecutionStep(nodeId: '2', nodeName: 'Fetch API', nodeType: 'action', status: ExecutionStatus.error, startedAt: now.subtract(const Duration(hours: 2)), finishedAt: now.subtract(const Duration(hours: 2)), durationMs: 3100, error: 'Connection timeout'),
        ],
      ),
      Execution(
        workflowId: '1', workflowName: 'Email Automation',
        status: ExecutionStatus.running,
        startedAt: now.subtract(const Duration(minutes: 1)),
        steps: [
          ExecutionStep(nodeId: '1', nodeName: 'Email Trigger', nodeType: 'trigger', status: ExecutionStatus.success, startedAt: now.subtract(const Duration(minutes: 1)), finishedAt: now.subtract(const Duration(minutes: 1)), durationMs: 150),
          ExecutionStep(nodeId: '2', nodeName: 'AI Classify', nodeType: 'action', status: ExecutionStatus.running, startedAt: now.subtract(const Duration(seconds: 50)), durationMs: 50000),
        ],
      ),
      Execution(
        workflowId: '5', workflowName: 'Social Media Poster',
        status: ExecutionStatus.success,
        startedAt: now.subtract(const Duration(hours: 5)),
        finishedAt: now.subtract(const Duration(hours: 5)),
        totalDurationMs: 8500,
      ),
      Execution(
        workflowId: '3', workflowName: 'Slack Notifications',
        status: ExecutionStatus.cancelled,
        startedAt: now.subtract(const Duration(days: 1)),
        finishedAt: now.subtract(const Duration(days: 1)),
        totalDurationMs: 1200,
      ),
    ]);
  }

  void addExecution(Execution execution) {
    _executions.insert(0, execution);
    notifyListeners();
  }

  List<Execution> getByWorkflow(String workflowId) {
    return _executions.where((e) => e.workflowId == workflowId).toList();
  }

  int get successCount => _executions.where((e) => e.status == ExecutionStatus.success).length;
  int get errorCount => _executions.where((e) => e.status == ExecutionStatus.error).length;
  int get runningCount => _executions.where((e) => e.status == ExecutionStatus.running).length;
}
