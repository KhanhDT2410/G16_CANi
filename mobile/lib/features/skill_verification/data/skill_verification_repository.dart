import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/core/network/dio_client.dart';
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
      throw Exception('Không thể tải danh mục kỹ năng: $e');
    }
  }

  Future<List<WorkerSkillPointModel>> getSkillPoints() async {
    try {
      final response = await _dio.get('/v1/workers/skills/points');
      final data = response.data['data'] as List<dynamic>? ?? [];
      return data.map((e) => WorkerSkillPointModel.fromJson(e as Map<String, dynamic>)).toList();
    } catch (e) {
      throw Exception('Không thể tải bảng điểm kỹ năng: $e');
    }
  }

  Future<List<SkillVerificationResult>> getHistory() async {
    try {
      final response = await _dio.get('/v1/workers/skills/history');
      final data = response.data['data'] as List<dynamic>? ?? [];
      return data.map((e) => SkillVerificationResult.fromJson(e as Map<String, dynamic>)).toList();
    } catch (e) {
      throw Exception('Không thể tải lịch sử thẩm định: $e');
    }
  }

  Future<SkillVerificationResult> submitVerification({
    required Uint8List fileBytes,
    required String fileName,
    String? skillId,
    String? notes,
  }) async {
    try {
      final formData = FormData.fromMap({
        'file': MultipartFile.fromBytes(fileBytes, filename: fileName),
        if (skillId != null && skillId.isNotEmpty) 'skillId': skillId,
        if (notes != null && notes.isNotEmpty) 'notes': notes,
      });

      final response = await _dio.post(
        '/v1/workers/skills/verify',
        data: formData,
        options: Options(
          contentType: 'multipart/form-data',
        ),
      );

      final data = response.data['data'] as Map<String, dynamic>;
      return SkillVerificationResult.fromJson(data);
    } on DioException catch (e) {
      final errorMsg = e.response?.data?['message'] ?? e.message ?? 'Lỗi không xác định khi gửi tài liệu';
      throw Exception(errorMsg);
    } catch (e) {
      throw Exception('Không thể gửi tài liệu thẩm định: $e');
    }
  }

  Future<List<WorkerSkillItem>> getMySkills() async {
    try {
      final response = await _dio.get('/v1/workers/skills/my-skills');
      final data = response.data['data'] as List<dynamic>? ?? [];
      return data.map((e) => WorkerSkillItem.fromJson(e as Map<String, dynamic>)).toList();
    } catch (e) {
      throw Exception('Không thể tải danh sách kỹ năng đã kích hoạt: $e');
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
      final errorMsg = e.response?.data?['message'] ?? e.message ?? 'Lỗi khi khai báo kỹ năng';
      throw Exception(errorMsg);
    } catch (e) {
      throw Exception('Không thể khai báo kỹ năng: $e');
    }
  }
}
