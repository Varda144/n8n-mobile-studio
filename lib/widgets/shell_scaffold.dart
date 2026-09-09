import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../theme/app_theme.dart';

class ShellScaffold extends StatefulWidget {
  final Widget child;
  const ShellScaffold({super.key, required this.child});

  @override
  State<ShellScaffold> createState() => _ShellScaffoldState();
}

class _ShellScaffoldState extends State<ShellScaffold> {
  int _selectedIndex = 0;

  final _navItems = [
    Icons.dashboard_rounded,
    Icons.account_tree_rounded,
    Icons.play_circle_outline_rounded,
    Icons.auto_awesome_rounded,
    Icons.settings_rounded,
  ];

  final _navLabels = ['Dashboard', 'Workflows', 'Executions', 'AI', 'Settings'];

  final _navRoutes = ['/', '/workflows', '/executions', '/ai', '/settings'];

  @override
  Widget build(BuildContext context) {
    final currentPath = GoRouterState.of(context).uri.path;
    return Scaffold(
      body: widget.child,
      bottomNavigationBar: NavigationBar(
        selectedIndex: _selectedIndex,
        onDestinationSelected: (i) {
          setState(() => _selectedIndex = i);
          context.go(_navRoutes[i]);
        },
        animationDuration: const Duration(milliseconds: 400),
        destinations: List.generate(
          _navItems.length,
          (i) => NavigationDestination(
            icon: Icon(_navItems[i], color: currentPath == _navRoutes[i] ? AppTheme.primaryColor : null),
            selectedIcon: Icon(_navItems[i], color: AppTheme.primaryColor),
            label: _navLabels[i],
          ),
        ),
      ),
      floatingActionButton: _buildFab(context, currentPath),
    );
  }

  Widget? _buildFab(BuildContext context, String path) {
    if (path == '/workflows' || path == '/') {
      return FloatingActionButton.extended(
        onPressed: () => context.push('/workflows/create'),
        backgroundColor: AppTheme.primaryColor,
        icon: const Icon(Icons.add, color: Colors.white),
        label: const Text('New Workflow', style: TextStyle(color: Colors.white)),
      );
    }
    return null;
  }
}
