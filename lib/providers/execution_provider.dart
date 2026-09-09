import 'package:flutter/foundation.dart';
import '../models/workflow_model.dart';
import '../models/execution_model.dart';
import '../engine/execution_engine.dart';
import '../engine/workflow_data.dart' as engine;
import '../services/webhook_server.dart';
import '../services/cron_scheduler.dart';

class ExecutionProvider extends ChangeNotifier {
  final List<Execution> _executions = [];
  List<Execution> get executions => List.unmodifiable(_executions);
  ExecutionEngine? _engine;
  engine.WorkflowExecutionResult? _lastResult;
  engine.WorkflowExecutionResult? get lastResult => _lastResult;

  // Live execution state
  String? _currentExecutingNode;
  String? get currentExecutingNode => _currentExecutingNode;
  ExecutionStatus? _currentExecutionStatus;
  ExecutionStatus? get currentExecutionStatus => _currentExecutionStatus;
  int _currentStep = 0;
  int _currentStepTotal = 0;
  int get currentStep => _currentStep;
  int get currentStepTotal => _currentStepTotal;
  bool _isExecuting = false;
  bool get isExecuting => _isExecuting;

  // Services
  final LocalWebhookServer _webhookServer = LocalWebhookServer();
  final CronScheduler _scheduler = CronScheduler();

  ExecutionProvider() {
    _engine = ExecutionEngine();
    _engine!.onProgress = _onNodeProgress;
    _webhookServer.onWebhookReceived = _onWebhookReceived;
    _scheduler.onTrigger = _onCronTrigger;
    _seedData();
  }

  void _onWebhookReceived(String path, Map<String, dynamic> data, Map<String, String> headers) {
    debugPrint('Webhook received at $path: $data');
    // Find workflow registered for this path and execute it
    final webhooks = _webhookServer.getRegisteredWebhooks();
    for (final wh in webhooks) {
      if (wh['path'] == path) {
        final workflowId = wh['workflowId'];
        if (workflowId != null) {
          // Execute the workflow with webhook data
          _executeWebhookWorkflow(workflowId, data);
        }
      }
    }
  }

  void _onCronTrigger(String workflowId) {
    debugPrint('Cron triggered for workflow: $workflowId');
    // Find the workflow and execute it
    // This would need access to the workflow provider
  }

  Future<void> _executeWebhookWorkflow(String workflowId, Map<String, dynamic> data) async {
    // This would execute the workflow with the webhook data
    debugPrint('Executing webhook workflow $workflowId with data: $data');
  }

  /// Start the webhook server
  Future<void> startWebhookServer({int port = 8080}) async {
    await _webhookServer.start(port: port);
    notifyListeners();
  }

  /// Stop the webhook server
  Future<void> stopWebhookServer() async {
    await _webhookServer.stop();
    notifyListeners();
  }

  /// Register a webhook for a workflow
  void registerWebhook(String path, String workflowId) {
    _webhookServer.registerWebhook(path, workflowId);
    notifyListeners();
  }

  /// Unregister a webhook
  void unregisterWebhook(String path) {
    _webhookServer.unregisterWebhook(path);
    notifyListeners();
  }

  /// Get all registered webhooks
  List<Map<String, String>> getRegisteredWebhooks() {
    return _webhookServer.getRegisteredWebhooks();
  }

  /// Get webhook URL
  String getWebhookUrl(String path) {
    return _webhookServer.getWebhookUrl(path);
  }

  /// Check if webhook server is running
  bool get isWebhookServerRunning => _webhookServer.isRunning;

  /// Get webhook server port
  int get webhookServerPort => _webhookServer.port;

  /// Schedule a workflow with cron
  void scheduleWorkflow(String cronExpr, String workflowId) {
    _scheduler.schedule(cronExpr, workflowId);
    notifyListeners();
  }

  /// Unschedule a workflow
  void unscheduleWorkflow(String cronExpr) {
    _scheduler.unschedule(cronExpr);
    notifyListeners();
  }

  /// Get all scheduled workflows
  List<Map<String, String>> getScheduledWorkflows() {
    return _scheduler.getScheduledWorkflows();
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
    ]);
  }

  void _onNodeProgress(String nodeName, engine.ExecutionStatus status, int step, int total) {
    _currentExecutingNode = nodeName;
    _currentStep = step;
    _currentStepTotal = total;
    _currentExecutionStatus = _convertStatus(status);
    notifyListeners();
  }

  ExecutionStatus _convertStatus(engine.ExecutionStatus s) {
    switch (s) {
      case engine.ExecutionStatus.running: return ExecutionStatus.running;
      case engine.ExecutionStatus.success: return ExecutionStatus.success;
      case engine.ExecutionStatus.error: return ExecutionStatus.error;
      case engine.ExecutionStatus.cancelled: return ExecutionStatus.cancelled;
      case engine.ExecutionStatus.waiting: return ExecutionStatus.waiting;
      case engine.ExecutionStatus.pending: return ExecutionStatus.waiting;
    }
  }

  /// Execute a workflow using the real execution engine
  Future<engine.WorkflowExecutionResult> executeWorkflow(Workflow workflow) async {
    _isExecuting = true;
    _currentStep = 0;
    _currentStepTotal = 0;
    notifyListeners();

    // Convert workflow model to engine definition
    final definition = WorkflowConverter.fromModels(
      workflow.id,
      workflow.name,
      workflow.nodes,
      workflow.connections,
    );

    // Execute
    _lastResult = await _engine!.execute(definition);

    // Record execution
    final execution = Execution(
      workflowId: workflow.id,
      workflowName: workflow.name,
      status: _convertStatus(_lastResult!.status),
      startedAt: _lastResult!.startedAt,
      finishedAt: _lastResult!.finishedAt,
      totalDurationMs: _lastResult!.totalDurationMs,
      steps: _lastResult!.stepResults.map((sr) => ExecutionStep(
        nodeId: sr.nodeName,
        nodeName: sr.nodeName,
        nodeType: sr.nodeType,
        status: _convertStatus(sr.status),
        startedAt: sr.startedAt,
        finishedAt: sr.finishedAt,
        durationMs: sr.durationMs,
        outputData: sr.output.isNotEmpty ? sr.output.first.json : null,
        error: sr.error,
      )).toList(),
    );

    _executions.insert(0, execution);
    _isExecuting = false;
    _currentExecutingNode = null;
    notifyListeners();

    return _lastResult!;
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
