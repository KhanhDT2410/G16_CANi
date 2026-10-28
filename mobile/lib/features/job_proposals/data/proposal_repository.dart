import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/core/network/dio_client.dart';
import '../models/proposal_models.dart';

final proposalRepositoryProvider = Provider<ProposalRepository>((ref) {
  final dio = ref.watch(dioProvider);
  return ProposalRepository(dio);
});

class ProposalRepository {
  final Dio _dio;

  ProposalRepository(this._dio);

  // Lấy các đề xuất việc làm đang chờ Worker phản hồi
  Future<List<JobProposalItem>> fetchProposals({String? token}) async {
    try {
      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;
      final response = await _dio.get('/v1/workers/proposals', options: options);
      if (response.data != null && response.data['data'] != null) {
        final List list = response.data['data'];
        return list.map((item) => JobProposalItem.fromJson(item)).toList();
      }
      return [];
    } catch (e) {
      // Fallback demo khi lỗi mạng hoặc chưa có dữ liệu backend
      return [
        JobProposalItem(
          proposalId: 'prop-m01',
          shiftId: 'shift-m101',
          eventId: 'evt-m01',
          eventName: 'Lễ Khai Mạc Triển Lãm Công Nghệ Quốc Tế',
          venueAddress: 'SECC, 799 Nguyễn Văn Linh, Quận 7, TP.HCM',
          jobType: 'Lễ tân / Check-in',
          description: 'Đón tiếp khách mời VIP, quét mã QR đại biểu vào hội trường.',
          startTime: DateTime.now().add(const Duration(days: 1, hours: 2)),
          endTime: DateTime.now().add(const Duration(days: 1, hours: 6)),
          hourlyWage: 60000,
          allowance: 30000,
          totalEstimatedPay: 270000,
          matchScore: 95.0,
          status: 'PENDING',
        ),
        JobProposalItem(
          proposalId: 'prop-m02',
          shiftId: 'shift-m102',
          eventId: 'evt-m02',
          eventName: 'Gala Dinner Ra Mắt Sản Phẩm Mới',
          venueAddress: 'Gem Center, Quận 1, TP.HCM',
          jobType: 'Pha chế (Bartender/Barista)',
          description: 'Phục vụ đồ uống tại sảnh tiệc chính cho 200 khách mời.',
          startTime: DateTime.now().add(const Duration(days: 2, hours: 10)),
          endTime: DateTime.now().add(const Duration(days: 2, hours: 15)),
          hourlyWage: 75000,
          allowance: 50000,
          totalEstimatedPay: 425000,
          matchScore: 89.0,
          status: 'PENDING',
        ),
      ];
    }
  }

  // Chấp nhận ca làm việc
  Future<bool> acceptProposal(String proposalId, {String? token}) async {
    try {
      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;
      final response = await _dio.post('/v1/workers/proposals/$proposalId/accept', options: options);
      return response.statusCode == 200;
    } catch (e) {
      return true; // Fallback demo
    }
  }

  // Từ chối lời mời
  Future<bool> rejectProposal(String proposalId, String reason, {String? token}) async {
    try {
      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;
      final response = await _dio.post(
        '/v1/workers/proposals/$proposalId/reject',
        queryParameters: {'reason': reason},
        options: options,
      );
      return response.statusCode == 200;
    } catch (e) {
      return true; // Fallback demo
    }
  }

  // Lấy các ca làm việc của Worker
  Future<List<ShiftAssignmentItem>> fetchAssignments({String? token}) async {
    try {
      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;
      final response = await _dio.get('/v1/workers/assignments', options: options);
      if (response.data != null && response.data['data'] != null) {
        final List list = response.data['data'];
        return list.map((item) => ShiftAssignmentItem.fromJson(item)).toList();
      }
      return [];
    } catch (e) {
      // Fallback Demo Data
      return [
        ShiftAssignmentItem(
          assignmentId: 'assign-m01',
          shiftId: 'shift-m100',
          eventId: 'evt-m00',
          eventName: 'Hội Thảo Chuyển Đổi Số Doanh Nghiệp',
          venueAddress: 'Khách sạn Rex, 141 Nguyễn Huệ, Quận 1',
          jobType: 'Điều phối sự kiện',
          startTime: DateTime.now().subtract(const Duration(days: 1)),
          endTime: DateTime.now().subtract(const Duration(days: 1, hours: -4)),
          hourlyWage: 60000,
          totalPay: 240000,
          status: 'COMPLETED',
          paymentStatus: 'DECLARED_PENDING_WORKER',
          declaredAmount: 240000,
          paymentMethod: 'BANK_TRANSFER',
        ),
      ];
    }
  }

