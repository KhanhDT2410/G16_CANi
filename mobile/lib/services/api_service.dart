import 'package:dio/dio.dart';

/// API Service configuration for CANi mobile app.
///
/// This service provides a pre-configured Dio client for making HTTP requests
/// to the CANi backend API.
///
/// For Android emulator: Use http://10.0.2.2:8080 to access host machine localhost
/// For physical device: Use the host machine's LAN IP address (e.g., http://192.168.1.x:8080)
///
/// The base URL can be configured through environment variables or by modifying
/// the API_BASE_URL constant below.
class ApiService {
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080',
  );

  static final Dio _dio = Dio(
    BaseOptions(
      baseUrl: '$apiBaseUrl/api',
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 10),
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
    ),
  )..interceptors.add(
      InterceptorsWrapper(
        onError: (error, handler) {
          // Note: Use debugPrint or proper logging in production
          handler.next(error);
        },
      ),
    );

  static Dio get dio => _dio;

  /// Check backend health status
  static Future<Response> checkHealth() async {
    return await _dio.get('/health');
  }
}
