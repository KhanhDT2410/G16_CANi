class EkycVerificationItem {
  final String verificationId;
  final String workerId;
  final String workerName;
  final String cccdNumberMasked;
  final String fullName;
  final String? dob;
  final String? gender;
  final String? nationality;
  final String? residenceAddress;
  final String? expiryDate;
  final String? frontImageUrl;
  final String? backImageUrl;
  final String? selfieImageUrl;
  final double faceMatchScore;
  final double ocrConfidence;
  final String routingDecision;
  final String routingReason;
  final String status; // APPROVED, PENDING_REVIEW, REJECTED
  final DateTime createdAt;

  EkycVerificationItem({
    required this.verificationId,
    required this.workerId,
    required this.workerName,
    required this.cccdNumberMasked,
    required this.fullName,
    this.dob,
    this.gender,
    this.nationality,
    this.residenceAddress,
    this.expiryDate,
    this.frontImageUrl,
    this.backImageUrl,
    this.selfieImageUrl,
    required this.faceMatchScore,
    required this.ocrConfidence,
    required this.routingDecision,
    required this.routingReason,
    required this.status,
    required this.createdAt,
  });

  factory EkycVerificationItem.fromJson(Map<String, dynamic> json) {
    return EkycVerificationItem(
      verificationId: json['verificationId'] ?? '',
      workerId: json['workerId'] ?? '',
      workerName: json['workerName'] ?? '',
      cccdNumberMasked: json['cccdNumberMasked'] ?? '',
      fullName: json['fullName'] ?? '',
      dob: json['dob'],
      gender: json['gender'],
      nationality: json['nationality'],
      residenceAddress: json['residenceAddress'],
      expiryDate: json['expiryDate'],
      frontImageUrl: json['frontImageUrl'],
      backImageUrl: json['backImageUrl'],
      selfieImageUrl: json['selfieImageUrl'],
      faceMatchScore: (json['faceMatchScore'] is num) ? (json['faceMatchScore'] as num).toDouble() : 0.0,
      ocrConfidence: (json['ocrConfidence'] is num) ? (json['ocrConfidence'] as num).toDouble() : 0.0,
      routingDecision: json['routingDecision'] ?? 'MANUAL_REVIEW',
      routingReason: json['routingReason'] ?? '',
      status: json['status'] ?? 'PENDING_REVIEW',
      createdAt: json['createdAt'] != null ? DateTime.tryParse(json['createdAt']) ?? DateTime.now() : DateTime.now(),
    );
  }

  bool get isApproved => status == 'APPROVED';
  bool get isPending => status == 'PENDING_REVIEW';
  bool get isRejected => status == 'REJECTED';
}
