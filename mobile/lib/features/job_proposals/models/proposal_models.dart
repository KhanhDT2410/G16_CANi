class JobProposalItem {
  final String proposalId;
  final String shiftId;
  final String eventId;
  final String eventName;
  final String? eoId;
  final String? eoName;
  final bool eoVerified;
  final double? eoRatingAvg;
  final String venueAddress;
  final String jobType;
  final String description;
  final DateTime startTime;
  final DateTime endTime;
  final DateTime? arrivalTime;
  final DateTime? expiresAt;
  final List<String> requiredSkillNames;
  final double hourlyWage;
  final double allowance;
  final double totalEstimatedPay;
  final double matchScore;
  final String status;
  final String? imageUrl;

  JobProposalItem({
    required this.proposalId,
    required this.shiftId,
    required this.eventId,
    required this.eventName,
    this.eoId,
    this.eoName,
    this.eoVerified = false,
    this.eoRatingAvg,
    required this.venueAddress,
    required this.jobType,
    required this.description,
    required this.startTime,
    required this.endTime,
    this.arrivalTime,
    this.expiresAt,
    this.requiredSkillNames = const [],
    required this.hourlyWage,
    required this.allowance,
    required this.totalEstimatedPay,
    required this.matchScore,
    required this.status,
    this.imageUrl,
  });

  factory JobProposalItem.fromJson(Map<String, dynamic> json) {
    return JobProposalItem(
      proposalId: json['proposalId'] ?? '',
      shiftId: json['shiftId'] ?? '',
      eventId: json['eventId'] ?? '',
      eventName: json['eventName'] ?? 'Sự kiện',
      eoId: json['eoId']?.toString(),
      eoName: json['eoName'] as String?,
      eoVerified: json['eoVerified'] == true,
      eoRatingAvg: (json['eoRatingAvg'] is num) ? (json['eoRatingAvg'] as num).toDouble() : null,
      venueAddress: json['venueAddress'] ?? '',
      jobType: json['jobType'] ?? '',
      description: json['description'] ?? '',
      startTime: json['startTime'] != null ? DateTime.tryParse(json['startTime'])?.toLocal() ?? DateTime.now() : DateTime.now(),
      endTime: json['endTime'] != null ? DateTime.tryParse(json['endTime'])?.toLocal() ?? DateTime.now().add(const Duration(hours: 4)) : DateTime.now().add(const Duration(hours: 4)),
      arrivalTime: json['arrivalTime'] != null ? DateTime.tryParse(json['arrivalTime'] as String)?.toLocal() : null,
      expiresAt: json['expiresAt'] != null ? DateTime.tryParse(json['expiresAt'].toString())?.toLocal() : null,
      requiredSkillNames: (json['requiredSkillNames'] as List<dynamic>? ?? []).cast<String>(),
      hourlyWage: (json['hourlyWage'] is num) ? (json['hourlyWage'] as num).toDouble() : 0.0,
      allowance: (json['allowance'] is num) ? (json['allowance'] as num).toDouble() : 0.0,
      totalEstimatedPay: (json['totalEstimatedPay'] is num) ? (json['totalEstimatedPay'] as num).toDouble() : 0.0,
      matchScore: (json['matchScore'] is num) ? (json['matchScore'] as num).toDouble() : 0.0,
      status: json['status'] ?? 'PENDING',
      imageUrl: json['imageUrl'] as String?,
    );
  }
}

class ShiftAssignmentItem {
  final String assignmentId;
  final String shiftId;
  final String eventId;
  final String eventName;
  final String? eoId;
  final String? eoName;
  final bool eoVerified;
  final double? eoRatingAvg;
  final String venueAddress;
  final String jobType;
  final DateTime startTime;
  final DateTime endTime;
  final DateTime? arrivalTime;
  final List<String> requiredSkillNames;
  final double hourlyWage;
  final double totalPay;
  final String status;
  final bool eoConfirmed;
  final String paymentStatus; // NOT_DECLARED, DECLARED_PENDING_WORKER, CONFIRMED_FULL, CONFIRMED_PARTIAL, DISPUTED
  final double declaredAmount;
  final String? paymentMethod;
  final double? venueLat;
  final double? venueLng;
  final String? attendanceStatus;
  final DateTime? checkinTime;
  final DateTime? checkoutTime;
  final double? hoursWorked;
  final String? imageUrl;
  final String? proofImageUrl;
  final DateTime? proofSubmittedAt;
  final String? workerConfirmStatus;
  final String? paymentProofUrl;
  final int? eoRating;
  final String? eoReviewComment;
  final DateTime? eoReviewedAt;
  final bool? hasWorkerReviewed;