  // Worker Xác nhận tình trạng nhận tiền lương trực tiếp (Mục 12 SRS)
  Future<bool> confirmPayment(String assignmentId, String confirmStatus, String comment, {String? token}) async {
    try {
      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;
      final response = await _dio.post(
        '/v1/payments/worker/confirm/$assignmentId',
        data: {'confirmStatus': confirmStatus, 'comment': comment},
        options: options,
      );
      return response.statusCode == 200;
    } catch (e) {
      return true; // Fallback demo
    }
  }

  // Quét QR Check-in điểm danh vào ca làm việc
  Future<AttendanceCheckResult> checkin({
    required String assignmentId,
    required String qrContent,
    double? lat,
    double? lng,
    String? token,
  }) async {
    final options = Options(
      headers: token != null ? {'Authorization': 'Bearer $token'} : null,
    );
    final response = await _dio.post(
      '/v1/workers/assignments/$assignmentId/checkin',
      data: {
        'qrContent': qrContent,
        'lat': lat,
        'lng': lng,
      },
      options: options,
    );
    if (response.data != null && response.data['data'] != null) {
      return AttendanceCheckResult.fromJson(response.data['data']);
    }
    return AttendanceCheckResult(
      assignmentId: assignmentId,
      message: 'Điểm danh Check-in thành công!',
    );
  }

  // Quét QR Check-out hoàn thành ca làm việc
  Future<AttendanceCheckResult> checkout({
    required String assignmentId,
    required String qrContent,
    double? lat,
    double? lng,
    String? token,
  }) async {
    final options = Options(
      headers: token != null ? {'Authorization': 'Bearer $token'} : null,
    );
    final response = await _dio.post(
      '/v1/workers/assignments/$assignmentId/checkout',
      data: {
        'qrContent': qrContent,
        'lat': lat,
        'lng': lng,
      },
      options: options,
    );
    if (response.data != null && response.data['data'] != null) {
      return AttendanceCheckResult.fromJson(response.data['data']);
    }
    return AttendanceCheckResult(
      assignmentId: assignmentId,
      message: 'Điểm danh Check-out thành công!',
    );
  }

  // UC-25: Lấy lịch rảnh tuần cố định của Worker
  Future<List<WorkerAvailabilitySlot>> fetchAvailability({String? token}) async {
    try {
      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;
      final response = await _dio.get('/v1/workers/availability', options: options);
      if (response.data != null && response.data['data'] != null) {
        final List list = response.data['data'];
        return list.map((item) => WorkerAvailabilitySlot.fromJson(item)).toList();
      }
      return [];
    } catch (_) {
      return [
        const WorkerAvailabilitySlot(dayOfWeek: 1, startTime: '18:00', endTime: '23:00'),
        const WorkerAvailabilitySlot(dayOfWeek: 3, startTime: '18:00', endTime: '23:00'),
        const WorkerAvailabilitySlot(dayOfWeek: 5, startTime: '18:00', endTime: '23:00'),
        const WorkerAvailabilitySlot(dayOfWeek: 6, startTime: '08:00', endTime: '22:00'),
        const WorkerAvailabilitySlot(dayOfWeek: 0, startTime: '08:00', endTime: '22:00'),
      ];
    }
  }

  // UC-25: Cập nhật lịch rảnh tuần cố định
  Future<bool> updateAvailability(List<WorkerAvailabilitySlot> schedules, {String? token}) async {
    try {
      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;
      final response = await _dio.put(
        '/v1/workers/availability',
        data: {
          'schedules': schedules.map((s) => s.toJson()).toList(),
        },
        options: options,
      );
      return response.statusCode == 200;
    } catch (_) {
      return true;
    }
  }

  // UC-26 & UC-34: Lấy tiêu chí tìm việc (Bán kính, lương tối thiểu, khu vực)
  Future<WorkerPreferencesModel> fetchPreferences({String? token}) async {
    try {
      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;
      final response = await _dio.get('/v1/workers/preferences', options: options);
      if (response.data != null && response.data['data'] != null) {
        return WorkerPreferencesModel.fromJson(response.data['data']);
      }
      return const WorkerPreferencesModel();
    } catch (_) {
      return const WorkerPreferencesModel();
    }
  }

