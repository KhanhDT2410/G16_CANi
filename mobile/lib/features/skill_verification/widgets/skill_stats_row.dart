import 'package:flutter/material.dart';

import 'package:cani/features/skill_verification/models/skill_models.dart';
import 'skill_hub_theme.dart';

class SkillStatsRow extends StatelessWidget {
  final int totalSp;
  final List<WorkerSkillItem> mySkills;

  const SkillStatsRow({
    super.key,
    required this.totalSp,
    required this.mySkills,
  });

  @override
  Widget build(BuildContext context) {
    final int activeCount = mySkills.length;
    final int verifiedCount = mySkills.where((s) => s.isTier2AiVerified || s.isTier3PlatformProven).length;

    return Row(
      children: [
        _buildStatCard(
          label: 'Tổng điểm SP',
          value: '$totalSp SP',
          icon: Icons.bolt_rounded,
          iconColor: SkillHubColors.ctaHover,
          bgColor: const Color(0xFFE8F9FD),
        ),
        const SizedBox(width: 10),
        _buildStatCard(
          label: 'Kỹ năng khả dụng',
          value: '$activeCount',
          icon: Icons.checklist_rounded,
          iconColor: SkillHubColors.textPrimary,
          bgColor: SkillHubColors.surfaceTint,
        ),
        const SizedBox(width: 10),
        _buildStatCard(
          label: 'Huy hiệu đã duyệt',
          value: '$verifiedCount',
          icon: Icons.verified_rounded,
          iconColor: Colors.green.shade700,
          bgColor: const Color(0xFFE8F8F0),
        ),
      ],
    );
  }

  Widget _buildStatCard({
    required String label,
    required String value,
    required IconData icon,
    required Color iconColor,
    required Color bgColor,
  }) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 12),
        decoration: BoxDecoration(
          color: SkillHubColors.surface,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: SkillHubColors.borderSoft),
          boxShadow: const [
            BoxShadow(
              color: Color(0x0803045E),
              blurRadius: 8,
              offset: Offset(0, 2),
            ),
          ],
        ),
        child: Column(
          children: [
            Container(
              padding: const EdgeInsets.all(6),
              decoration: BoxDecoration(
                color: bgColor,
                shape: BoxShape.circle,
              ),
              child: Icon(icon, color: iconColor, size: 18),
            ),
            const SizedBox(height: 6),
            Text(
              value,
              style: const TextStyle(
                fontFamily: 'Inter',
                fontSize: 14,
                fontWeight: FontWeight.w800,
                color: SkillHubColors.textPrimary,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              label,
              style: const TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.w500,
                color: SkillHubColors.textSecondary,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ),
      ),
    );
  }
}
