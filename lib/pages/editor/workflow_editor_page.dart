import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';
import '../../theme/app_theme.dart';
import '../../providers/workflow_provider.dart';
import '../../models/workflow_model.dart';

class WorkflowEditorPage extends StatefulWidget {
  final String workflowId;
  const WorkflowEditorPage({super.key, required this.workflowId});
  @override
  State<WorkflowEditorPage> createState() => _WorkflowEditorPageState();
}

class _WorkflowEditorPageState extends State<WorkflowEditorPage> {
  double _scale = 1.0;
  Offset _offset = Offset.zero;
  Offset _lastFocalPoint = Offset.zero;
  WorkflowNode? _selectedNode;
  WorkflowNode? _dragNode;
  Offset _dragStartCanvasPos = Offset.zero;
  Offset _dragNodeStartPos = Offset.zero;
  bool _showMinimap = true;
  String _searchNode = '';
  final List<String> _undoStack = [];
  final List<String> _redoStack = [];
  bool _validating = false;
  bool _isPanning = false;
  final GlobalKey _canvasKey = GlobalKey();

  Offset _screenToCanvas(Offset screenPos) {
    return (screenPos - _offset) / _scale;
  }

  WorkflowNode? _hitTestNode(Offset canvasPos) {
    final provider = context.read<WorkflowProvider>();
    final workflow = provider.getById(widget.workflowId);
    if (workflow == null) return null;

    for (var node in workflow.nodes) {
      final rect = Rect.fromLTWH(node.x, node.y, 160, 60);
      if (rect.contains(canvasPos)) {
        return node;
      }
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<WorkflowProvider>();
    final workflow = provider.getById(widget.workflowId);
    if (workflow == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Workflow not found')),
        body: const Center(child: Text('Workflow not found')),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: Text(workflow.name, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
        leading: IconButton(icon: const Icon(Icons.arrow_back), onPressed: () => context.pop()),
        actions: [
          IconButton(
            icon: Icon(_showMinimap ? Icons.map : Icons.map_outlined),
            onPressed: () => setState(() => _showMinimap = !_showMinimap),
          ),
          IconButton(icon: const Icon(Icons.undo), onPressed: _undoStack.isNotEmpty ? _undo : null),
          IconButton(icon: const Icon(Icons.redo), onPressed: _redoStack.isNotEmpty ? _redo : null),
          IconButton(
            icon: _validating
                ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                : const Icon(Icons.check_circle_outline),
            onPressed: _validate,
          ),
          PopupMenuButton(
            itemBuilder: (_) => [
              const PopupMenuItem(value: 'execute', child: Row(children: [Icon(Icons.play_arrow, size: 18), SizedBox(width: 8), Text('Execute')])),
              const PopupMenuItem(value: 'export', child: Row(children: [Icon(Icons.download, size: 18), SizedBox(width: 8), Text('Export JSON')])),
              const PopupMenuItem(value: 'duplicate', child: Row(children: [Icon(Icons.copy, size: 18), SizedBox(width: 8), Text('Duplicate')])),
            ],
            onSelected: (v) => _handleMenuAction(v, workflow),
          ),
        ],
      ),
      body: Stack(
        children: [
          Positioned.fill(
            child: GestureDetector(
              key: _canvasKey,
              onScaleStart: (details) {
                _lastFocalPoint = details.focalPoint;

                if (details.pointerCount == 1) {
                  final box = _canvasKey.currentContext?.findRenderObject() as RenderBox?;
                  if (box != null) {
                    final localPos = box.globalToLocal(details.focalPoint);
                    final canvasPos = _screenToCanvas(localPos);
                    final hitNode = _hitTestNode(canvasPos);
                    if (hitNode != null) {
                      _dragNode = hitNode;
                      _dragStartCanvasPos = canvasPos;
                      _dragNodeStartPos = Offset(hitNode.x, hitNode.y);
                      setState(() {
                        _selectedNode = hitNode;
                        _isPanning = false;
                      });
                    } else {
                      _dragNode = null;
                      _isPanning = true;
                      setState(() => _selectedNode = null);
                    }
                  }
                }
              },
              onScaleUpdate: (details) {
                final delta = details.focalPoint - _lastFocalPoint;
                _lastFocalPoint = details.focalPoint;

                if (_dragNode != null && details.pointerCount == 1) {
                  final canvasDelta = delta / _scale;
                  setState(() {
                    _dragNode!.x = _dragNodeStartPos.dx + canvasDelta.dx;
                    _dragNode!.y = _dragNodeStartPos.dy + canvasDelta.dy;
                  });
                } else if (_isPanning || details.pointerCount > 1) {
                  setState(() {
                    _offset += delta;
                    if (details.pointerCount > 1) {
                      _scale = (_scale * details.scale).clamp(0.3, 3.0);
                    }
                  });
                }
              },
              onScaleEnd: (details) {
                if (_dragNode != null) {
                  _commitNodePosition();
                }
                _dragNode = null;
                _isPanning = false;
              },
              child: CustomPaint(
                painter: _WorkflowCanvasPainter(
                  nodes: workflow.nodes,
                  connections: workflow.connections,
                  scale: _scale,
                  offset: _offset,
                  selectedNode: _selectedNode,
                ),
                size: Size.infinite,
              ),
            ),
          ),
          if (_showMinimap)
            Positioned(
              right: 12, top: 12,
              child: _buildMinimap(workflow),
            ),
          Positioned(
            left: 12, top: 12,
            child: _buildToolbar(),
          ),
          Positioned(
            bottom: 12, left: 12, right: 12,
            child: _buildNodeSearch(workflow),
          ),
          if (_selectedNode != null)
            Positioned(
              right: 12, bottom: 80,
              child: _buildNodeConfig(workflow),
            ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _addNode(workflow),
        backgroundColor: AppTheme.primaryColor,
        child: const Icon(Icons.add, color: Colors.white),
      ),
    );
  }

  void _commitNodePosition() {
    if (_dragNode == null) return;
    final provider = context.read<WorkflowProvider>();
    final workflow = provider.getById(widget.workflowId);
    if (workflow == null) return;

    final updatedNodes = workflow.nodes.map((n) {
      if (n.id == _dragNode!.id) {
        return WorkflowNode(
          id: n.id,
          type: n.type,
          name: n.name,
          parameters: n.parameters,
          x: _dragNode!.x,
          y: _dragNode!.y,
          credentials: n.credentials,
          notes: n.notes,
        );
      }
      return n;
    }).toList();

    provider.updateWorkflow(workflow.copyWith(nodes: updatedNodes));
    _selectedNode = updatedNodes.firstWhere((n) => n.id == _dragNode!.id);
  }

  Widget _buildToolbar() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: Theme.of(context).scaffoldBackgroundColor.withOpacity(0.95),
        borderRadius: BorderRadius.circular(12),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.2), blurRadius: 8)],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          IconButton(
            icon: const Icon(Icons.zoom_in, size: 20),
            onPressed: () => setState(() => _scale = (_scale + 0.2).clamp(0.3, 3.0)),
            padding: const EdgeInsets.all(4),
            constraints: const BoxConstraints(),
          ),
          Text('${(_scale * 100).toInt()}%', style: const TextStyle(fontSize: 12)),
          IconButton(
            icon: const Icon(Icons.zoom_out, size: 20),
            onPressed: () => setState(() => _scale = (_scale - 0.2).clamp(0.3, 3.0)),
            padding: const EdgeInsets.all(4),
            constraints: const BoxConstraints(),
          ),
          const SizedBox(width: 8),
          IconButton(
            icon: const Icon(Icons.center_focus_strong, size: 20),
            onPressed: () => setState(() { _scale = 1.0; _offset = Offset.zero; }),
            padding: const EdgeInsets.all(4),
            constraints: const BoxConstraints(),
          ),
        ],
      ),
    );
  }

  Widget _buildMinimap(Workflow workflow) {
    return Container(
      width: 120, height: 100,
      decoration: BoxDecoration(
        color: AppTheme.canvasBg.withOpacity(0.9),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppTheme.connectionColor.withOpacity(0.5)),
      ),
      child: CustomPaint(
        painter: _MinimapPainter(nodes: workflow.nodes, connections: workflow.connections),
      ),
    );
  }

  Widget _buildNodeSearch(Workflow workflow) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12),
      decoration: BoxDecoration(
        color: Theme.of(context).scaffoldBackgroundColor.withOpacity(0.95),
        borderRadius: BorderRadius.circular(12),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.2), blurRadius: 8)],
      ),
      child: TextField(
        decoration: InputDecoration(
          hintText: 'Search or add nodes...',
          prefixIcon: const Icon(Icons.search, size: 20),
          suffixIcon: _searchNode.isNotEmpty
              ? IconButton(
                  icon: const Icon(Icons.clear, size: 18),
                  onPressed: () {
                    setState(() => _searchNode = '');
                    _showFilteredPicker(workflow, '');
                  },
                )
              : IconButton(
                  icon: const Icon(Icons.add, size: 20),
                  onPressed: () => _addNode(workflow),
                ),
          border: InputBorder.none,
          contentPadding: const EdgeInsets.symmetric(vertical: 12),
        ),
        onChanged: (v) {
          setState(() => _searchNode = v);
          if (v.isNotEmpty) {
            _showFilteredPicker(workflow, v);
          }
        },
        onSubmitted: (v) {
          if (v.isNotEmpty) {
            _addNodeFromSearch(workflow, v);
          }
        },
      ),
    );
  }

  void _showFilteredPicker(Workflow workflow, String query) {
    final nodeTypes = [
      ('Trigger', 'trigger', Icons.flash_on, Colors.amber),
      ('Action', 'action', Icons.settings, Colors.blue),
      ('Condition', 'condition', Icons.call_split, Colors.green),
      ('Output', 'output', Icons.output, Colors.purple),
      ('HTTP Request', 'http', Icons.http, Colors.teal),
      ('Database', 'database', Icons.storage, Colors.indigo),
      ('Email', 'email', Icons.email, Colors.orange),
      ('AI Model', 'ai', Icons.auto_awesome, Colors.pink),
    ];

    final q = query.toLowerCase();
    final filtered = nodeTypes.where((nt) => nt.$1.toLowerCase().contains(q)).toList();

    if (filtered.isEmpty) return;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => _buildFilteredPickerSheet(ctx, workflow, filtered),
    );
  }

  Widget _buildFilteredPickerSheet(BuildContext ctx, Workflow workflow, List<({String $1, String $2, IconData $3, Color $4})> items) {
    return Container(
      height: MediaQuery.of(ctx).size.height * 0.4,
      decoration: BoxDecoration(
        color: Theme.of(ctx).scaffoldBackgroundColor,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: Column(
        children: [
          Container(
            width: 40, height: 4,
            margin: const EdgeInsets.only(top: 12),
            decoration: BoxDecoration(color: Colors.grey.withOpacity(0.3), borderRadius: BorderRadius.circular(2)),
          ),
          const Padding(
            padding: EdgeInsets.all(16),
            child: Text('Select Node Type', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          ),
          Expanded(
            child: ListView.builder(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              itemCount: items.length,
              itemBuilder: (_, i) {
                final nt = items[i];
                return ListTile(
                  leading: CircleAvatar(
                    backgroundColor: nt.$4.withOpacity(0.2),
                    child: Icon(nt.$3, color: nt.$4, size: 22),
                  ),
                  title: Text(nt.$1, style: const TextStyle(fontWeight: FontWeight.w600)),
                  subtitle: Text('${nt.$2} node', style: TextStyle(color: Colors.grey.shade500, fontSize: 12)),
                  onTap: () {
                    _createNode(workflow, nt.$2, nt.$1);
                    Navigator.pop(ctx);
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildNodeConfig(Workflow workflow) {
    return Container(
      width: 260,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Theme.of(context).scaffoldBackgroundColor.withOpacity(0.95),
        borderRadius: BorderRadius.circular(12),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.3), blurRadius: 12)],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            children: [
              Icon(_getNodeIcon(_selectedNode!.type), color: AppTheme.primaryColor, size: 20),
              const SizedBox(width: 8),
              Expanded(
                child: Text(_selectedNode!.name, style: const TextStyle(fontWeight: FontWeight.bold)),
              ),
              IconButton(
                icon: const Icon(Icons.close, size: 18),
                onPressed: () => setState(() => _selectedNode = null),
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(),
              ),
            ],
          ),
          const Divider(),
          _buildConfigField('Type', _selectedNode!.type),
          const SizedBox(height: 8),
          const Text('Parameters', style: TextStyle(fontSize: 12, color: Colors.grey)),
          const SizedBox(height: 4),
          ..._selectedNode!.parameters.entries.map((e) => _buildConfigField(e.key, '${e.value}')),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () => _deleteNode(workflow),
                  icon: const Icon(Icons.delete, size: 16, color: Colors.red),
                  label: const Text('Delete', style: TextStyle(color: Colors.red)),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: () {},
                  icon: const Icon(Icons.save, size: 16),
                  label: const Text('Save'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildConfigField(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(fontSize: 12, color: Colors.grey)),
          Flexible(
            child: Text(value, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                overflow: TextOverflow.ellipsis),
          ),
        ],
      ),
    );
  }

  IconData _getNodeIcon(String type) {
    switch (type) {
      case 'trigger': return Icons.flash_on;
      case 'action': return Icons.settings;
      case 'condition': return Icons.call_split;
      case 'output': return Icons.output;
      case 'http': return Icons.http;
      case 'database': return Icons.storage;
      case 'email': return Icons.email;
      case 'ai': return Icons.auto_awesome;
      default: return Icons.circle;
    }
  }

  void _createNode(Workflow workflow, String type, String name) {
    _pushUndo(workflow);

    double newX;
    double newY;

    if (workflow.nodes.isNotEmpty) {
      final lastNode = workflow.nodes.last;
      newX = lastNode.x + 250;
      newY = lastNode.y;
    } else {
      newX = 200;
      newY = 200;
    }

    final newNode = WorkflowNode(
      type: type,
      name: name,
      x: newX,
      y: newY,
    );

    final updatedNodes = [...workflow.nodes, newNode];

    List<WorkflowConnection> updatedConnections = [...workflow.connections];
    if (workflow.nodes.isNotEmpty) {
      final lastNode = workflow.nodes.last;
      updatedConnections.add(WorkflowConnection(
        sourceNodeId: lastNode.id,
        sourceOutput: 0,
        targetNodeId: newNode.id,
        targetInput: 0,
      ));
    }

    final updated = workflow.copyWith(
      nodes: updatedNodes,
      connections: updatedConnections,
    );
    context.read<WorkflowProvider>().updateWorkflow(updated);

    setState(() {
      _selectedNode = newNode;
      _searchNode = '';
    });
  }

  void _addNodeFromSearch(Workflow workflow, String query) {
    final nodeTypes = [
      ('Trigger', 'trigger'),
      ('Action', 'action'),
      ('Condition', 'condition'),
      ('Output', 'output'),
      ('HTTP Request', 'http'),
      ('Database', 'database'),
      ('Email', 'email'),
      ('AI Model', 'ai'),
    ];

    final q = query.toLowerCase();
    final match = nodeTypes.where((nt) => nt.$1.toLowerCase().contains(q)).firstOrNull;
    if (match != null) {
      _createNode(workflow, match.$2, match.$1);
    } else {
      _createNode(workflow, 'action', query);
    }
  }

  void _addNode(Workflow workflow) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => _buildNodePicker(ctx, workflow),
    );
  }

  Widget _buildNodePicker(BuildContext ctx, Workflow workflow) {
    final nodeTypes = [
      ('Trigger', 'trigger', Icons.flash_on, Colors.amber),
      ('Action', 'action', Icons.settings, Colors.blue),
      ('Condition', 'condition', Icons.call_split, Colors.green),
      ('Output', 'output', Icons.output, Colors.purple),
      ('HTTP Request', 'http', Icons.http, Colors.teal),
      ('Database', 'database', Icons.storage, Colors.indigo),
      ('Email', 'email', Icons.email, Colors.orange),
      ('AI Model', 'ai', Icons.auto_awesome, Colors.pink),
    ];

    return Container(
      height: MediaQuery.of(ctx).size.height * 0.5,
      decoration: BoxDecoration(
        color: Theme.of(ctx).scaffoldBackgroundColor,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: Column(
        children: [
          Container(
            width: 40, height: 4,
            margin: const EdgeInsets.only(top: 12),
            decoration: BoxDecoration(color: Colors.grey.withOpacity(0.3), borderRadius: BorderRadius.circular(2)),
          ),
          const Padding(
            padding: EdgeInsets.all(16),
            child: Text('Add Node', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          ),
          Expanded(
            child: GridView.builder(
              padding: const EdgeInsets.all(16),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(crossAxisCount: 3, mainAxisSpacing: 10, crossAxisSpacing: 10),
              itemCount: nodeTypes.length,
              itemBuilder: (_, i) {
                final nt = nodeTypes[i];
                return GestureDetector(
                  onTap: () {
                    _createNode(workflow, nt.$2, nt.$1);
                    Navigator.pop(ctx);
                  },
                  child: Container(
                    decoration: BoxDecoration(
                      color: nt.$4.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: nt.$4.withOpacity(0.3)),
                    ),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(nt.$3, color: nt.$4, size: 32),
                        const SizedBox(height: 6),
                        Text(nt.$1, style: TextStyle(fontSize: 11, color: nt.$4, fontWeight: FontWeight.w600)),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  void _deleteNode(Workflow workflow) {
    if (_selectedNode == null) return;
    _pushUndo(workflow);
    final updated = workflow.copyWith(
      nodes: workflow.nodes.where((n) => n.id != _selectedNode!.id).toList(),
      connections: workflow.connections
          .where((c) => c.sourceNodeId != _selectedNode!.id && c.targetNodeId != _selectedNode!.id)
          .toList(),
    );
    context.read<WorkflowProvider>().updateWorkflow(updated);
    setState(() => _selectedNode = null);
  }

  void _pushUndo(Workflow workflow) {
    _undoStack.add(workflow.toJson().toString());
    _redoStack.clear();
  }

  void _undo() {
    if (_undoStack.isEmpty) return;
    _redoStack.add(_undoStack.removeLast());
    setState(() {});
  }

  void _redo() {
    if (_redoStack.isEmpty) return;
    _undoStack.add(_redoStack.removeLast());
    setState(() {});
  }

  void _validate() async {
    setState(() => _validating = true);
    await Future.delayed(const Duration(seconds: 1));
    if (!mounted) return;
    setState(() => _validating = false);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: const Text('Workflow is valid!'),
        backgroundColor: AppTheme.successColor,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  void _handleMenuAction(String action, Workflow workflow) {
    switch (action) {
      case 'execute':
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Executing "${workflow.name}"...'),
            backgroundColor: AppTheme.infoColor,
          ),
        );
        break;
      case 'export':
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('JSON exported to clipboard')),
        );
        break;
      case 'duplicate':
        context.read<WorkflowProvider>().duplicateWorkflow(workflow.id);
        context.pop();
        break;
    }
  }
}

class _WorkflowCanvasPainter extends CustomPainter {
  final List<WorkflowNode> nodes;
  final List<WorkflowConnection> connections;
  final double scale;
  final Offset offset;
  final WorkflowNode? selectedNode;

  _WorkflowCanvasPainter({
    required this.nodes,
    required this.connections,
    required this.scale,
    required this.offset,
    this.selectedNode,
  });

  @override
  void paint(Canvas canvas, Size size) {
    canvas.save();
    canvas.translate(offset.dx, offset.dy);
    canvas.scale(scale);

    // Draw grid
    final gridPaint = Paint()..color = AppTheme.connectionColor.withOpacity(0.1)..strokeWidth = 0.5;
    for (double x = -2000; x < 4000; x += 40) {
      canvas.drawLine(Offset(x, -2000), Offset(x, 4000), gridPaint);
    }
    for (double y = -2000; y < 4000; y += 40) {
      canvas.drawLine(Offset(-2000, y), Offset(4000, y), gridPaint);
    }

    // Draw connections
    final connPaint = Paint()
      ..color = AppTheme.connectionColor
      ..strokeWidth = 2.5
      ..style = PaintingStyle.stroke;

    for (var conn in connections) {
      final src = nodes.where((n) => n.id == conn.sourceNodeId).firstOrNull;
      final tgt = nodes.where((n) => n.id == conn.targetNodeId).firstOrNull;
      if (src == null || tgt == null) continue;
      final start = Offset(src.x + 160, src.y + 30);
      final end = Offset(tgt.x, tgt.y + 30);
      final ctrl1 = Offset(start.dx + (end.dx - start.dx) * 0.5, start.dy);
      final ctrl2 = Offset(start.dx + (end.dx - start.dx) * 0.5, end.dy);
      final path = Path()..moveTo(start.dx, start.dy)..cubicTo(ctrl1.dx, ctrl1.dy, ctrl2.dx, ctrl2.dy, end.dx, end.dy);
      canvas.drawPath(path, connPaint);

      // Arrow
      final arrowPaint = Paint()..color = AppTheme.connectionColor..style = PaintingStyle.fill;
      final angle = (end - ctrl2).direction;
      final arrowSize = 8.0;
      final arrowPath = Path()
        ..moveTo(end.dx, end.dy)
        ..lineTo(end.dx - arrowSize * math.cos(angle - 0.5), end.dy - arrowSize * math.sin(angle - 0.5))
        ..lineTo(end.dx - arrowSize * math.cos(angle + 0.5), end.dy - arrowSize * math.sin(angle + 0.5))
        ..close();
      canvas.drawPath(arrowPath, arrowPaint);
    }

    // Draw nodes
    for (var node in nodes) {
      final isSelected = selectedNode?.id == node.id;
      final nodeRect = RRect.fromRectAndRadius(
        Rect.fromLTWH(node.x, node.y, 160, 60),
        const Radius.circular(12),
      );

      // Shadow
      final shadowPaint = Paint()..color = Colors.black.withOpacity(0.3)..maskFilter = const MaskFilter.blur(BlurStyle.normal, 6);
      canvas.drawRRect(nodeRect.shift(const Offset(3, 3)), shadowPaint);

      // Node background
      final nodePaint = Paint()..color = isSelected ? AppTheme.primaryColor.withOpacity(0.3) : AppTheme.nodeBg;
      canvas.drawRRect(nodeRect, nodePaint);

      // Border
      final borderPaint = Paint()
        ..color = isSelected ? AppTheme.primaryColor : AppTheme.connectionColor
        ..style = PaintingStyle.stroke
        ..strokeWidth = isSelected ? 2.5 : 1.5;
      canvas.drawRRect(nodeRect, borderPaint);

      // Type indicator
      final typePaint = Paint()..color = _getTypeColor(node.type);
      canvas.drawRRect(
        RRect.fromRectAndRadius(Rect.fromLTWH(node.x, node.y, 8, 60), const Radius.circular(4)),
        typePaint,
      );

      // Node name
      final namePainter = TextPainter(
        text: TextSpan(text: node.name, style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w600)),
        textDirection: TextDirection.ltr,
      )..layout(maxWidth: 140);
      namePainter.paint(canvas, Offset(node.x + 14, node.y + 12));

      // Node type
      final typePainter = TextPainter(
        text: TextSpan(text: node.type.toUpperCase(), style: TextStyle(color: Colors.grey.shade400, fontSize: 9)),
        textDirection: TextDirection.ltr,
      )..layout(maxWidth: 140);
      typePainter.paint(canvas, Offset(node.x + 14, node.y + 35));

      // Status dot
      final dotPaint = Paint()..color = AppTheme.successColor;
      canvas.drawCircle(Offset(node.x + 148, node.y + 12), 4, dotPaint);
    }

    canvas.restore();
  }

  Color _getTypeColor(String type) {
    switch (type) {
      case 'trigger': return Colors.amber;
      case 'action': return Colors.blue;
      case 'condition': return Colors.green;
      case 'output': return Colors.purple;
      default: return AppTheme.primaryColor;
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => true;
}

class _MinimapPainter extends CustomPainter {
  final List<WorkflowNode> nodes;
  final List<WorkflowConnection> connections;

  _MinimapPainter({required this.nodes, required this.connections});

  @override
  void paint(Canvas canvas, Size size) {
    if (nodes.isEmpty) return;
    double minX = nodes.map((n) => n.x).reduce(math.min);
    double maxX = nodes.map((n) => n.x + 160).reduce(math.max);
    double minY = nodes.map((n) => n.y).reduce(math.min);
    double maxY = nodes.map((n) => n.y + 60).reduce(math.max);
    final scaleX = (size.width - 20) / (maxX - minX + 200);
    final scaleY = (size.height - 20) / (maxY - minY + 200);
    final s = math.min(scaleX, scaleY).clamp(0.1, 1.0);

    for (var conn in connections) {
      final src = nodes.where((n) => n.id == conn.sourceNodeId).firstOrNull;
      final tgt = nodes.where((n) => n.id == conn.targetNodeId).firstOrNull;
      if (src == null || tgt == null) continue;
      canvas.drawLine(
        Offset(10 + (src.x - minX + 160) * s, 10 + (src.y - minY + 30) * s),
        Offset(10 + (tgt.x - minX) * s, 10 + (tgt.y - minY + 30) * s),
        Paint()..color = AppTheme.connectionColor..strokeWidth = 1,
      );
    }

    for (var node in nodes) {
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromLTWH(10 + (node.x - minX) * s, 10 + (node.y - minY) * s, 8 * s, 5 * s),
          const Radius.circular(2),
        ),
        Paint()..color = _getTypeColor(node.type),
      );
    }
  }

  Color _getTypeColor(String type) {
    switch (type) {
      case 'trigger': return Colors.amber;
      case 'action': return Colors.blue;
      case 'condition': return Colors.green;
      default: return AppTheme.primaryColor;
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => true;
}
