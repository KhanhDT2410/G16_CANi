import 'package:flutter/material.dart';

import 'package:cani/features/skill_verification/models/skill_models.dart';
import 'skill_hub_theme.dart';

class DynamicSkillMatrix extends StatelessWidget {
  final List<WorkerSkillItem> mySkills;
  final VoidCallback onDeclareSkills;
  final VoidCallback onStartAiVerification;

  const DynamicSkillMatrix({
    super.key,
    required this.mySkills,
    required this.onDeclareSkills,
    required this.onStartAiVerification,
  });

  @override
  Widget build(BuildContext context) {
    final tier1Skills = mySkills.where((s) => s.isTier1SelfDeclared).toList();
    final tier2Skills = mySkills.where((s) => s.isTier2AiVerified).toList();
    final tier3Skills = mySkills.where((s) => s.isTier3PlatformProven).toList();

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: SkillHubColors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: SkillHubColors.borderSoft, width: 1.5),
        boxShadow: const [
          BoxShadow(
            color: Color(0x0A03045E),
            blurRadius: 10,
            offset: Offset(0, 3),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Tier 1: Self-Declared
          _buildTierGroup(
            title: 'KỸ NĂNG TỰ KHAI BÁO',
            subHeader: 'Ứng tuyển ca cơ bản',
            dotColor: SkillHubColors.borderSoft,
            skills: tier1Skills,
            emptyPrompt: '+ Khai báo kỹ năng có kinh nghiệm',
            onEmptyTap: onDeclareSkills,
            chipBuilder: (skill) => _buildPill(
              label: skill.skillName,
              subLabel: skill.proficiencyLabel,
              bgColor: SkillHubColors.surfaceTint,
              borderColor: SkillHubColors.borderSoft,
              textColor: SkillHubColors.textPrimary,
            ),
          ),
          const SizedBox(height: 12),
          const Divider(height: 1, color: Color(0x3390E0EF)),
          const SizedBox(height: 12),

          // Tier 2: AI Verified
          _buildTierGroup(
            title: 'CHỨNG CHỈ XÁC THỰC AI',
            subHeader: 'Huy hiệu đã duyệt',
            dotColor: SkillHubColors.ctaPrimary,
            skills: tier2Skills,
            emptyPrompt: 'Tải lên chứng chỉ hoặc chứng nhận nghề',
            onEmptyTap: onStartAiVerification,
            chipBuilder: (skill) => _buildPill(
              label: '✓ ${skill.skillName}',
              subLabel: skill.proficiencyLabel,
              bgColor: const Color(0xFFE8F9FD),
              borderColor: SkillHubColors.ctaPrimary,
              textColor: SkillHubColors.textSecondary,
              isBold: true,
            ),
          ),
          const SizedBox(height: 12),
          const Divider(height: 1, color: Color(0x3390E0EF)),
          const SizedBox(height: 12),

          // Tier 3: Platform-Proven
          _buildTierGroup(
            title: 'KINH NGHIỆM THỰC CHIẾN',
            subHeader: 'Tích lũy từ ca làm đánh giá cao',
            dotColor: SkillHubColors.textPrimary,
            skills: tier3Skills,
            emptyPrompt: 'Tự động mở khóa qua các ca hoàn thành tốt',
            chipBuilder: (skill) => _buildPill(
              label: '★ ${skill.skillName}',
              subLabel: skill.proficiencyLabel,
              bgColor: const Color(0xFFE6E8F3),
              borderColor: SkillHubColors.textPrimary,
              textColor: SkillHubColors.textPrimary,
              isBold: true,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTierGroup({
    required String title,
    required String subHeader,
    required Color dotColor,
    required List<WorkerSkillItem> skills,
    required String emptyPrompt,
    VoidCallback? onEmptyTap,
    required Widget Function(WorkerSkillItem) chipBuilder,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Container(
              width: 7,
              height: 7,
              decoration: BoxDecoration(
                color: dotColor,
                shape: BoxShape.circle,
                boxShadow: [
                  BoxShadow(
                    color: dotColor.withValues(alpha: 0.5),
                    blurRadius: 4,
                  ),
                ],
              ),
            ),
            const SizedBox(width: 7),
            Expanded(
              child: Text.rich(
                TextSpan(
                  children: [
                    TextSpan(
                      text: title,
                      style: const TextStyle(
                        fontSize: 10.5,
                        fontWeight: FontWeight.w700,
                        color: SkillHubColors.textPrimary,
                        letterSpacing: 0.3,
                      ),
                    ),
                    const TextSpan(text: ' '),
                    TextSpan(
                      text: '($subHeader)',
                      style: const TextStyle(
                        fontSize: 10,
                        fontWeight: FontWeight.w500,
                        color: SkillHubColors.textSecondary,
                      ),
                    ),
                  ],
                ),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        if (skills.isEmpty)
          InkWell(
            onTap: onEmptyTap,
            borderRadius: BorderRadius.circular(6),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 4.0),
              child: Text(
                emptyPrompt,
                style: TextStyle(
                  fontSize: 11,
                  fontStyle: FontStyle.italic,
                  color: onEmptyTap != null ? SkillHubColors.ctaPrimary : Colors.grey,
                  fontWeight: onEmptyTap != null ? FontWeight.w600 : FontWeight.normal,
                ),
              ),
            ),
          )
        else
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: skills.map(chipBuilder).toList(),
          ),
      ],
    );
  }

  Widget _buildPill({
    required String label,
    required String subLabel,
    required Color bgColor,
    required Color borderColor,
    required Color textColor,
    bool isBold = false,
  }) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: borderColor, width: 1.2),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            label,
            style: TextStyle(
              fontSize: 11.5,
              fontWeight: isBold ? FontWeight.w700 : FontWeight.w600,
              color: textColor,
            ),
          ),
          const SizedBox(width: 4),
          Text(
            '• $subLabel',
            style: TextStyle(
              fontSize: 10,
              fontWeight: FontWeight.w500,
              color: textColor.withValues(alpha: 0.75),
            ),
          ),
        ],
      ),
    );
  }
}
