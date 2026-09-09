import 'package:flutter/foundation.dart';
import '../models/credential_model.dart';

class CredentialsProvider extends ChangeNotifier {
  final List<Credential> _credentials = [];
  List<Credential> get credentials => List.unmodifiable(_credentials);

  CredentialsProvider() {
    _credentials.addAll([
      Credential(name: 'Gmail Account', type: 'Gmail', description: 'Primary email', data: {'user': 'me@gmail.com'}),
      Credential(name: 'Slack Workspace', type: 'Slack', description: 'Main workspace', synced: true),
      Credential(name: 'AWS Production', type: 'AWS', data: {'region': 'us-east-1'}),
      Credential(name: 'GitHub Token', type: 'GitHub', data: {'token': '***'}),
      Credential(name: 'PostgreSQL DB', type: 'PostgreSQL', data: {'host': 'db.example.com'}),
      Credential(name: 'Stripe Payments', type: 'Stripe', synced: true),
    ]);
  }

  void addCredential(Credential cred) {
    _credentials.insert(0, cred);
    notifyListeners();
  }

  void updateCredential(Credential cred) {
    final i = _credentials.indexWhere((c) => c.id == cred.id);
    if (i != -1) { _credentials[i] = cred; notifyListeners(); }
  }

  void deleteCredential(String id) {
    _credentials.removeWhere((c) => c.id == id);
    notifyListeners();
  }

  List<Credential> search(String q) {
    if (q.isEmpty) return credentials;
    final lq = q.toLowerCase();
    return _credentials.where((c) => c.name.toLowerCase().contains(lq) || c.type.toLowerCase().contains(lq)).toList();
  }
}