  ShiftAssignmentItem({
    required this.assignmentId,
    required this.shiftId,
    required this.eventId,
    required this.eventName,
    this.eoId,
    this.eoName,
    this.eoVerified = false,
    this.eoRatingAvg,
    required this.venueAddress,
    required this.jobType,
    required this.startTime,
    required this.endTime,
    this.arrivalTime,
    this.requiredSkillNames = const [],
    required this.hourlyWage,
    required this.totalPay,
    required this.status,
    this.eoConfirmed = false,
    required this.paymentStatus,
    required this.declaredAmount,
    this.paymentMethod,
    this.venueLat,
    this.venueLng,
    this.attendanceStatus,
    this.checkinTime,
    this.checkoutTime,
    this.hoursWorked,
    this.imageUrl,
    this.proofImageUrl,
    this.proofSubmittedAt,
    this.workerConfirmStatus,
    this.paymentProofUrl,
    this.eoRating,
    this.eoReviewComment,
    this.eoReviewedAt,
    this.hasWorkerReviewed,
  });

  factory ShiftAssignmentItem.fromJson(Map<String, dynamic> json) {
    return ShiftAssignmentItem(
      assignmentId: json['assignmentId']?.toString() ?? '',
      shiftId: json['shiftId']?.toString() ?? '',
      eventId: json['eventId']?.toString() ?? '',
      eventName: json['eventName']?.toString() ?? 'Sự kiện',
      eoId: json['eoId']?.toString(),
      eoName: json['eoName'] as String?,
      eoVerified: json['eoVerified'] == true,
      eoRatingAvg: (json['eoRatingAvg'] is num) ? (json['eoRatingAvg'] as num).toDouble() : null,
      venueAddress: json['venueAddress']?.toString() ?? '',
      jobType: json['jobType']?.toString() ?? '',
      startTime: json['startTime'] != null ? DateTime.tryParse(json['startTime'].toString())?.toLocal() ?? DateTime.now() : DateTime.now(),
      endTime: json['endTime'] != null ? DateTime.tryParse(json['endTime'].toString())?.toLocal() ?? DateTime.now().add(const Duration(hours: 4)) : DateTime.now().add(const Duration(hours: 4)),
      arrivalTime: json['arrivalTime'] != null ? DateTime.tryParse(json['arrivalTime'].toString())?.toLocal() : null,
      requiredSkillNames: (json['requiredSkillNames'] is List)
          ? (json['requiredSkillNames'] as List).map((e) => e.toString()).toList()
          : const [],
      hourlyWage: (json['hourlyWage'] is num)
          ? (json['hourlyWage'] as num).toDouble()
          : (double.tryParse(json['hourlyWage']?.toString() ?? '') ?? 0.0),
      totalPay: (json['totalPay'] is num)
          ? (json['totalPay'] as num).toDouble()
          : (double.tryParse(json['totalPay']?.toString() ?? '') ?? 0.0),
      status: json['status']?.toString() ?? 'CONFIRMED',
      eoConfirmed: json['eoConfirmed'] == true,
      paymentStatus: json['paymentStatus']?.toString() ?? 'NOT_DECLARED',
      declaredAmount: (json['declaredAmount'] is num)
          ? (json['declaredAmount'] as num).toDouble()
          : (double.tryParse(json['declaredAmount']?.toString() ?? '') ?? 0.0),
      paymentMethod: json['paymentMethod']?.toString(),
      venueLat: (json['venueLat'] is num)
          ? (json['venueLat'] as num).toDouble()
          : double.tryParse(json['venueLat']?.toString() ?? ''),
      venueLng: (json['venueLng'] is num)
          ? (json['venueLng'] as num).toDouble()
          : double.tryParse(json['venueLng']?.toString() ?? ''),
      attendanceStatus: json['attendanceStatus']?.toString(),
      checkinTime: json['checkinTime'] != null ? DateTime.tryParse(json['checkinTime'].toString())?.toLocal() : null,
      checkoutTime: json['checkoutTime'] != null ? DateTime.tryParse(json['checkoutTime'].toString())?.toLocal() : null,
      hoursWorked: (json['hoursWorked'] is num)
          ? (json['hoursWorked'] as num).toDouble()
          : double.tryParse(json['hoursWorked']?.toString() ?? ''),
      imageUrl: json['imageUrl'] as String?,
      proofImageUrl: json['proofImageUrl'] as String?,
      proofSubmittedAt: json['proofSubmittedAt'] != null ? DateTime.tryParse(json['proofSubmittedAt'].toString())?.toLocal() : null,
      workerConfirmStatus: json['workerConfirmStatus'] as String?,
      paymentProofUrl: json['paymentProofUrl'] as String?,
      eoRating: (json['eoRating'] is num)
          ? (json['eoRating'] as num).toInt()
          : int.tryParse(json['eoRating']?.toString() ?? ''),
      eoReviewComment: json['eoReviewComment'] as String?,
      eoReviewedAt: json['eoReviewedAt'] != null ? DateTime.tryParse(json['eoReviewedAt'].toString())?.toLocal() : null,
      hasWorkerReviewed: json['hasWorkerReviewed'] as bool?,
    );
  }
}

