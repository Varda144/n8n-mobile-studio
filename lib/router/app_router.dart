import 'package:go_router/go_router.dart';
import 'package:flutter/material.dart';
import '../pages/dashboard/dashboard_page.dart';
import '../pages/workflows/workflow_list_page.dart';
import '../pages/workflows/workflow_create_page.dart';
import '../pages/editor/workflow_editor_page.dart';
import '../pages/executions/executions_page.dart';
import '../pages/executions/execution_detail_page.dart';
import '../pages/webhooks/webhooks_page.dart';
import '../pages/credentials/credentials_page.dart';
import '../pages/templates/templates_page.dart';
import '../pages/ai/ai_page.dart';
import '../pages/mcp/mcp_page.dart';
import '../pages/settings/settings_page.dart';
import '../pages/search/search_page.dart';
import '../pages/about/about_page.dart';
import '../pages/notifications/notifications_page.dart';
import '../widgets/shell_scaffold.dart';

final GlobalKey<NavigatorState> _rootNavigatorKey = GlobalKey<NavigatorState>();

final appRouter = GoRouter(
  navigatorKey: _rootNavigatorKey,
  initialLocation: '/',
  routes: [
    ShellRoute(
      builder: (context, state, child) => ShellScaffold(child: child),
      routes: [
        GoRoute(path: '/', builder: (_, __) => const DashboardPage()),
        GoRoute(path: '/workflows', builder: (_, __) => const WorkflowListPage()),
        GoRoute(path: '/workflows/create', builder: (_, __) => const WorkflowCreatePage()),
        GoRoute(path: '/workflows/:id', builder: (_, state) => WorkflowEditorPage(workflowId: state.pathParameters['id']!)),
        GoRoute(path: '/executions', builder: (_, __) => const ExecutionsPage()),
        GoRoute(path: '/executions/:id', builder: (_, state) => ExecutionDetailPage(executionId: state.pathParameters['id']!)),
        GoRoute(path: '/webhooks', builder: (_, __) => const WebhooksPage()),
        GoRoute(path: '/credentials', builder: (_, __) => const CredentialsPage()),
        GoRoute(path: '/templates', builder: (_, __) => const TemplatesPage()),
        GoRoute(path: '/ai', builder: (_, __) => const AiPage()),
        GoRoute(path: '/mcp', builder: (_, __) => const McpPage()),
        GoRoute(path: '/settings', builder: (_, __) => const SettingsPage()),
        GoRoute(path: '/search', builder: (_, __) => const SearchPage()),
        GoRoute(path: '/about', builder: (_, __) => const AboutPage()),
        GoRoute(path: '/notifications', builder: (_, __) => const NotificationsPage()),
      ],
    ),
  ],
);
