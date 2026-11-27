class WorkerDisputeItem {
  final String id;
  final String? assignmentId;
  final String? eventTitle;
  final String? shiftTitle;
  final DateTime? shiftStartTime;
  final DateTime? shiftEndTime;
  final String? shiftLocation;
  final String? respondentName;
  final String type; // PAYMENT, WORK_QUALITY, ATTENDANCE, CONDUCT, OTHER
  final String status; // OPEN, INVESTIGATING, RESOLVED, UNVERIFIED, ESCALATED, FINAL_RESOLVED
  final String? outcome;
  final String title;
  final String description;
  final String? evidence;
  final List<DisputeEvidenceItem> evidences;
  final String? resolution;
  final String? decisionRationale;
  final String? finalDecisionRationale;
  final String? escalationReason;
  final DateTime? slaDeadline;
  final DateTime? createdAt;
  final DateTime? resolvedAt;

  const WorkerDisputeItem({
    required this.id,
    this.assignmentId,
    this.eventTitle,
    this.shiftTitle,
    this.shiftStartTime,
    this.shiftEndTime,
    this.shiftLocation,
    this.respondentName,
    required this.type,
    required this.status,
    this.outcome,
    required this.title,
    required this.description,
    this.evidence,
    this.evidences = const [],
    this.resolution,
    this.decisionRationale,
    this.finalDecisionRationale,
    this.escalationReason,
    this.slaDeadline,
    this.createdAt,
    this.resolvedAt,
  });

  factory WorkerDisputeItem.fromJson(Map<String, dynamic> json) {
    final rawEvidences = (json['evidences'] is List)
        ? (json['evidences'] as List)
            .whereType<Map<String, dynamic>>()
            .map(DisputeEvidenceItem.fromJson)
            .toList()
        : const <DisputeEvidenceItem>[];
    final legacyEvidence = json['evidence'] as String?;
    final fallbackEvidence = (legacyEvidence != null && legacyEvidence.trim().isNotEmpty)
        ? legacyEvidence.trim()
        : (rawEvidences.isNotEmpty ? rawEvidences.first.fileUrl : null);

    return WorkerDisputeItem(
      id: (json['id'] ?? json['disputeId'] ?? '').toString(),
      assignmentId: json['assignmentId']?.toString(),
      eventTitle: (json['eventName'] ?? json['eventTitle']) as String?,
      shiftTitle: (json['jobType'] ?? json['shiftTitle']) as String?,
      shiftStartTime: json['shiftStartTime'] != null ? DateTime.tryParse(json['shiftStartTime'].toString())?.toLocal() : null,
      shiftEndTime: json['shiftEndTime'] != null ? DateTime.tryParse(json['shiftEndTime'].toString())?.toLocal() : null,
      shiftLocation: json['shiftLocation'] as String?,
      respondentName: json['respondentName'] as String?,
      type: (json['type'] ?? 'PAYMENT').toString(),
      status: (json['status'] ?? 'OPEN').toString(),
      outcome: json['outcome']?.toString(),
      title: (json['title'] ?? 'Khiếu nại tranh chấp').toString(),
      description: (json['description'] ?? '').toString(),
      evidence: fallbackEvidence,
      evidences: rawEvidences,
      resolution: json['resolution'] as String?,
      decisionRationale: json['decisionRationale'] as String?,
      finalDecisionRationale: json['finalDecisionRationale'] as String?,
      escalationReason: json['escalationReason'] as String?,
      slaDeadline: json['slaDeadline'] != null ? DateTime.tryParse(json['slaDeadline'].toString())?.toLocal() : null,
      createdAt: json['createdAt'] != null ? DateTime.tryParse(json['createdAt'].toString())?.toLocal() : null,
      resolvedAt: json['resolvedAt'] != null ? DateTime.tryParse(json['resolvedAt'].toString())?.toLocal() : null,
    );
  }

  /// Phân loại trạng thái nghiệp vụ: Đang xử lý (bao gồm cả hồ sơ đang kháng nghị) vs Đã có kết quả.
  static bool isStatusInProgress(String rawStatus) {
    switch (rawStatus.trim().toUpperCase()) {
      case 'OPEN':
      case 'PENDING':
      case 'INVESTIGATING':
      case 'IN_REVIEW':
      case 'ESCALATED':
      case 'APPEAL_REQUESTED':
      case 'APPEALING':
      case 'UNDER_APPEAL':
        return true;
      default:
        return false;
    }
  }

  bool get isInProgress => isStatusInProgress(status);

  bool get isOpen => isInProgress;

  bool get hasResult => !isInProgress;

  bool get isAppealing {
    final normalized = status.trim().toUpperCase();
    return normalized == 'ESCALATED' ||
        normalized == 'APPEAL_REQUESTED' ||
        normalized == 'APPEALING' ||
        normalized == 'UNDER_APPEAL';
  }

  List<DisputeEvidenceItem> get allEvidences {
    if (evidences.isNotEmpty) return evidences;
    if (evidence != null && evidence!.trim().isNotEmpty) {
      return [
        DisputeEvidenceItem(
          id: 'legacy-evidence',
          fileType: 'IMAGE',
          fileUrl: evidence!.trim(),
        ),
      ];
    }
    return const [];
  }

  String? get effectiveResolutionText {
    if (finalDecisionRationale != null && finalDecisionRationale!.trim().isNotEmpty) {
      return finalDecisionRationale!.trim();
    }
    if (decisionRationale != null && decisionRationale!.trim().isNotEmpty) {
      return decisionRationale!.trim();
    }
    if (resolution != null && resolution!.trim().isNotEmpty) {
      return resolution!.trim();
    }
    return null;
  }

  String get typeLabel {
    switch (type) {
      case 'PAYMENT':
        return 'Thù lao & Thanh toán';
      case 'WORK_QUALITY':
        return 'Công việc & Yêu cầu';
      case 'ATTENDANCE':
      case 'NO_SHOW':
        return 'Thời gian & Điểm danh';
      case 'CONDUCT':
      case 'CANCELATION':
        return 'Thái độ & Hủy ca';
      default:
        return 'Vấn đề khác';
    }
  }

  String get statusLabel {
    switch (status.trim().toUpperCase()) {
      case 'OPEN':
      case 'PENDING':
        return 'Chờ tiếp nhận';
      case 'INVESTIGATING':
      case 'IN_REVIEW':
        return 'CSKH đang điều tra';
      case 'RESOLVED':
        return 'Đã phân xử';
      case 'UNVERIFIED':
        return 'Chưa đủ căn cứ';
      case 'REJECTED':
        return 'Đã từ chối';
      case 'ESCALATED':
      case 'APPEAL_REQUESTED':
      case 'APPEALING':
      case 'UNDER_APPEAL':
        return 'Đang kháng nghị Admin';
      case 'FINAL_RESOLVED':
        return 'Quyết định cuối cùng';
      case 'CLOSED':
        return 'Đã đóng';
      default:
        return status;
    }
  }

  String get outcomeLabel {
    switch (outcome) {
      case 'EO_FAULT':
        return 'Lỗi từ phía BTC';
      case 'WORKER_FAULT':
        return 'Lỗi từ phía Nhân sự';
      case 'PARTIAL':
        return 'Thỏa thuận một phần';
      case 'UPHOLD':
        return 'Giữ nguyên quyết định';
      case 'UNVERIFIED':
        return 'Chưa đủ chứng cứ';
      default:
        return '';
    }
  }
}

