import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../theme/app_theme.dart';
import '../../providers/execution_provider.dart';
import '../../models/execution_model.dart';

class ExecutionDetailPage extends StatelessWidget {
  final String executionId;
  const ExecutionDetailPage({super.key, required this.executionId});

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<ExecutionProvider>();
    final exec = provider.executions.where((e) => e.id == executionId).firstOrNull;
    if (exec == null) {
      return Scaffold(appBar: AppBar(title: const Text('Execution not found')));
    }

    final color = exec.status.name == 'success' ? AppTheme.successColor
        : exec.status.name == 'error' ? AppTheme.errorColor : AppTheme.infoColor;

    return Scaffold(
      appBar: AppBar(
        title: Text('Execution', style: const TextStyle(fontSize: 16)),
        actions: [
          if (exec.status.name == 'error')
            IconButton(icon: const Icon(Icons.refresh), onPressed: () {}),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: color.withValues(alpha: 0.3)),
              ),
              child: Row(
                children: [
                  Icon(exec.status.name == 'success' ? Icons.check_circle
                      : exec.status.name == 'error' ? Icons.error : Icons.hourglass_empty,
                      color: color, size: 36),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(exec.workflowName, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                        Text(exec.status.name.toUpperCase(), style: TextStyle(color: color, fontWeight: FontWeight.w600)),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                _buildInfo('Duration', exec.duration != null ? '${exec.duration!.inMilliseconds}ms' : 'Running...'),
                const SizedBox(width: 16),
                _buildInfo('Steps', '${exec.itemCount}'),
                const SizedBox(width: 16),
                _buildInfo('Started', '${exec.startedAt.hour}:${exec.startedAt.minute}'),
              ],
            ),
            const SizedBox(height: 20),
            const Text('Steps', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
            const SizedBox(height: 10),
            ...exec.steps.asMap().entries.map((entry) => _buildStep(entry.key, entry.value)),
          ],
        ),
      ),
    );
  }

  Widget _buildInfo(String label, String value) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: const TextStyle(fontSize: 11, color: Colors.grey)),
            Text(value, style: const TextStyle(fontWeight: FontWeight.bold)),
          ],
        ),
      ),
    );
  }

  Widget _buildStep(int index, ExecutionStep step) {
    final color = step.status.name == 'success' ? AppTheme.successColor
        : step.status.name == 'error' ? AppTheme.errorColor : Colors.grey;
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: color.withValues(alpha: 0.3)),
      ),
      child: Row(
        children: [
          Container(
            width: 28, height: 28,
            decoration: BoxDecoration(color: color.withValues(alpha: 0.2), shape: BoxShape.circle),
            child: Center(
              child: step.status.name == 'success'
                  ? const Icon(Icons.check, size: 14, color: AppTheme.successColor)
                  : step.status.name == 'error'
                      ? const Icon(Icons.close, size: 14, color: AppTheme.errorColor)
                      : Text('${index + 1}', style: const TextStyle(fontSize: 11)),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(step.nodeName, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
                Text(step.nodeType, style: const TextStyle(fontSize: 11, color: Colors.grey)),
                if (step.error != null)
                  Text(step.error!, style: const TextStyle(fontSize: 11, color: AppTheme.errorColor)),
              ],
            ),
          ),
          Text('${step.durationMs}ms', style: const TextStyle(fontSize: 11, color: Colors.grey)),
        ],
      ),
    );
  }
}
