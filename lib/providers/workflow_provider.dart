import 'package:flutter/foundation.dart';
import '../models/workflow_model.dart';

class WorkflowProvider extends ChangeNotifier {
  final List<Workflow> _workflows = [];
  List<Workflow> get workflows => List.unmodifiable(_workflows);

  WorkflowProvider() {
    _seedData();
  }

  void _seedData() {
    _workflows.addAll([
      Workflow(
        name: 'Email Automation',
        description: 'Auto-respond to incoming emails',
        status: WorkflowStatus.active,
        triggerType: TriggerType.webhook,
        nodes: [
          WorkflowNode(type: 'trigger', name: 'Email Trigger', x: 100, y: 200),
          WorkflowNode(type: 'action', name: 'AI Classify', x: 350, y: 200),
          WorkflowNode(type: 'action', name: 'Send Reply', x: 600, y: 200),
        ],
        connections: [
          WorkflowConnection(sourceNodeId: '', sourceOutput: 0, targetNodeId: ''),
        ],
        executionCount: 142,
      ),
      Workflow(
        name: 'Data Sync Pipeline',
        description: 'Sync data between APIs every hour',
        status: WorkflowStatus.active,
        triggerType: TriggerType.cron,
        triggerCron: '0 * * * *',
        nodes: [
          WorkflowNode(type: 'trigger', name: 'Cron Trigger', x: 100, y: 200),
          WorkflowNode(type: 'action', name: 'Fetch API', x: 350, y: 200),
          WorkflowNode(type: 'action', name: 'Transform', x: 600, y: 200),
          WorkflowNode(type: 'action', name: 'Write DB', x: 850, y: 200),
        ],
        executionCount: 89,
      ),
      Workflow(
        name: 'Slack Notifications',
        description: 'Forward alerts to Slack channels',
        status: WorkflowStatus.inactive,
        triggerType: TriggerType.event,
        nodes: [
          WorkflowNode(type: 'trigger', name: 'Webhook', x: 100, y: 200),
          WorkflowNode(type: 'action', name: 'Format Message', x: 350, y: 200),
          WorkflowNode(type: 'action', name: 'Slack Post', x: 600, y: 200),
        ],
        executionCount: 23,
      ),
      Workflow(
        name: 'Invoice Generator',
        description: 'Generate PDF invoices from CSV',
        status: WorkflowStatus.draft,
        triggerType: TriggerType.manual,
        nodes: [
          WorkflowNode(type: 'trigger', name: 'Manual', x: 100, y: 200),
          WorkflowNode(type: 'action', name: 'Read CSV', x: 350, y: 200),
          WorkflowNode(type: 'action', name: 'Generate PDF', x: 600, y: 200),
        ],
        executionCount: 5,
      ),
      Workflow(
        name: 'Social Media Poster',
        description: 'Post to multiple social platforms',
        status: WorkflowStatus.active,
        triggerType: TriggerType.cron,
        triggerCron: '0 9 * * *',
        nodes: [
          WorkflowNode(type: 'trigger', name: 'Schedule', x: 100, y: 200),
          WorkflowNode(type: 'action', name: 'AI Content', x: 350, y: 200),
          WorkflowNode(type: 'action', name: 'Twitter', x: 600, y: 150),
          WorkflowNode(type: 'action', name: 'LinkedIn', x: 600, y: 250),
        ],
        executionCount: 67,
      ),
    ]);
  }

  void addWorkflow(Workflow workflow) {
    _workflows.insert(0, workflow);
    notifyListeners();
  }

  void updateWorkflow(Workflow workflow) {
    final index = _workflows.indexWhere((w) => w.id == workflow.id);
    if (index != -1) {
      _workflows[index] = workflow;
      notifyListeners();
    }
  }

  void deleteWorkflow(String id) {
    _workflows.removeWhere((w) => w.id == id);
    notifyListeners();
  }

  void duplicateWorkflow(String id) {
    final source = _workflows.firstWhere((w) => w.id == id);
    final dup = Workflow(
      name: '${source.name} (Copy)',
      description: source.description,
      status: WorkflowStatus.draft,
      triggerType: source.triggerType,
      nodes: source.nodes.map((n) => WorkflowNode.fromJson(n.toJson())).toList(),
      connections: source.connections.map((c) => WorkflowConnection.fromJson(c.toJson())).toList(),
    );
    _workflows.insert(0, dup);
    notifyListeners();
  }

  void toggleActive(String id) {
    final index = _workflows.indexWhere((w) => w.id == id);
    if (index != -1) {
      final w = _workflows[index];
      _workflows[index] = w.copyWith(
        status: w.status == WorkflowStatus.active
            ? WorkflowStatus.inactive
            : WorkflowStatus.active,
      );
      notifyListeners();
    }
  }

  Workflow? getById(String id) {
    try {
      return _workflows.firstWhere((w) => w.id == id);
    } catch (_) {
      return null;
    }
  }

  List<Workflow> search(String query) {
    if (query.isEmpty) return workflows;
    final q = query.toLowerCase();
    return _workflows
        .where((w) =>
            w.name.toLowerCase().contains(q) ||
            w.description.toLowerCase().contains(q))
        .toList();
  }
}
