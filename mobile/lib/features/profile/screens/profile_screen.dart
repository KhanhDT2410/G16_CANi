// CANi · tab "Tài khoản" — Worker profile screen.
// Composition of profile blocks wired to the real aggregated providers.

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/providers/auth_provider.dart';
import 'package:cani/features/auth/screens/forgot_password_screen.dart';
import 'package:cani/features/auth/widgets/terms_of_service_dialog.dart';
import 'package:cani/features/ekyc/screens/ekyc_verification_screen.dart';
import 'package:cani/features/profile/providers/profile_providers.dart';
import 'package:cani/features/profile/widgets/profile_block_cv.dart';
import 'package:cani/features/profile/widgets/profile_block_ekyc.dart';
import 'package:cani/features/profile/widgets/profile_block_identity.dart';
import 'package:cani/features/profile/widgets/profile_block_logout.dart';
import 'package:cani/features/profile/widgets/profile_block_security.dart';
import 'package:cani/features/profile/widgets/profile_block_settings.dart';
import 'package:cani/features/profile/widgets/profile_block_trust.dart';
import 'package:cani/features/profile/widgets/profile_tokens.dart';
import 'package:cani/features/skill_verification/screens/skill_hub_screen.dart';
import 'package:cani/features/skill_verification/widgets/skill_hub_theme.dart';
import 'package:cani/features/skill_verification/widgets/skill_point_policy_dialog.dart';

class WorkerProfileScreen extends ConsumerWidget {
  const WorkerProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final data = ref.watch(profileDataProvider);
    final notificationsEnabled = ref.watch(notificationSettingsProvider);
    final t = Theme.of(context).profileX;

    if (data.user == null) {
      return Scaffold(
        backgroundColor: t.canvas,
        appBar: _appBar(context),
        body: const Center(child: Text('Chưa có thông tin người dùng')),
      );
    }

    return Scaffold(
      backgroundColor: t.canvas,
      appBar: _appBar(context),
      body: RefreshIndicator(
        onRefresh: () async {},
        child: ListView(
          padding: const EdgeInsets.symmetric(vertical: 16),
          children: [
            ProfileIdentityHeader(user: data.user),
            ProfileTrustSkillStrip(
              totalSp: data.totalSp,
              tier: data.tier,
              avgRating: data.avgRating,
              nextThreshold: SkillHubColors.getNextTierThreshold(data.totalSp),
              nextTierLabel: SkillHubColors.getNextTierName(data.totalSp),
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const SkillHubScreen()),
              ),
            ),
            ProfileEkycCard(
              status: data.ekycResult,
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const EkycVerificationScreen()),
              ),
            ),
            const ProfileCvBlock(),
            ProfileSettingsBlock(
              notificationsEnabled: notificationsEnabled,
              onToggleNotifications: (val) {
                ref.read(notificationSettingsProvider.notifier).toggle(val);
              },
              onTapTermsAndPolicies: () => _showTermsBottomSheet(context, data.user?.id),
            ),
            ProfileSecurityBlock(
              isGoogleLinked: data.user?.isGoogleLinked ?? false,
              googleEmail: data.user?.email,
              onChangePassword: () {
                Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => ForgotPasswordScreen(
                      initialEmail: data.user?.email,
                      isChangePassword: true,
                    ),
                  ),
                );
              },
              onTapGoogleAccount: () {
                final isLinked = data.user?.isGoogleLinked ?? false;
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(
                      isLinked
                          ? 'Tài khoản đã liên kết bảo mật với Google (${data.user?.email ?? ''})'
                          : 'Tài khoản chưa liên kết với Google.',
                    ),
                    behavior: SnackBarBehavior.floating,
                  ),
                );
              },
            ),
            ProfileLogoutFooter(
              onLogout: () => ref.read(authStateProvider.notifier).logout(),
            ),
          ],
        ),
      ),
    );
  }

  PreferredSizeWidget _appBar(BuildContext context) {
    final t = Theme.of(context).profileX;
    return AppBar(
      title: Text(
        'Tài khoản',
        style: TextStyle(
          color: t.text,
          fontWeight: FontWeight.bold,
          fontSize: 22,
        ),
      ),
      backgroundColor: t.canvas,
      elevation: 0,
      centerTitle: false,
      actions: [
        IconButton(
          icon: const Icon(Icons.dark_mode_outlined),
          tooltip: 'Chế độ Sáng / Tối',
          color: t.accentDeep,
          onPressed: () {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                content: Text('Tính năng chuyển đổi giao diện Sáng / Tối đang được phát triển.'),
                behavior: SnackBarBehavior.floating,
                duration: Duration(seconds: 2),
              ),
            );
          },
        ),
        const SizedBox(width: 8),
      ],
    );
  }

  void _showTermsBottomSheet(BuildContext context, String? userId) {
    final t = Theme.of(context).profileX;
    showModalBottomSheet(
      context: context,
      backgroundColor: t.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 20, horizontal: 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: Text(
                  'Quy chế & Điều khoản nền tảng',
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        color: t.text,
                        fontWeight: FontWeight.bold,
                      ),
                ),
              ),
              const SizedBox(height: 12),
              ListTile(
                leading: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: t.chipSoft,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Icon(Icons.description_outlined, color: t.accentDeep, size: 22),
                ),
                title: const Text('Điều khoản sử dụng CANi', style: TextStyle(fontWeight: FontWeight.w600)),
                subtitle: const Text('Quy tắc nhận ca, cam kết không bỏ ca (No-Show)', style: TextStyle(fontSize: 12)),
                trailing: Icon(Icons.chevron_right_rounded, color: t.muted),
                onTap: () {
                  Navigator.of(ctx).pop();
                  TermsOfServiceDialog.show(context);
                },
              ),
              ListTile(
                leading: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: t.chipSoft,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Icon(Icons.workspace_premium_outlined, color: t.accentDeep, size: 22),
                ),
                title: const Text('Quy chế Điểm tín nhiệm (SP)', style: TextStyle(fontWeight: FontWeight.w600)),
                subtitle: const Text('Cơ chế thăng hạng Silver/Gold/Diamond & Mức thù lao', style: TextStyle(fontSize: 12)),
                trailing: Icon(Icons.chevron_right_rounded, color: t.muted),
                onTap: () {
                  Navigator.of(ctx).pop();
                  SkillPointPolicyDialog.show(context, userId: userId);
                },
              ),
            ],
          ),
        ),
      ),
    );
  }
}
