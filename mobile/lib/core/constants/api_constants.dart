import 'dart:io' show Platform;
import 'package:flutter/foundation.dart' show kIsWeb;

/// API configuration constants.
///
/// For Windows Desktop / Web: Uses http://localhost:8080
/// For Android emulator: Uses http://10.0.2.2:8080 to access host machine localhost
class ApiConstants {
  ApiConstants._();

  static String get defaultBaseUrl {
    // Sử dụng http://localhost:8080 (kết hợp lệnh 'adb reverse tcp:8080 tcp:8080' giúp máy thật Android cắm cáp USB gọi thẳng vào Backend trên PC mà không lo khác mạng Wi-Fi hay tường lửa)
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
