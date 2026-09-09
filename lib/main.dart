import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'theme/app_theme.dart';
import 'router/app_router.dart';
import 'providers/workflow_provider.dart';
import 'providers/execution_provider.dart';
import 'providers/credentials_provider.dart';
import 'providers/templates_provider.dart';
import 'providers/settings_provider.dart';
import 'providers/notifications_provider.dart';
import 'providers/search_provider.dart';
import 'providers/ai_provider.dart';
import 'providers/mcp_provider.dart';
import 'providers/instance_provider.dart';
import 'services/credential_store.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  // Initialize credential store
  CredentialStore().initialize();
  runApp(const N8nMobileStudio());
}

class N8nMobileStudio extends StatelessWidget {
  const N8nMobileStudio({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => InstanceProvider()),
        ChangeNotifierProvider(create: (_) => WorkflowProvider()),
        ChangeNotifierProvider(create: (_) => ExecutionProvider()),
        ChangeNotifierProvider(create: (_) => CredentialsProvider()),
        ChangeNotifierProvider(create: (_) => TemplatesProvider()),
        ChangeNotifierProvider(create: (_) => SettingsProvider()),
        ChangeNotifierProvider(create: (_) => NotificationsProvider()),
        ChangeNotifierProvider(create: (_) => SearchProvider()),
        ChangeNotifierProvider(create: (_) => AiProvider()),
        ChangeNotifierProvider(create: (_) => McpProvider()),
      ],
      child: Consumer<SettingsProvider>(
        builder: (context, settings, _) {
          return MaterialApp.router(
            title: 'N8N Mobile Studio',
            debugShowCheckedModeBanner: false,
            theme: AppTheme.lightTheme,
            darkTheme: AppTheme.darkTheme,
            themeMode: settings.themeMode,
            routerConfig: appRouter,
          );
        },
      ),
    );
  }
}
