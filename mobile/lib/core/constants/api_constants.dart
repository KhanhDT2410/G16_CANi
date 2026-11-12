import 'package:flutter/foundation.dart' show kDebugMode;

/// API configuration constants.
///
/// In release mode, secrets must be passed via `--dart-define`.
/// In debug mode (development), fallback sandbox values are provided for local testing.
class ApiConstants {
  ApiConstants._();

  static String get defaultBaseUrl {
    return 'http://localhost:8080';
  }

  static final String baseUrl = const String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: '',
  ).isEmpty
      ? defaultBaseUrl
      : const String.fromEnvironment('API_BASE_URL');

  static const String apiPath = '/api';

  static const Duration connectTimeout = Duration(seconds: 15);
  static const Duration receiveTimeout = Duration(seconds: 15);

  static const String supabaseUrl = String.fromEnvironment(
    'SUPABASE_URL',
    defaultValue: kDebugMode ? 'https://hgnilsojrauajmyslhug.supabase.co' : '',
  );

  static const String supabaseAnonKey = String.fromEnvironment(
    'SUPABASE_ANON_KEY',
    defaultValue: kDebugMode
        ? 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imhnbmlsc29qcmF1YWpteXNsaHVnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE4MDU3MDcsImV4cCI6MjEwNzM4MTcwN30.25kiZ2qaQXl6oM3eJOHDCMYKR544OCsHNOwaVvhaGyw'
        : '',
  );

  static const String googleWebClientId = String.fromEnvironment(
    'GOOGLE_WEB_CLIENT_ID',
    defaultValue: kDebugMode
        ? '498475929919-n3a74frl23n8ftq50nv0j1a9f54juj5s.apps.googleusercontent.com'
        : '',
  );
}
