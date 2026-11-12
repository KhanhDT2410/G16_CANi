import 'package:flutter/material.dart';

import 'skill_hub_theme.dart';

class HeroTierCard extends StatelessWidget {
  final int totalSp;
  final String currentTier;

  const HeroTierCard({
    super.key,
    required this.totalSp,
    required this.currentTier,
  });

  @override
  Widget build(BuildContext context) {
    final tierColor = SkillHubColors.getTierColor(currentTier);
    final tierIcon = SkillHubColors.getTierIcon(currentTier);
    final tierTitle = SkillHubColors.getTierTitle(currentTier);
    final tierBenefit = SkillHubColors.getTierBenefit(currentTier);
    final nextThreshold = SkillHubColors.getNextTierThreshold(totalSp);
    final nextTierName = SkillHubColors.getNextTierName(totalSp);

    // Calculate progress clamped between 0.0 and 1.0
    final double progress = (totalSp / nextThreshold).clamp(0.0, 1.0);

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: SkillHubColors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: SkillHubColors.borderSoft, width: 1.5),
        boxShadow: const [
          BoxShadow(
            color: Color(0x0D03045E),
            blurRadius: 16,
            offset: Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              // Tier Icon Badge Container
              Container(
                width: 60,
                height: 60,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(16),
                  gradient: LinearGradient(
                    colors: [
                      tierColor.withValues(alpha: 0.20),
                      tierColor.withValues(alpha: 0.85),
                    ],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  border: Border.all(color: SkillHubColors.borderSoft, width: 1.5),
                  boxShadow: [
                    BoxShadow(
                      color: tierColor.withValues(alpha: 0.35),
                      blurRadius: 10,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child: Center(
                  child: Icon(
                    tierIcon,
                    color: Colors.white,
                    size: 30,
                  ),
                ),
              ),
              const SizedBox(width: 14),
              // Dynamic tier info
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      tierTitle,
                      style: const TextStyle(
                        fontFamily: 'Inter',
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                        color: SkillHubColors.textPrimary,
                        letterSpacing: -0.3,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      tierBenefit,
                      style: const TextStyle(
                        fontSize: 11.5,
                        fontWeight: FontWeight.w500,
                        color: SkillHubColors.textSecondary,
                        height: 1.3,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          // Progress bar and labels
          Column(
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(99),
                child: LinearProgressIndicator(
                  value: progress,
                  minHeight: 9,
                  backgroundColor: const Color(0xFFCAF0F8),
                  valueColor: const AlwaysStoppedAnimation<Color>(SkillHubColors.ctaPrimary),
                ),
              ),
              const SizedBox(height: 6),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Tiến độ lên $nextTierName',
                    style: const TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w500,
                      color: SkillHubColors.textSecondary,
                    ),
                  ),
                  Text(
                    '$totalSp / $nextThreshold SP',
                    style: const TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      color: SkillHubColors.textPrimary,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }
}
