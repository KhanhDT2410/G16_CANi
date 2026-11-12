import 'dart:typed_data';
import 'package:dio/dio.dart';
import '../models/ekyc_models.dart';

class EkycRepository {
  final Dio _dio;

  EkycRepository(this._dio);

  Future<EkycVerificationItem> submitEkyc({
    required Uint8List frontBytes,
    required String frontFileName,
    required Uint8List backBytes,
    required String backFileName,
    required Uint8List selfieBytes,
    required String selfieFileName,
    String? token,
    String? userId,
    String? cccdNumber,
    String? fullName,
    String? dob,
    String? gender,
    String? originAddress,
    String? residenceAddress,
    String? issueDate,
    String? expiryDate,
  }) async {
    try {
      final formDataMap = <String, dynamic>{
        'frontImage': MultipartFile.fromBytes(frontBytes, filename: frontFileName),
        'backImage': MultipartFile.fromBytes(backBytes, filename: backFileName),
        'selfieImage': MultipartFile.fromBytes(selfieBytes, filename: selfieFileName),
      };
      if (userId != null && userId.isNotEmpty) formDataMap['userId'] = userId;
      if (cccdNumber != null && cccdNumber.isNotEmpty) formDataMap['cccdNumber'] = cccdNumber;
      if (fullName != null && fullName.isNotEmpty) formDataMap['fullName'] = fullName;
      if (dob != null && dob.isNotEmpty) formDataMap['dob'] = dob;
      if (gender != null && gender.isNotEmpty) formDataMap['gender'] = gender;
      if (originAddress != null && originAddress.isNotEmpty) formDataMap['originAddress'] = originAddress;
      if (residenceAddress != null && residenceAddress.isNotEmpty) formDataMap['residenceAddress'] = residenceAddress;
      if (issueDate != null && issueDate.isNotEmpty) formDataMap['issueDate'] = issueDate;
      if (expiryDate != null && expiryDate.isNotEmpty) formDataMap['expiryDate'] = expiryDate;

      final formData = FormData.fromMap(formDataMap);

      final queryParams = <String, dynamic>{};
      if (userId != null && userId.isNotEmpty) {
        queryParams['userId'] = userId;
      }

      final options = Options(
        headers: token != null ? {'Authorization': 'Bearer $token'} : null,
        receiveTimeout: const Duration(seconds: 90), // AI Multimodal cần thời gian phân tích ảnh
      );

      final response = await _dio.post(
        '/v1/workers/ekyc/submit',
        data: formData,
        queryParameters: queryParams,
        options: options,
      );

      if (response.data != null && response.data['data'] != null) {
        return EkycVerificationItem.fromJson(response.data['data']);
      }
      throw Exception('Không nhận được dữ liệu phản hồi từ máy chủ eKYC');
    } on DioException catch (de) {
      if (de.response?.statusCode == 401) {
        throw Exception('Phiên đăng nhập đã hết hạn (401). Vui lòng đăng nhập lại để tiếp tục.');
      }
      final serverMsg = (de.response?.data is Map)
          ? de.response?.data['message']?.toString()
          : null;
      throw Exception(serverMsg ?? 'Lỗi kết nối máy chủ eKYC (${de.message})');
    } catch (e) {
      throw Exception('Lỗi xử lý eKYC: $e');
    }
  }

  Future<EkycVerificationItem?> getMyEkycStatus({String? token}) async {
    try {
      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;

      final response = await _dio.get('/v1/workers/ekyc/status', options: options);
      if (response.data != null && response.data['data'] != null) {
        return EkycVerificationItem.fromJson(response.data['data']);
      }
      return null;
    } catch (e) {
      return null;
    }
  }
}
