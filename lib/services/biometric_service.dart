import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';

/// Biometric authentication service that uses Android's BiometricPrompt
/// via MethodChannel for actual fingerprint/face authentication.
class BiometricService {
  static const MethodChannel _channel =
      MethodChannel('com.n8n.mobile.studio/biometric');

  /// Check if biometric authentication is available on the device
  static Future<bool> isAvailable() async {
    try {
      final result = await _channel.invokeMethod<bool>('isBiometricAvailable');
      return result ?? false;
    } on PlatformException {
      return false;
    } catch (e) {
      return false;
    }
  }

  /// Check if the device has biometrics enrolled
  static Future<BiometricStatus> checkStatus() async {
    try {
      final available = await isAvailable();
      if (available) {
        return BiometricStatus.available;
      }
      return BiometricStatus.notAvailable;
    } catch (e) {
      return BiometricStatus.error;
    }
  }

  /// Show the biometric prompt and authenticate the user
  static Future<BiometricResult> authenticate({
    String title = 'Authenticate',
    String subtitle = 'Verify your identity',
    String negativeButtonText = 'Use PIN',
  }) async {
    try {
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
        'authenticate',
        {
          'title': title,
          'subtitle': subtitle,
          'negativeButtonText': negativeButtonText,
        },
      );

      if (result == null) {
        return BiometricResult(success: false, error: 'no_result');
      }

      final success = result['success'] as bool? ?? false;
      final error = result['error'] as String?;

      return BiometricResult(
        success: success,
        error: error,
        cancelled: error == 'user_cancel',
      );
    } on PlatformException catch (e) {
      debugPrint('Biometric auth error: ${e.message}');
      return BiometricResult(success: false, error: e.code);
    } catch (e) {
      debugPrint('Biometric auth unexpected error: $e');
      return BiometricResult(success: false, error: 'unexpected');
    }
  }

  /// Check if biometric is enrolled on the device
  static Future<bool> isEnrolled() async {
    final status = await checkStatus();
    return status == BiometricStatus.available;
  }
}

class BiometricResult {
  final bool success;
  final String? error;
  final bool cancelled;

  BiometricResult({
    required this.success,
    this.error,
    this.cancelled = false,
  });
}

enum BiometricStatus {
  available,
  notEnrolled,
  notAvailable,
  error,
}
