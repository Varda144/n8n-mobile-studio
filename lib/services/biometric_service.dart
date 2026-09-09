import 'dart:async';
import 'package:flutter/services.dart';

class BiometricService {
  static const MethodChannel _channel = MethodChannel('com.n8n.mobile.studio/biometric');

  static Future<bool> isAvailable() async {
    try {
      final result = await _channel.invokeMethod<bool>('isBiometricAvailable');
      return result ?? false;
    } on PlatformException {
      return false;
    }
  }

  static Future<BiometricResult> authenticate({String title = 'Authenticate', String subtitle = 'Verify your identity'}) async {
    try {
      final result = await _channel.invokeMapMethod<String, dynamic>(
        'authenticate',
        {'title': title, 'subtitle': subtitle},
      );
      if (result == null) return BiometricResult.failed;
      final success = result['success'] as bool? ?? false;
      final error = result['error'] as String?;
      if (success) return BiometricResult.success;
      if (error == 'user_cancel' || error == 'user_cancelled') return BiometricResult.cancelled;
      if (error == 'not_enrolled') return BiometricResult.notEnrolled;
      return BiometricResult.failed;
    } on PlatformException {
      return BiometricResult.failed;
    }
  }
}

enum BiometricResult { success, cancelled, notEnrolled, failed }
