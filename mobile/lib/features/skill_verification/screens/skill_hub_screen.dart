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

class SkillHubScreen extends ConsumerStatefulWidget {
  const SkillHubScreen({super.key});

  @override
  ConsumerState<SkillHubScreen> createState() => _SkillHubScreenState();
}

class _SkillHubScreenState extends ConsumerState<SkillHubScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final user = ref.read(authStateProvider).user;
      if (user != null) {
        SkillPointPolicyDialog.checkAndShow(context, user.id);
      }
    });
  }

  void _openAiVerificationWizard() {
    Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => const AiVerificationWizardScreen()),
    );
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(skillVerificationProvider);
    final user = ref.watch(authStateProvider).user;

    final pointsList = state.skillPoints;
    final totalAllSp = pointsList.fold<int>(0, (sum, p) => sum + p.totalSp);
    final currentTier = SkillHubColors.resolveHighestTier(pointsList);

    final isDark = Theme.of(context).brightness == Brightness.dark;
    final canvasBg = isDark ? Theme.of(context).scaffoldBackgroundColor : SkillHubColors.canvas;
    final primaryTextColor = isDark ? const Color(0xFFF8FAFC) : SkillHubColors.textPrimary;
    final secondaryTextColor = isDark ? const Color(0xFF94A3B8) : SkillHubColors.textSecondary;

    return Scaffold(
      backgroundColor: canvasBg,
      appBar: AppBar(
        backgroundColor: canvasBg,
        elevation: 0,
        scrolledUnderElevation: 0,
        iconTheme: IconThemeData(color: primaryTextColor),
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Kỹ năng của tôi',
              style: TextStyle(
                fontFamily: 'Inter',
                fontSize: 17,
                fontWeight: FontWeight.w800,
                color: primaryTextColor,
              ),
            ),
            Text(
              user != null ? 'Hồ sơ năng lực · ${user.fullName}' : 'Phát triển kỹ năng & xác minh tài liệu',
              style: TextStyle(
                fontSize: 11.5,
                fontWeight: FontWeight.w500,
                color: secondaryTextColor,
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: Icon(Icons.help_outline_rounded, color: primaryTextColor),
            tooltip: 'Quy chế điểm kỹ năng',
            onPressed: () => SkillPointPolicyDialog.show(context, userId: user?.id),
          ),
          const SizedBox(width: 4),
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
                    HeroTierCard(
                      totalSp: totalAllSp,
                      currentTier: currentTier,
                    ),
                    const SizedBox(height: 12),
                    SkillStatsRow(
                      totalSp: totalAllSp,
                      mySkills: state.mySkills,
                    ),
                    const SizedBox(height: 12),
                    QuickActionBar(
                      onDeclareSkills: () => DeclareSkillsSheet.show(context),
                      onStartAiVerification: _openAiVerificationWizard,
                      onViewHistory: () => CertificateVaultSheet.show(context),
                      onViewPolicy: () => SkillPointPolicyDialog.show(context, userId: user?.id),
                    ),
                    const SizedBox(height: 16),
                    DynamicSkillMatrix(
                      mySkills: state.mySkills,
                      skillPoints: state.skillPoints,
                      onDeclareSkills: () => DeclareSkillsSheet.show(context),
                      onStartAiVerification: _openAiVerificationWizard,
                    ),
                    const SizedBox(height: 28),
                  ],
                ),
              ),
            ),
    );
  }
}
