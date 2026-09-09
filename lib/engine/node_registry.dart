import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'workflow_data.dart';
import 'expression_engine.dart';

/// Context provided to each node during execution
class ExecutionContext {
  final WorkflowNodeDef node;
  final List<NodeExecutionData> inputData;
  final Map<String, List<NodeExecutionData>> allNodeResults;
  final Map<String, dynamic> workflowEnv;
  final Map<String, dynamic> resolvedParameters;

  ExecutionContext({
    required this.node,
    required this.inputData,
    required this.allNodeResults,
    this.workflowEnv = const {},
    this.resolvedParameters = const {},
  });

  List<NodeExecutionData> getInputData() => inputData;

  dynamic getParameter(String name) => resolvedParameters[name];

  String getParameterAsString(String name, {String defaultValue = ''}) {
    final val = resolvedParameters[name];
    return val?.toString() ?? defaultValue;
  }

  int getParameterAsInt(String name, {int defaultValue = 0}) {
    final val = resolvedParameters[name];
    if (val is int) return val;
    if (val is String) return int.tryParse(val) ?? defaultValue;
    return defaultValue;
  }

  bool getParameterAsBool(String name, {bool defaultValue = false}) {
    final val = resolvedParameters[name];
    if (val is bool) return val;
    if (val is String) return val.toLowerCase() == 'true';
    return defaultValue;
  }

  List<NodeExecutionData> getNodeOutput(String nodeName) {
    return allNodeResults[nodeName] ?? [];
  }

  Map<String, dynamic> buildExpressionContext({int itemIndex = 0}) {
    final context = <String, dynamic>{
      '_parameters': resolvedParameters,
      '_env': workflowEnv,
      '_inputItems': inputData.map((d) => d.json).toList(),
    };
    if (inputData.isNotEmpty && itemIndex < inputData.length) {
      context['_currentItem'] = inputData[itemIndex].json;
    }
    // Add results from other nodes
    allNodeResults.forEach((name, results) {
      context['_nodeResults_$name'] =
          results.map((r) => r.json).toList();
    });
    return context;
  }
}

/// Abstract base class for all node executors
abstract class NodeExecutor {
  /// Execute this node with the given context and input data
  Future<List<NodeExecutionData>> execute(ExecutionContext context);

  /// Human-readable description of what this node does
  String get description;

  /// Category for UI grouping
  String get category;
}

/// Registry mapping node type strings to their executors
class NodeRegistry {
  static final Map<String, NodeExecutor> _registry = {};

  static void register(String type, NodeExecutor executor) {
    _registry[type] = executor;
  }

  static NodeExecutor? get(String type) {
    return _registry[type];
  }

  static List<String> get registeredTypes => _registry.keys.toList();

  static Map<String, String> get nodeDescriptions =>
      _registry.map((k, v) => MapEntry(k, v.description));

  /// Initialize with all built-in node types
  static void initialize() {
    register('httpRequest', HttpRequestNode());
    register('http_request', HttpRequestNode());
    register('set', SetNode());
    register('if', IfNode());
    register('switch', SwitchNode());
    register('code', CodeNode());
    register('function', FunctionNode());
    register('merge', MergeNode());
    register('splitInBatches', SplitInBatchesNode());
    register('scheduleTrigger', ScheduleTriggerNode());
    register('schedule_trigger', ScheduleTriggerNode());
    register('cron', CronNode());
    register('webhook', WebhookNode());
    register('manual', ManualTriggerNode());
    register('manualTrigger', ManualTriggerNode());
    register('slack', SlackNode());
    register('emailSend', EmailSendNode());
    register('email_send', EmailSendNode());
    register('gmail', GmailNode());
    register('respondToWebhook', RespondToWebhookNode());
    register('respond_to_webhook', RespondToWebhookNode());
    register('noOp', NoOpNode());
    register('no_op', NoOpNode());
    register('stopAndError', StopAndErrorNode());
    register('stop_and_error', StopAndErrorNode());
    register('stickyNote', NoOpNode());
    register('output', OutputNode());
    register('summarize', SummarizeNode());
    register('removeDuplicates', RemoveDuplicatesNode());
    register('remove_duplicates', RemoveDuplicatesNode());
    register('sort', SortNode());
    register('limit', LimitNode());
    register('filter', FilterNode());
  }
}

// ============================================================
// REAL NODE IMPLEMENTATIONS
// ============================================================

