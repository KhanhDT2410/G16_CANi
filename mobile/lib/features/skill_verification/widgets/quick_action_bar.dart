import 'package:flutter/material.dart';

import 'skill_hub_theme.dart';

class QuickActionBar extends StatelessWidget {
  final VoidCallback onStartAiVerification;
  final VoidCallback onDeclareSkills;
  final VoidCallback onViewHistory;

  const QuickActionBar({
    super.key,
    required this.onStartAiVerification,
    required this.onDeclareSkills,
    required this.onViewHistory,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        // Main AI Verification Button
        Expanded(
          child: SizedBox(
            height: 44,
            child: ElevatedButton.icon(
              onPressed: onStartAiVerification,
              icon: const Icon(Icons.document_scanner_outlined, size: 18, color: Colors.white),
              label: const Text(
                'Thẩm định kỹ năng AI',
                style: TextStyle(
                  fontFamily: 'Inter',
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  color: Colors.white,
                ),
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: SkillHubColors.ctaPrimary,
                elevation: 0,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10),
                ),
                padding: const EdgeInsets.symmetric(horizontal: 14),
              ),
            ),
          ),
        ),
        const SizedBox(width: 8),

        // Sub-add Button (+) for fast Self-declaration
        Tooltip(
          message: 'Tự khai báo kỹ năng',
          child: InkWell(
            onTap: onDeclareSkills,
            borderRadius: BorderRadius.circular(10),
            child: Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: SkillHubColors.surface,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: SkillHubColors.borderSoft, width: 1.5),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x0A03045E),
                    blurRadius: 6,
                    offset: Offset(0, 2),
                  ),
                ],
              ),
              child: const Icon(
                Icons.add,
                size: 22,
                color: SkillHubColors.textSecondary,
              ),
            ),
          ),
        ),
        const SizedBox(width: 8),

        // Vault / History button
        Tooltip(
          message: 'Kho chứng chỉ & Lịch sử',
          child: InkWell(
            onTap: onViewHistory,
            borderRadius: BorderRadius.circular(10),
            child: Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: SkillHubColors.surface,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: SkillHubColors.borderSoft, width: 1.5),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x0A03045E),
                    blurRadius: 6,
                    offset: Offset(0, 2),
                  ),
                ],
              ),
              child: const Icon(
                Icons.history_edu_outlined,
                size: 20,
                color: SkillHubColors.textSecondary,
              ),
            ),
          ),
        ),
      ],
    );
  }
}
