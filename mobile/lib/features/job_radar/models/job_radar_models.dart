class NearbyShiftItem {
  final String shiftId;
  final String? eventId;
  final String eventName;
  final String? eoId;
  final String? eoName;
  final bool eoVerified;
  final double? eoRatingAvg;
  final String jobType;
  final String? description;
  final String locationAddress;
  final double locationLat;
  final double locationLng;
  final double hourlyWage;
  final double allowance;
  final DateTime? startTime;
  final DateTime? endTime;
  final DateTime? arrivalTime;
  final List<String> requiredSkillNames;
  final List<String> preferredSkillNames;
  final String? performedSkillName;
  final String? customSkillNotes;
  final double distanceKm;
  final int requiredWorkers;
  final int confirmedWorkers;
  final int remainingWorkers;
  final String? imageUrl;
  final bool isConflicting;
  final String? conflictReason;
  final bool emergency;

  const NearbyShiftItem({
    required this.shiftId,
    this.eventId,
    required this.eventName,
    this.eoId,
    this.eoName,
    this.eoVerified = false,
    this.eoRatingAvg,
    required this.jobType,
    this.description,
    required this.locationAddress,
    required this.locationLat,
    required this.locationLng,
    required this.hourlyWage,
    required this.allowance,
    this.startTime,
    this.endTime,
    this.arrivalTime,
    this.requiredSkillNames = const [],
    this.preferredSkillNames = const [],
    this.performedSkillName,
    this.customSkillNotes,
    required this.distanceKm,
    required this.requiredWorkers,
    required this.confirmedWorkers,
    required this.remainingWorkers,
    this.imageUrl,
    this.isConflicting = false,
    this.conflictReason,
    this.emergency = false,
  });

  factory NearbyShiftItem.fromJson(Map<String, dynamic> json) {
    return NearbyShiftItem(
      shiftId: json['shiftId'] as String? ?? '',
      eventId: json['eventId'] as String?,
      eventName: json['eventName'] as String? ?? 'Sự kiện',
      eoId: json['eoId']?.toString(),
      eoName: json['eoName'] as String?,
      eoVerified: json['eoVerified'] == true,
      eoRatingAvg: (json['eoRatingAvg'] as num?)?.toDouble(),
      jobType: json['jobType'] as String? ?? 'Nhân sự',
      description: json['description'] as String?,
      locationAddress: json['locationAddress'] as String? ?? '',
      locationLat: (json['locationLat'] as num?)?.toDouble() ?? 10.7769,
      locationLng: (json['locationLng'] as num?)?.toDouble() ?? 106.7009,
      hourlyWage: (json['hourlyWage'] as num?)?.toDouble() ?? 0.0,
      allowance: (json['allowance'] as num?)?.toDouble() ?? 0.0,
      startTime: json['startTime'] != null ? DateTime.tryParse(json['startTime'] as String)?.toLocal() : null,
      endTime: json['endTime'] != null ? DateTime.tryParse(json['endTime'] as String)?.toLocal() : null,
      arrivalTime: json['arrivalTime'] != null ? DateTime.tryParse(json['arrivalTime'] as String)?.toLocal() : null,
      requiredSkillNames: (json['requiredSkillNames'] as List<dynamic>? ?? []).cast<String>(),
      preferredSkillNames: (json['preferredSkillNames'] as List<dynamic>? ?? []).cast<String>(),
      performedSkillName: json['performedSkillName'] as String?,
      customSkillNotes: json['customSkillNotes'] as String?,
      distanceKm: (json['distanceKm'] as num?)?.toDouble() ?? 0.0,
      requiredWorkers: (json['requiredWorkers'] as num?)?.toInt() ?? 1,
      confirmedWorkers: (json['confirmedWorkers'] as num?)?.toInt() ?? 0,
      remainingWorkers: (json['remainingWorkers'] as num?)?.toInt() ?? 1,
      imageUrl: json['imageUrl'] as String?,
      isConflicting: json['isConflicting'] as bool? ?? false,
      conflictReason: json['conflictReason'] as String?,
      emergency: json['emergency'] as bool? ?? false,
    );
  }
}
