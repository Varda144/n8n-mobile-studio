import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../theme/app_theme.dart';
import '../../providers/ai_provider.dart';

class AiPage extends StatefulWidget {
  const AiPage({super.key});
  @override
  State<AiPage> createState() => _AiPageState();
}

class _AiPageState extends State<AiPage> {
  final _promptCtrl = TextEditingController();
  String _selectedAction = 'generate';
  String _result = '';

  final _actions = [
    ('Generate Workflow', 'generate', Icons.auto_awesome, AppTheme.primaryColor),
    ('Explain Workflow', 'explain', Icons.info_outline, Colors.blue),
    ('Fix Workflow', 'fix', Icons.build, Colors.orange),
    ('Optimize Workflow', 'optimize', Icons.speed, Colors.green),
    ('Debug Workflow', 'debug', Icons.bug_report, Colors.red),
  ];

  @override
  Widget build(BuildContext context) {
    final ai = context.watch<AiProvider>();
    return Scaffold(
      appBar: AppBar(
        title: const Text('AI Assistant', style: TextStyle(fontWeight: FontWeight.bold)),
        actions: [
          if (ai.history.isNotEmpty)
            IconButton(
              icon: const Icon(Icons.history),
              onPressed: () => _showHistory(ai),
            ),
        ],
      ),
      body: Column(
        children: [
          Container(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: _actions.map((a) {
                final selected = _selectedAction == a.$2;
                return Expanded(
                  child: GestureDetector(
                    onTap: () => setState(() => _selectedAction = a.$2),
                    child: Container(
                      margin: const EdgeInsets.symmetric(horizontal: 3),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      decoration: BoxDecoration(
                        color: selected ? a.$4.withOpacity(0.2) : AppTheme.surfaceDark,
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(
                          color: selected ? a.$4 : Colors.transparent,
                          width: 2,
                        ),
                      ),
                      child: Column(
                        children: [
                          Icon(a.$3, color: selected ? a.$4 : Colors.grey, size: 24),
                          const SizedBox(height: 4),
                          Text(a.$1.split(' ').first, style: TextStyle(
                            fontSize: 10,
                            color: selected ? a.$4 : Colors.grey,
                            fontWeight: FontWeight.w600,
                          )),
                        ],
                      ),
                    ),
                  ),
                );
              }).toList(),
            ),
          ),
          Expanded(
            child: _result.isNotEmpty
                ? SingleChildScrollView(
                    padding: const EdgeInsets.all(16),
                    child: Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: AppTheme.surfaceDark,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Icon(_getActionIcon(), color: _getActionColor(), size: 20),
                              const SizedBox(width: 8),
                              Text(_getActionLabel(), style: TextStyle(fontWeight: FontWeight.bold, color: _getActionColor())),
                            ],
                          ),
                          const SizedBox(height: 12),
                          Text(_result, style: const TextStyle(height: 1.5)),
                        ],
                      ),
                    ),
                  )
                : Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(_getActionIcon(), size: 64, color: _getActionColor().withOpacity(0.3)),
                        const SizedBox(height: 16),
                        Text(_getActionLabel(), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                        const SizedBox(height: 8),
                        Text(_getActionHint(), style: const TextStyle(color: Colors.grey)),
                      ],
                    ),
                  ),
          ),
          if (ai.loading)
            const Padding(
              padding: EdgeInsets.all(16),
              child: LinearProgressIndicator(color: AppTheme.primaryColor),
            ),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Theme.of(context).scaffoldBackgroundColor,
              border: Border(top: BorderSide(color: Colors.grey.withOpacity(0.2))),
            ),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _promptCtrl,
                    decoration: InputDecoration(
                      hintText: _getHintText(),
                      prefixIcon: Icon(_getActionIcon(), size: 20),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                IconButton(
                  onPressed: ai.loading ? null : () => _execute(ai),
                  icon: Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(color: AppTheme.primaryColor, borderRadius: BorderRadius.circular(10)),
                    child: const Icon(Icons.send, color: Colors.white, size: 20),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  void _execute(AiProvider ai) async {
    final prompt = _promptCtrl.text;
    if (prompt.isEmpty) return;
    String result;
    switch (_selectedAction) {
      case 'generate': result = await ai.generateWorkflow(prompt); break;
      case 'explain': result = await ai.explainWorkflow(prompt); break;
      case 'fix': result = await ai.fixWorkflow(prompt); break;
      case 'optimize': result = await ai.optimizeWorkflow(prompt); break;
      case 'debug': result = await ai.debugWorkflow(prompt); break;
      default: result = '';
    }
    setState(() => _result = result);
    _promptCtrl.clear();
  }

  void _showHistory(AiProvider ai) {
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (ctx) => Container(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('AI History', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
            const SizedBox(height: 16),
            Expanded(
              child: ListView.builder(
                itemCount: ai.history.length,
                itemBuilder: (_, i) {
                  final h = ai.history[i];
                  return ListTile(
                    leading: Icon(_getIconForAction(h['action']!), size: 20),
                    title: Text(h['prompt']!, style: const TextStyle(fontWeight: FontWeight.w600)),
                    subtitle: Text(h['result']!, maxLines: 2, overflow: TextOverflow.ellipsis),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  IconData _getIconForAction(String action) {
    switch (action) {
      case 'generate': return Icons.auto_awesome;
      case 'explain': return Icons.info_outline;
      case 'fix': return Icons.build;
      case 'optimize': return Icons.speed;
      case 'debug': return Icons.bug_report;
      default: return Icons.help;
    }
  }

  String _getActionLabel() {
    return _actions.firstWhere((a) => a.$2 == _selectedAction).$1;
  }

  IconData _getActionIcon() {
    return _actions.firstWhere((a) => a.$2 == _selectedAction).$3;
  }

  Color _getActionColor() {
    return _actions.firstWhere((a) => a.$2 == _selectedAction).$4;
  }

  String _getHintText() {
    switch (_selectedAction) {
      case 'generate': return 'Describe your workflow...';
      case 'explain': return 'Paste workflow JSON...';
      case 'fix': return 'Describe the error...';
      case 'optimize': return 'Paste workflow to optimize...';
      case 'debug': return 'Paste execution log...';
      default: return 'Enter prompt...';
    }
  }

  String _getActionHint() {
    switch (_selectedAction) {
      case 'generate': return 'Describe what you want to automate';
      case 'explain': return 'Paste a workflow to understand it';
      case 'fix': return 'Describe the error you\'re seeing';
      case 'optimize': return 'Get optimization suggestions';
      case 'debug': return 'Debug execution failures';
      default: return '';
    }
  }
}
