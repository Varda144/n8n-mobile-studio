import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../theme/app_theme.dart';
import '../../providers/settings_provider.dart';

class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsProvider>();
    return Scaffold(
      appBar: AppBar(title: const Text('Settings', style: TextStyle(fontWeight: FontWeight.bold))),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _buildSection('Appearance', [
            _buildSwitchTile(
              icon: Icons.dark_mode,
              title: 'Dark Mode',
              subtitle: 'Use dark theme',
              value: settings.themeMode == ThemeMode.dark,
              onChanged: (_) => settings.toggleTheme(),
              color: Colors.purple,
            ),
          ]),
          const SizedBox(height: 16),
          _buildSection('General', [
            _buildSwitchTile(
              icon: Icons.notifications_active,
              title: 'Notifications',
              subtitle: 'Enable push notifications',
              value: settings.notifications,
              onChanged: (_) => settings.toggleNotifications(),
              color: Colors.blue,
            ),
            _buildSwitchTile(
              icon: Icons.save,
              title: 'Auto Save',
              subtitle: 'Save changes automatically',
              value: settings.autoSave,
              onChanged: (_) => settings.toggleAutoSave(),
              color: Colors.green,
            ),
            _buildSwitchTile(
              icon: Icons.cloud_sync,
              title: 'Cloud Sync',
              subtitle: 'Sync data across devices',
              value: settings.cloudSync,
              onChanged: (_) => settings.toggleCloudSync(),
              color: Colors.teal,
            ),
          ]),
          const SizedBox(height: 16),
          _buildSection('Offline', [
            _buildSwitchTile(
              icon: Icons.offline_bolt,
              title: 'Offline Mode',
              subtitle: 'Cache data for offline use',
              value: settings.offlineMode,
              onChanged: (_) => settings.toggleOfflineMode(),
              color: Colors.orange,
            ),
          ]),
          const SizedBox(height: 16),
          _buildSection('Security', [
            _buildSwitchTile(
              icon: Icons.fingerprint,
              title: 'Biometric Lock',
              subtitle: 'Require biometric to open app',
              value: settings.biometricLock,
              onChanged: (_) => settings.toggleBiometric(),
              color: Colors.red,
            ),
            _buildNavTile(
              icon: Icons.security,
              title: 'Security Settings',
              subtitle: 'Manage app security',
              color: Colors.indigo,
              onTap: () {},
            ),
          ]),
          const SizedBox(height: 16),
          _buildSection('Language', [
            _buildNavTile(
              icon: Icons.language,
              title: 'Language',
              subtitle: settings.language,
              color: Colors.cyan,
              onTap: () {},
            ),
          ]),
          const SizedBox(height: 16),
          _buildSection('Data', [
            _buildNavTile(
              icon: Icons.backup,
              title: 'Backup Data',
              subtitle: 'Export all app data',
              color: Colors.blue,
              onTap: () {},
            ),
            _buildNavTile(
              icon: Icons.restore,
              title: 'Restore Data',
              subtitle: 'Import from backup',
              color: Colors.green,
              onTap: () {},
            ),
            _buildNavTile(
              icon: Icons.delete_sweep,
              title: 'Clear Cache',
              subtitle: 'Free up storage space',
              color: Colors.red,
              onTap: () {},
            ),
          ]),
          const SizedBox(height: 16),
          _buildNavTile(
            icon: Icons.info_outline,
            title: 'About',
            subtitle: 'Version 1.0.0',
            color: AppTheme.primaryColor,
            onTap: () => Navigator.pushNamed(context, '/about'),
          ),
        ],
      ),
    );
  }

  Widget _buildSection(String title, List<Widget> children) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: Colors.grey)),
        const SizedBox(height: 8),
        Container(
          decoration: BoxDecoration(
            color: AppTheme.surface,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(children: children),
        ),
      ],
    );
  }

  Widget _buildSwitchTile({
    required IconData icon,
    required String title,
    required String subtitle,
    required bool value,
    required ValueChanged<bool> onChanged,
    required Color color,
  }) {
    return ListTile(
      leading: Container(
        width: 36, height: 36,
        decoration: BoxDecoration(color: color.withOpacity(0.15), borderRadius: BorderRadius.circular(8)),
        child: Icon(icon, color: color, size: 20),
      ),
      title: Text(title, style: const TextStyle(fontSize: 14)),
      subtitle: Text(subtitle, style: const TextStyle(fontSize: 11, color: Colors.grey)),
      trailing: Switch(value: value, onChanged: onChanged, activeColor: AppTheme.primaryColor),
    );
  }

  Widget _buildNavTile({
    required IconData icon,
    required String title,
    required String subtitle,
    required Color color,
    required VoidCallback onTap,
  }) {
    return ListTile(
      leading: Container(
        width: 36, height: 36,
        decoration: BoxDecoration(color: color.withOpacity(0.15), borderRadius: BorderRadius.circular(8)),
        child: Icon(icon, color: color, size: 20),
      ),
      title: Text(title, style: const TextStyle(fontSize: 14)),
      subtitle: Text(subtitle, style: const TextStyle(fontSize: 11, color: Colors.grey)),
      trailing: const Icon(Icons.chevron_right, size: 20),
      onTap: onTap,
    );
  }
}
