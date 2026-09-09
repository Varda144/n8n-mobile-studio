import 'package:flutter/foundation.dart';
import '../models/app_models.dart';
import '../models/workflow_model.dart';

class SearchProvider extends ChangeNotifier {
  String _query = '';
  String get query => _query;
  List<SearchableItem> _results = [];
  List<SearchableItem> get results => List.unmodifiable(_results);
  final List<String> _recentSearches = ['Email Automation', 'Slack', 'webhook'];
  List<String> get recentSearches => List.unmodifiable(_recentSearches);

  void search(String q, List<Workflow> workflows) {
    _query = q;
    if (q.isEmpty) { _results = []; notifyListeners(); return; }
    final lq = q.toLowerCase();
    _results = workflows
        .where((w) => w.name.toLowerCase().contains(lq) || w.description.toLowerCase().contains(lq))
        .map((w) => SearchableItem(id: w.id, title: w.name, subtitle: w.description, type: 'workflow', route: '/workflows/${w.id}'))
        .toList();
    if (q.isNotEmpty && !_recentSearches.contains(q)) {
      _recentSearches.insert(0, q);
      if (_recentSearches.length > 10) _recentSearches.removeLast();
    }
    notifyListeners();
  }

  void clear() { _query = ''; _results = []; notifyListeners(); }
}
