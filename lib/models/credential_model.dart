import 'package:uuid/uuid.dart';

const _uuid = Uuid();

class Credential {
  final String id;
  String name;
  String type;
  String? description;
  Map<String, dynamic> data;
  bool synced;
  DateTime createdAt;
  DateTime updatedAt;

  Credential({
    String? id,
    required this.name,
    required this.type,
    this.description,
    Map<String, dynamic>? data,
    this.synced = false,
    DateTime? createdAt,
    DateTime? updatedAt,
  })  : id = id ?? _uuid.v4(),
        data = data ?? {},
        createdAt = createdAt ?? DateTime.now(),
        updatedAt = updatedAt ?? DateTime.now();

  Credential copyWith({
    String? name,
    String? type,
    String? description,
    Map<String, dynamic>? data,
    bool? synced,
  }) =>
      Credential(
        id: id,
        name: name ?? this.name,
        type: type ?? this.type,
        description: description ?? this.description,
        data: data ?? this.data,
        synced: synced ?? this.synced,
        createdAt: createdAt,
        updatedAt: DateTime.now(),
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'type': type,
        'description': description,
        'data': data,
        'synced': synced,
        'createdAt': createdAt.toIso8601String(),
        'updatedAt': updatedAt.toIso8601String(),
      };

  factory Credential.fromJson(Map<String, dynamic> json) => Credential(
        id: json['id'],
        name: json['name'],
        type: json['type'],
        description: json['description'],
        data: Map<String, dynamic>.from(json['data'] ?? {}),
        synced: json['synced'] ?? false,
        createdAt: DateTime.parse(json['createdAt']),
        updatedAt: DateTime.parse(json['updatedAt']),
      );
}