class AttendanceCheckResult {
  final String assignmentId;
  final String? attendanceId;
  final String? attendanceStatus;
  final String? assignmentStatus;
  final DateTime? checkinTime;
  final DateTime? checkoutTime;
  final double? hoursWorked;
  final bool? isOnTime;
  final String? proofImageUrl;
  final String message;

  AttendanceCheckResult({
    required this.assignmentId,
    this.attendanceId,
    this.attendanceStatus,
    this.assignmentStatus,
    this.checkinTime,
    this.checkoutTime,
    this.hoursWorked,
    this.isOnTime,
    this.proofImageUrl,
    required this.message,
  });

  factory AttendanceCheckResult.fromJson(Map<String, dynamic> json) {
    return AttendanceCheckResult(
      assignmentId: json['assignmentId'] ?? '',
      attendanceId: json['attendanceId'],
      attendanceStatus: json['attendanceStatus'],
      assignmentStatus: json['assignmentStatus'],
      checkinTime: json['checkinTime'] != null ? DateTime.tryParse(json['checkinTime'])?.toLocal() : null,
      checkoutTime: json['checkoutTime'] != null ? DateTime.tryParse(json['checkoutTime'])?.toLocal() : null,
      hoursWorked: (json['hoursWorked'] is num) ? (json['hoursWorked'] as num).toDouble() : null,
      isOnTime: json['isOnTime'] ?? true,
      proofImageUrl: json['proofImageUrl'] as String?,
      message: json['message'] ?? 'Thành công',
    );
  }
}

class WorkerAvailabilitySlot {
  final int dayOfWeek; // 0: CN, 1: T2, ..., 6: T7
  final String startTime; // "08:00"
  final String endTime; // "17:00"
  final bool isAvailable;

  const WorkerAvailabilitySlot({
    required this.dayOfWeek,
    required this.startTime,
    required this.endTime,
    this.isAvailable = true,
  });

  factory WorkerAvailabilitySlot.fromJson(Map<String, dynamic> json) {
    String normalizeTime(String? raw, String fallback) {
      if (raw == null || raw.isEmpty) return fallback;
      final parts = raw.split(':');
      if (parts.length >= 2) {
        return '${parts[0].padLeft(2, '0')}:${parts[1].padLeft(2, '0')}';
      }
      return raw;
    }

    return WorkerAvailabilitySlot(
      dayOfWeek: (json['dayOfWeek'] is num) ? (json['dayOfWeek'] as num).toInt() : 1,
      startTime: normalizeTime(json['startTime']?.toString(), '08:00'),
      endTime: normalizeTime(json['endTime']?.toString(), '17:00'),
      isAvailable: json['isAvailable'] ?? true,
    );
  }

  Map<String, dynamic> toJson() => {
        'dayOfWeek': dayOfWeek,
        'startTime': startTime.length == 5 ? '$startTime:00' : startTime,
        'endTime': endTime.length == 5 ? '$endTime:00' : endTime,
        'isAvailable': isAvailable,
      };

  WorkerAvailabilitySlot copyWith({
    int? dayOfWeek,
    String? startTime,
    String? endTime,
    bool? isAvailable,
  }) {
    return WorkerAvailabilitySlot(
      dayOfWeek: dayOfWeek ?? this.dayOfWeek,
      startTime: startTime ?? this.startTime,
      endTime: endTime ?? this.endTime,
      isAvailable: isAvailable ?? this.isAvailable,
    );
  }
}

