import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../theme/app_theme.dart';
import '../../providers/mcp_provider.dart';

class McpPage extends StatelessWidget {
  const McpPage({super.key});

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<McpProvider>();
    return Scaffold(
      appBar: AppBar(
        title: const Text('MCP Servers', style: TextStyle(fontWeight: FontWeight.bold)),
        actions: [
          IconButton(
            icon: const Icon(Icons.add),
            onPressed: () {
              showModalBottomSheet(
                context: context,
                shape: const RoundedRectangleBorder(
                  borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
                ),
                builder: (ctx) => Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Add MCP Server', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
                      const SizedBox(height: 16),
                      const TextField(decoration: InputDecoration(hintText: 'Server Name', prefixIcon: Icon(Icons.label))),
                      const SizedBox(height: 12),
                      const TextField(decoration: InputDecoration(hintText: 'Server URL', prefixIcon: Icon(Icons.link))),
                      const SizedBox(height: 16),
                      SizedBox(width: double.infinity, child: ElevatedButton(onPressed: () => Navigator.pop(ctx), child: const Text('Connect'))),
                    ],
                  ),
                ),
              );
            },
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                gradient: LinearGradient(colors: [Colors.indigo, Colors.indigo.shade700]),
                borderRadius: BorderRadius.circular(16),
              ),
              child: Row(
                children: [
                  const Icon(Icons.device_hub, color: Colors.white, size: 32),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('Model Context Protocol', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16)),
                        Text('${provider.servers.where((s) => s.connected).length} servers connected', style: TextStyle(color: Colors.white.withValues(alpha: 0.8), fontSize: 12)),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),
            const Text('Servers', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 10),
            ...provider.servers.map((server) => Card(
              margin: const EdgeInsets.only(bottom: 10),
              child: ListTile(
                leading: Container(
                  width: 40, height: 40,
                  decoration: BoxDecoration(
                    color: (server.connected ? AppTheme.successColor : Colors.grey).withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Icon(Icons.dns, color: server.connected ? AppTheme.successColor : Colors.grey, size: 22),
                ),
                title: Text(server.name, style: const TextStyle(fontWeight: FontWeight.w600)),
                subtitle: Row(
                  children: [
                    Text('${server.toolCount} tools', style: const TextStyle(fontSize: 12, color: Colors.grey)),
                    const SizedBox(width: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: (server.connected ? AppTheme.successColor : Colors.grey).withValues(alpha: 0.15),
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: Text(server.connected ? 'Connected' : 'Disconnected',
                          style: TextStyle(fontSize: 10, color: server.connected ? AppTheme.successColor : Colors.grey)),
                    ),
                  ],
                ),
                trailing: Switch(
                  value: server.connected,
                  onChanged: (_) => provider.toggleConnection(server.id),
                  activeColor: AppTheme.primaryColor,
                ),
              ),
            )),
            const SizedBox(height: 20),
            const Text('Available Tools', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 10),
            ...provider.tools.map((tool) => Card(
              margin: const EdgeInsets.only(bottom: 8),
              child: ListTile(
                leading: Container(
                  width: 36, height: 36,
                  decoration: BoxDecoration(
                    color: AppTheme.primaryColor.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(Icons.code, color: AppTheme.primaryColor, size: 18),
                ),
                title: Text(tool.name, style: const TextStyle(fontWeight: FontWeight.w600, fontFamily: 'monospace')),
                subtitle: Text(tool.description, style: const TextStyle(fontSize: 12, color: Colors.grey)),
                trailing: Text(tool.server, style: const TextStyle(fontSize: 10, color: Colors.grey)),
              ),
            )),
          ],
        ),
      ),
    );
  }
}
