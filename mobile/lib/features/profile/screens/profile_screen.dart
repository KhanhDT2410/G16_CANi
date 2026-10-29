// CANi · tab "Tài khoản" — Worker profile screen.
// Composition of profile blocks wired to the real aggregated providers.

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/providers/auth_provider.dart';
import 'package:cani/features/ekyc/screens/ekyc_verification_screen.dart';
import 'package:cani/features/profile/providers/profile_providers.dart';
import 'package:cani/features/profile/widgets/profile_block_cv.dart';
import 'package:cani/features/profile/widgets/profile_block_ekyc.dart';
import 'package:cani/features/profile/widgets/profile_block_identity.dart';
import 'package:cani/features/profile/widgets/profile_block_logout.dart';
import 'package:cani/features/profile/widgets/profile_block_public.dart';
import 'package:cani/features/profile/widgets/profile_block_security.dart';
import 'package:cani/features/profile/widgets/profile_block_settings.dart';
import 'package:cani/features/profile/widgets/profile_block_trust.dart';
import 'package:cani/features/profile/widgets/profile_tokens.dart';
import 'package:cani/features/skill_verification/widgets/skill_hub_theme.dart';

class WorkerProfileScreen extends ConsumerWidget {
  const WorkerProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final data = ref.watch(profileDataProvider);
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
            ),
            ProfileEkycCard(
              status: data.ekycResult,
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const EkycVerificationScreen()),
              ),
            ),
            const ProfileCvBlock(),
            const ProfileSettingsBlock(),
            const ProfileSecurityBlock(),
            const ProfilePublicProfileBlock(),
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
    );
  }
}
