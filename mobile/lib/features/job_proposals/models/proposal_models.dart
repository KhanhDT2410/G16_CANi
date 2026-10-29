class JobProposalItem {
  final String proposalId;
  final String shiftId;
  final String eventId;
  final String eventName;
  final String venueAddress;
  final String jobType;
  final String description;
  final DateTime startTime;
  final DateTime endTime;
  final double hourlyWage;
  final double allowance;
  final double totalEstimatedPay;
  final double matchScore;
  final String status;

  JobProposalItem({
    required this.proposalId,
    required this.shiftId,
    required this.eventId,
    required this.eventName,
    required this.venueAddress,
    required this.jobType,
    required this.description,
    required this.startTime,
    required this.endTime,
    required this.hourlyWage,
    required this.allowance,
    required this.totalEstimatedPay,
    required this.matchScore,
    required this.status,
  });

  factory JobProposalItem.fromJson(Map<String, dynamic> json) {
    return JobProposalItem(
      proposalId: json['proposalId'] ?? '',
      shiftId: json['shiftId'] ?? '',
      eventId: json['eventId'] ?? '',
      eventName: json['eventName'] ?? 'Sự kiện',
      venueAddress: json['venueAddress'] ?? '',
      jobType: json['jobType'] ?? '',
      description: json['description'] ?? '',
      startTime: json['startTime'] != null ? DateTime.tryParse(json['startTime']) ?? DateTime.now() : DateTime.now(),
      endTime: json['endTime'] != null ? DateTime.tryParse(json['endTime']) ?? DateTime.now().add(const Duration(hours: 4)) : DateTime.now().add(const Duration(hours: 4)),
      hourlyWage: (json['hourlyWage'] is num) ? (json['hourlyWage'] as num).toDouble() : 0.0,
      allowance: (json['allowance'] is num) ? (json['allowance'] as num).toDouble() : 0.0,
      totalEstimatedPay: (json['totalEstimatedPay'] is num) ? (json['totalEstimatedPay'] as num).toDouble() : 0.0,
      matchScore: (json['matchScore'] is num) ? (json['matchScore'] as num).toDouble() : 0.0,
      status: json['status'] ?? 'PENDING',
    );
  }
}

class ShiftAssignmentItem {
  final String assignmentId;
  final String shiftId;
  final String eventId;
  final String eventName;
  final String venueAddress;
  final String jobType;
  final DateTime startTime;
  final DateTime endTime;
  final double hourlyWage;
  final double totalPay;
  final String status;
  final String paymentStatus; // NOT_DECLARED, DECLARED_PENDING_WORKER, CONFIRMED_FULL, CONFIRMED_PARTIAL, DISPUTED
  final double declaredAmount;
  final String? paymentMethod;
  final double? venueLat;
  final double? venueLng;
  final String? attendanceStatus;
  final DateTime? checkinTime;
  final DateTime? checkoutTime;
  final double? hoursWorked;

  ShiftAssignmentItem({
    required this.assignmentId,
    required this.shiftId,
    required this.eventId,
    required this.eventName,
    required this.venueAddress,
    required this.jobType,
    required this.startTime,
    required this.endTime,
    required this.hourlyWage,
    required this.totalPay,
    required this.status,
    required this.paymentStatus,
    required this.declaredAmount,
    this.paymentMethod,
    this.venueLat,
    this.venueLng,
    this.attendanceStatus,
    this.checkinTime,
    this.checkoutTime,
    this.hoursWorked,
  });

