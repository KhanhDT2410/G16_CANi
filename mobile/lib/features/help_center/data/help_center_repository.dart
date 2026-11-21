import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/core/network/dio_client.dart';
import 'package:cani/features/help_center/models/help_center_models.dart';

final helpCenterRepositoryProvider = Provider<HelpCenterRepository>((ref) {
  final dio = ref.watch(dioProvider);
  return HelpCenterRepository(dio);
});

class HelpCenterRepository {
  final Dio _dio;

  HelpCenterRepository(this._dio);

  /// Lấy danh sách câu hỏi thường gặp đã xuất bản (mặc định chỉ lấy nhóm WORKER và chung)
  Future<List<FaqItemModel>> fetchFaqs({String? category, String targetRole = 'WORKER'}) async {
    try {
      final queryParams = <String, dynamic>{
        'targetRole': targetRole,
        if (category != null && category.isNotEmpty) 'category': category,
      };

      final res = await _dio.get(
        '/v1/faqs',
        queryParameters: queryParams,
      );

      final body = res.data;
      if (body != null && body['data'] is List) {
        final list = body['data'] as List<dynamic>;
        return list.map((item) => FaqItemModel.fromJson(item as Map<String, dynamic>)).toList();
      }
      return const [];
    } catch (e) {
      throw Exception('Không thể tải danh sách câu hỏi thường gặp: $e');
    }
  }

  /// Xem chi tiết câu hỏi thường gặp và tăng lượt xem
  Future<FaqItemModel> fetchFaqDetail(String id) async {
    try {
      final res = await _dio.get('/v1/faqs/$id');
      final body = res.data;
      if (body != null && body['data'] != null) {
        return FaqItemModel.fromJson(body['data'] as Map<String, dynamic>);
      }
      throw Exception('Không tìm thấy dữ liệu câu hỏi');
    } catch (e) {
      throw Exception('Lỗi khi tải chi tiết câu hỏi: $e');
    }
  }

  /// Lấy danh sách các yêu cầu hỗ trợ của người dùng hiện tại
  Future<List<SupportTicketModel>> fetchMyTickets() async {
    try {
      final res = await _dio.get('/v1/support/tickets/my');
      final body = res.data;
      if (body != null && body['data'] is List) {
        final list = body['data'] as List<dynamic>;
        return list.map((item) => SupportTicketModel.fromJson(item as Map<String, dynamic>)).toList();
      }
      return const [];
    } catch (e) {
      throw Exception('Không thể tải danh sách yêu cầu hỗ trợ: $e');
    }
  }

  /// Xem chi tiết phiếu yêu cầu hỗ trợ kèm luồng trao đổi
  Future<SupportTicketModel> fetchTicketDetail(String id) async {
    try {
      final res = await _dio.get('/v1/support/tickets/$id');
      final body = res.data;
      if (body != null && body['data'] != null) {
        return SupportTicketModel.fromJson(body['data'] as Map<String, dynamic>);
      }
      throw Exception('Không tìm thấy phiếu hỗ trợ');
    } catch (e) {
      throw Exception('Lỗi khi tải chi tiết phiếu hỗ trợ: $e');
    }
  }

  /// Tạo một phiếu yêu cầu hỗ trợ mới (Mô tả tối thiểu 30 ký tự)
  Future<SupportTicketModel> createTicket({
    required String category,
    required String subject,
    required String description,
    List<String> attachments = const [],
  }) async {
    try {
      final res = await _dio.post(
        '/v1/support/tickets',
        data: {
          'category': category,
          'subject': subject.trim(),
          'description': description.trim(),
          'attachments': attachments,
        },
      );
      final body = res.data;
      if (body != null && body['data'] != null) {
        return SupportTicketModel.fromJson(body['data'] as Map<String, dynamic>);
      }
      throw Exception('Phản hồi không hợp lệ từ máy chủ');
    } catch (e) {
      if (e is DioException && e.response?.data != null) {
        final errData = e.response!.data;
        if (errData is Map && errData['message'] != null) {
          throw Exception(errData['message']);
        }
      }
      throw Exception('Không thể tạo yêu cầu hỗ trợ: $e');
    }
  }

  /// Phản hồi trao đổi trong phiếu hỗ trợ
  Future<SupportTicketModel> replyTicket({
    required String ticketId,
    required String message,
    List<String> attachments = const [],
  }) async {
    try {
      final res = await _dio.post(
        '/v1/support/tickets/$ticketId/reply',
        data: {
          'message': message.trim(),
          'attachments': attachments,
        },
      );
      final body = res.data;
      if (body != null && body['data'] != null) {
        return SupportTicketModel.fromJson(body['data'] as Map<String, dynamic>);
      }
      throw Exception('Phản hồi không hợp lệ từ máy chủ');
    } catch (e) {
      if (e is DioException && e.response?.data != null) {
        final errData = e.response!.data;
        if (errData is Map && errData['message'] != null) {
          throw Exception(errData['message']);
        }
      }
      throw Exception('Không thể gửi phản hồi: $e');
    }
  }

  /// Lấy toàn bộ lịch sử trò chuyện trực tuyến với CSKH của người dùng hiện tại
  Future<List<SupportChatMessageModel>> fetchMySupportChat() async {
    try {
      final res = await _dio.get('/v1/support/chat/my');
      final body = res.data;
      if (body != null && body['data'] != null) {
        final rawMessages = body['data']['messages'];
        if (rawMessages is List) {
          return rawMessages
              .map((item) => SupportChatMessageModel.fromJson(item as Map<String, dynamic>))
              .toList();
        }
      }
      return const [];
    } catch (e) {
      throw Exception('Không thể tải lịch sử trò chuyện với CSKH: $e');
    }
  }

  /// Gửi tin nhắn mới tới CSKH (hỗ trợ kèm mã ca làm việc nếu cần)
  Future<SupportChatMessageModel> sendSupportChatMessage({
    required String messageText,
    String? shiftId,
    String? attachmentUrl,
  }) async {
    try {
      final res = await _dio.post(
        '/v1/support/chat/send',
        data: {
          'messageText': messageText.trim(),
          if (shiftId != null && shiftId.isNotEmpty) 'shiftId': shiftId,
          if (attachmentUrl != null && attachmentUrl.isNotEmpty) 'attachmentUrl': attachmentUrl,
        },
      );
      final body = res.data;
      if (body != null && body['data'] != null) {
        return SupportChatMessageModel.fromJson(body['data'] as Map<String, dynamic>);
      }
      throw Exception('Phản hồi không hợp lệ từ máy chủ');
    } catch (e) {
      if (e is DioException && e.response?.data != null) {
        final errData = e.response!.data;
        if (errData is Map && errData['message'] != null) {
          throw Exception(errData['message']);
        }
      }
      throw Exception('Không thể gửi tin nhắn hỗ trợ: $e');
    }
  }
}
