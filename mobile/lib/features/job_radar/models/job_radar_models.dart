class NearbyShiftItem {
  final String shiftId;
  final String? eventId;
  final String eventName;
  final String jobType;
  final String? description;
  final String locationAddress;
  final double locationLat;
  final double locationLng;
  final double hourlyWage;
  final double allowance;
  final DateTime? startTime;
  final DateTime? endTime;
  final double distanceKm;
  final int requiredWorkers;
  final int confirmedWorkers;
  final int remainingWorkers;

  const NearbyShiftItem({
    required this.shiftId,
    this.eventId,
    required this.eventName,
    required this.jobType,
    this.description,
    required this.locationAddress,
    required this.locationLat,
    required this.locationLng,
    required this.hourlyWage,
    required this.allowance,
    this.startTime,
    this.endTime,
    required this.distanceKm,
    required this.requiredWorkers,
    required this.confirmedWorkers,
    required this.remainingWorkers,
  });

  factory NearbyShiftItem.fromJson(Map<String, dynamic> json) {
    return NearbyShiftItem(
      shiftId: json['shiftId'] as String? ?? '',
      eventId: json['eventId'] as String?,
      eventName: json['eventName'] as String? ?? 'Sự kiện',
      jobType: json['jobType'] as String? ?? 'Nhân sự',
      description: json['description'] as String?,
      locationAddress: json['locationAddress'] as String? ?? '',
      locationLat: (json['locationLat'] as num?)?.toDouble() ?? 10.7769,
      locationLng: (json['locationLng'] as num?)?.toDouble() ?? 106.7009,
      hourlyWage: (json['hourlyWage'] as num?)?.toDouble() ?? 0.0,
      allowance: (json['allowance'] as num?)?.toDouble() ?? 0.0,
      startTime: json['startTime'] != null ? DateTime.tryParse(json['startTime'] as String) : null,
      endTime: json['endTime'] != null ? DateTime.tryParse(json['endTime'] as String) : null,
      distanceKm: (json['distanceKm'] as num?)?.toDouble() ?? 0.0,
      requiredWorkers: (json['requiredWorkers'] as num?)?.toInt() ?? 1,
      confirmedWorkers: (json['confirmedWorkers'] as num?)?.toInt() ?? 0,
      remainingWorkers: (json['remainingWorkers'] as num?)?.toInt() ?? 1,
    );
  }
}
