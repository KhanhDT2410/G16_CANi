import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/auth.dart';
import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';
import 'package:cani/features/skill_verification/screens/ai_verification_wizard_screen.dart';
import 'package:cani/features/skill_verification/widgets/certificate_vault_sheet.dart';
import 'package:cani/features/skill_verification/widgets/declare_skills_sheet.dart';
import 'package:cani/features/skill_verification/widgets/dynamic_skill_matrix.dart';
import 'package:cani/features/skill_verification/widgets/hero_tier_card.dart';
import 'package:cani/features/skill_verification/widgets/quick_action_bar.dart';
import 'package:cani/features/skill_verification/widgets/skill_hub_theme.dart';
import 'package:cani/features/skill_verification/widgets/skill_point_policy_dialog.dart';
import 'package:cani/features/skill_verification/widgets/skill_stats_row.dart';

class SkillHubScreen extends ConsumerWidget {
  const SkillHubScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(skillVerificationProvider);
    final user = ref.watch(authStateProvider).user;

    // Automatically check and show the SP policy popup for first-time workers entering this screen
    if (user != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        SkillPointPolicyDialog.checkAndShow(context, user.id);
      });
    }

    final pointsList = state.skillPoints;
    final totalAllSp = pointsList.fold<int>(0, (sum, p) => sum + p.totalSp);
    final currentTier = pointsList.isNotEmpty ? pointsList.first.tier : 'STARTER';

    return Scaffold(
      backgroundColor: SkillHubColors.canvas,
      appBar: AppBar(
        backgroundColor: SkillHubColors.canvas,
        elevation: 0,
        scrolledUnderElevation: 0,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Trung Tâm Kỹ Năng',
              style: TextStyle(
                fontFamily: 'Inter',
                fontSize: 17,
                fontWeight: FontWeight.w800,
                color: SkillHubColors.textPrimary,
              ),
            ),
            Text(
              user != null ? 'Xin chào, ${user.fullName}' : 'Kỹ năng & Thăng hạng nghề nghiệp',
              style: const TextStyle(
                fontSize: 11.5,
                fontWeight: FontWeight.w500,
                color: SkillHubColors.textSecondary,
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.info_outline, color: SkillHubColors.textPrimary),
            tooltip: 'Quy chế Điểm kỹ năng',
            onPressed: () => SkillPointPolicyDialog.show(context, userId: user?.id),
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: state.isLoading
          ? const Center(
              child: CircularProgressIndicator(color: SkillHubColors.ctaPrimary),
            )
          : RefreshIndicator(
              color: SkillHubColors.ctaPrimary,
              onRefresh: () => ref.read(skillVerificationProvider.notifier).loadAll(),
              child: SingleChildScrollView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 12.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // 1. Hero Tier Card (Clean Sky design)
                    HeroTierCard(
                      totalSp: totalAllSp,
                      currentTier: currentTier,
                    ),

                    const SizedBox(height: 12),

                    // 2. Dynamic 3-Column Highlights Stat Row
                    SkillStatsRow(
                      totalSp: totalAllSp,
                      mySkills: state.mySkills,
                    ),

                    const SizedBox(height: 12),

                    // 3. Quick Action Bar
                    QuickActionBar(
                      onStartAiVerification: () {
                        Navigator.of(context).push(
                          MaterialPageRoute(builder: (_) => const AiVerificationWizardScreen()),
                        );
                      },
                      onDeclareSkills: () => DeclareSkillsSheet.show(context),
                      onViewHistory: () => CertificateVaultSheet.show(context),
                    ),

                    const SizedBox(height: 14),

                    // 4. Dynamic Skill Matrix (3 Official Verification Tiers)
                    DynamicSkillMatrix(
                      mySkills: state.mySkills,
                      onDeclareSkills: () => DeclareSkillsSheet.show(context),
                      onStartAiVerification: () {
                        Navigator.of(context).push(
                          MaterialPageRoute(builder: (_) => const AiVerificationWizardScreen()),
                        );
                      },
                    ),

                    const SizedBox(height: 14),

                    // 5. Educational SP Policy Callout Banner (Ensures screen is balanced, not empty)
                    Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: SkillHubColors.surface,
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(color: SkillHubColors.borderSoft),
                        boxShadow: const [
                          BoxShadow(
                            color: Color(0x0603045E),
                            blurRadius: 8,
                            offset: Offset(0, 2),
                          ),
                        ],
                      ),
                      child: Row(
                        children: [
                          Container(
                            padding: const EdgeInsets.all(8),
                            decoration: const BoxDecoration(
                              color: Color(0xFFE8F9FD),
                              shape: BoxShape.circle,
                            ),
                            child: const Icon(
                              Icons.help_outline_rounded,
                              color: SkillHubColors.ctaHover,
                              size: 20,
                            ),
                          ),
                          const SizedBox(width: 12),
                          const Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  'Điểm Kỹ Năng (SP) hoạt động thế nào?',
                                  style: TextStyle(
                                    fontSize: 12.5,
                                    fontWeight: FontWeight.w700,
                                    color: SkillHubColors.textPrimary,
                                  ),
                                ),
                                SizedBox(height: 2),
                                Text(
                                  'Tìm hiểu cơ chế thăng hạng, đánh giá và mở khóa thù lao cao',
                                  style: TextStyle(fontSize: 11, color: SkillHubColors.textSecondary),
                                ),
                              ],
                            ),
                          ),
                          TextButton(
                            onPressed: () => SkillPointPolicyDialog.show(context, userId: user?.id),
                            child: const Text(
                              'Xem quy chế',
                              style: TextStyle(
                                fontWeight: FontWeight.w700,
                                fontSize: 12,
                                color: SkillHubColors.ctaPrimary,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),

                    const SizedBox(height: 28),
                  ],
                ),
              ),
            ),
    );
  }
}
