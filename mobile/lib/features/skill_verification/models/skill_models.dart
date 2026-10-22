class SkillModel {
  final String skillId;
  final String name;
  final String? category;
  final String? description;

  const SkillModel({
    required this.skillId,
    required this.name,
    this.category,
    this.description,
  });

  factory SkillModel.fromJson(Map<String, dynamic> json) {
    return SkillModel(
      skillId: json['skillId'] as String? ?? json['id'] as String? ?? '',
      name: json['name'] as String? ?? '',
      category: json['category'] as String?,
      description: json['description'] as String?,
    );
  }
}

class ExtractedSkillItem {
  final String skillName;
  final String taxonomyRole;
  final double confidenceScore;
  final String evidenceSnippet;

  const ExtractedSkillItem({
    required this.skillName,
    required this.taxonomyRole,
    required this.confidenceScore,
    required this.evidenceSnippet,
  });

  factory ExtractedSkillItem.fromJson(Map<String, dynamic> json) {
    return ExtractedSkillItem(
      skillName: json['skillName'] as String? ?? '',
      taxonomyRole: json['taxonomyRole'] as String? ?? '',
      confidenceScore: (json['confidenceScore'] as num?)?.toDouble() ?? 0.0,
      evidenceSnippet: json['evidenceSnippet'] as String? ?? '',
    );
  }
}

class SkillVerificationResult {
  final String id;
  final String workerId;
  final String? skillId;
  final String skillName;
  final String jobType;
  final String? recipientName;
  final String? identityNumber;
  final String? institution;
  final String? issueDate;
  final bool hasSealOrSignature;
  final double overallConfidence;
  final String routingDecision;
  final String routingReason;
  final String status;
  final int spAwarded;
  final int currentTotalSp;
  final String currentTier;
  final String? createdAt;
  final List<ExtractedSkillItem> extractedSkills;

  const SkillVerificationResult({
    required this.id,
    required this.workerId,
    this.skillId,
    required this.skillName,
    required this.jobType,
    this.recipientName,
    this.identityNumber,
    this.institution,
    this.issueDate,
    required this.hasSealOrSignature,
    required this.overallConfidence,
    required this.routingDecision,
    required this.routingReason,
    required this.status,
    required this.spAwarded,
    required this.currentTotalSp,
    required this.currentTier,
    this.createdAt,
    required this.extractedSkills,
  });

  factory SkillVerificationResult.fromJson(Map<String, dynamic> json) {
    var rawSkills = json['extractedSkills'] as List<dynamic>? ?? [];
    List<ExtractedSkillItem> skills = rawSkills
        .map((e) => ExtractedSkillItem.fromJson(e as Map<String, dynamic>))
        .toList();

    return SkillVerificationResult(
      id: json['id'] as String? ?? '',
      workerId: json['workerId'] as String? ?? '',
      skillId: json['skillId'] as String?,
      skillName: json['skillName'] as String? ?? json['jobType'] as String? ?? '',
      jobType: json['jobType'] as String? ?? '',
      recipientName: json['recipientName'] as String?,
      identityNumber: json['identityNumber'] as String?,
      institution: json['institution'] as String?,
      issueDate: json['issueDate'] as String?,
      hasSealOrSignature: json['hasSealOrSignature'] as bool? ?? false,
      overallConfidence: (json['overallConfidence'] as num?)?.toDouble() ?? 0.0,
      routingDecision: json['routingDecision'] as String? ?? 'ADMIN_REVIEW',
      routingReason: json['routingReason'] as String? ?? '',
      status: json['status'] as String? ?? 'PENDING_REVIEW',
      spAwarded: json['spAwarded'] as int? ?? 0,
      currentTotalSp: json['currentTotalSp'] as int? ?? 0,
      currentTier: json['currentTier'] as String? ?? 'STARTER',
      createdAt: json['createdAt'] as String?,
      extractedSkills: skills,
    );
  }
}

class WorkerSkillPointModel {
  final String id;
  final String jobType;
  final int spVerify;
  final int spExperience;
  final int spRating;
  final int totalSp;
  final String tier;
  final int totalShiftsCompleted;
  final double avgRating;

  const WorkerSkillPointModel({
    required this.id,
    required this.jobType,
    required this.spVerify,
    required this.spExperience,
    required this.spRating,
    required this.totalSp,
    required this.tier,
    required this.totalShiftsCompleted,
    required this.avgRating,
  });

  factory WorkerSkillPointModel.fromJson(Map<String, dynamic> json) {
    return WorkerSkillPointModel(
      id: json['id'] as String? ?? '',
      jobType: json['jobType'] as String? ?? '',
      spVerify: json['spVerify'] as int? ?? 0,
      spExperience: json['spExperience'] as int? ?? 0,
      spRating: json['spRating'] as int? ?? 0,
      totalSp: json['totalSp'] as int? ?? 0,
      tier: json['tier'] as String? ?? 'STARTER',
      totalShiftsCompleted: json['totalShiftsCompleted'] as int? ?? 0,
      avgRating: (json['avgRating'] as num?)?.toDouble() ?? 0.0,
    );
  }
}

class WorkerSkillItem {
  final String id;
  final String skillId;
  final String skillName;
  final String? category;
  final String proficiency;
  final String verificationTier;
  final int spVerifyAwarded;
  final String? verifiedAt;
  final String? expiresAt;
  final bool isExpired;

  const WorkerSkillItem({
    required this.id,
    required this.skillId,
    required this.skillName,
    this.category,
    required this.proficiency,
    required this.verificationTier,
    required this.spVerifyAwarded,
    this.verifiedAt,
    this.expiresAt,
    this.isExpired = false,
  });

  factory WorkerSkillItem.fromJson(Map<String, dynamic> json) {
    return WorkerSkillItem(
      id: json['id'] as String? ?? '',
      skillId: json['skillId'] as String? ?? '',
      skillName: json['skillName'] as String? ?? '',
      category: json['category'] as String?,
      proficiency: json['proficiency'] as String? ?? 'BEGINNER',
      verificationTier: json['verificationTier'] as String? ?? 'SELF_DECLARED',
      spVerifyAwarded: json['spVerifyAwarded'] as int? ?? 0,
      verifiedAt: json['verifiedAt'] as String?,
      expiresAt: json['expiresAt'] as String?,
      isExpired: json['isExpired'] as bool? ?? false,
    );
  }

  bool get isTier1SelfDeclared => verificationTier == 'SELF_DECLARED';
  bool get isTier2AiVerified => verificationTier == 'AI_DOCUMENT';
  bool get isTier3PlatformProven => verificationTier == 'PLATFORM_PROVEN';

  String get tierBadgeLabel {
    switch (verificationTier) {
      case 'PLATFORM_PROVEN':
        return 'Platform-Proven ★';
      case 'AI_DOCUMENT':
        return 'AI Verified ✓';
      case 'SELF_DECLARED':
      default:
        return 'Tự khai báo';
    }
  }

  String get proficiencyLabel {
    switch (proficiency.toUpperCase()) {
      case 'EXPERT':
        return 'Chuyên gia';
      case 'ADVANCED':
        return 'Nâng cao';
      case 'INTERMEDIATE':
        return 'Trung cấp';
      case 'BEGINNER':
      default:
        return 'Cơ bản';
    }
  }
}
