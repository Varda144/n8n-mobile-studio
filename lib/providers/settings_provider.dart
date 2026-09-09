import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

class SettingsProvider extends ChangeNotifier {
  ThemeMode _themeMode = ThemeMode.dark;
  ThemeMode get themeMode => _themeMode;
  bool _notifications = true;
  bool get notifications => _notifications;
  bool _autoSave = true;
  bool get autoSave => _autoSave;
  bool _offlineMode = true;
  bool get offlineMode => _offlineMode;
  String _language = 'English';
  String get language => _language;
  bool _biometricLock = false;
  bool get biometricLock => _biometricLock;
  bool _cloudSync = true;
  bool get cloudSync => _cloudSync;

  void toggleTheme() {
    _themeMode = _themeMode == ThemeMode.dark ? ThemeMode.light : ThemeMode.dark;
    notifyListeners();
  }

  void setThemeMode(ThemeMode mode) { _themeMode = mode; notifyListeners(); }
  void toggleNotifications() { _notifications = !_notifications; notifyListeners(); }
  void toggleAutoSave() { _autoSave = !_autoSave; notifyListeners(); }
  void toggleOfflineMode() { _offlineMode = !_offlineMode; notifyListeners(); }
  void setLanguage(String l) { _language = l; notifyListeners(); }
  void toggleBiometric() { _biometricLock = !_biometricLock; notifyListeners(); }
  void toggleCloudSync() { _cloudSync = !_cloudSync; notifyListeners(); }
}