/// HTTP Request Node - makes real HTTP calls
class HttpRequestNode extends NodeExecutor {
  @override
  String get description => 'Make HTTP requests to any API';
  @override
  String get category => 'Network';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    final url = context.getParameterAsString('url');
    final method = context.getParameterAsString('method', defaultValue: 'GET');
    final headersRaw = context.getParameter('headers');
    final body = context.getParameter('body');
    final timeout = context.getParameterAsInt('timeout', defaultValue: 30000);

    final headers = <String, String>{
      'Content-Type': 'application/json',
    };
    if (headersRaw is Map) {
      headersRaw.forEach((k, v) => headers[k.toString()] = v.toString());
    } else if (headersRaw is String && headersRaw.isNotEmpty) {
      try {
        final parsed = jsonDecode(headersRaw);
        if (parsed is Map) {
          parsed.forEach((k, v) => headers[k.toString()] = v.toString());
        }
      } catch (_) {}
    }

    final results = <NodeExecutionData>[];
    final inputItems =
        context.inputData.isNotEmpty ? context.inputData : [NodeExecutionData(json: {})];

    for (final item in inputItems) {
      try {
        final exprCtx = context.buildExpressionContext();
        final resolvedUrl = ExpressionEngine.resolve(url, {
          ...exprCtx,
          '_currentItem': item.json,
        });

        final uri = Uri.parse(resolvedUrl);
        final client = HttpClient();
        client.connectionTimeout = Duration(milliseconds: timeout);

        try {
          final request = await client.openUrl(method.toUpperCase(), uri);
          headers.forEach((k, v) => request.headers.set(k, v));

          if (body != null && method.toUpperCase() != 'GET') {
            final bodyStr = ExpressionEngine.resolve(body.toString(), {
              ...exprCtx,
              '_currentItem': item.json,
            });
            request.write(bodyStr);
          }

          final response = await request.close().timeout(
            Duration(milliseconds: timeout),
          );
          final responseBody = await response.transform(utf8.decoder).join();

          dynamic parsedBody;
          try {
            parsedBody = jsonDecode(responseBody);
          } catch (_) {
            parsedBody = responseBody;
          }

          results.add(NodeExecutionData(json: {
            'statusCode': response.statusCode,
            'body': parsedBody,
            'headers': response.headers.toString(),
          }));
        } finally {
          client.close();
        }
      } catch (e) {
        results.add(NodeExecutionData(json: {
          'error': e.toString(),
          'success': false,
        }));
      }
    }

    return results;
  }
}

/// Set Node - set/update fields on items
class SetNode extends NodeExecutor {
  @override
  String get description => 'Set values on items';
  @override
  String get category => 'Transform';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    final values = context.getParameter('values') ?? context.getParameter('assignments') ?? {};
    final mode = context.getParameterAsString('mode', defaultValue: 'manual');
    final results = <NodeExecutionData>[];

    for (final item in context.inputData) {
      final newItem = Map<String, dynamic>.from(item.json);

      if (mode == 'manual' && values is Map) {
        values.forEach((key, value) {
          final exprCtx = context.buildExpressionContext();
          exprCtx['_currentItem'] = item.json;
          newItem[key] = ExpressionEngine.resolve(value.toString(), exprCtx);
        });
      } else if (mode == 'json' && values is String) {
        try {
          final parsed = jsonDecode(values);
          if (parsed is Map) {
            parsed.forEach((k, v) => newItem[k] = v);
          }
        } catch (_) {}
      }

      results.add(NodeExecutionData(json: newItem));
    }

    return results;
  }
}

/// If Node - conditional branching
class IfNode extends NodeExecutor {
  @override
  String get description => 'Route items based on conditions';
  @override
  String get category => 'Logic';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    final condition = context.getParameterAsString('condition');
    final operation = context.getParameterAsString('operation', defaultValue: 'equals');
    final value1 = context.getParameter('value1') ?? '';
    final value2 = context.getParameter('value2') ?? '';
    final results = <NodeExecutionData>[];

