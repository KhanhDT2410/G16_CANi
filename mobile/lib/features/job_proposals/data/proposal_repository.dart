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
      '/workers/assignments/$assignmentId/checkin',
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
      '/workers/assignments/$assignmentId/checkout',
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
}