class WorkerPreferencesModel {
  final String baseAddress;
  final double? baseLat;
  final double? baseLng;
  final double minWage;
  final double maxDistanceKm;
  final bool? isOnline;

  const WorkerPreferencesModel({
    this.baseAddress = 'TP. Hồ Chí Minh',
    this.baseLat,
    this.baseLng,
    this.minWage = 35000,
    this.maxDistanceKm = 15.0,
    this.isOnline,
  });

  factory WorkerPreferencesModel.fromJson(Map<String, dynamic> json) {
    return WorkerPreferencesModel(
      baseAddress: json['baseAddress'] ?? 'TP. Hồ Chí Minh',
      baseLat: (json['baseLat'] is num) ? (json['baseLat'] as num).toDouble() : null,
      baseLng: (json['baseLng'] is num) ? (json['baseLng'] as num).toDouble() : null,
      minWage: (json['minWage'] is num) ? (json['minWage'] as num).toDouble() : 35000,
      maxDistanceKm: (json['maxDistanceKm'] is num) ? (json['maxDistanceKm'] as num).toDouble() : 15.0,
      isOnline: json['isOnline'] is bool ? json['isOnline'] as bool : null,
    );
  }

  Map<String, dynamic> toJson() => {
        'baseAddress': baseAddress,
        if (baseLat != null) 'baseLat': baseLat,
        if (baseLng != null) 'baseLng': baseLng,
        'minWage': minWage,
        'maxDistanceKm': maxDistanceKm,
      };

  WorkerPreferencesModel copyWith({
    String? baseAddress,
    double? baseLat,
    double? baseLng,
    double? minWage,
    double? maxDistanceKm,
    bool? isOnline,
  }) {
    return WorkerPreferencesModel(
      baseAddress: baseAddress ?? this.baseAddress,
      baseLat: baseLat ?? this.baseLat,
      baseLng: baseLng ?? this.baseLng,
      minWage: minWage ?? this.minWage,
      maxDistanceKm: maxDistanceKm ?? this.maxDistanceKm,
      isOnline: isOnline ?? this.isOnline,
    );
  }
}

class ShiftChecklistItem {
  final String id;
  final String title;
  final String? instruction;
  final bool isCompleted;
  final DateTime? completedAt;
  final String? evidenceImageUrl;
  final DateTime? evidenceSubmittedAt;
  final bool isRequiredEvidence;

  const ShiftChecklistItem({
    required this.id,
    required this.title,
    this.instruction,
    this.isCompleted = false,
    this.completedAt,
    this.evidenceImageUrl,
    this.evidenceSubmittedAt,
    this.isRequiredEvidence = false,
  });

  factory ShiftChecklistItem.fromJson(Map<String, dynamic> json) {
    return ShiftChecklistItem(
      id: json['id']?.toString() ?? '',
      title: json['title'] ?? '',
      instruction: json['instruction'],
      isCompleted: json['isCompleted'] ?? false,
      completedAt: json['completedAt'] != null ? DateTime.tryParse(json['completedAt'].toString())?.toLocal() : null,
      evidenceImageUrl: json['evidenceImageUrl'],
      evidenceSubmittedAt: json['evidenceSubmittedAt'] != null ? DateTime.tryParse(json['evidenceSubmittedAt'].toString())?.toLocal() : null,
      isRequiredEvidence: json['isRequiredEvidence'] ?? json['requiresEvidence'] ?? false,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'title': title,
        if (instruction != null) 'instruction': instruction,
        'isCompleted': isCompleted,
        if (completedAt != null) 'completedAt': completedAt!.toUtc().toIso8601String(),
        if (evidenceImageUrl != null) 'evidenceImageUrl': evidenceImageUrl,
        if (evidenceSubmittedAt != null) 'evidenceSubmittedAt': evidenceSubmittedAt!.toUtc().toIso8601String(),
        'isRequiredEvidence': isRequiredEvidence,
      };

  ShiftChecklistItem copyWith({
    String? id,
    String? title,
    String? instruction,
    bool? isCompleted,
    DateTime? completedAt,
    String? evidenceImageUrl,
    DateTime? evidenceSubmittedAt,
    bool? isRequiredEvidence,
  }) {
    return ShiftChecklistItem(
      id: id ?? this.id,
      title: title ?? this.title,
      instruction: instruction ?? this.instruction,
      isCompleted: isCompleted ?? this.isCompleted,
      completedAt: completedAt ?? this.completedAt,
      evidenceImageUrl: evidenceImageUrl ?? this.evidenceImageUrl,
      evidenceSubmittedAt: evidenceSubmittedAt ?? this.evidenceSubmittedAt,
      isRequiredEvidence: isRequiredEvidence ?? this.isRequiredEvidence,
    );
  }
}

class WorkerIncomeSummary {
  final double totalEarnings;
  final double pendingEarnings;
  final int completedShiftsCount;
  final double totalHoursWorked;
  final double avgRating;
  final int reliabilityScore;

