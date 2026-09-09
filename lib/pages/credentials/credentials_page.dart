import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../theme/app_theme.dart';
import '../../providers/credentials_provider.dart';
import '../../models/credential_model.dart';

class CredentialsPage extends StatelessWidget {
  const CredentialsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<CredentialsProvider>();
    return Scaffold(
      appBar: AppBar(
        title: const Text('Credentials', style: TextStyle(fontWeight: FontWeight.bold)),
        actions: [
          IconButton(
            icon: const Icon(Icons.add),
            onPressed: () {
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
                      const Text('New Credential', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
                      const SizedBox(height: 16),
                      const TextField(decoration: InputDecoration(hintText: 'Credential Name', prefixIcon: Icon(Icons.label))),
                      const SizedBox(height: 12),
                      const TextField(decoration: InputDecoration(hintText: 'Type (e.g., Gmail, Slack, AWS)', prefixIcon: Icon(Icons.category))),
                      const SizedBox(height: 12),
                      const TextField(decoration: InputDecoration(hintText: 'API Key / Token', prefixIcon: Icon(Icons.key)), obscureText: true),
                      const SizedBox(height: 12),
                      const TextField(decoration: InputDecoration(hintText: 'Description (optional)', prefixIcon: Icon(Icons.description))),
                      const SizedBox(height: 16),
                      SizedBox(
                        width: double.infinity,
                        child: ElevatedButton.icon(
                          onPressed: () => Navigator.pop(ctx),
                          icon: const Icon(Icons.save),
                          label: const Text('Save Credential'),
                        ),
                      ),
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
        itemCount: provider.credentials.length,
        itemBuilder: (ctx, i) {
          final cred = provider.credentials[i];
          return Card(
            margin: const EdgeInsets.only(bottom: 10),
            child: ListTile(
              leading: Container(
                width: 40, height: 40,
                decoration: BoxDecoration(
                  color: _getTypeColor(cred.type).withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Icon(_getTypeIcon(cred.type), color: _getTypeColor(cred.type), size: 22),
              ),
              title: Text(cred.name, style: const TextStyle(fontWeight: FontWeight.w600)),
              subtitle: Row(
                children: [
                  Text(cred.type, style: TextStyle(fontSize: 12, color: _getTypeColor(cred.type))),
                  const SizedBox(width: 8),
                  if (cred.synced) ...[
                    Icon(Icons.cloud_done, size: 12, color: AppTheme.successColor),
                    const SizedBox(width: 2),
                    Text('Synced', style: TextStyle(fontSize: 10, color: AppTheme.successColor)),
                  ],
                ],
              ),
              trailing: PopupMenuButton(
                itemBuilder: (_) => [
                  const PopupMenuItem(value: 'edit', child: Row(children: [Icon(Icons.edit, size: 18), SizedBox(width: 8), Text('Edit')])),
                  const PopupMenuItem(value: 'test', child: Row(children: [Icon(Icons.wifi_find, size: 18), SizedBox(width: 8), Text('Test Connection')])),
                  const PopupMenuItem(value: 'delete', child: Row(children: [Icon(Icons.delete, size: 18, color: Colors.red), SizedBox(width: 8), Text('Delete', style: TextStyle(color: Colors.red))])),
                ],
                onSelected: (v) {
                  if (v == 'delete') provider.deleteCredential(cred.id);
                },
              ),
            ),
          );
        },
      ),
    );
  }

  Color _getTypeColor(String type) {
    switch (type.toLowerCase()) {
      case 'gmail': return Colors.red;
      case 'slack': return Colors.purple;
      case 'aws': return Colors.orange;
      case 'github': return Colors.grey;
      case 'postgresql': return Colors.blue;
      case 'stripe': return Colors.indigo;
      default: return AppTheme.primaryColor;
    }
  }

  IconData _getTypeIcon(String type) {
    switch (type.toLowerCase()) {
      case 'gmail': return Icons.email;
      case 'slack': return Icons.chat;
      case 'aws': return Icons.cloud;
      case 'github': return Icons.code;
      case 'postgresql': return Icons.storage;
      case 'stripe': return Icons.payment;
      default: return Icons.vpn_key;
    }
  }
}
