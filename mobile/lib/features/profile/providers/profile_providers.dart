import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'package:cani/features/auth/models/user_dto.dart';
import 'package:cani/features/auth/providers/auth_provider.dart';
import 'package:cani/features/ekyc/models/ekyc_models.dart';
import 'package:cani/features/ekyc/providers/ekyc_provider.dart';
import 'package:cani/features/skill_verification/models/skill_models.dart';
import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';

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

  String get tier =>
      skillPoints.isEmpty ? 'STARTER' : skillPoints.first.tier;

  double get avgRating =>
      skillPoints.isEmpty ? 0.0 : skillPoints.first.avgRating;

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

/// Quản lý trạng thái bật/tắt thông báo của ứng dụng lưu trữ bền vững
final notificationSettingsProvider =
    StateNotifierProvider<NotificationSettingsNotifier, bool>((ref) {
  return NotificationSettingsNotifier();
});

class NotificationSettingsNotifier extends StateNotifier<bool> {
  NotificationSettingsNotifier() : super(true) {
    _load();
  }

  static const _storage = FlutterSecureStorage();
  static const _key = 'cani_push_notifications_enabled';

  Future<void> _load() async {
    final val = await _storage.read(key: _key);
    if (val != null) {
      state = val == 'true';
    }
  }

  Future<void> toggle(bool enabled) async {
    state = enabled;
    await _storage.write(key: _key, value: enabled.toString());
  }
}

