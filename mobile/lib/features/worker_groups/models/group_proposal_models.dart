import 'package:flutter/foundation.dart';

@immutable
class GroupEligibleMemberModel {
  final String workerId;
  final String fullName;
  final bool eligible;
  final String? reasonCode;

  const GroupEligibleMemberModel({
    required this.workerId,
    required this.fullName,
    required this.eligible,
    this.reasonCode,
  });

  factory GroupEligibleMemberModel.fromJson(Map<String, dynamic> json) {
    return GroupEligibleMemberModel(
      workerId: json['workerId']?.toString() ?? '',
      fullName: json['fullName']?.toString() ?? 'Thành viên',
      eligible: json['eligible'] as bool? ?? false,
      reasonCode: json['reasonCode']?.toString(),
    );
  }
}

@immutable
class GroupProposalModel {
  final String proposalId;
  final String? groupId;
  final String? shiftId;
  final String? eventName;
  final String? jobType;
  final DateTime? startTime;
  final DateTime? endTime;
  final double? hourlyWage;
  final int? requiredWorkers;
  final int? remainingWorkers;
  final bool emergency;
  final String status;
  final double? matchScore;
  final DateTime? expiresAt;
  final int? claimableSlots;
  final int? eligibleCount;
  final List<GroupEligibleMemberModel> eligibleMembers;
  final String? source;

  const GroupProposalModel({
    required this.proposalId,
    this.groupId,
    this.shiftId,
    this.eventName,
    this.jobType,
    this.startTime,
    this.endTime,
    this.hourlyWage,
    this.requiredWorkers,
    this.remainingWorkers,
    this.emergency = false,
    this.status = 'PENDING',
    this.matchScore,
    this.expiresAt,
    this.claimableSlots,
    this.eligibleCount,
    this.eligibleMembers = const [],
    this.source,
  });

  factory GroupProposalModel.fromJson(Map<String, dynamic> json) {
    final membersRaw = (json['eligibleMembers'] as List<dynamic>?) ?? [];
    return GroupProposalModel(
      proposalId: json['proposalId']?.toString() ?? '',
      groupId: json['groupId']?.toString(),
      shiftId: json['shiftId']?.toString(),
      eventName: json['eventName']?.toString(),
      jobType: json['jobType']?.toString(),
      startTime: json['startTime'] != null
          ? DateTime.tryParse(json['startTime'].toString())?.toLocal()
          : null,
      endTime: json['endTime'] != null
          ? DateTime.tryParse(json['endTime'].toString())?.toLocal()
          : null,
      hourlyWage: (json['hourlyWage'] as num?)?.toDouble(),
      requiredWorkers: (json['requiredWorkers'] as num?)?.toInt(),
      remainingWorkers: (json['remainingWorkers'] as num?)?.toInt(),
      emergency: json['emergency'] as bool? ?? false,
      status: json['status']?.toString() ?? 'PENDING',
      matchScore: (json['matchScore'] as num?)?.toDouble(),
      expiresAt: json['expiresAt'] != null
          ? DateTime.tryParse(json['expiresAt'].toString())?.toLocal()
          : null,
      claimableSlots: (json['claimableSlots'] as num?)?.toInt(),
      eligibleCount: (json['eligibleCount'] as num?)?.toInt(),
      eligibleMembers: membersRaw
          .whereType<Map<String, dynamic>>()
          .map(GroupEligibleMemberModel.fromJson)
          .toList(),
      source: json['source']?.toString(),
    );
  }
}

/// Map mã lý do (backend MatchingDispatchService) về nhãn tiếng Việt.
String groupEligibilityReasonLabel(String? reasonCode) {
  switch (reasonCode) {
    case 'KYC_UNAPPROVED':
      return 'Chưa xác minh KYC';
    case 'USER_INACTIVE':
      return 'Tài khoản đang bị vô hiệu';
    case 'LOW_RELIABILITY':
      return 'Điểm uy tín không đạt yêu cầu';
    case 'LOW_EXPERIENCE':
      return 'Thiếu kinh nghiệm ca tương đương';
    case 'MISSING_SKILLS':
      return 'Thiếu kỹ năng yêu cầu';
    case 'SHIFT_CONFLICT':
      return 'Đã trùng lịch ca khác';
    case 'WAGE_BELOW_MIN':
      return 'Lương ca dưới mức yêu cầu';
    case 'UNAVAILABLE':
      return 'Không rảnh khung giờ này';
    default:
      return (reasonCode != null && reasonCode.isNotEmpty) ? reasonCode : 'Không đủ điều kiện';
  }
}

/// 'HH:mm' kiểu các card ca hiện có (padLeft 2).
String formatHm(DateTime? dt) => dt == null
    ? '—'
    : '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';

/// Số lương theo kiểu _formatNumber của job_preferences_card (1.200.000).
String formatWage(num? value) {
  if (value == null) return '—';
  return value.round().toString().replaceAllMapped(
        RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
        (m) => '${m[1]}.',
      );
}
