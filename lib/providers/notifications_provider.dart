import 'package:flutter/foundation.dart';
import '../models/app_models.dart';

class NotificationsProvider extends ChangeNotifier {
  final List<AppNotification> _notifications = [];
  List<AppNotification> get notifications => List.unmodifiable(_notifications);

  NotificationsProvider() {
    _notifications.addAll([
      AppNotification(id: '1', title: 'Workflow Executed', message: 'Email Automation completed successfully', type: 'success'),
      AppNotification(id: '2', title: 'Execution Failed', message: 'Data Sync Pipeline encountered an error', type: 'error'),
      AppNotification(id: '3', title: 'New Template', message: 'AI Content Generator template is now available', type: 'info'),
      AppNotification(id: '4', title: 'Credential Expiring', message: 'Gmail credential expires in 7 days', type: 'warning'),
      AppNotification(id: '5', title: 'Workflow Active', message: 'Social Media Poster is now active', type: 'success'),
    ]);
  }

  int get unreadCount => _notifications.where((n) => !n.read).length;
  void markRead(String id) {
    final i = _notifications.indexWhere((n) => n.id == id);
    if (i != -1) { _notifications[i].read = true; notifyListeners(); }
  }
  void markAllRead() {
    for (var n in _notifications) { n.read = true; }
    notifyListeners();
  }
  void addNotification(AppNotification n) { _notifications.insert(0, n); notifyListeners(); }
}
