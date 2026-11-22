import 'package:flutter/foundation.dart';

@immutable
class WorkerGroupModel {
  final String groupId;
  final String name;
  final String? description;
  final String? skillFocus;
  final String? leaderWorkerId;
  final String? leaderName;
  final String? inviteCode;
  final int minSize;
  final int maxSize;
  final int memberCount;
  final double avgRating;
  final int reliabilityScore;
  final bool isActive;
  final DateTime? createdAt;

  const WorkerGroupModel({
    required this.groupId,
    required this.name,
    this.description,
    this.skillFocus,
    this.leaderWorkerId,
    this.leaderName,
    this.inviteCode,
    this.minSize = 5,
    this.maxSize = 20,
    this.memberCount = 1,
    this.avgRating = 5.0,
    this.reliabilityScore = 100,
    this.isActive = true,
    this.createdAt,
  });

  factory WorkerGroupModel.fromJson(Map<String, dynamic> json) {
    return WorkerGroupModel(
      groupId: json['groupId']?.toString() ?? '',
      name: json['name']?.toString() ?? 'Đội của tôi',
      description: json['description']?.toString(),
      skillFocus: json['skillFocus']?.toString(),
      leaderWorkerId: json['leaderWorkerId']?.toString(),
      leaderName: json['leaderName']?.toString(),
      inviteCode: json['inviteCode']?.toString(),
      minSize: (json['minSize'] as num?)?.toInt() ?? 5,
      maxSize: (json['maxSize'] as num?)?.toInt() ?? 20,
      memberCount: (json['memberCount'] as num?)?.toInt() ?? 1,
      avgRating: (json['avgRating'] as num?)?.toDouble() ?? 5.0,
      reliabilityScore: (json['reliabilityScore'] as num?)?.toInt() ?? 100,
      isActive: json['isActive'] as bool? ?? true,
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString())
          : null,
    );
  }

  Map<String, dynamic> toJson() => {
        'groupId': groupId,
        'name': name,
        'description': description,
        'skillFocus': skillFocus,
        'minSize': minSize,
        'maxSize': maxSize,
      };
}

@immutable
class WorkerGroupMemberModel {
  final String membershipId;
  final String workerId;
  final String? userId;
  final String fullName;
  final String? phone;
  final String? avatarUrl;
  final String roleInGroup; // LEADER, DEPUTY, MEMBER
  final String? roleTitle;
  final String status; // ACTIVE, INVITED, LEFT
  final int reliabilityScore;
  final double avgRating;
  final int totalShiftsCompleted;
  final DateTime? joinedAt;

  const WorkerGroupMemberModel({
    required this.membershipId,
    required this.workerId,
    this.userId,
    required this.fullName,
    this.phone,
    this.avatarUrl,
    required this.roleInGroup,
    this.roleTitle,
    required this.status,
    this.reliabilityScore = 100,
    this.avgRating = 5.0,
    this.totalShiftsCompleted = 0,
    this.joinedAt,
  });

  factory WorkerGroupMemberModel.fromJson(Map<String, dynamic> json) {
    return WorkerGroupMemberModel(
      membershipId: json['membershipId']?.toString() ?? '',
      workerId: json['workerId']?.toString() ?? '',
      userId: json['userId']?.toString(),
      fullName: json['fullName']?.toString() ?? 'Thành viên',
      phone: json['phone']?.toString(),
      avatarUrl: json['avatarUrl']?.toString(),
      roleInGroup: json['roleInGroup']?.toString() ?? 'MEMBER',
      roleTitle: json['roleTitle']?.toString(),
      status: json['status']?.toString() ?? 'ACTIVE',
      reliabilityScore: (json['reliabilityScore'] as num?)?.toInt() ?? 100,
      avgRating: (json['avgRating'] as num?)?.toDouble() ?? 5.0,
      totalShiftsCompleted:
          (json['totalShiftsCompleted'] as num?)?.toInt() ?? 0,
      joinedAt: json['joinedAt'] != null
          ? DateTime.tryParse(json['joinedAt'].toString())
          : null,
    );
  }

  bool get isLeader => roleInGroup.toUpperCase() == 'LEADER';
}

@immutable
class CreateWorkerGroupModel {
  final String name;
  final String? description;
  final String? skillFocus;
  final int minSize;
  final int maxSize;

  const CreateWorkerGroupModel({
    required this.name,
    this.description,
    this.skillFocus,
    this.minSize = 5,
    this.maxSize = 20,
  });

  Map<String, dynamic> toJson() => {
        'name': name,
        'description': description,
        'skillFocus': skillFocus,
        'minSize': minSize,
        'maxSize': maxSize,
      };
}
