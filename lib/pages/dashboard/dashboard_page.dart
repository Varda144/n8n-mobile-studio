import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';
import '../../theme/app_theme.dart';
import '../../providers/workflow_provider.dart';
import '../../providers/execution_provider.dart';
import '../../providers/notifications_provider.dart';
import '../../providers/instance_provider.dart';

class DashboardPage extends StatelessWidget {
  const DashboardPage({super.key});

  @override
  Widget build(BuildContext context) {
    final workflows = context.watch<WorkflowProvider>();
    final executions = context.watch<ExecutionProvider>();
    final notifications = context.watch<NotificationsProvider>();
    final instances = context.watch<InstanceProvider>();

    return Scaffold(
      appBar: AppBar(
        title: const Text('N8N Studio', style: TextStyle(fontWeight: FontWeight.bold)),
        actions: [
          IconButton(
            icon: Badge(
              label: Text('${notifications.unreadCount}', style: const TextStyle(color: Colors.white, fontSize: 10)),
              isLabelVisible: notifications.unreadCount > 0,
              child: const Icon(Icons.notifications_outlined),
            ),
            onPressed: () => context.push('/notifications'),
          ),
          IconButton(
            icon: const Icon(Icons.search),
            onPressed: () => context.push('/search'),
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildInstanceSelector(context, instances),
            const SizedBox(height: 20),
            _buildStatsRow(workflows, executions),
            const SizedBox(height: 20),
            _buildQuickActions(context),
            const SizedBox(height: 20),
            _buildSectionTitle('Recent Workflows'),
            const SizedBox(height: 8),
            ...workflows.workflows.take(3).map((w) => _buildWorkflowCard(context, w)),
            const SizedBox(height: 20),
            _buildSectionTitle('Recent Executions'),
            const SizedBox(height: 8),
            ...executions.executions.take(3).map((e) => _buildExecutionCard(context, e)),
          ],
        ),
      ),
    );
  }

  Widget _buildInstanceSelector(BuildContext context, InstanceProvider instances) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: LinearGradient(colors: [AppTheme.primaryColor, AppTheme.primaryColor.withOpacity(0.7)]),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          const Icon(Icons.cloud_rounded, color: Colors.white, size: 32),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(instances.activeInstance?.name ?? 'No Instance',
                    style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16)),
                Text(instances.activeInstance?.url ?? '',
                    style: TextStyle(color: Colors.white.withOpacity(0.8), fontSize: 12)),
              ],
            ),
          ),
          Icon(Icons.arrow_forward_ios, color: Colors.white.withOpacity(0.7), size: 16),
        ],
      ),
    );
  }

  Widget _buildStatsRow(WorkflowProvider w, ExecutionProvider e) {
    return Row(
      children: [
        _buildStatCard('Workflows', '${w.workflows.length}', Icons.account_tree, Colors.blue),
        const SizedBox(width: 12),
        _buildStatCard('Running', '${e.runningCount}', Icons.play_circle, Colors.green),
        const SizedBox(width: 12),
        _buildStatCard('Errors', '${e.errorCount}', Icons.error_outline, Colors.red),
        const SizedBox(width: 12),
        _buildStatCard('Success', '${e.successCount}', Icons.check_circle, Colors.teal),
      ],
    );
  }

  Widget _buildStatCard(String label, String value, IconData icon, Color color) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: Theme.of(context).cardTheme.color,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: color.withOpacity(0.3)),
        ),
        child: Column(
          children: [
            Icon(icon, color: color, size: 24),
            const SizedBox(height: 4),
            Text(value, style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18, color: color)),
            Text(label, style: const TextStyle(fontSize: 10, color: Colors.grey)),
          ],
        ),
      ),
    );
  }

  Widget _buildQuickActions(BuildContext context) {
    final actions = [
      ('Templates', Icons.dashboard_customize, '/templates', Colors.orange),
      ('Webhooks', Icons.webhook, '/webhooks', Colors.purple),
      ('Credentials', Icons.vpn_key, '/credentials', Colors.teal),
      ('MCP', Icons.device_hub, '/mcp', Colors.indigo),
    ];
    return Row(
      children: actions.map((a) {
        return Expanded(
          child: GestureDetector(
            onTap: () => context.push(a.$3),
            child: Container(
              margin: const EdgeInsets.symmetric(horizontal: 4),
              padding: const EdgeInsets.symmetric(vertical: 16),
              decoration: BoxDecoration(
                color: a.$4.withOpacity(0.15),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                children: [
                  Icon(a.$2, color: a.$4, size: 28),
                  const SizedBox(height: 6),
                  Text(a.$1, style: TextStyle(fontSize: 11, color: a.$4, fontWeight: FontWeight.w600)),
                ],
              ),
            ),
          ),
        );
      }).toList(),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Text(title, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold));
  }

  Widget _buildWorkflowCard(BuildContext context, workflow) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: workflow.status.name == 'active' ? AppTheme.successColor : Colors.grey,
          child: Icon(Icons.account_tree, color: Colors.white, size: 20),
        ),
        title: Text(workflow.name, style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: Text('${workflow.nodes.length} nodes', style: const TextStyle(fontSize: 12)),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('${workflow.executionCount}', style: const TextStyle(fontSize: 12, color: Colors.grey)),
            const Icon(Icons.chevron_right),
          ],
        ),
        onTap: () => context.push('/workflows/${workflow.id}'),
      ),
    );
  }

  Widget _buildExecutionCard(BuildContext context, exec) {
    final color = exec.status.name == 'success' ? AppTheme.successColor
        : exec.status.name == 'error' ? AppTheme.errorColor
        : exec.status.name == 'running' ? AppTheme.infoColor : Colors.grey;
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        leading: CircleAvatar(backgroundColor: color.withOpacity(0.2), child: Icon(
          exec.status.name == 'success' ? Icons.check : exec.status.name == 'error' ? Icons.close : Icons.play_arrow,
          color: color, size: 20,
        )),
        title: Text(exec.workflowName, style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: Text(exec.status.name.toUpperCase(), style: TextStyle(fontSize: 11, color: color, fontWeight: FontWeight.w600)),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => context.push('/executions/${exec.id}'),
      ),
    );
  }
}
