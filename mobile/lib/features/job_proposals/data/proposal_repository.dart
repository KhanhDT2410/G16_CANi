import 'dart:developer';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/core/network/dio_client.dart';
import '../models/dispute_models.dart';
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

final assignmentsListProvider = FutureProvider<List<ShiftAssignmentItem>>((ref) async {
  final repo = ref.watch(proposalRepositoryProvider);
  return repo.fetchAssignments();
});

final proposalsListProvider = FutureProvider<List<JobProposalItem>>((ref) async {
  final repo = ref.watch(proposalRepositoryProvider);
  try {
    return await repo.fetchProposals();
  } catch (_) {
    return const [];
  }
});

final myDisputesListProvider = FutureProvider<List<WorkerDisputeItem>>((ref) async {
  final repo = ref.watch(proposalRepositoryProvider);
  try {
    return await repo.fetchMyDisputes();
  } catch (_) {
    return const [];
  }
});

/// Thống kê thu nhập và tích lũy thực chiến của Worker (UC-60 / UC-64)
final incomeSummaryProvider = FutureProvider<WorkerIncomeSummary>((ref) async {
  final repo = ref.watch(proposalRepositoryProvider);
  try {
    return await repo.fetchIncomeSummary();
  } catch (_) {
    return const WorkerIncomeSummary();
  }
});

