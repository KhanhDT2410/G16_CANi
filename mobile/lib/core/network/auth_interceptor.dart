import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

/// Interceptor that attaches the Bearer access token to outgoing HTTP requests.
///
/// Priority:
/// 1. In-memory cache token (fastest)
/// 2. Supabase session token (for Google OAuth)
/// 3. Secure storage token (for Email/Password login)
class AuthInterceptor extends Interceptor {
  AuthInterceptor([this._storage = const FlutterSecureStorage()]);

  final FlutterSecureStorage _storage;
  static String? inMemoryToken;

  @override
  void onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    String? token = inMemoryToken;

    if (token == null || token.isEmpty) {
      try {
        token = Supabase.instance.client.auth.currentSession?.accessToken;
      } catch (_) {}
    }

    if (token == null || token.isEmpty) {
      try {
        token = await _storage.read(key: 'cani_token');
        inMemoryToken = token;
      } catch (_) {}
    }

    if (token != null && token.isNotEmpty) {
      options.headers['Authorization'] = 'Bearer $token';
    }

    handler.next(options);
  }
}
