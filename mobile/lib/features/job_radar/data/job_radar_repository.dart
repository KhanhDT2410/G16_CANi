import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/core/network/dio_client.dart';
import 'package:cani/core/utils/app_error_formatter.dart';
import 'package:cani/features/job_radar/models/job_radar_models.dart';

final jobRadarRepositoryProvider = Provider<JobRadarRepository>((ref) {
  final dio = ref.watch(dioProvider);
  return JobRadarRepository(dio);
});

class JobRadarRepository {
  final Dio _dio;

  JobRadarRepository(this._dio);

  /// Cập nhật trạng thái kiếm việc (Online / Offline)
  Future<bool> setOnlineStatus({
    required bool isOnline,
    double? lat,
    double? lng,
  }) async {
    try {
      final response = await _dio.post(
        '/v1/workers/status',
        data: {
          'isOnline': isOnline,
          'currentLat': lat ?? 10.7769,
          'currentLng': lng ?? 106.7009,
        },
      );
      final data = response.data;
      if (data != null && data['data'] is bool) {
        return data['data'] as bool;
      }
      return isOnline;
    } on DioException catch (e) {
      throw Exception(
        AppErrorFormatter.format(
          e,
          fallback: 'Không thể cập nhật trạng thái nhận việc.',
        ),
      );
    }
  }

  /// Quét tìm các ca làm việc gần đây trên bản đồ
  Future<List<NearbyShiftItem>> getNearbyShifts({
    double lat = 10.7769,
    double lng = 106.7009,
    double radiusKm = 15.0,
  }) async {
    try {
      final response = await _dio.get(
        '/v1/workers/shifts/nearby',
        queryParameters: {'lat': lat, 'lng': lng, 'radiusKm': radiusKm},
      );

      final data = response.data;
      if (data != null && data['data'] is List) {
        return (data['data'] as List)
            .map(
              (item) => NearbyShiftItem.fromJson(item as Map<String, dynamic>),
            )
            .toList();
      }
      return [];
    } on DioException catch (e) {
      final statusCode = e.response?.statusCode;
      final serverMessage = e.response?.data is Map
          ? (e.response?.data['message'] as String?)
          : null;
      if (serverMessage != null && serverMessage.trim().isNotEmpty) {
        throw Exception(serverMessage.trim());
      }
      if (statusCode == 401) {
        throw Exception('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
      }
      if (statusCode == 403) {
        throw Exception('Tài khoản chưa có quyền truy cập dữ liệu việc làm.');
      }
      if (e.type == DioExceptionType.connectionTimeout ||
          e.type == DioExceptionType.receiveTimeout ||
          e.type == DioExceptionType.connectionError) {
        throw Exception('Không kết nối được tới máy chủ CANi.');
      }
      throw Exception('Không thể tải dữ liệu radar ($statusCode).');
    }
  }

  /// Ứng tuyển trực tiếp vào ca làm việc từ Job Radar Map
  Future<void> applyForShift(String shiftId) async {
    try {
      await _dio.post('/v1/workers/shifts/$shiftId/apply');
    } on DioException catch (e) {
      throw Exception(
        AppErrorFormatter.format(
          e,
          fallback: 'Không thể ứng tuyển ca làm việc. Vui lòng thử lại.',
        ),
      );
    }
  }
}
