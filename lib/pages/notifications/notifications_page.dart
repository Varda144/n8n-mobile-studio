import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../theme/app_theme.dart';
import '../../providers/notifications_provider.dart';

class NotificationsPage extends StatelessWidget {
  const NotificationsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<NotificationsProvider>();
    return Scaffold(
      appBar: AppBar(
        title: const Text('Notifications', style: TextStyle(fontWeight: FontWeight.bold)),
        actions: [
          if (provider.unreadCount > 0)
            TextButton(
              onPressed: provider.markAllRead,
              child: const Text('Mark all read'),
            ),
        ],
      ),
      body: provider.notifications.isEmpty
          ? const Center(child: Text('No notifications'))
          : ListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: provider.notifications.length,
              itemBuilder: (ctx, i) {
                final notif = provider.notifications[i];
                final color = notif.type == 'success' ? AppTheme.successColor
                    : notif.type == 'error' ? AppTheme.errorColor
                    : notif.type == 'warning' ? AppTheme.warningColor
                    : AppTheme.infoColor;
                return Card(
                  margin: const EdgeInsets.only(bottom: 8),
                  color: notif.read ? null : color.withOpacity(0.05),
                  child: ListTile(
                    leading: Container(
                      width: 40, height: 40,
                      decoration: BoxDecoration(
                        color: color.withOpacity(0.15),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Icon(_getIcon(notif.type), color: color, size: 20),
                    ),
                    title: Text(notif.title, style: TextStyle(
                      fontWeight: notif.read ? FontWeight.normal : FontWeight.bold,
                      fontSize: 14,
                    )),
                    subtitle: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const SizedBox(height: 4),
                        Text(notif.message, style: const TextStyle(fontSize: 12, color: Colors.grey), maxLines: 2),
                        const SizedBox(height: 4),
                        Text(_formatTime(notif.timestamp), style: const TextStyle(fontSize: 10, color: Colors.grey)),
                      ],
                    ),
                    trailing: !notif.read
                        ? Container(
                            width: 8, height: 8,
                            decoration: BoxDecoration(color: color, shape: BoxShape.circle),
                          )
                        : null,
                    onTap: () => provider.markRead(notif.id),
                  ),
                );
              },
            ),
    );
  }

  IconData _getIcon(String type) {
    switch (type) {
      case 'success': return Icons.check_circle;
      case 'error': return Icons.error;
      case 'warning': return Icons.warning;
      default: return Icons.info;
    }
  }

  String _formatTime(DateTime dt) {
    final diff = DateTime.now().difference(dt);
    if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
    if (diff.inHours < 24) return '${diff.inHours}h ago';
    return '${diff.inDays}d ago';
  }
}
