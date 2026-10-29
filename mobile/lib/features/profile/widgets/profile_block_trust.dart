// CANi · tab "Tài khoản" — Block 2 · Trust & Skill Strip.
// avg rating, tier, total SP, tiến độ tới tier kế — widget dumb, screen truyền values.

import 'package:flutter/material.dart';

import 'package:cani/features/profile/widgets/profile_tokens.dart';
import 'package:cani/features/skill_verification/widgets/skill_hub_theme.dart';

class ProfileTrustSkillStrip extends StatelessWidget {
  const ProfileTrustSkillStrip({
    required this.totalSp,
    required this.tier,
    required this.avgRating,
    required this.nextThreshold,
    required this.nextTierLabel,
    super.key,
  });

  final int totalSp;
  final String tier; // code: 'GOLD', 'SILVER', ...
  final double avgRating; // 0..5, 0 = chưa có rating
  final int nextThreshold;
  final String nextTierLabel;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;
    final tierColor = SkillHubColors.getTierColor(tier);
    final progress = (totalSp / nextThreshold).clamp(0.0, 1.0);
    return Container(
      margin: const EdgeInsets.all(16),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: t.surface,
        borderRadius: BorderRadius.circular(t.radius),
        border: Border.all(color: t.border),
      ),
      child: Column(children: [
        Row(children: [
          Text('TÍN NHIỆM & KỸ NĂNG',
              style: Theme.of(context).textTheme.labelLarge?.copyWith(color: t.muted)),
          const Spacer(),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
            decoration: BoxDecoration(
                color: t.surface,
                borderRadius: BorderRadius.circular(99),
                border: Border.all(color: tierColor)),
            child: Text(SkillHubColors.getTierTitle(tier),
                style: Theme.of(context)
                    .textTheme
                    .labelSmall
                    ?.copyWith(color: tierColor, fontWeight: FontWeight.w600)),
          ),
        ]),
        const SizedBox(height: 12),
        Row(crossAxisAlignment: CrossAxisAlignment.center, children: [
          Column(children: [
            Text(avgRating.toStringAsFixed(1),
                style: Theme.of(context)
                    .textTheme
                    .headlineMedium
                    ?.copyWith(color: t.accentDeep)),
            Text('Đánh giá TB',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(color: t.muted)),
          ]),
          const SizedBox(width: 18),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('$totalSp SP',
                  style: Theme.of(context)
                      .textTheme
                      .titleMedium
                      ?.copyWith(color: t.text, fontWeight: FontWeight.w600)),
              const SizedBox(height: 6),
              LinearProgressIndicator(
                minHeight: 8,
                value: progress,
                color: t.accent,
                backgroundColor: t.skeleton,
              ),
              const SizedBox(height: 4),
              Text('Cần ${nextThreshold - totalSp} SP để lên $nextTierLabel',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(color: t.muted)),
            ]),
          ),
        ]),
      ]),
    );
  }
}
