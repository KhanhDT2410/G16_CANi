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
  final double? faceMatchScore;
  final double? ocrConfidence;
  final String? provider;
  final String? failureCode;
  final bool retryable;
  final int retryCount;
  final bool imagesAvailable;
  final String routingDecision;
  final String routingReason;
  final String status; // PENDING, PROCESSING, PENDING_REVIEW, APPROVED, REJECTED, TECHNICAL_FAILURE
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
    this.faceMatchScore,
    this.ocrConfidence,
    this.provider,
    this.failureCode,
    this.retryable = false,
    this.retryCount = 0,
    this.imagesAvailable = false,
    required this.routingDecision,
    required this.routingReason,
    required this.status,
    required this.createdAt,
  });

  factory EkycVerificationItem.fromJson(Map<String, dynamic> json) {
    final parsedStatus = (json['status'] ?? 'PENDING').toString().toUpperCase();
    return EkycVerificationItem(
      verificationId: json['verificationId']?.toString() ?? '',
      workerId: json['workerId']?.toString() ?? '',
      workerName: json['workerName']?.toString() ?? '',
      cccdNumberMasked: json['cccdNumberMasked']?.toString() ?? '',
      fullName: json['fullName']?.toString() ?? '',
      dob: json['dob']?.toString(),
      gender: json['gender']?.toString(),
      nationality: json['nationality']?.toString(),
      residenceAddress: json['residenceAddress']?.toString(),
      expiryDate: json['expiryDate']?.toString(),
      frontImageUrl: json['frontImageUrl']?.toString(),
      backImageUrl: json['backImageUrl']?.toString(),
      selfieImageUrl: json['selfieImageUrl']?.toString(),
      faceMatchScore: (json['faceMatchScore'] is num)
          ? (json['faceMatchScore'] as num).toDouble()
          : null,
      ocrConfidence: (json['ocrConfidence'] is num)
          ? (json['ocrConfidence'] as num).toDouble()
          : null,
      provider: json['provider']?.toString(),
      failureCode: json['failureCode']?.toString(),
      retryable: json['retryable'] == true || parsedStatus == 'TECHNICAL_FAILURE',
      retryCount: (json['retryCount'] is num) ? (json['retryCount'] as num).toInt() : 0,
      imagesAvailable: json['imagesAvailable'] == true,
      routingDecision: json['routingDecision']?.toString() ?? 'MANUAL_REVIEW',
      routingReason: json['routingReason']?.toString() ?? '',
      status: parsedStatus,
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString())?.toLocal() ?? DateTime.now()
          : DateTime.now(),
    );
  }

  bool get isApproved => status == 'APPROVED';
  bool get isPendingReview => status == 'PENDING_REVIEW';
  bool get isProcessing => status == 'PROCESSING' || status == 'PENDING';
  bool get isPending => isPendingReview || isProcessing;
  bool get isTechnicalFailure => status == 'TECHNICAL_FAILURE';
  bool get isRejected => status == 'REJECTED';
  bool get canRetry => isTechnicalFailure || isRejected || retryable;
}
