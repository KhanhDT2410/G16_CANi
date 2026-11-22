import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/models/user_dto.dart';
import 'package:cani/features/auth/providers/auth_provider.dart';
import 'package:cani/features/ekyc/models/ekyc_models.dart';
import 'package:cani/features/ekyc/providers/ekyc_provider.dart';
import 'package:cani/features/skill_verification/models/skill_models.dart';
import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';
import 'package:cani/features/skill_verification/widgets/skill_hub_theme.dart';

/// Aggregated, typed view of profile-relevant state re-exposed from the
/// real feature providers. No state is duplicated or invented here.
class ProfileData {
  const ProfileData({
    required this.user,
    required this.skillPoints,
    required this.ekycResult,
  });

  final UserDto? user;
  final List<WorkerSkillPointModel> skillPoints;
  final EkycVerificationItem? ekycResult;

  int get totalSp =>
      skillPoints.fold(0, (sum, point) => sum + point.totalSp);

  String get tier => SkillHubColors.resolveHighestTier(skillPoints);

  double get avgRating {
    double weightedSum = 0.0;
    int totalWeight = 0;
    for (final point in skillPoints) {
      if (point.hasRealRating) {
        final weight = point.ratingCount > 0 ? point.ratingCount : 1;
        weightedSum += point.avgRating * weight;
        totalWeight += weight;
      }
    }
    if (totalWeight == 0) {
      return 0.0;
    }
    return weightedSum / totalWeight;
  }

  bool get isEkycApproved => ekycResult?.isApproved ?? false;
  bool get isEkycPending => ekycResult?.isPending ?? false;
  bool get isEkycRejected => ekycResult?.isRejected ?? false;
}

/// Re-exposes user / skill-points / eKYC state to the profile feature via a
/// single [Provider] that watches the upstream state notifiers.
final profileDataProvider = Provider<ProfileData>((ref) {
  final user = ref.watch(authStateProvider).user;
  final skillPoints = ref.watch(skillVerificationProvider).skillPoints;
  final ekycResult = ref.watch(ekycProvider).result;

  return ProfileData(
    user: user,
    skillPoints: skillPoints,
    ekycResult: ekycResult,
  );
});
