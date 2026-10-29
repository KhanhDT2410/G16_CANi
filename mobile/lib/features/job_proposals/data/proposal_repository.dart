import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/core/network/dio_client.dart';
import '../models/proposal_models.dart';

class RepositoryException implements Exception {
  final String message;
  final dynamic originalError;

  const RepositoryException(this.message, [this.originalError]);

  @override
  String toString() => message;
}

final proposalRepositoryProvider = Provider<ProposalRepository>((ref) {
  final dio = ref.watch(dioProvider);
  return ProposalRepository(dio);
});

final proposalsCountProvider = FutureProvider<int>((ref) async {
  try {
    final repo = ref.watch(proposalRepositoryProvider);
    final list = await repo.fetchProposals();
    return list.length;
  } catch (_) {
    return 0;
  }
});

class ProposalRepository {
  final Dio _dio;

  ProposalRepository(this._dio);

  /// Cờ bật/tắt dữ liệu giả lập (mock demo data).
  /// Mặc định là false (OFF) trong production. Có thể bật qua `--dart-define=DEMO_MODE=true`.
  static const bool demoMode = bool.fromEnvironment('DEMO_MODE', defaultValue: false);

  String _extractErrorMessage(dynamic error, String fallback) {
    if (error is DioException) {
      final resData = error.response?.data;
      if (resData is Map && resData['message'] != null) {
        return resData['message'].toString();
      }
      if (error.type == DioExceptionType.connectionTimeout || error.type == DioExceptionType.receiveTimeout) {
        return 'Hết thời gian chờ kết nối máy chủ.';
      }
      if (error.type == DioExceptionType.connectionError) {
        return 'Không thể kết nối đến máy chủ CANi.';
      }
    }
    return fallback;
  }

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
      if (demoMode) {
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
      throw RepositoryException(_extractErrorMessage(e, 'Không thể tải danh sách lời mời việc làm.'), e);
    }
  }

  // Chấp nhận ca làm việc
  Future<bool> acceptProposal(String proposalId, {String? token}) async {
    try {
      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;
      final response = await _dio.post('/v1/workers/proposals/$proposalId/accept', options: options);
      return response.statusCode == 200;
    } catch (e) {
      throw RepositoryException(_extractErrorMessage(e, 'Chấp nhận ca làm việc thất bại.'), e);
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
      throw RepositoryException(_extractErrorMessage(e, 'Từ chối ca làm việc thất bại.'), e);
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
      if (demoMode) {
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
      throw RepositoryException(_extractErrorMessage(e, 'Không thể tải danh sách ca làm việc của bạn.'), e);
    }
  }

  // Worker Xác nhận tình trạng nhận tiền lương trực tiếp (Mục 12 SRS)
  Future<bool> confirmPayment(String assignmentId, String confirmStatus, String comment, {String? token}) async {
    try {
      // Ánh xạ trạng thái từ Domain/UI (PaymentStatus) sang Payload backend (DirectPaymentService)
      // UI: CONFIRMED_FULL -> Backend: RECEIVED_FULL
      // UI: CONFIRMED_PARTIAL -> Backend: RECEIVED_PARTIAL
      final apiStatus = switch (confirmStatus) {
        'CONFIRMED_FULL' => 'RECEIVED_FULL',
        'CONFIRMED_PARTIAL' => 'RECEIVED_PARTIAL',
        _ => confirmStatus,
      };

      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;
      final response = await _dio.post(
        '/v1/payments/worker/confirm/$assignmentId',
        data: {'confirmStatus': apiStatus, 'comment': comment},
        options: options,
      );
      return response.statusCode == 200;
    } catch (e) {
      throw RepositoryException(_extractErrorMessage(e, 'Xác nhận tình trạng thanh toán thất bại.'), e);
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
    try {
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
    } catch (e) {
      throw RepositoryException(_extractErrorMessage(e, 'Điểm danh Check-in thất bại. Vui lòng quét lại mã QR.'), e);
    }
  }

  // Quét QR Check-out hoàn thành ca làm việc
  Future<AttendanceCheckResult> checkout({
    required String assignmentId,
    required String qrContent,
    double? lat,
    double? lng,
    String? token,
  }) async {
    try {
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
    } catch (e) {
      throw RepositoryException(_extractErrorMessage(e, 'Điểm danh Check-out thất bại. Vui lòng quét lại mã QR.'), e);
    }
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
    } catch (e) {
      if (demoMode) {
        return const [
          WorkerAvailabilitySlot(dayOfWeek: 1, startTime: '18:00', endTime: '23:00'),
          WorkerAvailabilitySlot(dayOfWeek: 3, startTime: '18:00', endTime: '23:00'),
          WorkerAvailabilitySlot(dayOfWeek: 5, startTime: '18:00', endTime: '23:00'),
          WorkerAvailabilitySlot(dayOfWeek: 6, startTime: '08:00', endTime: '22:00'),
          WorkerAvailabilitySlot(dayOfWeek: 0, startTime: '08:00', endTime: '22:00'),
        ];
      }
      throw RepositoryException(_extractErrorMessage(e, 'Không thể tải lịch rảnh của bạn.'), e);
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
    } catch (e) {
      throw RepositoryException(_extractErrorMessage(e, 'Cập nhật lịch rảnh thất bại.'), e);
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
    } catch (e) {
      if (demoMode) {
        return const WorkerPreferencesModel();
      }
      throw RepositoryException(_extractErrorMessage(e, 'Không thể tải cài đặt tiêu chí tìm việc.'), e);
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
    } catch (e) {
      throw RepositoryException(_extractErrorMessage(e, 'Cập nhật tiêu chí tìm việc thất bại.'), e);
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
    } catch (e) {
      throw RepositoryException(_extractErrorMessage(e, 'Hủy ca làm việc thất bại.'), e);
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
    } catch (e) {
      if (demoMode) {
        return _defaultChecklist();
      }
      throw RepositoryException(_extractErrorMessage(e, 'Không thể tải checklist ca làm việc.'), e);
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
    } catch (e) {
      throw RepositoryException(_extractErrorMessage(e, 'Cập nhật Checklist thất bại.'), e);
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
    } catch (e) {
      throw RepositoryException(_extractErrorMessage(e, 'Gửi đánh giá Ban tổ chức thất bại.'), e);
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
    } catch (e) {
      if (demoMode) {
        return const WorkerIncomeSummary(
          totalEarnings: 1450000,
          pendingEarnings: 240000,
          completedShiftsCount: 6,
          totalHoursWorked: 24.5,
          avgRating: 4.9,
          reliabilityScore: 100,
        );
      }
      throw RepositoryException(_extractErrorMessage(e, 'Không thể tải thống kê thu nhập.'), e);
    }
  }
}