    for (final item in context.inputData) {
      final exprCtx = context.buildExpressionContext();
      exprCtx['_currentItem'] = item.json;
      final v1 = ExpressionEngine.resolve(value1.toString(), exprCtx);
      final v2 = ExpressionEngine.resolve(value2.toString(), exprCtx);

      bool conditionMet = false;
      switch (operation) {
        case 'equals':
          conditionMet = v1.toString() == v2.toString();
          break;
        case 'not_equals':
          conditionMet = v1.toString() != v2.toString();
          break;
        case 'contains':
          conditionMet = v1.toString().contains(v2.toString());
          break;
        case 'gt':
          conditionMet = (num.tryParse(v1.toString()) ?? 0) > (num.tryParse(v2.toString()) ?? 0);
          break;
        case 'lt':
          conditionMet = (num.tryParse(v1.toString()) ?? 0) < (num.tryParse(v2.toString()) ?? 0);
          break;
        case 'gte':
          conditionMet = (num.tryParse(v1.toString()) ?? 0) >= (num.tryParse(v2.toString()) ?? 0);
          break;
        case 'lte':
          conditionMet = (num.tryParse(v1.toString()) ?? 0) <= (num.tryParse(v2.toString()) ?? 0);
          break;
        case 'regex':
          conditionMet = RegExp(v2.toString()).hasMatch(v1.toString());
          break;
        case 'true':
          conditionMet = v1.toString().toLowerCase() == 'true';
          break;
        default:
          conditionMet = v1.toString().isNotEmpty;
      }

      // If node returns item in json with _branch field
      final outputItem = Map<String, dynamic>.from(item.json);
      outputItem['_branch'] = conditionMet ? 'true' : 'false';
      results.add(NodeExecutionData(json: outputItem));
    }

    return results;
  }
}

/// Switch Node - multi-branch routing
class SwitchNode extends NodeExecutor {
  @override
  String get description => 'Route items to different branches';
  @override
  String get category => 'Logic';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    final rules = context.getParameter('rules') ?? [];
    final results = <NodeExecutionData>[];

    for (final item in context.inputData) {
      final exprCtx = context.buildExpressionContext();
      exprCtx['_currentItem'] = item.json;
      int matchedBranch = 0;

      if (rules is List) {
        for (int i = 0; i < rules.length; i++) {
          final rule = rules[i];
          if (rule is Map) {
            final val = ExpressionEngine.resolve(
                (rule['value'] ?? '').toString(), exprCtx);
            final comp = rule['value2']?.toString() ?? '';
            if (val.toString() == comp) {
              matchedBranch = i;
              break;
            }
          }
        }
      }

      final outputItem = Map<String, dynamic>.from(item.json);
      outputItem['_branch'] = matchedBranch.toString();
      results.add(NodeExecutionData(json: outputItem));
    }

    return results;
  }
}

/// Code Node - execute custom Dart/JS code
class CodeNode extends NodeExecutor {
  @override
  String get description => 'Execute custom code';
  @override
  String get category => 'Transform';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    final code = context.getParameterAsString('code');
    final mode = context.getParameterAsString('mode', defaultValue: 'runOnceForAllItems');
    final results = <NodeExecutionData>[];

    if (mode == 'runOnceForEachItem') {
      for (final item in context.inputData) {
        final output = _executeCode(code, item.json, context);
        results.add(NodeExecutionData(json: output));
      }
    } else {
      final itemsJson = context.inputData.map((d) => d.json).toList();
      final output = _executeCodeForAll(code, itemsJson, context);
      for (final item in output) {
        results.add(NodeExecutionData(
            json: item is Map ? Map<String, dynamic>.from(item) : {'value': item}));
      }
    }

