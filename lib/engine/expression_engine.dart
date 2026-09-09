/// Expression engine that resolves n8n-style expressions like {{ $json.field }}
class ExpressionEngine {
  /// Resolve all expressions in a string value
  static String resolve(String template, Map<String, dynamic> context) {
    if (!template.contains('{{')) return template;
    return template.replaceAllMapped(
      RegExp(r'\{\{\s*(.+?)\s*\}\}'),
      (match) {
        final expr = match.group(1)!;
        return _evaluateExpression(expr, context).toString();
      },
    );
  }

  /// Resolve expressions in a map of parameters
  static Map<String, dynamic> resolveParameters(
      Map<String, dynamic> params, Map<String, dynamic> context) {
    final resolved = <String, dynamic>{};
    params.forEach((key, value) {
      if (value is String) {
        resolved[key] = resolve(value, context);
      } else if (value is Map) {
        resolved[key] = resolveParameters(
            Map<String, dynamic>.from(value), context);
      } else if (value is List) {
        resolved[key] = value.map((v) {
          if (v is String) return resolve(v, context);
          if (v is Map) {
            return resolveParameters(Map<String, dynamic>.from(v), context);
          }
          return v;
        }).toList();
      } else {
        resolved[key] = value;
      }
    });
    return resolved;
  }

  static dynamic _evaluateExpression(
      String expr, Map<String, dynamic> context) {
    // $json.field.subfield
    if (expr.startsWith('\$json')) {
      return _resolveJsonPath(expr.replaceFirst('\$json', ''), context);
    }

    // $input.all() - all input items
    if (expr.startsWith('\$input.all()')) {
      final items = context['_inputItems'] as List? ?? [];
      return items;
    }

    // $input.first() - first input item
    if (expr.startsWith('\$input.first()')) {
      final items = context['_inputItems'] as List? ?? [];
      return items.isNotEmpty ? items[0] : null;
    }

    // $input.last() - last input item
    if (expr.startsWith('\$input.last()')) {
      final items = context['_inputItems'] as List? ?? [];
      return items.isNotEmpty ? items.last : null;
    }

    // $input.item - current item
    if (expr == '\$input.item' || expr == '\$json') {
      return context['_currentItem'];
    }

    // $node["NodeName"].json
    final nodeMatch = RegExp(r'\$node\["(.+?)"\]\.json(.*)').firstMatch(expr);
    if (nodeMatch != null) {
      final nodeName = nodeMatch.group(1)!;
      final path = nodeMatch.group(2) ?? '';
      final nodeOutput = context['_nodeResults_$nodeName'];
      if (nodeOutput != null) {
        if (path.isEmpty) return nodeOutput;
        return _resolveJsonPath(path, nodeOutput);
      }
      return null;
    }

    // $parameter["paramName"]
    final paramMatch =
        RegExp(r'\$parameter\["(.+?)"\]').firstMatch(expr);
    if (paramMatch != null) {
      final paramName = paramMatch.group(1)!;
      return context['_parameters']?[paramName];
    }

    // $env.VAR
    if (expr.startsWith('\$env.')) {
      final varName = expr.replaceFirst('\$env.', '');
      return context['_env']?[varName];
    }

    // $now
    if (expr == '\$now') {
      return DateTime.now().toIso8601String();
    }

    // $timestamp
    if (expr == '\$timestamp') {
      return DateTime.now().millisecondsSinceEpoch;
    }

    // Plain number
    if (RegExp(r'^\d+$').hasMatch(expr)) {
      return int.tryParse(expr) ?? expr;
    }

    // Plain float
    if (RegExp(r'^\d+\.\d+$').hasMatch(expr)) {
      return double.tryParse(expr) ?? expr;
    }

    // Boolean
    if (expr == 'true') return true;
    if (expr == 'false') return false;
    if (expr == 'null') return null;

    // String literal
    if ((expr.startsWith('"') && expr.endsWith('"')) ||
        (expr.startsWith("'") && expr.endsWith("'"))) {
      return expr.substring(1, expr.length - 1);
    }

    // Fallback: return as-is
    return expr;
  }

  static dynamic _resolveJsonPath(String path, dynamic data) {
    if (path.isEmpty || path == '.') return data;
    final parts = path.split('.').where((p) => p.isNotEmpty).toList();
    dynamic current = data;
    for (final part in parts) {
      if (current is Map) {
        current = current[part];
      } else if (current is List && RegExp(r'^\d+$').hasMatch(part)) {
        current = current[int.parse(part)];
      } else {
        return null;
      }
    }
    return current;
  }

  /// Evaluate a condition expression
  static bool evaluateCondition(String condition, Map<String, dynamic> context) {
    final resolved = resolve(condition, context);
    final lower = resolved.toLowerCase();
    if (lower == 'true' || lower == '1') return true;
    if (lower == 'false' || lower == '0' || resolved.isEmpty) return false;
    return resolved.isNotEmpty;
  }
}
