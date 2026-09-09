class N8nInstance {
  final String id;
  String name;
  String url;
  String? apiKey;
  bool isActive;
  DateTime lastSynced;
  int workflowCount;

  N8nInstance({
    required this.id,
    required this.name,
    required this.url,
    this.apiKey,
    this.isActive = false,
    DateTime? lastSynced,
    this.workflowCount = 0,
  }) : lastSynced = lastSynced ?? DateTime.now();

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'url': url,
        'apiKey': apiKey,
        'isActive': isActive,
        'lastSynced': lastSynced.toIso8601String(),
        'workflowCount': workflowCount,
      };

  factory N8nInstance.fromJson(Map<String, dynamic> json) => N8nInstance(
        id: json['id'],
        name: json['name'],
        url: json['url'],
        apiKey: json['apiKey'],
        isActive: json['isActive'] ?? false,
        lastSynced: DateTime.parse(json['lastSynced']),
        workflowCount: json['workflowCount'] ?? 0,
      );
}

class AppNotification {
  final String id;
  final String title;
  final String message;
  final DateTime timestamp;
  bool read;
  final String type;

  AppNotification({
    required this.id,
    required this.title,
    required this.message,
    DateTime? timestamp,
    this.read = false,
    this.type = 'info',
  }) : timestamp = timestamp ?? DateTime.now();
}

class SearchableItem {
  final String id;
  final String title;
  final String subtitle;
  final String type;
  final String route;

  SearchableItem({
    required this.id,
    required this.title,
    required this.subtitle,
    required this.type,
    required this.route,
  });
}
