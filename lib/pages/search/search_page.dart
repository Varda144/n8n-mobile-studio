import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';
import '../../theme/app_theme.dart';
import '../../providers/search_provider.dart';
import '../../providers/workflow_provider.dart';

class SearchPage extends StatefulWidget {
  const SearchPage({super.key});
  @override
  State<SearchPage> createState() => _SearchPageState();
}

class _SearchPageState extends State<SearchPage> {
  final _searchCtrl = TextEditingController();
  final _focusNode = FocusNode();

  @override
  void initState() {
    super.initState();
    _focusNode.requestFocus();
  }

  @override
  Widget build(BuildContext context) {
    final searchProvider = context.watch<SearchProvider>();
    final workflowProvider = context.watch<WorkflowProvider>();

    return Scaffold(
      appBar: AppBar(
        title: TextField(
          controller: _searchCtrl,
          focusNode: _focusNode,
          autofocus: true,
          decoration: InputDecoration(
            hintText: 'Search workflows, executions...',
            border: InputBorder.none,
            prefixIcon: const Icon(Icons.search),
            suffixIcon: _searchCtrl.text.isNotEmpty
                ? IconButton(
                    icon: const Icon(Icons.clear),
                    onPressed: () {
                      _searchCtrl.clear();
                      searchProvider.clear();
                    },
                  )
                : null,
          ),
          onChanged: (v) => searchProvider.search(v, workflowProvider.workflows),
        ),
        actions: [
          TextButton(
            onPressed: () {
              searchProvider.clear();
              Navigator.pop(context);
            },
            child: const Text('Cancel'),
          ),
        ],
      ),
      body: _searchCtrl.text.isEmpty
          ? _buildRecentSearches(searchProvider)
          : _buildResults(searchProvider),
    );
  }

  Widget _buildRecentSearches(SearchProvider provider) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const Text('Recent Searches', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: Colors.grey)),
        const SizedBox(height: 8),
        ...provider.recentSearches.map((s) => ListTile(
          leading: const Icon(Icons.history, color: Colors.grey, size: 20),
          title: Text(s),
          onTap: () {
            _searchCtrl.text = s;
            context.read<SearchProvider>().search(s, context.read<WorkflowProvider>().workflows);
          },
        )),
        const SizedBox(height: 24),
        const Text('Quick Actions', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: Colors.grey)),
        const SizedBox(height: 8),
        _buildQuickAction('Create New Workflow', Icons.add, '/workflows/create'),
        _buildQuickAction('Browse Templates', Icons.dashboard_customize, '/templates'),
        _buildQuickAction('View Executions', Icons.play_circle_outline, '/executions'),
        _buildQuickAction('AI Assistant', Icons.auto_awesome, '/ai'),
      ],
    );
  }

  Widget _buildQuickAction(String label, IconData icon, String route) {
    return ListTile(
      leading: CircleAvatar(
        backgroundColor: AppTheme.primaryColor.withValues(alpha: 0.15),
        child: Icon(icon, color: AppTheme.primaryColor, size: 20),
      ),
      title: Text(label),
      trailing: const Icon(Icons.arrow_forward_ios, size: 16),
      onTap: () => context.push(route),
    );
  }

  Widget _buildResults(SearchProvider provider) {
    if (provider.results.isEmpty) {
      return const Center(child: Text('No results found'));
    }
    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: provider.results.length,
      itemBuilder: (ctx, i) {
        final item = provider.results[i];
        return Card(
          margin: const EdgeInsets.only(bottom: 8),
          child: ListTile(
            leading: CircleAvatar(
              backgroundColor: AppTheme.primaryColor.withValues(alpha: 0.15),
              child: const Icon(Icons.account_tree, color: AppTheme.primaryColor, size: 20),
            ),
            title: Text(item.title, style: const TextStyle(fontWeight: FontWeight.w600)),
            subtitle: Text(item.subtitle, maxLines: 1, overflow: TextOverflow.ellipsis),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.push(item.route),
          ),
        );
      },
    );
  }
}