/// Quản lý Tab con tại màn hình Ca Làm (0: Đang làm & Sắp tới, 1: Lịch sử ca làm)
final assignmentsSubTabProvider = StateProvider<int>((ref) => 0);
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
    String? proofImageUrl,
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
          if (proofImageUrl != null && proofImageUrl.isNotEmpty) 'proofImageUrl': proofImageUrl,
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

  // Tải ảnh bằng chứng nghiệm thu hoàn thành ca làm
  Future<String> uploadProofImage(String filePath, {String? token}) async {
    try {
      final fileName = filePath.split(RegExp(r'[\\/]')).last;
      final formData = FormData.fromMap({
        'file': await MultipartFile.fromFile(
          filePath,
          filename: fileName,
          contentType: DioMediaType('image', 'jpeg'),
        ),
      });
      final options = Options(
        headers: token != null ? {'Authorization': 'Bearer $token'} : null,
      );
      final response = await _dio.post(
        '/v1/workers/upload-proof',
        data: formData,
        options: options,
      );
      if (response.data != null && response.data['data'] != null && response.data['data']['url'] != null) {
        return response.data['data']['url'] as String;
      }
      throw RepositoryException('Không nhận được đường dẫn ảnh sau khi tải lên.');
    } catch (e) {
      throw RepositoryException(_extractErrorMessage(e, 'Tải ảnh bằng chứng thất bại. Vui lòng thử lại.'), e);
    }
  }

  // Tải ảnh bằng chứng trực tiếp từ bytes Camera
  Future<String> uploadProofBytes(Uint8List bytes, String fileName, {String? token}) async {
    try {
      final formData = FormData.fromMap({
        'file': MultipartFile.fromBytes(
          bytes,
          filename: fileName,
          contentType: DioMediaType('image', 'jpeg'),
        ),
      });
      final options = Options(
        headers: token != null ? {'Authorization': 'Bearer $token'} : null,
      );
      final response = await _dio.post(
        '/v1/workers/upload-proof',
        data: formData,
        options: options,
      );
      if (response.data != null && response.data['data'] != null && response.data['data']['url'] != null) {
        return response.data['data']['url'] as String;
      }
      throw RepositoryException('Không nhận được đường dẫn ảnh sau khi tải lên.');
    } catch (e) {
      throw RepositoryException(_extractErrorMessage(e, 'Tải ảnh bằng chứng thất bại. Vui lòng thử lại.'), e);
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
        return list.map((item) => ShiftChecklistItem.fromJson(item)).toList();
      }
      return const [];
    } catch (e) {
      if (demoMode) {
        return _demoChecklist();
      }
      throw RepositoryException(_extractErrorMessage(e, 'Không thể tải checklist ca làm việc.'), e);
    }
  }

  List<ShiftChecklistItem> _demoChecklist() => const [
        ShiftChecklistItem(id: 'chk_1', title: 'Có mặt đúng giờ & kiểm tra trang phục sự kiện', isCompleted: false),
        ShiftChecklistItem(id: 'chk_2', title: 'Nhận bàn giao khu vực phụ trách từ Quản lý ca', isCompleted: false),
        ShiftChecklistItem(id: 'chk_3', title: 'Thực hiện nhiệm vụ theo phân công trong suốt ca', isCompleted: false),
        ShiftChecklistItem(id: 'chk_4', title: 'Bàn giao trang thiết bị & dọn dẹp khu vực cuối ca', isCompleted: false),
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

  // Nộp hoặc cập nhật ảnh bằng chứng nghiệm thu ca làm cho EO kiểm tra trước khi duyệt công
  Future<bool> submitAssignmentProof(String assignmentId, String? proofImageUrl, {String? token}) async {
    try {
      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;
      final response = await _dio.put(
        '/v1/workers/assignments/$assignmentId/proof',
        data: {'proofImageUrl': proofImageUrl ?? ''},
        options: options,
      );
      return response.statusCode == 200;
    } catch (e) {
      throw RepositoryException(_extractErrorMessage(e, 'Lưu ảnh bằng chứng nghiệm thu thất bại.'), e);
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

  // UC-73: Worker tham gia ca làm việc qua Mã mời hoặc Quét QR mời (Invite Mode)
  Future<bool> joinViaInviteCode(String code, {String? token}) async {
    try {
      final options = token != null ? Options(headers: {'Authorization': 'Bearer $token'}) : null;
      final response = await _dio.post(
        '/v1/workers/invites/join',
        data: {'code': code.trim().toUpperCase()},
        options: options,
      );
      return response.statusCode == 200;
    } catch (e) {
      if (demoMode) {
        log('Demo Mode: Giả lập tham gia ca qua mã mời $code thành công');
        return true;
      }
      throw RepositoryException(_extractErrorMessage(e, 'Không thể tham gia ca qua mã mời này.'), e);
    }
  }

  // Chat trực tiếp trong Ca làm việc giữa Worker, EO (Ban tổ chức) và CSKH
  Future<List<ShiftChatMessageItem>> fetchShiftChatMessages(String shiftId) async {
    try {
      final response = await _dio.get('/v1/support/chat/my');
      final myMessages = <ShiftChatMessageItem>[];
      if (response.data != null && response.data['data'] != null) {
        final rawList = response.data['data']['messages'];
        if (rawList is List) {
          for (final item in rawList) {
            if (item is Map<String, dynamic>) {
              final msg = ShiftChatMessageItem.fromJson(item);
              if (msg.shiftId == null || msg.shiftId!.isEmpty || msg.shiftId == shiftId) {
                myMessages.add(msg);
              }
            }
          }
        }
      }

      // Gộp thêm toàn bộ tin nhắn chung theo shiftId (từ phía EO hoặc CSKH gắn với ca này)
      try {
        final shiftRes = await _dio.get('/v1/support/chat/shift/$shiftId');
        if (shiftRes.data != null && shiftRes.data['data'] is List) {
          final seenIds = myMessages.map((m) => m.id).toSet();
          for (final item in (shiftRes.data['data'] as List)) {
            if (item is Map<String, dynamic>) {
              final msg = ShiftChatMessageItem.fromJson(item);
              if (!seenIds.contains(msg.id)) {
                myMessages.add(msg);
                seenIds.add(msg.id);
              }
            }
          }
        }
      } catch (_) {
        // Fallback nếu endpoint shift/{shiftId} chưa sẵn sàng
      }

      myMessages.sort((a, b) => a.createdAt.compareTo(b.createdAt));
      return myMessages;
    } catch (e) {
      throw RepositoryException(_extractErrorMessage(e, 'Không thể tải tin nhắn ca làm việc.'), e);
    }
  }

  Future<ShiftChatMessageItem> sendShiftChatMessage({
    required String shiftId,
    required String messageText,
    String? attachmentUrl,
  }) async {
    try {
      final response = await _dio.post(
        '/v1/support/chat/send',
        data: {
          'messageText': messageText.trim(),
          'shiftId': shiftId,
          'attachmentUrl': ?attachmentUrl,
        },
      );
      if (response.data != null && response.data['data'] is Map<String, dynamic>) {
        return ShiftChatMessageItem.fromJson(response.data['data'] as Map<String, dynamic>);
      }
      return ShiftChatMessageItem(
        id: 'local-${DateTime.now().millisecondsSinceEpoch}',
        senderId: '',
        senderRole: 'WORKER',
        senderName: 'Tôi',
        messageText: messageText.trim(),
        shiftId: shiftId,
        createdAt: DateTime.now(),
      );
    } catch (e) {
      throw RepositoryException(_extractErrorMessage(e, 'Không thể gửi tin nhắn.'), e);
    }
  }

  // Lấy danh sách tranh chấp / khiếu nại của tôi
  Future<List<WorkerDisputeItem>> fetchMyDisputes() async {
    try {
      final response = await _dio.get('/v1/disputes/my');
      final data = response.data;
      if (data != null && data['data'] is List) {
        return (data['data'] as List)
            .whereType<Map<String, dynamic>>()
            .map((e) => WorkerDisputeItem.fromJson(e))
            .toList();
      }
      return [];
    } catch (e) {
      throw RepositoryException(_extractErrorMessage(e, 'Không thể tải danh sách khiếu nại.'), e);
    }
  }

  // Tạo mới khiếu nại tranh chấp
  Future<WorkerDisputeItem> createDispute(CreateDisputePayload payload) async {
    try {
      final response = await _dio.post('/v1/disputes', data: payload.toJson());
      final data = response.data;
      if (data != null && data['data'] is Map<String, dynamic>) {
        return WorkerDisputeItem.fromJson(data['data'] as Map<String, dynamic>);
      }
      return WorkerDisputeItem(
        id: 'local-${DateTime.now().millisecondsSinceEpoch}',
        assignmentId: payload.assignmentId,
        type: payload.type,
        status: 'PENDING',
        title: payload.title,
        description: payload.description,
        evidence: payload.evidence,
        createdAt: DateTime.now(),
      );
    } catch (e) {
      throw RepositoryException(_extractErrorMessage(e, 'Gửi khiếu nại thất bại.'), e);
    }
  }
}