  const WorkerIncomeSummary({
    this.totalEarnings = 0,
    this.pendingEarnings = 0,
    this.completedShiftsCount = 0,
    this.totalHoursWorked = 0,
    this.avgRating = 5.0,
    this.reliabilityScore = 100,
  });

  factory WorkerIncomeSummary.fromJson(Map<String, dynamic> json) {
    return WorkerIncomeSummary(
      totalEarnings: (json['totalEarnings'] is num) ? (json['totalEarnings'] as num).toDouble() : 0,
      pendingEarnings: (json['pendingEarnings'] is num) ? (json['pendingEarnings'] as num).toDouble() : 0,
      completedShiftsCount: (json['completedShiftsCount'] is num) ? (json['completedShiftsCount'] as num).toInt() : 0,
      totalHoursWorked: (json['totalHoursWorked'] is num) ? (json['totalHoursWorked'] as num).toDouble() : 0,
      avgRating: (json['avgRating'] is num) ? (json['avgRating'] as num).toDouble() : 5.0,
      reliabilityScore: (json['reliabilityScore'] is num) ? (json['reliabilityScore'] as num).toInt() : 100,
    );
  }
}

class ShiftChatMessageItem {
  final String id;
  final String senderId;
  final String senderRole;
  final String senderName;
  final String messageText;
  final String? attachmentUrl;
  final String? shiftId;
  final String? shiftCode;
  final String? shiftName;
  final DateTime createdAt;

  const ShiftChatMessageItem({
    required this.id,
    required this.senderId,
    required this.senderRole,
    required this.senderName,
    required this.messageText,
    this.attachmentUrl,
    this.shiftId,
    this.shiftCode,
    this.shiftName,
    required this.createdAt,
  });

  factory ShiftChatMessageItem.fromJson(Map<String, dynamic> json) {
    return ShiftChatMessageItem(
      id: json['id']?.toString() ?? '',
      senderId: json['senderId']?.toString() ?? '',
      senderRole: json['senderRole']?.toString() ?? 'WORKER',
      senderName: json['senderName']?.toString() ?? 'Người dùng',
      messageText: json['messageText']?.toString() ?? '',
      attachmentUrl: json['attachmentUrl']?.toString(),
      shiftId: json['shiftId']?.toString(),
      shiftCode: json['shiftCode']?.toString(),
      shiftName: json['shiftName']?.toString(),
      createdAt: json['createdAt'] != null
          ? (DateTime.tryParse(json['createdAt'].toString())?.toLocal() ?? DateTime.now())
          : DateTime.now(),
    );
  }
}

class EoPublicProfileData {
  final String eoId;
  final String? userId;
  final String companyName;
  final String? representativeName;
  final String? avatarUrl;
  final String? address;
  final String? phone;
  final String? email;
  final String? description;
  final bool isVerified;
  final DateTime? verifiedAt;
  final double ratingAvg;
  final int ratingCount;
  final int totalEventsCompleted;
  final int totalEventsPublished;
  final int totalEventsCreated;
  final int totalConfirmedWorkers;
  final List<EoPublicReviewItem> reviews;

  const EoPublicProfileData({
    required this.eoId,
    this.userId,
    required this.companyName,
    this.representativeName,
    this.avatarUrl,
    this.address,
    this.phone,
    this.email,
    this.description,
    this.isVerified = false,
    this.verifiedAt,
    this.ratingAvg = 0.0,
    this.ratingCount = 0,
    this.totalEventsCompleted = 0,
    this.totalEventsPublished = 0,
    this.totalEventsCreated = 0,
    this.totalConfirmedWorkers = 0,
    this.reviews = const [],
  });

  bool get hasValidRating => ratingCount > 0 && ratingAvg >= 1.0 && ratingAvg <= 5.0;

