import 'package:flutter/foundation.dart';
import '../models/app_models.dart';

class InstanceProvider extends ChangeNotifier {
  final List<N8nInstance> _instances = [];
  List<N8nInstance> get instances => List.unmodifiable(_instances);
  N8nInstance? _activeInstance;
  N8nInstance? get activeInstance => _activeInstance;

  InstanceProvider() {
    _instances.addAll([
      N8nInstance(id: '1', name: 'Production', url: 'https://n8n.example.com', apiKey: '***', isActive: true, workflowCount: 24),
      N8nInstance(id: '2', name: 'Development', url: 'https://dev-n8n.example.com', apiKey: '***', workflowCount: 12),
      N8nInstance(id: '3', name: 'Staging', url: 'https://staging-n8n.example.com', workflowCount: 8),
    ]);
    _activeInstance = _instances[0];
  }

  void addInstance(N8nInstance inst) { _instances.add(inst); notifyListeners(); }
  void removeInstance(String id) { _instances.removeWhere((i) => i.id == id); notifyListeners(); }
  void setActive(String id) {
    for (var i in _instances) { i.isActive = i.id == id; }
    _activeInstance = _instances.firstWhere((i) => i.id == id);
    notifyListeners();
  }
}
