import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';
import '../../theme/app_theme.dart';
import '../../providers/workflow_provider.dart';
import '../../models/workflow_model.dart';

class WorkflowListPage extends StatefulWidget {
  const WorkflowListPage({super.key});
  @override
  State<WorkflowListPage> createState() => _WorkflowListPageState();
}

class _WorkflowListPageState extends State<WorkflowListPage> {
  String _filter = 'All';
  String _search = '';

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<WorkflowProvider>();
    var workflows = _search.isEmpty ? provider.workflows : provider.search(_search);
    if (_filter != 'All') {
      workflows = workflows.where((w) => w.status.name == _filter.toLowerCase()).toList();
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('Workflows', style: TextStyle(fontWeight: FontWeight.bold)),
        actions: [
          PopupMenuButton<String>(
            icon: const Icon(Icons.filter_list),
            onSelected: (v) => setState(() => _filter = v),
            itemBuilder: (_) => ['All', 'Active', 'Inactive', 'Draft'].map((f) =>
              PopupMenuItem(value: f, child: Row(children: [
                if (_filter == f) const Icon(Icons.check, size: 16, color: AppTheme.primaryColor),
                if (_filter == f) const SizedBox(width: 8),
                Text(f),
              ]))
            ).toList(),
          ),
          IconButton(icon: const Icon(Icons.sort), onPressed: () {}),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
            child: TextField(
              decoration: InputDecoration(
                hintText: 'Search workflows...',
                prefixIcon: const Icon(Icons.search),
                suffixIcon: _search.isNotEmpty
                    ? IconButton(icon: const Icon(Icons.clear), onPressed: () => setState(() => _search = ''))
                    : null,
              ),
              onChanged: (v) => setState(() => _search = v),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              children: ['All', 'Active', 'Inactive', 'Draft'].map((f) {
                final isSelected = _filter == f;
                return Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: FilterChip(
                    label: Text(f, style: TextStyle(fontSize: 12, color: isSelected ? Colors.white : null)),
                    selected: isSelected,
                    onSelected: (_) => setState(() => _filter = f),
                    selectedColor: AppTheme.primaryColor,
                  ),
                );
              }).toList(),
            ),
          ),
          const SizedBox(height: 8),
          Expanded(
            child: workflows.isEmpty
                ? const Center(child: Text('No workflows found'))
                : ListView.builder(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    itemCount: workflows.length,
                    itemBuilder: (ctx, i) => _buildWorkflowCard(ctx, workflows[i]),
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildWorkflowCard(BuildContext context, Workflow workflow) {
    final isActive = workflow.status == WorkflowStatus.active;
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => context.push('/workflows/${workflow.id}'),
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
                      color: isActive ? AppTheme.successColor.withValues(alpha: 0.2) : Colors.grey.withValues(alpha: 0.2),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Icon(Icons.account_tree,
                        color: isActive ? AppTheme.successColor : Colors.grey, size: 22),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(workflow.name, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
                        Text(workflow.description, style: const TextStyle(color: Colors.grey, fontSize: 12), maxLines: 1, overflow: TextOverflow.ellipsis),
                      ],
                    ),
                  ),
                  PopupMenuButton(
                    itemBuilder: (_) => [
                      const PopupMenuItem(value: 'edit', child: Row(children: [Icon(Icons.edit, size: 18), SizedBox(width: 8), Text('Edit')])),
                      const PopupMenuItem(value: 'duplicate', child: Row(children: [Icon(Icons.copy, size: 18), SizedBox(width: 8), Text('Duplicate')])),
                      const PopupMenuItem(value: 'export', child: Row(children: [Icon(Icons.download, size: 18), SizedBox(width: 8), Text('Export')])),
                      PopupMenuItem(value: 'toggle', child: Row(children: [
                        Icon(isActive ? Icons.pause : Icons.play_arrow, size: 18),
                        const SizedBox(width: 8),
                        Text(isActive ? 'Deactivate' : 'Activate'),
                      ])),
                      const PopupMenuItem(value: 'delete', child: Row(children: [Icon(Icons.delete, size: 18, color: Colors.red), SizedBox(width: 8), Text('Delete', style: TextStyle(color: Colors.red))])),
                    ],
                    onSelected: (v) {
                      final provider = context.read<WorkflowProvider>();
                      switch (v) {
                        case 'edit': context.push('/workflows/${workflow.id}'); break;
                        case 'duplicate': provider.duplicateWorkflow(workflow.id); break;
                        case 'toggle': provider.toggleActive(workflow.id); break;
                        case 'delete': provider.deleteWorkflow(workflow.id); break;
                      }
                    },
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  _buildChip(workflow.status.name.toUpperCase(), isActive ? AppTheme.successColor : Colors.grey),
                  const SizedBox(width: 8),
                  _buildChip(workflow.triggerType.name, AppTheme.infoColor),
                  const SizedBox(width: 8),
                  _buildChip('${workflow.nodes.length} nodes', Colors.orange),
                  const Spacer(),
                  Icon(Icons.play_circle_outline, color: AppTheme.primaryColor, size: 20),
                  const SizedBox(width: 4),
                  Text('${workflow.executionCount}', style: TextStyle(color: AppTheme.primaryColor, fontWeight: FontWeight.w600)),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildChip(String label, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(label, style: TextStyle(fontSize: 10, color: color, fontWeight: FontWeight.w600)),
    );
  }
}
