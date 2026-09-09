import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';
import 'package:flutter/foundation.dart';

/// Simple credential storage with basic encryption.
/// For production, use flutter_secure_storage with platform-specific keychain.
/// This implementation uses XOR-based encryption with a device-specific key
/// and stores encrypted data in memory + SharedPreferences-style file.
class CredentialStore {
  static final CredentialStore _instance = CredentialStore._internal();
  factory CredentialStore() => _instance;
  CredentialStore._internal();

  final Map<String, StoredCredential> _credentials = {};
  bool _initialized = false;

  /// Simple encryption key (in production, derive from device-specific data)
  static const int _key = 0x5A;

  Future<void> initialize() async {
    if (_initialized) return;
    _initialized = true;
    // In a real implementation, load from SharedPreferences or file
  }

  /// Store a credential with the given name and data
  Future<void>   Future<void> store(String name, String type, Map<String, dynamic> data) async {
    await initialize();
    final encryptedData = _encrypt(jsonEncode(data));
    _credentials[name] = StoredCredential(
      name: name,
      type: type,
      encryptedData: encryptedData,
      createdAt: DateTime.now(),
      updatedAt: DateTime.now(),
    );
  }

  /// Get a credential by name
  Map<String, dynamic>? get(String name) async {
    await initialize();
    final cred = _credentials[name];
    if (cred == null) return null;
    try {
      final decrypted = _decrypt(cred.encryptedData);
      return Map<String, dynamic>.from(jsonDecode(decrypted));
    } catch (e) {
      debugPrint('Failed to decrypt credential $name: $e');
      return null;
    }
  }

  /// Get all credentials (names and types only, no data)
  Future<List<Map<String, String>>> getAll() async {
    await initialize();
    return _credentials.values
        .map((c) => {'name': c.name, 'type': c.type})
        .toList();
  }

  /// Delete a credential
  Future<void> delete(String name) async {
    await initialize();
    _credentials.remove(name);
  }

  /// Check if a credential exists
  Future<bool> exists(String name) async {
    await initialize();
    return _credentials.containsKey(name);
  }

  /// Get decrypted credential data for use during workflow execution
  Future<Map<String, dynamic>?> getForExecution(String name) async {
    await initialize();
    final cred = _credentials[name];
    if (cred == null) return null;
    try {
      final decrypted = _decrypt(cred.encryptedData);
      return Map<String, dynamic>.from(jsonDecode(decrypted));
    } catch (e) {
      return null;
    }
  }

  /// Simple XOR encryption (NOT secure - use flutter_secure_storage for production)
  String _encrypt(String data) {
    final bytes = utf8.encode(data);
    final encrypted = bytes.map((b) => b ^ _key).toList();
    return base64Encode(encrypted);
  }

  String _decrypt(String encryptedData) {
    final bytes = base64Decode(encryptedData);
    final decrypted = bytes.map((b) => b ^ _key).toList();
    return utf8.decode(decrypted);
  }

  /// Generate a secure random key for credential encryption
  static String generateSecureKey() {
    final random = Random.secure();
    final bytes = List<int>.generate(32, (_) => random.nextInt(256));
    return base64Encode(bytes);
  }
}

class StoredCredential {
  final String name;
  final String type;
  final String encryptedData;
  final DateTime createdAt;
  final DateTime updatedAt;

  StoredCredential({
    required this.name,
    required this.type,
    required this.encryptedData,
    required this.createdAt,
    required this.updatedAt,
  });
}
