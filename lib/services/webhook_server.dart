import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';

/// A local HTTP server that listens for webhook requests and triggers workflows.
/// Runs on localhost:8080 by default.
class LocalWebhookServer {
  static final LocalWebhookServer _instance = LocalWebhookServer._internal();
  factory LocalWebhookServer() => _instance;
  LocalWebhookServer._internal();

  HttpServer? _server;
  bool get isRunning => _server != null;
  int _port = 8080;
  int get port => _port;

  // Map of path -> workflow ID to trigger
  final Map<String, String> _registeredWebhooks = {};
  // Callback when a webhook is received
  void Function(String path, Map<String, dynamic> data, Map<String, String> headers)? onWebhookReceived;

  /// Start the local HTTP server
  Future<void> start({int port = 8080}) async {
    if (_server != null) return;
    _port = port;
    try {
      _server = await HttpServer.bind(InternetAddress.loopbackIPv4, port);
      debugPrint('Webhook server started on http://localhost:$port');
      _server!.listen(_handleRequest);
    } catch (e) {
      debugPrint('Failed to start webhook server: $e');
      _server = null;
    }
  }

  /// Stop the server
  Future<void> stop() async {
    await _server?.close();
    _server = null;
    _registeredWebhooks.clear();
  }

  /// Register a webhook path to trigger a specific workflow
  void registerWebhook(String path, String workflowId) {
    _registeredWebhooks[path] = workflowId;
  }

  /// Unregister a webhook path
  void unregisterWebhook(String path) {
    _registeredWebhooks.remove(path);
  }

  /// Get all registered webhook paths
  List<Map<String, String>> getRegisteredWebhooks() {
    return _registeredWebhooks.entries
        .map((e) => {'path': e.key, 'workflowId': e.value})
        .toList();
  }

  /// Get the full URL for a webhook path
  String getWebhookUrl(String path) {
    return 'http://localhost:$port$path';
  }

  Future<void> _handleRequest(HttpRequest request) async {
    // Handle CORS preflight
    if (request.method == 'OPTIONS') {
      _sendResponse(request, 200, {'success': true});
      return;
    }

    final path = request.uri.path;
    final method = request.method.toUpperCase();

    // Parse body
    Map<String, dynamic> body = {};
    try {
      final bodyString = await utf8.decoder.bind(request).join();
      if (bodyString.isNotEmpty) {
        body = jsonDecode(bodyString);
      }
    } catch (e) {
      // Body parsing failed - might be form data or empty
    }

    // Collect query parameters
    final queryParams = <String, String>{};
    request.uri.queryParameters.forEach((k, v) => queryParams[k] = v);

    // Collect headers
    final headers = <String, String>{};
    request.headers.forEach((name, values) {
      headers[name] = values.join(', ');
    });

    // Build the payload
    final payload = {
      'method': method,
      'path': path,
      'body': body,
      'query': queryParams,
      'headers': headers,
      'timestamp': DateTime.now().toIso8601String(),
    };

    // Check if this path is registered
    final workflowId = _registeredWebhooks[path];
    if (workflowId != null) {
      onWebhookReceived?.call(path, payload, headers);
      _sendResponse(request, 200, {
        'success': true,
        'message': 'Webhook received',
        'workflowId': workflowId,
      });
    } else if (path == '/api/health') {
      _sendResponse(request, 200, {'status': 'ok', 'running': true});
    } else {
      // Unknown path - still return 200 for webhook discovery
      _sendResponse(request, 200, {
        'success': false,
        'message': 'No webhook registered at this path',
        'registeredPaths': _registeredWebhooks.keys.toList(),
      });
    }
  }

  Future<void> _sendResponse(HttpRequest request, int statusCode, Map<String, dynamic> data) async {
    request.response.statusCode = statusCode;
    request.response.headers.contentType = ContentType.json;
    request.response.headers.add('Access-Control-Allow-Origin', '*');
    request.response.headers.add('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    request.response.headers.add('Access-Control-Allow-Headers', 'Content-Type, Authorization');
    request.response.write(jsonEncode(data));
    await request.response.close();
  }
}
