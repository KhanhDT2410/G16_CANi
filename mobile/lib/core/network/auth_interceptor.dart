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
    // 1. Kiểm tra session Supabase, nếu đã hết hạn thì tự động làm mới
    try {
      final session = Supabase.instance.client.auth.currentSession;
      if (session != null) {
        if (session.isExpired) {
          final refreshRes = await Supabase.instance.client.auth.refreshSession();
          if (refreshRes.session != null) {
            inMemoryToken = refreshRes.session!.accessToken;
            await _storage.write(key: 'cani_token', value: inMemoryToken);
          }
        } else if (inMemoryToken == null) {
          inMemoryToken = session.accessToken;
        }
      }
    } catch (_) {}

    String? token = inMemoryToken;

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

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) async {
    // Tự động làm mới token khi nhận mã 401 Unauthorized và thử lại request 1 lần
    if (err.response?.statusCode == 401 && err.requestOptions.extra['retry_count'] == null) {
      err.requestOptions.extra['retry_count'] = 1;
      try {
        String? newToken;

        // Cách 1: Thử refresh qua Supabase GoTrue
        try {
          final refreshRes = await Supabase.instance.client.auth.refreshSession();
          if (refreshRes.session != null) {
            newToken = refreshRes.session!.accessToken;
          }
        } catch (_) {}

        // Cách 2: Thử refresh qua Backend CANi API (/api/auth/refresh)
        if (newToken == null) {
          final refreshToken = await _storage.read(key: 'cani_refresh_token');
          if (refreshToken != null && refreshToken.isNotEmpty) {
            final refreshDio = Dio(BaseOptions(
              baseUrl: 'http://localhost:8080/api',
              connectTimeout: const Duration(seconds: 10),
            ));
            final res = await refreshDio.post('/auth/refresh', data: {
              'refreshToken': refreshToken,
            });
            if (res.statusCode == 200 && res.data?['data']?['token'] != null) {
              newToken = res.data['data']['token'] as String;
              final newRefresh = res.data['data']['refreshToken'] as String?;
              if (newRefresh != null) {
                await _storage.write(key: 'cani_refresh_token', value: newRefresh);
              }
            }
          }
        }

        if (newToken != null && newToken.isNotEmpty) {
          inMemoryToken = newToken;
          await _storage.write(key: 'cani_token', value: newToken);

          // Cập nhật token mới và gửi lại request ban đầu
          err.requestOptions.headers['Authorization'] = 'Bearer $newToken';
          final retryDio = Dio();
          final response = await retryDio.fetch(err.requestOptions);
          return handler.resolve(response);
        }
      } catch (_) {
        inMemoryToken = null;
      }
    }

    handler.next(err);
  }
}
