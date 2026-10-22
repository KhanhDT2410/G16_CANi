import 'package:dio/dio.dart';
import '../models/proposal_models.dart';

class ProposalRepository {
  final Dio _dio;
  static const String _baseUrl = 'http://localhost:8080/api/v1';

  ProposalRepository({Dio? dio})
      : _dio = dio ??
            Dio(BaseOptions(
              baseUrl: _baseUrl,
              connectTimeout: const Duration(seconds: 10),
              receiveTimeout: const Duration(seconds: 10),
            ));

  // Lấy các đề xuất việc làm đang chờ Worker phản hồi
  Future<List<JobProposalItem>> fetchProposals({String? token}) async {
    try {
      final options = Options(
        headers: token != null ? {'Authorization': 'Bearer $token'} : null,
      );
      final response = await _dio.get('/workers/proposals', options: options);
      if (response.data != null && response.data['data'] != null) {
        final List list = response.data['data'];
        return list.map((item) => JobProposalItem.fromJson(item)).toList();
      }
      return [];
    } catch (e) {
      // Mock Demo Data
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
      final options = Options(
        headers: token != null ? {'Authorization': 'Bearer $token'} : null,
      );
      final response = await _dio.post('/workers/proposals/$proposalId/accept', options: options);
      return response.statusCode == 200;
    } catch (e) {
      return true; // Fallback demo
    }
  }

  // Từ chối lời mời
  Future<bool> rejectProposal(String proposalId, String reason, {String? token}) async {
    try {
      final options = Options(
        headers: token != null ? {'Authorization': 'Bearer $token'} : null,
      );
      final response = await _dio.post('/workers/proposals/$proposalId/reject',
          queryParameters: {'reason': reason}, options: options);
      return response.statusCode == 200;
    } catch (e) {
      return true; // Fallback demo
    }
  }

  // Lấy các ca làm việc của Worker
  Future<List<ShiftAssignmentItem>> fetchAssignments({String? token}) async {
    try {
      final options = Options(
        headers: token != null ? {'Authorization': 'Bearer $token'} : null,
      );
      final response = await _dio.get('/workers/assignments', options: options);
      if (response.data != null && response.data['data'] != null) {
        final List list = response.data['data'];
        return list.map((item) => ShiftAssignmentItem.fromJson(item)).toList();
      }
      return [];
    } catch (e) {
      // Mock Demo Data
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
      final options = Options(
        headers: token != null ? {'Authorization': 'Bearer $token'} : null,
      );
      final response = await _dio.post(
        '/payments/worker/confirm/$assignmentId',
        data: {'confirmStatus': confirmStatus, 'comment': comment},
        options: options,
      );
      return response.statusCode == 200;
    } catch (e) {
      return true; // Fallback demo
    }
  }
}
