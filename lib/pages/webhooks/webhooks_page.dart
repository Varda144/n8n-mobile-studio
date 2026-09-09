import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../../theme/app_theme.dart';
import '../../providers/execution_provider.dart';

class WebhooksPage extends StatefulWidget {
  const WebhooksPage({super.key});
  @override
  State<WebhooksPage> createState() => _WebhooksPageState();
}

class _WebhooksPageState extends State<WebhooksPage> {
  final _pathCtrl = TextEditingController();
  final _workflowCtrl = TextEditingController();

  @override
  Widget build(BuildContext context) {
    final exec = context.watch<ExecutionProvider>();
    final webhooks = exec.getRegisteredWebhooks();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Webhooks', style: TextStyle(fontWeight: FontWeight.bold)),
        actions: [
          IconButton(
            icon: Icon(exec.isWebhookServerRunning ? Icons.stop : Icons.play_arrow),
            onPressed: () {
              if (exec.isWebhookServerRunning) {
                exec.stopWebhookServer();
              } else {
                exec.startWebhookServer();
              }
            },
          ),
          IconButton(
            icon: const Icon(Icons.add),
            onPressed: () => _showCreateDialog(exec),
          ),
        ],
      ),
      body: Column(
        children: [
          // Server status banner
          Container(
            margin: const EdgeInsets.all(16),
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: exec.isWebhookServerRunning
                  ? AppTheme.successColor.withOpacity(0.15)
                  : Colors.grey.withOpacity(0.15),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: exec.isWebhookServerRunning
                    ? AppTheme.successColor.withOpacity(0.3)
                    : Colors.grey.withOpacity(0.3),
              ),
            ),
            child: Row(
              children: [
                Icon(
                  exec.isWebhookServerRunning ? Icons.check_circle : Icons.cancel,
                  color: exec.isWebhookServerRunning ? AppTheme.successColor : Colors.grey,
                  size: 24,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        exec.isWebhookServerRunning ? 'Server Running' : 'Server Stopped',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          color: exec.isWebhookServerRunning ? AppTheme.successColor : Colors.grey,
                        ),
                      ),
                      if (exec.isWebhookServerRunning)
                        Text(
                          'http://localhost:${exec.webhookServerPort}',
                          style: const TextStyle(fontSize: 12, fontFamily: 'monospace'),
                        ),
                    ],
                  ),
                ),
                ElevatedButton.icon(
                  onPressed: () {
                    if (exec.isWebhookServerRunning) {
                      exec.stopWebhookServer();
                    } else {
                      exec.startWebhookServer();
                    }
                  },
                  icon: Icon(exec.isWebhookServerRunning ? Icons.stop : Icons.play_arrow, size: 16),
                  label: Text(exec.isWebhookServerRunning ? 'Stop' : 'Start'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: exec.isWebhookServerRunning ? AppTheme.errorColor : AppTheme.primaryColor,
                  ),
                ),
              ],
            ),
          ),
          // Webhook list
          Expanded(
            child: webhooks.isEmpty
                ? Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.webhook, size: 64, color: Colors.grey.withOpacity(0.3)),
                        const SizedBox(height: 16),
                        const Text('No webhooks registered', style: TextStyle(color: Colors.grey)),
                        const SizedBox(height: 8),
                        const Text('Start the server and add webhooks', style: TextStyle(fontSize: 12, color: Colors.grey)),
                      ],
                    ),
                  )
                : ListView.builder(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    itemCount: webhooks.length,
                    itemBuilder: (ctx, i) {
                      final wh = webhooks[i];
                      final url = exec.getWebhookUrl(wh['path'] ?? '');
                      return Card(
                        margin: const EdgeInsets.only(bottom: 10),
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
                                      color: AppTheme.primaryColor.withOpacity(0.15),
                                      borderRadius: BorderRadius.circular(10),
                                    ),
                                    child: const Icon(Icons.webhook, color: AppTheme.primaryColor, size: 22),
                                  ),
                                  const SizedBox(width: 12),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(wh['path'] ?? '', style: const TextStyle(fontWeight: FontWeight.bold, fontFamily: 'monospace')),
                                        Text('Workflow: ${wh['workflowId']}', style: const TextStyle(fontSize: 12, color: Colors.grey)),
                                      ],
                                    ),
                                  ),
                                  IconButton(
                                    icon: const Icon(Icons.delete, size: 18, color: Colors.red),
                                    onPressed: () => exec.unregisterWebhook(wh['path'] ?? ''),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 10),
                              Container(
                                padding: const EdgeInsets.all(10),
                                decoration: BoxDecoration(
                                  color: Colors.black.withOpacity(0.3),
                                  borderRadius: BorderRadius.circular(8),
                                ),
                                child: Row(
                                  children: [
                                    Expanded(
                                      child: Text(
                                        url,
                                        style: const TextStyle(fontSize: 11, fontFamily: 'monospace', color: AppTheme.primaryColor),
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ),
                                    IconButton(
                                      icon: const Icon(Icons.copy, size: 16),
                                      onPressed: () {
                                        Clipboard.setData(ClipboardData(text: url));
                                        ScaffoldMessenger.of(context).showSnackBar(
                                          const SnackBar(content: Text('URL copied to clipboard')),
                                        );
                                      },
                                    ),
                                  ],
                                ),
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }

  void _showCreateDialog(ExecutionProvider exec) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => Padding(
        padding: EdgeInsets.fromLTRB(24, 24, 24, MediaQuery.of(ctx).viewInsets.bottom + 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('New Webhook', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
            const SizedBox(height: 16),
            TextField(
              controller: _pathCtrl,
              decoration: const InputDecoration(
                hintText: 'Webhook Path (e.g., /api/webhook/my-trigger)',
                prefixIcon: Icon(Icons.link),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _workflowCtrl,
              decoration: const InputDecoration(
                hintText: 'Workflow ID',
                prefixIcon: Icon(Icons.account_tree),
              ),
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: () {
                  if (_pathCtrl.text.isNotEmpty && _workflowCtrl.text.isNotEmpty) {
                    exec.registerWebhook(_pathCtrl.text, _workflowCtrl.text);
                    _pathCtrl.clear();
                    _workflowCtrl.clear();
                    Navigator.pop(ctx);
                  }
                },
                child: const Text('Register Webhook'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    _pathCtrl.dispose();
    _workflowCtrl.dispose();
    super.dispose();
  }
}