class DisputeEvidenceItem {
  final String id;
  final String fileType;
  final String fileUrl;
  final String? label;
  final DateTime? createdAt;

  const DisputeEvidenceItem({
    required this.id,
    required this.fileType,
    required this.fileUrl,
    this.label,
    this.createdAt,
  });

  factory DisputeEvidenceItem.fromJson(Map<String, dynamic> json) {
    return DisputeEvidenceItem(
      id: (json['id'] ?? '').toString(),
      fileType: (json['fileType'] ?? 'IMAGE').toString(),
      fileUrl: (json['fileUrl'] ?? '').toString(),
      label: json['label'] as String?,
      createdAt: json['createdAt'] != null ? DateTime.tryParse(json['createdAt'].toString())?.toLocal() : null,
    );
  }
}

class DisputeEligibleAssignmentItem {
  final String assignmentId;
  final String eventName;
  final String jobType;
  final String counterpartyName;
  final DateTime? startTime;
  final DateTime? endTime;
  final String? status;

  const DisputeEligibleAssignmentItem({
    required this.assignmentId,
    required this.eventName,
    required this.jobType,
    required this.counterpartyName,
    this.startTime,
    this.endTime,
    this.status,
  });

  factory DisputeEligibleAssignmentItem.fromJson(Map<String, dynamic> json) {
    return DisputeEligibleAssignmentItem(
      assignmentId: (json['assignmentId'] ?? '').toString(),
      eventName: (json['eventName'] ?? 'Sự kiện').toString(),
      jobType: (json['jobType'] ?? 'Ca làm').toString(),
      counterpartyName: (json['counterpartyName'] ?? 'Ban tổ chức').toString(),
      startTime: json['startTime'] != null ? DateTime.tryParse(json['startTime'].toString())?.toLocal() : null,
      endTime: json['endTime'] != null ? DateTime.tryParse(json['endTime'].toString())?.toLocal() : null,
      status: json['status']?.toString(),
    );
  }
}