  factory ShiftAssignmentItem.fromJson(Map<String, dynamic> json) {
    return ShiftAssignmentItem(
      assignmentId: json['assignmentId'] ?? '',
      shiftId: json['shiftId'] ?? '',
      eventId: json['eventId'] ?? '',
      eventName: json['eventName'] ?? 'Sự kiện',
      venueAddress: json['venueAddress'] ?? '',
      jobType: json['jobType'] ?? '',
      startTime: json['startTime'] != null ? DateTime.tryParse(json['startTime']) ?? DateTime.now() : DateTime.now(),
      endTime: json['endTime'] != null ? DateTime.tryParse(json['endTime']) ?? DateTime.now().add(const Duration(hours: 4)) : DateTime.now().add(const Duration(hours: 4)),
      hourlyWage: (json['hourlyWage'] is num) ? (json['hourlyWage'] as num).toDouble() : 0.0,
      totalPay: (json['totalPay'] is num) ? (json['totalPay'] as num).toDouble() : 0.0,
      status: json['status'] ?? 'CONFIRMED',
      paymentStatus: json['paymentStatus'] ?? 'NOT_DECLARED',
      declaredAmount: (json['declaredAmount'] is num) ? (json['declaredAmount'] as num).toDouble() : 0.0,
      paymentMethod: json['paymentMethod'],
      venueLat: (json['venueLat'] is num) ? (json['venueLat'] as num).toDouble() : null,
      venueLng: (json['venueLng'] is num) ? (json['venueLng'] as num).toDouble() : null,
      attendanceStatus: json['attendanceStatus'],
      checkinTime: json['checkinTime'] != null ? DateTime.tryParse(json['checkinTime']) : null,
      checkoutTime: json['checkoutTime'] != null ? DateTime.tryParse(json['checkoutTime']) : null,
      hoursWorked: (json['hoursWorked'] is num) ? (json['hoursWorked'] as num).toDouble() : null,
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
    required this.message,
  });

  factory AttendanceCheckResult.fromJson(Map<String, dynamic> json) {
    return AttendanceCheckResult(
      assignmentId: json['assignmentId'] ?? '',
      attendanceId: json['attendanceId'],
      attendanceStatus: json['attendanceStatus'],
      assignmentStatus: json['assignmentStatus'],
      checkinTime: json['checkinTime'] != null ? DateTime.tryParse(json['checkinTime']) : null,
      checkoutTime: json['checkoutTime'] != null ? DateTime.tryParse(json['checkoutTime']) : null,
      hoursWorked: (json['hoursWorked'] is num) ? (json['hoursWorked'] as num).toDouble() : null,
      isOnTime: json['isOnTime'] ?? true,
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

  const WorkerPreferencesModel({
    this.baseAddress = 'TP. Hồ Chí Minh',
    this.baseLat,
    this.baseLng,
    this.minWage = 35000,
    this.maxDistanceKm = 15.0,
  });

  factory WorkerPreferencesModel.fromJson(Map<String, dynamic> json) {
    return WorkerPreferencesModel(
      baseAddress: json['baseAddress'] ?? 'TP. Hồ Chí Minh',
      baseLat: (json['baseLat'] is num) ? (json['baseLat'] as num).toDouble() : null,
      baseLng: (json['baseLng'] is num) ? (json['baseLng'] as num).toDouble() : null,
      minWage: (json['minWage'] is num) ? (json['minWage'] as num).toDouble() : 35000,
      maxDistanceKm: (json['maxDistanceKm'] is num) ? (json['maxDistanceKm'] as num).toDouble() : 15.0,
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
  }) {
    return WorkerPreferencesModel(
      baseAddress: baseAddress ?? this.baseAddress,
      baseLat: baseLat ?? this.baseLat,
      baseLng: baseLng ?? this.baseLng,
      minWage: minWage ?? this.minWage,
      maxDistanceKm: maxDistanceKm ?? this.maxDistanceKm,
    );
  }
}

class ShiftChecklistItem {
  final String id;
  final String title;
  final bool isCompleted;
  final DateTime? completedAt;

  const ShiftChecklistItem({
    required this.id,
    required this.title,
    this.isCompleted = false,
    this.completedAt,
  });

  factory ShiftChecklistItem.fromJson(Map<String, dynamic> json) {
    return ShiftChecklistItem(
      id: json['id']?.toString() ?? '',
      title: json['title'] ?? '',
      isCompleted: json['isCompleted'] ?? false,
      completedAt: json['completedAt'] != null ? DateTime.tryParse(json['completedAt'].toString()) : null,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'title': title,
        'isCompleted': isCompleted,
        if (completedAt != null) 'completedAt': completedAt!.toUtc().toIso8601String(),
      };

  ShiftChecklistItem copyWith({
    String? id,
    String? title,
    bool? isCompleted,
    DateTime? completedAt,
  }) {
    return ShiftChecklistItem(
      id: id ?? this.id,
      title: title ?? this.title,
      isCompleted: isCompleted ?? this.isCompleted,
      completedAt: completedAt ?? this.completedAt,
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

