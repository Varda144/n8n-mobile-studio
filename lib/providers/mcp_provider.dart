import 'package:flutter/foundation.dart';

class McpTool {
  final String name;
  final String description;
  final String server;
  bool enabled;
  McpTool({required this.name, required this.description, required this.server, this.enabled = true});
}

class McpServer {
  final String id;
  final String name;
  final String url;
  bool connected;
  int toolCount;
  McpServer({required this.id, required this.name, required this.url, this.connected = false, this.toolCount = 0});
}

class McpProvider extends ChangeNotifier {
  final List<McpServer> _servers = [];
  List<McpServer> get servers => List.unmodifiable(_servers);
  final List<McpTool> _tools = [];
  List<McpTool> get tools => List.unmodifiable(_tools);

  McpProvider() {
    _servers.addAll([
      McpServer(id: '1', name: 'File System', url: 'mcp://filesystem', connected: true, toolCount: 5),
      McpServer(id: '2', name: 'Database', url: 'mcp://database', connected: true, toolCount: 8),
      McpServer(id: '3', name: 'API Gateway', url: 'mcp://apigateway', connected: false, toolCount: 12),
      McpServer(id: '4', name: 'Memory Store', url: 'mcp://memory', connected: true, toolCount: 3),
    ]);
    _tools.addAll([
      McpTool(name: 'read_file', description: 'Read file contents', server: 'File System'),
      McpTool(name: 'write_file', description: 'Write file contents', server: 'File System'),
      McpTool(name: 'query_db', description: 'Execute SQL query', server: 'Database'),
      McpTool(name: 'insert_data', description: 'Insert data into table', server: 'Database'),
      McpTool(name: 'http_request', description: 'Make HTTP request', server: 'API Gateway'),
      McpTool(name: 'store_memory', description: 'Store data in memory', server: 'Memory Store'),
    ]);
  }

  void addServer(McpServer s) { _servers.add(s); notifyListeners(); }
  void removeServer(String id) { _servers.removeWhere((s) => s.id == id); notifyListeners(); }
  void toggleConnection(String id) {
    final i = _servers.indexWhere((s) => s.id == id);
    if (i != -1) { _servers[i].connected = !_servers[i].connected; notifyListeners(); }
  }
}
