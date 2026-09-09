import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';
import '../../theme/app_theme.dart';
import '../../providers/workflow_provider.dart';
import '../../models/workflow_model.dart';

class WorkflowCreatePage extends StatefulWidget {
  const WorkflowCreatePage({super.key});
  @override
  State<WorkflowCreatePage> createState() => _WorkflowCreatePageState();
}

class _WorkflowCreatePageState extends State<WorkflowCreatePage> {
  final _nameCtrl = TextEditingController();
  final _descCtrl = TextEditingController();
  TriggerType _trigger = TriggerType.manual;
  String _cron = '';
  bool _importMode = false;
  final _jsonCtrl = TextEditingController();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Create Workflow'),
        actions: [
          TextButton(
            onPressed: _save,
            child: const Text('CREATE', style: TextStyle(fontWeight: FontWeight.bold)),
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: _buildModeTab('New Workflow', false),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: _buildModeTab('Import JSON', true),
                ),
              ],
            ),
            const SizedBox(height: 20),
            if (!_importMode) ...[
              TextField(
                controller: _nameCtrl,
                decoration: const InputDecoration(hintText: 'Workflow Name', prefixIcon: Icon(Icons.title)),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _descCtrl,
                decoration: const InputDecoration(hintText: 'Description', prefixIcon: Icon(Icons.description)),
                maxLines: 3,
              ),
              const SizedBox(height: 20),
              const Text('Trigger Type', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
              const SizedBox(height: 10),
              ...TriggerType.values.map((t) => RadioListTile<TriggerType>(
                title: Text(t.name[0].toUpperCase() + t.name.substring(1)),
                subtitle: Text(_getTriggerDesc(t)),
                value: t, groupValue: _trigger,
                onChanged: (v) => setState(() => _trigger = v!),
                activeColor: AppTheme.primaryColor,
              )),
              if (_trigger == TriggerType.cron) ...[
                const SizedBox(height: 10),
                TextField(
                  decoration: const InputDecoration(
                    hintText: 'Cron Expression (e.g., 0 * * * *)',
                    prefixIcon: Icon(Icons.schedule),
                  ),
                  onChanged: (v) => _cron = v,
                ),
              ],
            ],
            if (_importMode) ...[
              TextField(
                controller: _jsonCtrl,
                decoration: const InputDecoration(
                  hintText: 'Paste workflow JSON here...',
                  prefixIcon: Icon(Icons.code),
                ),
                maxLines: 15,
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildModeTab(String label, bool isImport) {
    final selected = _importMode == isImport;
    return GestureDetector(
      onTap: () => setState(() => _importMode = isImport),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 14),
        decoration: BoxDecoration(
          color: selected ? AppTheme.primaryColor : Theme.of(context).cardTheme.color,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: selected ? AppTheme.primaryColor : Colors.grey.withValues(alpha: 0.3)),
        ),
        child: Center(
          child: Text(label, style: TextStyle(
            color: selected ? Colors.white : null,
            fontWeight: FontWeight.bold,
          )),
        ),
      ),
    );
  }

  String _getTriggerDesc(TriggerType t) {
    switch (t) {
      case TriggerType.manual: return 'Execute manually from the app';
      case TriggerType.webhook: return 'Triggered by HTTP webhook';
      case TriggerType.cron: return 'Runs on a schedule';
      case TriggerType.event: return 'Triggered by system events';
    }
  }

  void _save() {
    if (_nameCtrl.text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter a workflow name')),
      );
      return;
    }
    final workflow = Workflow(
      name: _nameCtrl.text,
      description: _descCtrl.text,
      triggerType: _trigger,
      triggerCron: _cron,
      nodes: [
        WorkflowNode(
          type: 'trigger',
          name: _trigger.name[0].toUpperCase() + _trigger.name.substring(1) + ' Trigger',
          x: 100,
          y: 200,
        ),
      ],
    );
    context.read<WorkflowProvider>().addWorkflow(workflow);
    context.go('/workflows');
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _descCtrl.dispose();
    _jsonCtrl.dispose();
    super.dispose();
  }
}
