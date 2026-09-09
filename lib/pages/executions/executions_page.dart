import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';
import '../../theme/app_theme.dart';
import '../../providers/execution_provider.dart';
import '../../models/execution_model.dart';

class ExecutionsPage extends StatelessWidget {
  const ExecutionsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<ExecutionProvider>();
    return DefaultTabController(
      length: 4,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Executions', style: TextStyle(fontWeight: FontWeight.bold)),
          bottom: const TabBar(
            tabs: [
              Tab(text: 'All'),
              Tab(text: 'Running'),
              Tab(text: 'Success'),
              Tab(text: 'Errors'),
            ],
            labelColor: AppTheme.primaryColor,
            unselectedLabelColor: Colors.grey,
            indicatorColor: AppTheme.primaryColor,
          ),
        ),
        body: TabBarView(
          children: [
            _buildList(context, provider.executions),
            _buildList(context, provider.executions.where((e) => e.status == ExecutionStatus.running).toList()),
            _buildList(context, provider.executions.where((e) => e.status == ExecutionStatus.success).toList()),
            _buildList(context, provider.executions.where((e) => e.status == ExecutionStatus.error).toList()),
          ],
        ),
      ),
    );
  }

  Widget _buildList(BuildContext context, List<Execution> execs) {
    if (execs.isEmpty) return const Center(child: Text('No executions found'));
    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: execs.length,
      itemBuilder: (ctx, i) => _buildCard(ctx, execs[i]),
    );
  }

  Widget _buildCard(BuildContext context, Execution exec) {
    final color = exec.status.name == 'success' ? AppTheme.successColor
        : exec.status.name == 'error' ? AppTheme.errorColor
        : exec.status.name == 'running' ? AppTheme.infoColor : Colors.grey;
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => context.push('/executions/${exec.id}'),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 40, height: 40,
                    decoration: BoxDecoration(
                      color: color.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Icon(exec.status.name == 'success' ? Icons.check
                        : exec.status.name == 'error' ? Icons.close
                        : exec.status.name == 'running' ? Icons.hourglass_empty
                        : Icons.cancel, color: color, size: 22),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(exec.workflowName, style: const TextStyle(fontWeight: FontWeight.bold)),
                        Text(exec.status.name.toUpperCase(), style: TextStyle(fontSize: 11, color: color, fontWeight: FontWeight.w600)),
                      ],
                    ),
                  ),
                  Text(_formatDuration(exec), style: const TextStyle(fontSize: 12, color: Colors.grey)),
                ],
              ),
              const SizedBox(height: 10),
              Row(
                children: [
                  Text('${exec.itemCount} steps', style: const TextStyle(fontSize: 12, color: Colors.grey)),
                  const SizedBox(width: 12),
                  Text(_formatTime(exec.startedAt), style: const TextStyle(fontSize: 12, color: Colors.grey)),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _formatDuration(Execution exec) {
    if (exec.duration == null) return 'Running...';
    final ms = exec.duration!.inMilliseconds;
    if (ms < 1000) return '${ms}ms';
    return '${(ms / 1000).toStringAsFixed(1)}s';
  }

  String _formatTime(DateTime dt) {
    return '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
  }
}
