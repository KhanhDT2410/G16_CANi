class BlockedUserModel {
  final String id;
  final String blockedUserId;
  final String blockedUserName;
  final String? blockedUserEmail;
  final String? reason;
  final String? createdAt;

  const BlockedUserModel({
    required this.id,
    required this.blockedUserId,
    required this.blockedUserName,
    this.blockedUserEmail,
    this.reason,
    this.createdAt,
  });

  factory BlockedUserModel.fromJson(Map<String, dynamic> json) {
    return BlockedUserModel(
      id: (json['id'] ?? json['blockId'] ?? '').toString(),
      blockedUserId: (json['blockedUserId'] ?? json['blockedId'] ?? '').toString(),
      blockedUserName: (json['blockedUserName'] ?? json['blockedName'] ?? 'Người dùng').toString(),
      blockedUserEmail: json['blockedUserEmail'] as String?,
      reason: json['reason'] as String?,
      createdAt: json['createdAt'] as String?,
    );
  }
}
