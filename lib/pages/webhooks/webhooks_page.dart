import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

class WebhooksPage extends StatelessWidget {
  const WebhooksPage({super.key});

  @override
  Widget build(BuildContext context) {
    final webhooks = [
      ('/api/webhook/email', 'Email Automation', true, 142),
      ('/api/webhook/slack', 'Slack Notifications', true, 56),
      ('/api/webhook/stripe', 'Payment Processing', false, 0),
      ('/api/webhook/form', 'Form Submission', true, 89),
    ];

    return Scaffold(
      appBar: AppBar(
        title: const Text('Webhooks', style: TextStyle(fontWeight: FontWeight.bold)),
        actions: [
          IconButton(
            icon: const Icon(Icons.add),
            onPressed: () {
              showModalBottomSheet(
                context: context,
                shape: const RoundedRectangleBorder(
                  borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
                ),
                builder: (ctx) => Container(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('New Webhook', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
                      const SizedBox(height: 16),
                      const TextField(decoration: InputDecoration(hintText: 'Webhook Path', prefixIcon: Icon(Icons.link))),
                      const SizedBox(height: 12),
                      const TextField(decoration: InputDecoration(hintText: 'Select Workflow', prefixIcon: Icon(Icons.account_tree))),
                      const SizedBox(height: 16),
                      SizedBox(width: double.infinity, child: ElevatedButton(onPressed: () => Navigator.pop(ctx), child: const Text('Create Webhook'))),
                    ],
                  ),
                ),
              );
            },
          ),
        ],
      ),
      body: ListView.builder(
        padding: const EdgeInsets.all(16),
        itemCount: webhooks.length,
        itemBuilder: (ctx, i) {
          final wh = webhooks[i];
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
                          color: (wh.$3 ? AppTheme.successColor : Colors.grey).withOpacity(0.15),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Icon(Icons.webhook, color: wh.$3 ? AppTheme.successColor : Colors.grey, size: 22),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(wh.$2, style: const TextStyle(fontWeight: FontWeight.bold)),
                            Text(wh.$1, style: const TextStyle(fontSize: 12, color: Colors.grey, fontFamily: 'monospace')),
                          ],
                        ),
                      ),
                      Switch(
                        value: wh.$3,
                        onChanged: (_) {},
                        activeColor: AppTheme.primaryColor,
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      _buildInfo(Icons.web, 'GET / POST'),
                      const SizedBox(width: 16),
                      _buildInfo(Icons.play_circle_outline, '${wh.$4} calls'),
                      const Spacer(),
                      IconButton(icon: const Icon(Icons.copy, size: 18), onPressed: () {}),
                      IconButton(icon: const Icon(Icons.open_in_new, size: 18), onPressed: () {}),
                    ],
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildInfo(IconData icon, String text) {
    return Row(
      children: [
        Icon(icon, size: 14, color: Colors.grey),
        const SizedBox(width: 4),
        Text(text, style: const TextStyle(fontSize: 12, color: Colors.grey)),
      ],
    );
  }
}