  factory EoPublicProfileData.fromJson(Map<String, dynamic> json) {
    final rawReviews = (json['reviews'] is List)
        ? (json['reviews'] as List)
            .whereType<Map<String, dynamic>>()
            .map(EoPublicReviewItem.fromJson)
            .toList()
        : const <EoPublicReviewItem>[];

    final parsedRatingCount = _parseNonNegativeInt(json['ratingCount']);
    final rawAvg = _parseDouble(json['ratingAvg']);
    final validRatingAvg = (parsedRatingCount > 0 && rawAvg != null && rawAvg >= 1.0 && rawAvg <= 5.0)
        ? rawAvg
        : 0.0;

    return EoPublicProfileData(
      eoId: json['eoId']?.toString() ?? '',
      userId: json['userId']?.toString(),
      companyName: (json['companyName'] as String?)?.trim().isNotEmpty == true
          ? (json['companyName'] as String).trim()
          : 'Ban tổ chức sự kiện',
      representativeName: json['representativeName'] as String?,
      avatarUrl: json['avatarUrl'] as String?,
      address: json['address'] as String?,
      phone: json['phone'] as String?,
      email: json['email'] as String?,
      description: json['description'] as String?,
      isVerified: json['isVerified'] == true,
      verifiedAt: json['verifiedAt'] != null ? DateTime.tryParse(json['verifiedAt'].toString())?.toLocal() : null,
      ratingAvg: validRatingAvg,
      ratingCount: validRatingAvg > 0.0 ? parsedRatingCount : 0,
      totalEventsCompleted: _parseNonNegativeInt(json['totalEventsCompleted']),
      totalEventsPublished: _parseNonNegativeInt(json['totalEventsPublished']),
      totalEventsCreated: _parseNonNegativeInt(json['totalEventsCreated']),
      totalConfirmedWorkers: _parseNonNegativeInt(json['totalConfirmedWorkers']),
      reviews: rawReviews,
    );
  }

  static int _parseNonNegativeInt(dynamic value) {
    if (value is num) {
      final v = value.toInt();
      return v >= 0 ? v : 0;
    }
    if (value is String) {
      final v = int.tryParse(value.trim());
      if (v != null && v >= 0) return v;
    }
    return 0;
  }

  static double? _parseDouble(dynamic value) {
    if (value is num) return value.toDouble();
    if (value is String) return double.tryParse(value.trim());
    return null;
  }
}

class EoPublicReviewItem {
  final String reviewId;
  final String? reviewerUserId;
  final String? reviewerWorkerId;
  final String reviewerWorkerName;
  final String? eventName;
  final String? shiftName;
  final int? rating;
  final String? comment;
  final List<String> tags;
  final DateTime? createdAt;

  const EoPublicReviewItem({
    required this.reviewId,
    this.reviewerUserId,
    this.reviewerWorkerId,
    required this.reviewerWorkerName,
    this.eventName,
    this.shiftName,
    this.rating,
    this.comment,
    this.tags = const [],
    this.createdAt,
  });

  bool get hasValidRating => rating != null && rating! >= 1 && rating! <= 5;

  factory EoPublicReviewItem.fromJson(Map<String, dynamic> json) {
    return EoPublicReviewItem(
      reviewId: json['reviewId']?.toString() ?? '',
      reviewerUserId: json['reviewerUserId']?.toString(),
      reviewerWorkerId: json['reviewerWorkerId']?.toString(),
      reviewerWorkerName: (json['reviewerWorkerName'] as String?)?.trim().isNotEmpty == true
          ? (json['reviewerWorkerName'] as String).trim()
          : 'Nhân sự CANi',
      eventName: json['eventName'] as String?,
      shiftName: json['shiftName'] as String?,
      rating: _parseValidStarRating(json['rating']),
      comment: json['comment'] as String?,
      tags: (json['tags'] is List) ? (json['tags'] as List).map((e) => e.toString()).toList() : const [],
      createdAt: json['createdAt'] != null ? DateTime.tryParse(json['createdAt'].toString())?.toLocal() : null,
    );
  }

  static int? _parseValidStarRating(dynamic raw) {
    int? parsed;
    if (raw is int) {
      parsed = raw;
    } else if (raw is num) {
      if (raw == raw.roundToDouble()) {
        parsed = raw.toInt();
      }
    } else if (raw is String) {
      parsed = int.tryParse(raw.trim());
    }
    if (parsed != null && parsed >= 1 && parsed <= 5) {
      return parsed;
    }
    return null;
  }
}

