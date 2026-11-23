import 'dart:typed_data';
import 'package:dio/dio.dart';
import '../models/ekyc_models.dart';

/// Ngoại lệ phân biệt lỗi kết nối / kỹ thuật (cho phép retry) với lỗi dữ liệu đầu vào.
class EkycSubmitException implements Exception {
  final String message;
  final bool isTechnicalError;
  final bool isUnauthorized;

  const EkycSubmitException(
    this.message, {
    this.isTechnicalError = false,
    this.isUnauthorized = false,
  });

  @override
  String toString() => message;
}

class EkycRepository {
  final Dio _dio;

  EkycRepository(this._dio);

  DioMediaType _imageMediaType(String fileName) {
    final lower = fileName.toLowerCase();
    if (lower.endsWith('.png')) return DioMediaType('image', 'png');
    if (lower.endsWith('.webp')) return DioMediaType('image', 'webp');
    return DioMediaType('image', 'jpeg');
  }

  Future<EkycConsentPolicy> getConsentPolicy({String? token}) async {
    try {
      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;
      final response = await _dio.get('/v1/ekyc/consent-policy', options: options);
      if (response.data != null && response.data['data'] != null) {
        return EkycConsentPolicy.fromJson(
          Map<String, dynamic>.from(response.data['data'] as Map),
        );
      }
      return EkycConsentPolicy.fallback;
    } catch (_) {
      return EkycConsentPolicy.fallback;
    }
  }

  Future<EkycVerificationItem> submitEkyc({
    required Uint8List frontBytes,
    required String frontFileName,
    required Uint8List backBytes,
    required String backFileName,
    required Uint8List selfieBytes,
    required String selfieFileName,
    bool consentAccepted = false,
    String? consentNoticeVersion,
    String? token,
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
        'frontImage': MultipartFile.fromBytes(
          frontBytes,
          filename: frontFileName,
          contentType: _imageMediaType(frontFileName),
        ),
        'backImage': MultipartFile.fromBytes(
          backBytes,
          filename: backFileName,
          contentType: _imageMediaType(backFileName),
        ),
        'selfieImage': MultipartFile.fromBytes(
          selfieBytes,
          filename: selfieFileName,
          contentType: _imageMediaType(selfieFileName),
        ),
        'consentAccepted': consentAccepted.toString(),
        if (consentNoticeVersion != null && consentNoticeVersion.isNotEmpty)
          'consentNoticeVersion': consentNoticeVersion,
      };
      // Các trường dưới đây chỉ phục vụ đối chiếu chéo phía backend, không ghi đè dữ liệu VNPT eKYC
      if (cccdNumber != null && cccdNumber.isNotEmpty) formDataMap['cccdNumber'] = cccdNumber;
      if (fullName != null && fullName.isNotEmpty) formDataMap['fullName'] = fullName;
      if (dob != null && dob.isNotEmpty) formDataMap['dob'] = dob;
      if (gender != null && gender.isNotEmpty) formDataMap['gender'] = gender;
      if (originAddress != null && originAddress.isNotEmpty) formDataMap['originAddress'] = originAddress;
      if (residenceAddress != null && residenceAddress.isNotEmpty) formDataMap['residenceAddress'] = residenceAddress;
      if (issueDate != null && issueDate.isNotEmpty) formDataMap['issueDate'] = issueDate;
      if (expiryDate != null && expiryDate.isNotEmpty) formDataMap['expiryDate'] = expiryDate;

      final formData = FormData.fromMap(formDataMap);

      final options = Options(
        headers: token != null ? {'Authorization': 'Bearer $token'} : null,
        receiveTimeout: const Duration(seconds: 60),
      );

      final response = await _dio.post(
        '/v1/workers/ekyc/submit',
        data: formData,
        options: options,
      );

      if (response.data != null && response.data['data'] != null) {
        return EkycVerificationItem.fromJson(
          Map<String, dynamic>.from(response.data['data'] as Map),
        );
      }
      throw const EkycSubmitException(
        'Máy chủ không trả về dữ liệu phản hồi eKYC. Vui lòng thử lại.',
        isTechnicalError: true,
      );
    } on EkycSubmitException {
      rethrow;
    } on DioException catch (de) {
      final statusCode = de.response?.statusCode;
      if (statusCode == 401) {
        throw const EkycSubmitException(
          'Phiên đăng nhập đã hết hạn (401). Vui lòng đăng nhập lại để tiếp tục.',
          isUnauthorized: true,
        );
      }
      final isConnectionOrTimeout = de.type == DioExceptionType.connectionTimeout ||
          de.type == DioExceptionType.sendTimeout ||
          de.type == DioExceptionType.receiveTimeout ||
          de.type == DioExceptionType.connectionError;
      if (isConnectionOrTimeout) {
        throw const EkycSubmitException(
          'Không thể kết nối tới máy chủ xác thực (lỗi mạng hoặc quá thời gian chờ). Hồ sơ của bạn chưa bị từ chối — vui lòng thử lại.',
          isTechnicalError: true,
        );
      }
      if (statusCode != null && statusCode >= 500) {
        throw const EkycSubmitException(
          'Hệ thống xác thực đang gặp sự cố kỹ thuật tạm thời. Hồ sơ chưa bị từ chối — vui lòng thử lại sau.',
          isTechnicalError: true,
        );
      }
      final serverMsg = (de.response?.data is Map)
          ? de.response?.data['message']?.toString()
          : null;
      throw EkycSubmitException(
        serverMsg ?? 'Yêu cầu tải ảnh eKYC không hợp lệ. Vui lòng kiểm tra ảnh và thử lại.',
        isTechnicalError: false,
      );
    } catch (_) {
      throw const EkycSubmitException(
        'Đã xảy ra sự cố kết nối tạm thời khi gửi hồ sơ eKYC. Vui lòng thử lại.',
        isTechnicalError: true,
      );
    }
  }

  Future<EkycVerificationItem?> getMyEkycStatus({String? token}) async {
    try {
      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;

      final response = await _dio.get('/v1/workers/ekyc/status', options: options);
      if (response.data != null && response.data['data'] != null) {
        return EkycVerificationItem.fromJson(
          Map<String, dynamic>.from(response.data['data'] as Map),
        );
      }
      return null;
    } catch (_) {
      return null;
    }
  }

  Future<bool> resetEkyc({String? token}) async {
    try {
      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;
      final response = await _dio.post('/v1/workers/ekyc/reset', options: options);
      return response.statusCode == 200;
    } catch (_) {
      return false;
    }
  }
}
