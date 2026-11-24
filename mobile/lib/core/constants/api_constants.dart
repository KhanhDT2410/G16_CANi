import 'package:flutter/foundation.dart';

/// API configuration constants.
///
/// In release mode, secrets must be passed via `--dart-define`.
/// In debug mode (development), fallback sandbox values are provided for local testing.
class ApiConstants {
  ApiConstants._();

  static String get defaultBaseUrl {
    // Trên Web: localhost trỏ tới máy chủ đang chạy web
    if (kIsWeb) {
      return 'http://localhost:8080';
    }
    // Ưu tiên loopback 127.0.0.1 (qua adb reverse khi cắm USB); Dio fallback sẽ thử IP LAN/10.0.2.2 nếu không cắm cáp
    return 'http://127.0.0.1:8080';
  }

  static final String baseUrl = const String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: '',
  ).isEmpty
      ? defaultBaseUrl
      : const String.fromEnvironment('API_BASE_URL');

  static const String apiPath = '/api';

  static const Duration connectTimeout = Duration(seconds: 6);
  static const Duration receiveTimeout = Duration(seconds: 10);

  static const String supabaseUrl = String.fromEnvironment(
    'SUPABASE_URL',
    defaultValue: 'https://hgnilsojrauajmyslhug.supabase.co',
  );

  static const String supabaseAnonKey = String.fromEnvironment(
    'SUPABASE_ANON_KEY',
    defaultValue:
        'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imhnbmlsc29qcmF1YWpteXNsaHVnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE4MDU3MDcsImV4cCI6MjEwNzM4MTcwN30.25kiZ2qaQXl6oM3eJOHDCMYKR544OCsHNOwaVvhaGyw',
  );

  static const String googleWebClientId = String.fromEnvironment(
    'GOOGLE_WEB_CLIENT_ID',
    defaultValue:
        '498475929919-n3a74frl23n8ftq50nv0j1a9f54juj5s.apps.googleusercontent.com',
  );
}