  // UC-26 & UC-34: Cập nhật tiêu chí tìm việc
  Future<bool> updatePreferences(WorkerPreferencesModel prefs, {String? token}) async {
    try {
      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;
      final response = await _dio.put(
        '/v1/workers/preferences',
        data: prefs.toJson(),
        options: options,
      );
      return response.statusCode == 200;
    } catch (_) {
      return true;
    }
  }

  // UC-53: Worker hủy ca làm việc đã nhận
  Future<bool> cancelAssignment(String assignmentId, String reason, {String? token}) async {
    try {
      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;
      final response = await _dio.post(
        '/v1/workers/assignments/$assignmentId/cancel',
        queryParameters: {'reason': reason},
        options: options,
      );
      return response.statusCode == 200;
    } catch (_) {
      return true;
    }
  }

  // UC-45: Lấy danh sách checklist đầu việc trong ca
  Future<List<ShiftChecklistItem>> fetchChecklist(String assignmentId, {String? token}) async {
    try {
      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;
      final response = await _dio.get('/v1/workers/assignments/$assignmentId/checklist', options: options);
      if (response.data != null && response.data['data'] != null) {
        final List list = response.data['data'];
        if (list.isNotEmpty) {
          return list.map((item) => ShiftChecklistItem.fromJson(item)).toList();
        }
      }
      return _defaultChecklist();
    } catch (_) {
      return _defaultChecklist();
    }
  }

  List<ShiftChecklistItem> _defaultChecklist() => const [
        ShiftChecklistItem(id: '1', title: 'Có mặt đúng giờ & kiểm tra trang phục sự kiện', isCompleted: true),
        ShiftChecklistItem(id: '2', title: 'Nhận bàn giao khu vực phụ trách từ Quản lý ca', isCompleted: false),
        ShiftChecklistItem(id: '3', title: 'Thực hiện nhiệm vụ theo phân công trong suốt ca', isCompleted: false),
        ShiftChecklistItem(id: '4', title: 'Bàn giao trang thiết bị & dọn dẹp khu vực cuối ca', isCompleted: false),
      ];

  // UC-45: Cập nhật tiến độ checklist đầu việc
  Future<bool> updateChecklist(String assignmentId, List<ShiftChecklistItem> items, {String? token}) async {
    try {
      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;
      final response = await _dio.put(
        '/v1/workers/assignments/$assignmentId/checklist',
        data: {
          'items': items.map((i) => i.toJson()).toList(),
        },
        options: options,
      );
      return response.statusCode == 200;
    } catch (_) {
      return true;
    }
  }

  // UC-69: Worker đánh giá Ban tổ chức sau ca làm việc
  Future<bool> submitReview({
    required String assignmentId,
    required int rating,
    required int criteriaWorkEnvironment,
    required int criteriaSupport,
    required int criteriaPaymentTiming,
    required String comment,
    required List<String> tags,
    String? token,
  }) async {
    try {
      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;
      final response = await _dio.post(
        '/v1/workers/assignments/$assignmentId/reviews',
        data: {
          'rating': rating,
          'criteriaWorkEnvironment': criteriaWorkEnvironment,
          'criteriaSupport': criteriaSupport,
          'criteriaPaymentTiming': criteriaPaymentTiming,
          'comment': comment,
          'tags': tags,
        },
        options: options,
      );
      return response.statusCode == 200;
    } catch (_) {
      return true;
    }
  }

  // UC-60 & UC-64: Thống kê thu nhập và ví làm việc
  Future<WorkerIncomeSummary> fetchIncomeSummary({String? token}) async {
    try {
      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;
      final response = await _dio.get('/v1/workers/income-summary', options: options);
      if (response.data != null && response.data['data'] != null) {
        return WorkerIncomeSummary.fromJson(response.data['data']);
      }
      return const WorkerIncomeSummary();
    } catch (_) {
      return const WorkerIncomeSummary(
        totalEarnings: 1450000,
        pendingEarnings: 240000,
        completedShiftsCount: 6,
        totalHoursWorked: 24.5,
        avgRating: 4.9,
        reliabilityScore: 100,
      );
    }
  }
}
