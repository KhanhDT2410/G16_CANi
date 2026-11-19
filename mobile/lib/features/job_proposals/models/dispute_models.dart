class WorkerDisputeItem {
  final String id;
  final String? assignmentId;
  final String? eventTitle;
  final String? shiftTitle;
  final String type; // PAYMENT, WORK_QUALITY, NO_SHOW, CANCELATION
  final String status; // PENDING, IN_REVIEW, RESOLVED, REJECTED, ESCALATED
  final String title;
  final String description;
  final String? evidence;
  final String? resolution;
  final DateTime? createdAt;
  final DateTime? resolvedAt;

  const WorkerDisputeItem({
    required this.id,
    this.assignmentId,
    this.eventTitle,
    this.shiftTitle,
    required this.type,
    required this.status,
    required this.title,
    required this.description,
    this.evidence,
    this.resolution,
    this.createdAt,
    this.resolvedAt,
  });

  factory WorkerDisputeItem.fromJson(Map<String, dynamic> json) {
    return WorkerDisputeItem(
      id: (json['id'] ?? json['disputeId'] ?? '').toString(),
      assignmentId: json['assignmentId']?.toString(),
      eventTitle: json['eventTitle'] as String?,
      shiftTitle: json['shiftTitle'] as String?,
      type: (json['type'] ?? 'PAYMENT').toString(),
      status: (json['status'] ?? 'PENDING').toString(),
      title: (json['title'] ?? 'Khiếu nại tranh chấp').toString(),
      description: (json['description'] ?? '').toString(),
      evidence: json['evidence'] as String?,
      resolution: json['resolution'] as String?,
      createdAt: json['createdAt'] != null ? DateTime.tryParse(json['createdAt'].toString()) : null,
      resolvedAt: json['resolvedAt'] != null ? DateTime.tryParse(json['resolvedAt'].toString()) : null,
    );
  }

  String get typeLabel {
    switch (type) {
      case 'PAYMENT':
        return 'Thù lao & Lương';
      case 'WORK_QUALITY':
        return 'Chất lượng công việc';
      case 'NO_SHOW':
        return 'Vắng mặt / Bỏ ca';
      case 'CANCELATION':
        return 'Hủy ca đột xuất';
      default:
        return 'Tranh chấp khác';
    }
  }

  String get statusLabel {
    switch (status) {
      case 'PENDING':
        return 'Đang chờ xử lý';
      case 'IN_REVIEW':
        return 'CSKH đang thụ lý';
      case 'RESOLVED':
        return 'Đã giải quyết';
      case 'REJECTED':
        return 'Đã từ chối';
      case 'ESCALATED':
        return 'Admin đang phân xử';
      default:
        return status;
    }
  }
}

class CreateDisputePayload {
  final String? assignmentId;
  final String type;
  final String title;
  final String description;
  final String? evidence;

  const CreateDisputePayload({
    this.assignmentId,
    this.type = 'PAYMENT',
    required this.title,
    required this.description,
    this.evidence,
  });

  Map<String, dynamic> toJson() {
    return {
      if (assignmentId != null && assignmentId!.isNotEmpty) 'assignmentId': assignmentId,
      'type': type,
      'title': title,
      'description': description,
      if (evidence != null && evidence!.isNotEmpty) 'evidence': evidence,
    };
  }
}
