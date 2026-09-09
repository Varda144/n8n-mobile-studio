import 'package:uuid/uuid.dart';
import 'workflow_model.dart';

const _uuid = Uuid();

class WorkflowTemplate {
  final String id;
  final String name;
  final String description;
  final String category;
  final String author;
  final int usesCount;
  final List<String> nodes;
  final Workflow workflow;
  final String? imageUrl;

  WorkflowTemplate({
    String? id,
    required this.name,
    this.description = '',
    this.category = 'General',
    this.author = 'Community',
    this.usesCount = 0,
    List<String>? nodes,
    required this.workflow,
    this.imageUrl,
  })  : id = id ?? _uuid.v4(),
        nodes = nodes ?? [];

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'description': description,
        'category': category,
        'author': author,
        'usesCount': usesCount,
        'nodes': nodes,
        'workflow': workflow.toJson(),
      };

  factory WorkflowTemplate.fromJson(Map<String, dynamic> json) =>
      WorkflowTemplate(
        id: json['id'],
        name: json['name'],
        description: json['description'] ?? '',
        category: json['category'] ?? 'General',
        author: json['author'] ?? 'Community',
        usesCount: json['usesCount'] ?? 0,
        nodes: List<String>.from(json['nodes'] ?? []),
        workflow: Workflow.fromJson(json['workflow']),
      );
}
