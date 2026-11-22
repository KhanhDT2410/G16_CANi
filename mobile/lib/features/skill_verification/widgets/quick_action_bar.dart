import 'package:flutter/material.dart';

import 'skill_hub_theme.dart';

/// Thanh 4 tác vụ nhanh không trùng lặp trên màn hình Kỹ năng của tôi.
class QuickActionBar extends StatelessWidget {
  final VoidCallback onDeclareSkills;
  final VoidCallback onStartAiVerification;
  final VoidCallback onViewHistory;
  final VoidCallback? onViewPolicy;

  const QuickActionBar({
    super.key,
    required this.onStartAiVerification,
    required this.onDeclareSkills,
    required this.onViewHistory,
    this.onViewPolicy,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Row(
          children: [
            Expanded(
              child: _buildActionCard(
                icon: Icons.add_circle_outline_rounded,
                label: 'Khai báo kỹ năng',
                color: SkillHubColors.textPrimary,
                bgColor: SkillHubColors.surface,
                onTap: onDeclareSkills,
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: _buildActionCard(
                icon: Icons.document_scanner_outlined,
                label: 'Tải chứng chỉ',
                color: Colors.white,
                bgColor: SkillHubColors.ctaPrimary,
                isPrimary: true,
                onTap: onStartAiVerification,
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            Expanded(
              child: _buildActionCard(
                icon: Icons.folder_copy_outlined,
                label: 'Kho chứng chỉ',
                color: SkillHubColors.textSecondary,
                bgColor: SkillHubColors.surface,
                onTap: onViewHistory,
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: _buildActionCard(
                icon: Icons.menu_book_outlined,
                label: 'Quy chế điểm',
                color: SkillHubColors.textSecondary,
                bgColor: SkillHubColors.surface,
                onTap: onViewPolicy ?? onViewHistory,
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildActionCard({
    required IconData icon,
    required String label,
    required Color color,
    required Color bgColor,
    required VoidCallback onTap,
    bool isPrimary = false,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        height: 44,
        padding: const EdgeInsets.symmetric(horizontal: 10),
        decoration: BoxDecoration(
          color: bgColor,
          borderRadius: BorderRadius.circular(12),
          border: isPrimary ? null : Border.all(color: SkillHubColors.borderSoft, width: 1.2),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 17, color: color),
            const SizedBox(width: 6),
            Flexible(
              child: Text(
                label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontSize: 12.5,
                  fontWeight: FontWeight.w700,
                  color: color,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
