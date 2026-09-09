import 'dart:convert';
import 'dart:math';
import 'package:flutter/foundation.dart';

/// Simple credential storage with basic encryption.
class CredentialStore {
  static final CredentialStore _instance = CredentialStore._internal();
  factory CredentialStore() => _instance;
  CredentialStore._internal();

  final Map<String, StoredCredential> _credentials = {};
  bool _initialized = false;
  static const int _key = 0x5A;

  Future<void> initialize() async {
    if (_initialized) return;
    _initialized = true;
  }

  Future<void> store(String name, String type, Map<String, dynamic> data) async {
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

  Future<Map<String, dynamic>?> get(String name) async {
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

  Future<List<Map<String, String>>> getAll() async {
    await initialize();
    return _credentials.values
        .map((c) => {'name': c.name, 'type': c.type})
        .toList();
  }

  Future<void> delete(String name) async {
    await initialize();
    _credentials.remove(name);
  }

  Future<bool> exists(String name) async {
    await initialize();
    return _credentials.containsKey(name);
  }

  Future<Map<String, dynamic>?> getForExecution(String name) async {
    return get(name);
  }

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
