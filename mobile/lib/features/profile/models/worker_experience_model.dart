class WorkerExperienceModel {
  final int yearsExperience;
  final String? bio;
  final int totalShiftsCompleted;
  final String? skillTier;
  final double ratingAvg;
  final int reliabilityScore;

  const WorkerExperienceModel({
    required this.yearsExperience,
    this.bio,
    this.totalShiftsCompleted = 0,
    this.skillTier,
    this.ratingAvg = 0.0,
    this.reliabilityScore = 100,
  });

  factory WorkerExperienceModel.fromJson(Map<String, dynamic> json) {
    return WorkerExperienceModel(
      yearsExperience: json['yearsExperience'] as int? ?? 0,
      bio: json['bio'] as String?,
      totalShiftsCompleted: json['totalShiftsCompleted'] as int? ?? 0,
      skillTier: json['skillTier'] as String?,
      ratingAvg: (json['ratingAvg'] as num?)?.toDouble() ?? 0.0,
      reliabilityScore: json['reliabilityScore'] as int? ?? 100,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'yearsExperience': yearsExperience,
      'bio': bio,
    };
  }

  WorkerExperienceModel copyWith({
    int? yearsExperience,
    String? bio,
    int? totalShiftsCompleted,
    String? skillTier,
    double? ratingAvg,
    int? reliabilityScore,
  }) {
    return WorkerExperienceModel(
      yearsExperience: yearsExperience ?? this.yearsExperience,
      bio: bio ?? this.bio,
      totalShiftsCompleted: totalShiftsCompleted ?? this.totalShiftsCompleted,
      skillTier: skillTier ?? this.skillTier,
      ratingAvg: ratingAvg ?? this.ratingAvg,
      reliabilityScore: reliabilityScore ?? this.reliabilityScore,
    );
  }
}