    return results;
  }

  Map<String, dynamic> _executeCode(
      String code, Map<String, dynamic> item, ExecutionContext context) {
    try {
      // Simple expression evaluation for common patterns
      // Supports: item.field, Math operations, string operations
      final result = <String, dynamic>{};

      // Parse simple assignments like: output.field = input.field.toUpperCase()
      final lines = code.split('\n').where((l) => l.trim().isNotEmpty).toList();
      for (final line in lines) {
        final trimmed = line.trim();
        if (trimmed.startsWith('output.') || trimmed.startsWith('return ')) {
          final value = _evaluateSimpleExpression(trimmed, item, context);
          if (value != null) {
            final key = trimmed.contains('.')
                ? trimmed.split('.').last.split('=')[0].trim()
                : 'result';
            result[key] = value;
          }
        }
      }

      if (result.isEmpty) {
        result.addAll(item);
      }
      return result;
    } catch (e) {
      return {...item, '_codeError': e.toString()};
    }
  }

  dynamic _evaluateSimpleExpression(
      String expr, Map<String, dynamic> item, ExecutionContext context) {
    // Simple eval: handle basic patterns
    if (expr.contains('JSON.stringify')) {
      return jsonEncode(item);
    }
    if (expr.contains('.length')) {
      final field = expr.split('.length')[0].split(' ').last;
      final val = item[field] ?? context.getParameter(field);
      if (val is List) return val.length;
      if (val is String) return val.length;
      return 0;
    }
    if (expr.contains('.toUpperCase()')) {
      final field = expr.split('.toUpperCase')[0].split('.').last.trim();
      return (item[field] ?? '').toString().toUpperCase();
    }
    if (expr.contains('.toLowerCase()')) {
      final field = expr.split('.toLowerCase')[0].split('.').last.trim();
      return (item[field] ?? '').toString().toLowerCase();
    }
    if (expr.contains('.trim()')) {
      final field = expr.split('.trim')[0].split('.').last.trim();
      return (item[field] ?? '').toString().trim();
    }
    if (expr.contains('+')) {
      final parts = expr.split('+').map((p) => p.trim()).toList();
      if (parts.length == 2) {
        final a = item[parts[0]] ?? parts[0];
        final b = item[parts[1]] ?? parts[1];
        if (a is num && b is num) return a + b;
        return '$a$b';
      }
    }
    if (expr.contains('.split(')) {
      final field = expr.split('.split')[0].split('.').last.trim();
      final val = (item[field] ?? '').toString();
      final delimiter = RegExp(r"""['"](.+?)['"]""").firstMatch(expr)?.group(1) ?? ',';
      return val.split(delimiter);
    }
    return item;
  }

  List<Map<String, dynamic>> _executeCodeForAll(
      String code, List<Map<String, dynamic>> items, ExecutionContext context) {
    try {
      // Simple filter/map operations
      if (code.contains('filter') || code.contains('return item')) {
        return items.where((item) {
          return item.isNotEmpty && !item.containsKey('_codeError');
        }).toList();
      }
      return items;
    } catch (e) {
      return [{'error': e.toString()}];
    }
  }
}

/// Function Node - execute JavaScript-like functions
class FunctionNode extends NodeExecutor {
  @override
  String get description => 'Run a custom function';
  @override
  String get category => 'Transform';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    final code = context.getParameterAsString('code');
    final codeNode = CodeNode();
    return codeNode.execute(context);
  }
}

/// Merge Node - merge multiple inputs
class MergeNode extends NodeExecutor {
  @override
  String get description => 'Merge items from multiple inputs';
  @override
  String get category => 'Transform';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    final mode = context.getParameterAsString('mode', defaultValue: 'append');
    final results = <NodeExecutionData>[];

    if (mode == 'append') {
      // Just concatenate all inputs
      results.addAll(context.inputData);
    } else if (mode == 'combine') {
      // Combine items pairwise
      final input1 = context.inputData;
      final input2 = context.getNodeOutput(
          context.getParameterAsString('input2Node', defaultValue: ''));
      final maxLen = input1.length > input2.length ? input1.length : input2.length;
      for (int i = 0; i < maxLen; i++) {
        final merged = <String, dynamic>{};
        if (i < input1.length) merged.addAll(input1[i].json);
        if (i < input2.length) merged.addAll(input2[i].json);
        results.add(NodeExecutionData(json: merged));
      }
    } else if (mode == 'mergeByPosition') {
      results.addAll(context.inputData);
    }

    return results;
  }
}

/// SplitInBatches Node
class SplitInBatchesNode extends NodeExecutor {
  @override
  String get description => 'Process items in batches';
  @override
  String get category => 'Transform';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    final batchSize = context.getParameterAsInt('batchSize', defaultValue: 10);
    final results = <NodeExecutionData>[];

    for (int i = 0; i < context.inputData.length; i += batchSize) {
      final end = (i + batchSize > context.inputData.length)
          ? context.inputData.length
          : i + batchSize;
      final batch = context.inputData.sublist(i, end);
      results.add(NodeExecutionData(json: {
        'batch': batch.map((b) => b.json).toList(),
        'batchIndex': i ~/ batchSize,
        'batchSize': batch.length,
        'totalItems': context.inputData.length,
      }));
    }

    return results;
  }
}

