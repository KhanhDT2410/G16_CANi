import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/core/network/dio_client.dart';
import 'package:cani/features/job_radar/models/job_radar_models.dart';

final jobRadarRepositoryProvider = Provider<JobRadarRepository>((ref) {
  final dio = ref.watch(dioProvider);
  return JobRadarRepository(dio);
});

class JobRadarRepository {
  final Dio _dio;

  JobRadarRepository(this._dio);

  /// Cờ bật/tắt dữ liệu giả lập (mock demo data).
  /// Mặc định là false (OFF) trong production. Có thể bật qua `--dart-define=DEMO_MODE=true`.
  static const bool demoMode = bool.fromEnvironment('DEMO_MODE', defaultValue: false);

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
      if (data != null && data['data'] != null) {
        return data['data'] as bool;
      }
      return isOnline;
    } catch (e) {
      if (demoMode) {
        return isOnline;
      }
      rethrow;
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
        queryParameters: {
          'lat': lat,
          'lng': lng,
          'radiusKm': radiusKm,
        },
      );

      final data = response.data;
      if (data != null && data['data'] is List) {
        return (data['data'] as List)
            .map((item) => NearbyShiftItem.fromJson(item as Map<String, dynamic>))
            .toList();
      }
      return [];
    } catch (e) {
      if (demoMode) {
        return _generateDemoShifts(lat, lng);
      }
      rethrow;
    }
  }

  /// Ứng tuyển trực tiếp vào ca làm việc từ Job Radar Map
  Future<void> applyForShift(String shiftId) async {
    try {
      await _dio.post('/v1/workers/shifts/$shiftId/apply');
    } on DioException catch (e) {
      if (demoMode) return;
      final serverMessage = e.response?.data is Map
          ? (e.response?.data['message'] as String?)
          : null;
      throw Exception(serverMessage ?? 'Không thể ứng tuyển ca làm việc: ${e.message}');
    } catch (e) {
      if (demoMode) return;
      rethrow;
    }
  }

  List<NearbyShiftItem> _generateDemoShifts(double centerLat, double centerLng) {
    return [
      NearbyShiftItem(
        shiftId: 'demo-shift-1',
        eventName: 'Lễ Khai Mạc Triển Lãm VinFast 2026',
        jobType: 'Lễ tân / Check-in',
        description: 'Đón tiếp khách VIP, hướng dẫn check-in mã QR tại sảnh chính triển lãm.',
        locationAddress: 'SECC Q7, 799 Nguyễn Văn Linh, Tân Phú, Q.7, TP.HCM',
        locationLat: centerLat + 0.015,
        locationLng: centerLng + 0.012,
        hourlyWage: 45000,
        allowance: 50000,
        distanceKm: 2.1,
        requiredWorkers: 4,
        confirmedWorkers: 2,
        remainingWorkers: 2,
        startTime: DateTime.now().add(const Duration(hours: 18)),
        endTime: DateTime.now().add(const Duration(hours: 23)),
      ),
      NearbyShiftItem(
        shiftId: 'demo-shift-2',
        eventName: 'Đêm Nhạc Monsoon Music Night',
        jobType: 'Phục vụ bàn tiệc / Barista',
        description: 'Pha chế cocktail, phục vụ đồ uống nhẹ cho khách tham dự sự kiện âm nhạc.',
        locationAddress: 'Nhà thi đấu Quân Khu 7, Hoàng Văn Thụ, Tân Bình, TP.HCM',
        locationLat: centerLat - 0.022,
        locationLng: centerLng - 0.018,
        hourlyWage: 40000,
        allowance: 30000,
        distanceKm: 3.8,
        requiredWorkers: 6,
        confirmedWorkers: 5,
        remainingWorkers: 1,
        startTime: DateTime.now().add(const Duration(hours: 24)),
        endTime: DateTime.now().add(const Duration(hours: 29)),
      ),
      NearbyShiftItem(
        shiftId: 'demo-shift-3',
        eventName: 'Hội Thảo Công Nghệ FPT Edu Tech 2026',
        jobType: 'Điều phối & Hỗ trợ kỹ thuật',
        description: 'Hỗ trợ micro cho diễn giả, điều phối khách mời vào các phòng hội trường.',
        locationAddress: 'FPT University HCMC, Khu CNC, Long Thạnh Mỹ, TP. Thủ Đức',
        locationLat: centerLat + 0.045,
        locationLng: centerLng + 0.035,
        hourlyWage: 50000,
        allowance: 40000,
        distanceKm: 7.2,
        requiredWorkers: 3,
        confirmedWorkers: 1,
        remainingWorkers: 2,
        startTime: DateTime.now().add(const Duration(days: 2, hours: 2)),
        endTime: DateTime.now().add(const Duration(days: 2, hours: 8)),
      ),
      NearbyShiftItem(
        shiftId: 'demo-shift-4',
        eventName: 'Tiệc Cưới Diamond Palace',
        jobType: 'Phục vụ bàn (F&B Staff)',
        description: 'Set up bàn tiệc sang trọng, tiếp món ăn và hỗ trợ quan khách trong tiệc cưới.',
        locationAddress: 'Trung tâm Hội nghị White Palace, Hoàng Văn Thụ, Phú Nhuận',
        locationLat: centerLat - 0.008,
        locationLng: centerLng + 0.020,
        hourlyWage: 38000,
        allowance: 20000,
        distanceKm: 1.6,
        requiredWorkers: 8,
        confirmedWorkers: 6,
        remainingWorkers: 2,
        startTime: DateTime.now().add(const Duration(hours: 12)),
        endTime: DateTime.now().add(const Duration(hours: 17)),
      ),
    ];
  }
}
