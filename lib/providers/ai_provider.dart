import 'package:flutter/foundation.dart';

class AiProvider extends ChangeNotifier {
  bool _loading = false;
  bool get loading => _loading;
  String _lastResult = '';
  String get lastResult => _lastResult;
  final List<Map<String, String>> _history = [];
  List<Map<String, String>> get history => List.unmodifiable(_history);

  Future<String> generateWorkflow(String prompt) async {
    _loading = true; notifyListeners();
    await Future.delayed(const Duration(seconds: 2));
    _lastResult = 'Generated workflow based on: "$prompt"\n\nNodes created:\n- Trigger Node\n- Processing Node\n- Action Node\n- Output Node';
    _history.add({'prompt': prompt, 'result': _lastResult, 'action': 'generate'});
    _loading = false; notifyListeners();
    return _lastResult;
  }

  Future<String> explainWorkflow(String workflowJson) async {
    _loading = true; notifyListeners();
    await Future.delayed(const Duration(seconds: 2));
    _lastResult = 'This workflow performs:\n1. Receives input data\n2. Validates and transforms\n3. Processes with business logic\n4. Outputs results';
    _history.add({'prompt': 'Explain', 'result': _lastResult, 'action': 'explain'});
    _loading = false; notifyListeners();
    return _lastResult;
  }

  Future<String> fixWorkflow(String error) async {
    _loading = true; notifyListeners();
    await Future.delayed(const Duration(seconds: 2));
    _lastResult = 'Fix suggestion:\n- Check node configuration\n- Verify credentials are active\n- Ensure data format matches expected input\n- Try increasing timeout settings';
    _history.add({'prompt': error, 'result': _lastResult, 'action': 'fix'});
    _loading = false; notifyListeners();
    return _lastResult;
  }

  Future<String> optimizeWorkflow(String workflowJson) async {
    _loading = true; notifyListeners();
    await Future.delayed(const Duration(seconds: 2));
    _lastResult = 'Optimization suggestions:\n- Combine redundant nodes\n- Use batch processing\n- Add error handling branches\n- Implement caching for API calls\nEstimated improvement: 40% faster execution';
    _history.add({'prompt': 'Optimize', 'result': _lastResult, 'action': 'optimize'});
    _loading = false; notifyListeners();
    return _lastResult;
  }

  Future<String> debugWorkflow(String executionLog) async {
    _loading = true; notifyListeners();
    await Future.delayed(const Duration(seconds: 2));
    _lastResult = 'Debug analysis:\n- Node "Fetch API" failed due to timeout\n- Recommended: Add retry policy (3 attempts)\n- Fallback node suggested\n- Error handling branch needed';
    _history.add({'prompt': executionLog, 'result': _lastResult, 'action': 'debug'});
    _loading = false; notifyListeners();
    return _lastResult;
  }
}
