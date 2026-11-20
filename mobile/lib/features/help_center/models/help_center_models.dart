import 'package:flutter/material.dart';

class FaqItemModel {
  final String id;
  final String category; // ACCOUNT, SHIFT, PAYMENT, DISPUTE
  final String? targetRole; // WORKER, ALL, EVENT_ORGANIZER
  final String question;
  final String? summary;
  final String answer;
  final String? imageUrl;
  final String? attachmentUrl;
  final String? attachmentName;
  final int viewCount;

  const FaqItemModel({
    required this.id,
    required this.category,
    this.targetRole,
    required this.question,
    this.summary,
    required this.answer,
    this.imageUrl,
    this.attachmentUrl,
    this.attachmentName,
    this.viewCount = 0,
  });

  factory FaqItemModel.fromJson(Map<String, dynamic> json) {
    return FaqItemModel(
      id: json['id']?.toString() ?? '',
      category: json['category']?.toString() ?? 'ACCOUNT',
      targetRole: json['targetRole']?.toString(),
      question: json['question']?.toString() ?? '',
      summary: json['summary']?.toString(),
      answer: json['answer']?.toString() ?? '',
      imageUrl: json['imageUrl']?.toString(),
      attachmentUrl: json['attachmentUrl']?.toString(),
      attachmentName: json['attachmentName']?.toString(),
      viewCount: json['viewCount'] is int ? json['viewCount'] as int : 0,
    );
  }

  String get categoryLabel {
    switch (category.toUpperCase()) {
      case 'ACCOUNT':
        return 'Tài khoản & Định danh';
      case 'SHIFT':
        return 'Ca làm & Điểm danh';
      case 'PAYMENT':
        return 'Thù lao & Thanh toán';
      case 'DISPUTE':
        return 'Tranh chấp & Khiếu nại';
      default:
        return 'Chung';
    }
  }
}

class SupportTicketModel {
  final String id;
  final String referenceNo;
  final String category; // ACCOUNT, BILLING, TECHNICAL, GENERAL, OTHER
  final String priority; // LOW, MEDIUM, HIGH, URGENT
  final String subject;
  final String description;
  final String status; // OPEN, IN_PROGRESS, RESOLVED, CLOSED
  final DateTime createdAt;
  final List<TicketMessageModel> messages;

  const SupportTicketModel({
    required this.id,
    required this.referenceNo,
    required this.category,
    required this.priority,
    required this.subject,
    required this.description,
    required this.status,
    required this.createdAt,
    this.messages = const [],
  });

  factory SupportTicketModel.fromJson(Map<String, dynamic> json) {
    final rawMessages = json['messages'] as List<dynamic>? ?? [];
    return SupportTicketModel(
      id: json['id']?.toString() ?? '',
      referenceNo: json['referenceNo']?.toString() ?? '',
      category: json['category']?.toString() ?? 'GENERAL',
      priority: json['priority']?.toString() ?? 'MEDIUM',
      subject: json['subject']?.toString() ?? '',
      description: json['description']?.toString() ?? '',
      status: json['status']?.toString() ?? 'OPEN',
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
      messages: rawMessages
          .map((m) => TicketMessageModel.fromJson(m as Map<String, dynamic>))
          .toList(),
    );
  }

  String get statusLabel {
    switch (status.toUpperCase()) {
      case 'OPEN':
        return 'Mới tiếp nhận';
      case 'IN_PROGRESS':
        return 'Đang xử lý';
      case 'RESOLVED':
        return 'Đã giải quyết';
      case 'CLOSED':
        return 'Đã đóng';
      default:
        return status;
    }
  }

  Color get statusColor {
    switch (status.toUpperCase()) {
      case 'OPEN':
        return const Color(0xFF2563EB); // Blue
      case 'IN_PROGRESS':
        return const Color(0xFFD97706); // Amber
      case 'RESOLVED':
        return const Color(0xFF16A34A); // Green
      case 'CLOSED':
        return const Color(0xFF64748B); // Slate
      default:
        return const Color(0xFF64748B);
    }
  }

  Color get statusBgColor {
    switch (status.toUpperCase()) {
      case 'OPEN':
        return const Color(0xFFEFF6FF);
      case 'IN_PROGRESS':
        return const Color(0xFFFEF3C7);
      case 'RESOLVED':
        return const Color(0xFFDCFCE7);
      case 'CLOSED':
        return const Color(0xFFF1F5F9);
      default:
        return const Color(0xFFF1F5F9);
    }
  }

  String get categoryLabel {
    switch (category.toUpperCase()) {
      case 'ACCOUNT':
        return 'Tài khoản';
      case 'BILLING':
        return 'Thù lao & Thanh toán';
      case 'TECHNICAL':
        return 'Lỗi kỹ thuật';
      case 'GENERAL':
        return 'Thắc mắc chung';
      default:
        return 'Khác';
    }
  }
}

class TicketMessageModel {
  final String id;
  final String authorName;
  final String authorRole;
  final String message;
  final bool isStaffReply;
  final DateTime createdAt;

  const TicketMessageModel({
    required this.id,
    required this.authorName,
    required this.authorRole,
    required this.message,
    required this.isStaffReply,
    required this.createdAt,
  });

  factory TicketMessageModel.fromJson(Map<String, dynamic> json) {
    return TicketMessageModel(
      id: json['id']?.toString() ?? '',
      authorName: json['authorName']?.toString() ?? 'Hệ thống',
      authorRole: json['authorRole']?.toString() ?? 'USER',
      message: json['message']?.toString() ?? '',
      isStaffReply: json['isStaffReply'] == true,
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
    );
  }
}

class SupportChatMessageModel {
  final String id;
  final String conversationId;
  final String senderId;
  final String senderRole; // WORKER, EO, CUSTOMER_CARE, ADMIN, SYSTEM
  final String senderName;
  final String messageText;
  final String? attachmentUrl;
  final String? shiftId;
  final String? shiftCode;
  final String? shiftName;
  final bool isRead;
  final DateTime createdAt;

  const SupportChatMessageModel({
    required this.id,
    required this.conversationId,
    required this.senderId,
    required this.senderRole,
    required this.senderName,
    required this.messageText,
    this.attachmentUrl,
    this.shiftId,
    this.shiftCode,
    this.shiftName,
    this.isRead = false,
    required this.createdAt,
  });

  factory SupportChatMessageModel.fromJson(Map<String, dynamic> json) {
    return SupportChatMessageModel(
      id: json['id']?.toString() ?? '',
      conversationId: json['conversationId']?.toString() ?? '',
      senderId: json['senderId']?.toString() ?? '',
      senderRole: json['senderRole']?.toString() ?? 'WORKER',
      senderName: json['senderName']?.toString() ?? 'Người dùng',
      messageText: json['messageText']?.toString() ?? '',
      attachmentUrl: json['attachmentUrl']?.toString(),
      shiftId: json['shiftId']?.toString(),
      shiftCode: json['shiftCode']?.toString(),
      shiftName: json['shiftName']?.toString(),
      isRead: json['isRead'] == true,
      createdAt: json['createdAt'] != null
          ? (DateTime.tryParse(json['createdAt'].toString())?.toLocal() ?? DateTime.now())
          : DateTime.now(),
    );
  }

  bool get isFromStaff =>
      senderRole == 'CUSTOMER_CARE' ||
      senderRole == 'ADMIN' ||
      senderRole == 'SYSTEM';
}
