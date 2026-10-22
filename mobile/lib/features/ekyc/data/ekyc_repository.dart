import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:cani/core/network/auth_interceptor.dart';
import '../models/ekyc_models.dart';

class EkycRepository {
  final Dio _dio;
  static const String _baseUrl = 'http://localhost:8080/api/v1';

  EkycRepository({Dio? dio})
      : _dio = dio ??
            (Dio(BaseOptions(
              baseUrl: _baseUrl,
              connectTimeout: const Duration(seconds: 25),
              receiveTimeout: const Duration(seconds: 90), // AI Multimodal cần thời gian phân tích
            ))..interceptors.add(AuthInterceptor()));

  Future<EkycVerificationItem> submitEkyc({
    required Uint8List frontBytes,
    required String frontFileName,
    required Uint8List backBytes,
    required String backFileName,
    required Uint8List selfieBytes,
    required String selfieFileName,
    String? token,
    String? userId,
  }) async {
    try {
      final formDataMap = <String, dynamic>{
        'frontImage': MultipartFile.fromBytes(frontBytes, filename: frontFileName),
        'backImage': MultipartFile.fromBytes(backBytes, filename: backFileName),
        'selfieImage': MultipartFile.fromBytes(selfieBytes, filename: selfieFileName),
      };
      if (userId != null && userId.isNotEmpty) {
        formDataMap['userId'] = userId;
      }
      final formData = FormData.fromMap(formDataMap);

      final queryParams = <String, dynamic>{};
      if (userId != null && userId.isNotEmpty) {
        queryParams['userId'] = userId;
      }

      final options = Options(
        headers: token != null ? {'Authorization': 'Bearer $token'} : null,
      );

      final response = await _dio.post(
        '/workers/ekyc/submit',
        data: formData,
        queryParameters: queryParams,
        options: options,
      );

      if (response.data != null && response.data['data'] != null) {
        return EkycVerificationItem.fromJson(response.data['data']);
      }
      throw Exception('Không nhận được dữ liệu phản hồi từ máy chủ AI eKYC');
    } on DioException catch (de) {
      final serverMsg = (de.response?.data is Map)
          ? de.response?.data['message']?.toString()
          : null;
      throw Exception(serverMsg ?? 'Lỗi kết nối máy chủ AI (${de.message})');
    } catch (e) {
      throw Exception('Lỗi xử lý eKYC: $e');
    }
  }

  Future<EkycVerificationItem?> getMyEkycStatus({String? token}) async {
    try {
      final options = Options(
        headers: token != null ? {'Authorization': 'Bearer $token'} : null,
      );

      final response = await _dio.get('/workers/ekyc/status', options: options);
      if (response.data != null && response.data['data'] != null) {
        return EkycVerificationItem.fromJson(response.data['data']);
      }
      return null;
    } catch (e) {
      return null;
    }
  }
}
