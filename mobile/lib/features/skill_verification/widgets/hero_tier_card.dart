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
    final tierColor = SkillHubColors.getDevelopmentLevelColor(currentTier);
    final tierIcon = SkillHubColors.getTierIcon(currentTier);
    final devLabel = SkillHubColors.getDevelopmentLevelLabel(currentTier);
    final rankLabel = SkillHubColors.getTierTitle(currentTier);
    final tierBenefit = SkillHubColors.getTierBenefit(currentTier);
    final nextThreshold = SkillHubColors.getNextTierThreshold(totalSp);
    final nextTierName = SkillHubColors.getNextTierName(totalSp);

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
              Container(
                width: 58,
                height: 58,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(16),
                  gradient: LinearGradient(
                    colors: [
                      tierColor.withValues(alpha: 0.25),
                      tierColor.withValues(alpha: 0.90),
                    ],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  border: Border.all(color: SkillHubColors.borderSoft, width: 1.5),
                ),
                child: Center(
                  child: Icon(tierIcon, color: Colors.white, size: 28),
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(
                            color: SkillHubColors.getDevelopmentLevelBg(currentTier),
                            borderRadius: BorderRadius.circular(99),
                          ),
                          child: Text(
                            devLabel,
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w800,
                              color: tierColor,
                            ),
                          ),
                        ),
                        const SizedBox(width: 6),
                        Text(
                          'Hạng $rankLabel',
                          style: const TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w700,
                            color: SkillHubColors.textSecondary,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text(
                      tierBenefit,
                      style: const TextStyle(
                        fontSize: 11.5,
                        fontWeight: FontWeight.w500,
                        color: SkillHubColors.textSecondary,
                        height: 1.35,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          ClipRRect(
            borderRadius: BorderRadius.circular(99),
            child: LinearProgressIndicator(
              value: progress,
              minHeight: 9,
              backgroundColor: const Color(0xFFE0F2FE),
              valueColor: const AlwaysStoppedAnimation<Color>(SkillHubColors.ctaPrimary),
            ),
          ),
          const SizedBox(height: 6),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                totalSp >= 900 ? 'Đã đạt hạng cao nhất' : 'Tiến độ lên $nextTierName',
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
    );
  }
}
