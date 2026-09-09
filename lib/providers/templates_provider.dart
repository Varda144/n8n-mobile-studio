import 'package:flutter/foundation.dart';
import '../models/template_model.dart';
import '../models/workflow_model.dart';

class TemplatesProvider extends ChangeNotifier {
  final List<WorkflowTemplate> _templates = [];
  List<WorkflowTemplate> get templates => List.unmodifiable(_templates);

  TemplatesProvider() {
    _seedData();
  }

  void _seedData() {
    _templates.addAll([
      WorkflowTemplate(name: 'Email Auto-Responder', description: 'Automatically respond to emails with AI', category: 'Email', author: 'n8n Team', usesCount: 1520, workflow: Workflow(name: 'Email Auto-Responder', nodes: [WorkflowNode(type: 'trigger', name: 'Email Trigger', x: 100, y: 200), WorkflowNode(type: 'action', name: 'AI Reply', x: 400, y: 200)])),
      WorkflowTemplate(name: 'Slack Bot', description: 'Create a smart Slack bot with AI', category: 'Messaging', author: 'Community', usesCount: 890, workflow: Workflow(name: 'Slack Bot', nodes: [WorkflowNode(type: 'trigger', name: 'Slack Trigger', x: 100, y: 200), WorkflowNode(type: 'action', name: 'AI Process', x: 400, y: 200), WorkflowNode(type: 'action', name: 'Slack Reply', x: 700, y: 200)])),
      WorkflowTemplate(name: 'Data Pipeline', description: 'ETL pipeline with transformations', category: 'Data', author: 'n8n Team', usesCount: 2340, workflow: Workflow(name: 'Data Pipeline')),
      WorkflowTemplate(name: 'Webhook Handler', description: 'Process incoming webhooks', category: 'API', author: 'Community', usesCount: 560, workflow: Workflow(name: 'Webhook Handler')),
      WorkflowTemplate(name: 'File Processor', description: 'Process uploaded files automatically', category: 'Files', author: 'n8n Team', usesCount: 430, workflow: Workflow(name: 'File Processor')),
      WorkflowTemplate(name: 'Scheduled Report', description: 'Generate and send reports on schedule', category: 'Reports', author: 'Community', usesCount: 780, workflow: Workflow(name: 'Scheduled Report')),
      WorkflowTemplate(name: 'CRM Sync', description: 'Sync contacts across CRM systems', category: 'CRM', author: 'n8n Team', usesCount: 1100, workflow: Workflow(name: 'CRM Sync')),
      WorkflowTemplate(name: 'AI Content Generator', description: 'Generate content using GPT models', category: 'AI', author: 'Community', usesCount: 3200, workflow: Workflow(name: 'AI Content Generator')),
    ]);
  }

  List<WorkflowTemplate> getByCategory(String cat) {
    if (cat == 'All') return templates;
    return _templates.where((t) => t.category == cat).toList();
  }

  List<String> get categories => ['All', ...{..._templates.map((t) => t.category)}];
}
