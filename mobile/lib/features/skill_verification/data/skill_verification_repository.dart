import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/core/network/dio_client.dart';
import 'package:cani/core/utils/app_error_formatter.dart';
import 'package:cani/features/skill_verification/models/skill_models.dart';

final skillVerificationRepositoryProvider = Provider<SkillVerificationRepository>((ref) {
  final dio = ref.watch(dioProvider);
  return SkillVerificationRepository(dio);
});

class SkillVerificationRepository {
  final Dio _dio;

  SkillVerificationRepository(this._dio);

  Future<List<SkillModel>> getTaxonomy() async {
    try {
      final response = await _dio.get('/v1/workers/skills/taxonomy');
      final data = response.data['data'] as List<dynamic>? ?? [];
      return data.map((e) => SkillModel.fromJson(e as Map<String, dynamic>)).toList();
    } catch (e) {
      throw Exception(AppErrorFormatter.format(e, fallback: 'Không thể tải danh mục kỹ năng.'));
    }
  }

  Future<List<WorkerSkillPointModel>> getSkillPoints() async {
    try {
      final response = await _dio.get('/v1/workers/skills/points');
      final data = response.data['data'] as List<dynamic>? ?? [];
      return data.map((e) => WorkerSkillPointModel.fromJson(e as Map<String, dynamic>)).toList();
    } catch (e) {
      throw Exception(AppErrorFormatter.format(e, fallback: 'Không thể tải bảng điểm kỹ năng.'));
    }
  }

  Future<List<SkillVerificationResult>> getHistory() async {
    try {
      final response = await _dio.get('/v1/workers/skills/history');
      final data = response.data['data'] as List<dynamic>? ?? [];
      return data.map((e) => SkillVerificationResult.fromJson(e as Map<String, dynamic>)).toList();
    } catch (e) {
      throw Exception(AppErrorFormatter.format(e, fallback: 'Không thể tải lịch sử thẩm định.'));
    }
  }

  DioMediaType _mediaTypeForFile(String fileName) {
    final lower = fileName.toLowerCase();
    if (lower.endsWith('.pdf')) return DioMediaType('application', 'pdf');
    if (lower.endsWith('.png')) return DioMediaType('image', 'png');
    if (lower.endsWith('.webp')) return DioMediaType('image', 'webp');
    return DioMediaType('image', 'jpeg');
  }

  Future<SkillVerificationResult> submitVerification({
    required Uint8List fileBytes,
    required String fileName,
    String? skillId,
    String? notes,
  }) async {
    return _uploadVerification(
      path: '/v1/workers/skills/verify',
      fileBytes: fileBytes,
      fileName: fileName,
      skillId: skillId,
      notes: notes,
    );
  }

  Future<SkillVerificationResult> resubmitVerification({
    required String verificationId,
    required Uint8List fileBytes,
    required String fileName,
    String? skillId,
    String? notes,
  }) async {
    return _uploadVerification(
      path: '/v1/workers/skills/verifications/$verificationId/resubmit',
      fileBytes: fileBytes,
      fileName: fileName,
      skillId: skillId,
      notes: notes,
    );
  }

  Future<SkillVerificationResult> _uploadVerification({
    required String path,
    required Uint8List fileBytes,
    required String fileName,
    String? skillId,
    String? notes,
  }) async {
    try {
      final formData = FormData.fromMap({
        'file': MultipartFile.fromBytes(
          fileBytes,
          filename: fileName,
          contentType: _mediaTypeForFile(fileName),
        ),
        if (skillId != null && skillId.isNotEmpty) 'skillId': skillId,
        if (notes != null && notes.isNotEmpty) 'notes': notes,
      });

      final response = await _dio.post(
        path,
        data: formData,
        options: Options(
          contentType: 'multipart/form-data',
        ),
      );

      final data = response.data['data'] as Map<String, dynamic>;
      return SkillVerificationResult.fromJson(data);
    } on DioException catch (e) {
      throw Exception(AppErrorFormatter.format(e, fallback: 'Lỗi không xác định khi gửi tài liệu.'));
    } catch (e) {
      throw Exception(AppErrorFormatter.format(e, fallback: 'Không thể gửi tài liệu thẩm định.'));
    }
  }

  Future<SkillVerificationResult> retryVerification(String verificationId) async {
    try {
      final response = await _dio.post('/v1/workers/skills/verifications/$verificationId/retry');
      final data = response.data['data'] as Map<String, dynamic>;
      return SkillVerificationResult.fromJson(data);
    } on DioException catch (e) {
      throw Exception(AppErrorFormatter.format(e, fallback: 'Lỗi khi thử lại xác minh chứng chỉ.'));
    } catch (e) {
      throw Exception(AppErrorFormatter.format(e, fallback: 'Không thể thử lại xác minh chứng chỉ.'));
    }
  }

  Future<List<WorkerSkillItem>> getMySkills() async {
    try {
      final response = await _dio.get('/v1/workers/skills/my-skills');
      final data = response.data['data'] as List<dynamic>? ?? [];
      return data.map((e) => WorkerSkillItem.fromJson(e as Map<String, dynamic>)).toList();
    } catch (e) {
      throw Exception(AppErrorFormatter.format(e, fallback: 'Không thể tải danh sách kỹ năng đã kích hoạt.'));
    }
  }

  Future<List<WorkerSkillItem>> declareSkills(List<Map<String, dynamic>> skills) async {
    try {
      final response = await _dio.post(
        '/v1/workers/skills/declare',
        data: {'skills': skills},
      );
      final data = response.data['data'] as List<dynamic>? ?? [];
      return data.map((e) => WorkerSkillItem.fromJson(e as Map<String, dynamic>)).toList();
    } on DioException catch (e) {
      throw Exception(AppErrorFormatter.format(e, fallback: 'Lỗi khi khai báo kỹ năng.'));
    } catch (e) {
      throw Exception(AppErrorFormatter.format(e, fallback: 'Không thể khai báo kỹ năng.'));
    }
  }
}