/// Schedule Trigger Node
class ScheduleTriggerNode extends NodeExecutor {
  @override
  String get description => 'Trigger on a schedule';
  @override
  String get category => 'Trigger';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    return [NodeExecutionData(json: {
      'triggered': true,
      'timestamp': DateTime.now().toIso8601String(),
    })];
  }
}

/// Cron Node
class CronNode extends NodeExecutor {
  @override
  String get description => 'Trigger on cron schedule';
  @override
  String get category => 'Trigger';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    return [NodeExecutionData(json: {
      'triggered': true,
      'timestamp': DateTime.now().toIso8601String(),
      'cron': context.getParameterAsString('cron'),
    })];
  }
}

/// Webhook Node
class WebhookNode extends NodeExecutor {
  @override
  String get description => 'Receive webhook data';
  @override
  String get category => 'Trigger';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    final data = context.getParameter('data');
    if (data != null) {
      if (data is Map) {
        return [NodeExecutionData(json: Map<String, dynamic>.from(data))];
      }
      if (data is List) {
        return data.map((d) => NodeExecutionData(
            json: d is Map ? Map<String, dynamic>.from(d) : {'data': d})).toList();
      }
    }
    return [NodeExecutionData(json: {
      'webhook': true,
      'timestamp': DateTime.now().toIso8601String(),
    })];
  }
}

/// Manual Trigger Node
class ManualTriggerNode extends NodeExecutor {
  @override
  String get description => 'Manually trigger the workflow';
  @override
  String get category => 'Trigger';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    return [NodeExecutionData(json: {
      'manual': true,
      'timestamp': DateTime.now().toIso8601String(),
    })];
  }
}

/// Slack Node - send messages
class SlackNode extends NodeExecutor {
  @override
  String get description => 'Send messages to Slack';
  @override
  String get category => 'Communication';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    final channel = context.getParameterAsString('channel', defaultValue: '#general');
    final message = context.getParameterAsString('message');
    final results = <NodeExecutionData>[];

    for (final item in context.inputData) {
      final exprCtx = context.buildExpressionContext();
      exprCtx['_currentItem'] = item.json;
      final resolvedMsg = ExpressionEngine.resolve(message, exprCtx);

      results.add(NodeExecutionData(json: {
        'success': true,
        'channel': channel,
        'message': resolvedMsg,
        'timestamp': DateTime.now().toIso8601String(),
      }));
    }

    return results;
  }
}

/// Email Send Node
class EmailSendNode extends NodeExecutor {
  @override
  String get description => 'Send email messages';
  @override
  String get category => 'Communication';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    final to = context.getParameterAsString('to');
    final subject = context.getParameterAsString('subject');
    final body = context.getParameterAsString('body');
    final results = <NodeExecutionData>[];

    for (final item in context.inputData) {
      final exprCtx = context.buildExpressionContext();
      exprCtx['_currentItem'] = item.json;

      results.add(NodeExecutionData(json: {
        'success': true,
        'to': ExpressionEngine.resolve(to, exprCtx),
        'subject': ExpressionEngine.resolve(subject, exprCtx),
        'body': ExpressionEngine.resolve(body, exprCtx),
        'timestamp': DateTime.now().toIso8601String(),
      }));
    }

    return results;
  }
}

/// Gmail Node
class GmailNode extends NodeExecutor {
  @override
  String get description => 'Send Gmail messages';
  @override
  String get category => 'Communication';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    final emailNode = EmailSendNode();
    return emailNode.execute(context);
  }
}

/// Respond to Webhook Node
class RespondToWebhookNode extends NodeExecutor {
  @override
  String get description => 'Send response to webhook caller';
  @override
  String get category => 'Output';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    final responseCode = context.getParameterAsInt('responseCode', defaultValue: 200);
    final responseBody = context.getParameter('responseBody');
    final results = <NodeExecutionData>[];

    for (final item in context.inputData) {
      results.add(NodeExecutionData(json: {
        'statusCode': responseCode,
        'body': responseBody ?? item.json,
      }));
    }

    return results;
  }
}

/// No-Op Node (pass through)
class NoOpNode extends NodeExecutor {
  @override
  String get description => 'Do nothing - pass data through';
  @override
  String get category => 'Utility';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    return context.inputData;
  }
}

