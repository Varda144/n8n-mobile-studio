import 'dart:async';
import 'package:flutter/foundation.dart';

/// Cron expression parser and scheduler for workflow triggers
class CronScheduler {
  static final CronScheduler _instance = CronScheduler._internal();
  factory CronScheduler() => _instance;
  CronScheduler._internal();

  final Map<String, Timer> _timers = {};
  final Map<String, String> _scheduledWorkflows = {}; // cronExpr -> workflowId
  void Function(String workflowId)? onTrigger;

  /// Parse a cron expression into components
  /// Format: "min hour day month weekday"
  static Map<String, List<int>?> parseCron(String cronExpr) {
    final parts = cronExpr.trim().split(RegExp(r'\s+'));
    if (parts.length < 5) return {};

    return {
      'minute': _parseCronPart(parts[0], 0, 59),
      'hour': _parseCronPart(parts[1], 0, 23),
      'day': _parseCronPart(parts[2], 1, 31),
      'month': _parseCronPart(parts[3], 1, 12),
      'weekday': _parseCronPart(parts[4], 0, 6),
    };
  }

  static List<int>? _parseCronPart(String part, int minVal, int maxVal) {
    if (part == '*') return null; // Any value

    final values = <int>[];
    for (final segment in part.split(',')) {
      if (segment.contains('/')) {
        final rangeParts = segment.split('/');
        final step = int.tryParse(rangeParts[1]) ?? 1;
        if (rangeParts[0] == '*') {
          for (int i = minVal; i <= maxVal; i += step) {
            values.add(i);
          }
        } else {
          final start = int.tryParse(rangeParts[0]) ?? minVal;
          for (int i = start; i <= maxVal; i += step) {
            values.add(i);
          }
        }
      } else if (segment.contains('-')) {
        final rangeParts = segment.split('-');
        final start = int.tryParse(rangeParts[0]) ?? minVal;
        final end = int.tryParse(rangeParts[1]) ?? maxVal;
        for (int i = start; i <= end; i++) {
          values.add(i);
        }
      } else {
        final val = int.tryParse(segment);
        if (val != null && val >= minVal && val <= maxVal) {
          values.add(val);
        }
      }
    }
    return values;
  }

  /// Schedule a workflow to run on a cron expression
  void schedule(String cronExpr, String workflowId) {
    // Cancel existing timer for this expression
    _timers[cronExpr]?.cancel();

    _scheduledWorkflows[cronExpr] = workflowId;

    // Check every minute if the cron matches
    _timers[cronExpr] = Timer.periodic(const Duration(minutes: 1), (_) {
      if (_matchesCron(cronExpr)) {
        debugPrint('Cron triggered: $cronExpr -> $workflowId');
        onTrigger?.call(workflowId);
      }
    });

    debugPrint('Scheduled workflow $workflowId with cron: $cronExpr');
  }

  /// Unschedule a workflow
  void unschedule(String cronExpr) {
    _timers[cronExpr]?.cancel();
    _timers.remove(cronExpr);
    _scheduledWorkflows.remove(cronExpr);
  }

  /// Unschedule all workflows
  void unscheduleAll() {
    for (final timer in _timers.values) {
      timer.cancel();
    }
    _timers.clear();
    _scheduledWorkflows.clear();
  }

  /// Get all scheduled workflows
  List<Map<String, String>> getScheduledWorkflows() {
    return _scheduledWorkflows.entries
        .map((e) => {'cron': e.key, 'workflowId': e.value})
        .toList();
  }

  /// Check if current time matches a cron expression
  bool _matchesCron(String cronExpr) {
    final now = DateTime.now();
    final parts = parseCron(cronExpr);
    if (parts.isEmpty) return false;

    // Check minute (if specified, current minute must be in the list)
    if (parts['minute'] != null && !parts['minute']!.contains(now.minute)) {
      return false;
    }
    if (parts['hour'] != null && !parts['hour']!.contains(now.hour)) {
      return false;
    }
    if (parts['day'] != null && !parts['day']!.contains(now.day)) {
      return false;
    }
    if (parts['month'] != null && !parts['month']!.contains(now.month)) {
      return false;
    }
    if (parts['weekday'] != null) {
      // Dart DateTime weekday: 1=Monday, 7=Sunday. Cron: 0=Sunday, 1=Monday...
      final cronWeekday = now.weekday == 7 ? 0 : now.weekday;
      if (!parts['weekday']!.contains(cronWeekday)) {
        return false;
      }
    }

    return true;
  }

  /// Get next run time for a cron expression
  DateTime? getNextRun(String cronExpr) {
    final now = DateTime.now();
    for (int i = 1; i <= 1440; i++) { // Check next 24 hours
      final future = now.add(Duration(minutes: i));
      if (_matchesCronAt(cronExpr, future)) {
        return future;
      }
    }
    return null;
  }

  bool _matchesCronAt(String cronExpr, DateTime time) {
    final parts = parseCron(cronExpr);
    if (parts.isEmpty) return false;

    if (parts['minute'] != null && !parts['minute']!.contains(time.minute)) return false;
    if (parts['hour'] != null && !parts['hour']!.contains(time.hour)) return false;
    if (parts['day'] != null && !parts['day']!.contains(time.day)) return false;
    if (parts['month'] != null && !parts['month']!.contains(time.month)) return false;
    if (parts['weekday'] != null) {
      final cronWeekday = time.weekday == 7 ? 0 : time.weekday;
      if (!parts['weekday']!.contains(cronWeekday)) return false;
    }

    return true;
  }
}