class CreateDisputePayload {
  final String assignmentId;
  final String type;
  final String title;
  final String description;
  final String? evidence;

  const CreateDisputePayload({
    required this.assignmentId,
    this.type = 'PAYMENT',
    required this.title,
    required this.description,
    this.evidence,
  });

  Map<String, dynamic> toJson() {
    return {
      'assignmentId': assignmentId,
      'type': type,
      'title': title,
      'description': description,
      if (evidence != null && evidence!.isNotEmpty) 'evidence': evidence,
    };
  }
}

class DisputeChatMessageItem {
  final String id;
  final String conversationId;
  final String senderId;
  final String senderName;
  final String senderRole;
  final String messageText;
  final String? attachmentUrl;
  final bool isRead;
  final bool isMe;
  final DateTime? createdAt;

  const DisputeChatMessageItem({
    required this.id,
    required this.conversationId,
    required this.senderId,
    required this.senderName,
    required this.senderRole,
    required this.messageText,
    this.attachmentUrl,
    this.isRead = false,
    this.isMe = false,
    this.createdAt,
  });

  factory DisputeChatMessageItem.fromJson(Map<String, dynamic> json) {
    return DisputeChatMessageItem(
      id: (json['id'] ?? '').toString(),
      conversationId: (json['conversationId'] ?? '').toString(),
      senderId: (json['senderId'] ?? '').toString(),
      senderName: (json['senderName'] ?? 'Người dùng').toString(),
      senderRole: (json['senderRole'] ?? '').toString(),
      messageText: (json['messageText'] ?? '').toString(),
      attachmentUrl: json['attachmentUrl'] as String?,
      isRead: json['isRead'] == true,
      isMe: json['isMe'] == true,
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString())?.toLocal()
          : null,
    );
  }
}

class DisputeChatData {
  final String disputeId;
  final String disputeTitle;
  final String disputeStatus;
  final String? conversationId;
  final List<DisputeChatMessageItem> messages;

  const DisputeChatData({
    required this.disputeId,
    required this.disputeTitle,
    required this.disputeStatus,
    this.conversationId,
    this.messages = const [],
  });

  factory DisputeChatData.fromJson(Map<String, dynamic> json) {
    final workerConv = json['workerConversation'] as Map<String, dynamic>?;
    final rawMessages = (workerConv?['messages'] is List)
        ? (workerConv!['messages'] as List)
            .whereType<Map<String, dynamic>>()
            .map(DisputeChatMessageItem.fromJson)
            .toList()
        : <DisputeChatMessageItem>[];

    return DisputeChatData(
      disputeId: (json['disputeId'] ?? '').toString(),
      disputeTitle: (json['disputeTitle'] ?? '').toString(),
      disputeStatus: (json['disputeStatus'] ?? 'OPEN').toString(),
      conversationId: workerConv?['conversationId']?.toString(),
      messages: rawMessages,
    );
  }
}
