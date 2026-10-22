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
    );
  }
}