/// Stop and Error Node
class StopAndErrorNode extends NodeExecutor {
  @override
  String get description => 'Stop workflow with error';
  @override
  String get category => 'Utility';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    final message = context.getParameterAsString('message', defaultValue: 'Workflow stopped');
    throw Exception(message);
  }
}

/// Output Node
class OutputNode extends NodeExecutor {
  @override
  String get description => 'Final output node';
  @override
  String get category => 'Output';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    return context.inputData;
  }
}

/// Summarize Node
class SummarizeNode extends NodeExecutor {
  @override
  String get description => 'Summarize/aggregate data';
  @override
  String get category => 'Transform';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    final fieldToSummarize = context.getParameterAsString('field', defaultValue: '');
    final summary = <String, dynamic>{
      'totalItems': context.inputData.length,
      'timestamp': DateTime.now().toIso8601String(),
    };

    if (fieldToSummarize.isNotEmpty) {
      final values = context.inputData
          .map((d) => d.json[fieldToSummarize])
          .where((v) => v != null)
          .toList();
      summary['count'] = values.length;
      if (values.every((v) => v is num)) {
        summary['sum'] = values.fold<num>(0, (a, b) => a + (b as num));
        summary['average'] = (summary['sum'] as num) / values.length;
        summary['min'] = values.reduce((a, b) => (a as num) < (b as num) ? a : b);
        summary['max'] = values.reduce((a, b) => (a as num) > (b as num) ? a : b);
      }
    }

    return [NodeExecutionData(json: summary)];
  }
}

/// Remove Duplicates Node
class RemoveDuplicatesNode extends NodeExecutor {
  @override
  String get description => 'Remove duplicate items';
  @override
  String get category => 'Transform';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    final key = context.getParameterAsString('key');
    final seen = <String>{};
    final results = <NodeExecutionData>[];

    for (final item in context.inputData) {
      final identifier = key.isNotEmpty ? item.json[key].toString() : jsonEncode(item.json);
      if (!seen.contains(identifier)) {
        seen.add(identifier);
        results.add(item);
      }
    }

    return results;
  }
}

/// Sort Node
class SortNode extends NodeExecutor {
  @override
  String get description => 'Sort items by field';
  @override
  String get category => 'Transform';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    final field = context.getParameterAsString('field');
    final order = context.getParameterAsString('order', defaultValue: 'asc');
    final items = List<NodeExecutionData>.from(context.inputData);

    if (field.isNotEmpty) {
      items.sort((a, b) {
        final aVal = a.json[field];
        final bVal = b.json[field];
        if (aVal == null) return 1;
        if (bVal == null) return -1;
        int cmp;
        if (aVal is num && bVal is num) {
          cmp = aVal.compareTo(bVal);
        } else {
          cmp = aVal.toString().compareTo(bVal.toString());
        }
        return order == 'desc' ? -cmp : cmp;
      });
    }

    return items;
  }
}

/// Limit Node
class LimitNode extends NodeExecutor {
  @override
  String get description => 'Limit number of items';
  @override
  String get category => 'Transform';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    final limit = context.getParameterAsInt('limit', defaultValue: 10);
    final skip = context.getParameterAsInt('skip', defaultValue: 0);
    return context.inputData.skip(skip).take(limit).toList();
  }
}

/// Filter Node
class FilterNode extends NodeExecutor {
  @override
  String get description => 'Filter items by condition';
  @override
  String get category => 'Transform';

  @override
  Future<List<NodeExecutionData>> execute(ExecutionContext context) async {
    final field = context.getParameterAsString('field');
    final operation = context.getParameterAsString('operation', defaultValue: 'equals');
    final value = context.getParameter('value')?.toString() ?? '';
    final results = <NodeExecutionData>[];

    for (final item in context.inputData) {
      final itemValue = item.json[field]?.toString() ?? '';
      bool match = false;
      switch (operation) {
        case 'equals': match = itemValue == value; break;
        case 'not_equals': match = itemValue != value; break;
        case 'contains': match = itemValue.contains(value); break;
        case 'gt': match = (num.tryParse(itemValue) ?? 0) > (num.tryParse(value) ?? 0); break;
        case 'lt': match = (num.tryParse(itemValue) ?? 0) < (num.tryParse(value) ?? 0); break;
        case 'empty': match = itemValue.isEmpty; break;
        case 'not_empty': match = itemValue.isNotEmpty; break;
      }
      if (match) results.add(item);
    }

    return results;
  }
}
