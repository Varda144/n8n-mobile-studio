import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

class AboutPage extends StatelessWidget {
  const AboutPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('About', style: TextStyle(fontWeight: FontWeight.bold))),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            const SizedBox(height: 20),
            Container(
              width: 100, height: 100,
              decoration: BoxDecoration(
                gradient: LinearGradient(colors: [AppTheme.primaryColor, AppTheme.primaryColor.withOpacity(0.7)]),
                borderRadius: BorderRadius.circular(24),
              ),
              child: const Icon(Icons.electric_bolt, color: Colors.white, size: 50),
            ),
            const SizedBox(height: 20),
            const Text('N8N Mobile Studio', style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold)),
            const SizedBox(height: 4),
            Text('v1.0.0', style: TextStyle(fontSize: 14, color: Colors.grey.shade400)),
            const SizedBox(height: 20),
            const Text(
              'The complete mobile-first workflow automation editor. Create, manage, and execute workflows on the go.',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.grey, height: 1.5),
            ),
            const SizedBox(height: 30),
            _buildInfoRow('Version', '1.0.0'),
            _buildInfoRow('Build', '2024.1'),
            _buildInfoRow('Platform', 'Flutter'),
            _buildInfoRow('License', 'MIT'),
            const SizedBox(height: 20),
            const Divider(),
            const SizedBox(height: 10),
            const Text('Features', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            _buildFeature('Visual Workflow Editor', 'Drag and drop nodes to build workflows'),
            _buildFeature('AI Assistant', 'Generate, explain, and optimize workflows with AI'),
            _buildFeature('Multi-Instance', 'Connect to multiple n8n instances'),
            _buildFeature('Offline Support', 'Work offline with local cache'),
            _buildFeature('MCP Integration', 'Connect to Model Context Protocol servers'),
            _buildFeature('Execution Debug', 'Debug failed workflow executions'),
            const SizedBox(height: 20),
            const Divider(),
            const SizedBox(height: 10),
            const Text('Connect', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            _buildLink('GitHub', Icons.code, 'github.com/n8n-io/n8n'),
            _buildLink('Documentation', Icons.book, 'docs.n8n.io'),
            _buildLink('Community', Icons.people, 'community.n8n.io'),
            const SizedBox(height: 30),
            Text('Made with Flutter', style: TextStyle(fontSize: 12, color: Colors.grey.shade500)),
          ],
        ),
      ),
    );
  }

  Widget _buildInfoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(color: Colors.grey)),
          Text(value, style: const TextStyle(fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }

  Widget _buildFeature(String title, String subtitle) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.check_circle, color: AppTheme.successColor, size: 20),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: const TextStyle(fontWeight: FontWeight.w600)),
                Text(subtitle, style: const TextStyle(fontSize: 12, color: Colors.grey)),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLink(String title, IconData icon, String url) {
    return ListTile(
      leading: Icon(icon, color: AppTheme.primaryColor, size: 20),
      title: Text(title),
      subtitle: Text(url, style: const TextStyle(fontSize: 11, color: Colors.grey)),
      trailing: const Icon(Icons.open_in_new, size: 16),
      dense: true,
    );
  }
}
