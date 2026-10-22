import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';
import 'package:cani/features/skill_verification/widgets/declare_skills_dialog.dart';

/// Displays the Worker's active skills and their 3 verification tiers (A-14, UC-42, UC-45, BR-16)
class MySkillsSection extends ConsumerWidget {
  const MySkillsSection({super.key});

  Color _getTierBgColor(String tier) {
    switch (tier.toUpperCase()) {
      case 'PLATFORM_PROVEN':
        return Colors.amber.shade100;
      case 'AI_DOCUMENT':
        return Colors.green.shade100;
      case 'SELF_DECLARED':
      default:
        return Colors.blueGrey.shade100;
    }
  }

  Color _getTierTextColor(String tier) {
    switch (tier.toUpperCase()) {
      case 'PLATFORM_PROVEN':
        return Colors.amber.shade900;
      case 'AI_DOCUMENT':
        return Colors.green.shade800;
      case 'SELF_DECLARED':
      default:
        return Colors.blueGrey.shade800;
    }
  }

  IconData _getTierIcon(String tier) {
    switch (tier.toUpperCase()) {
      case 'PLATFORM_PROVEN':
        return Icons.military_tech;
      case 'AI_DOCUMENT':
        return Icons.verified;
      case 'SELF_DECLARED':
      default:
        return Icons.edit_note;
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(skillVerificationProvider);
    final theme = Theme.of(context);
    final skills = state.mySkills;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              'Kỹ Năng Đã Kích Hoạt (A-14)',
              style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
            ),
            TextButton.icon(
              onPressed: () => DeclareSkillsDialog.show(context),
              icon: const Icon(Icons.add_circle_outline, size: 18),
              label: const Text('Khai báo kỹ năng'),
            ),
          ],
        ),
        const SizedBox(height: 8),

        if (skills.isEmpty)
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: theme.colorScheme.surfaceVariant.withOpacity(0.3),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: theme.colorScheme.outlineVariant),
            ),
            child: Column(
              children: [
                const Icon(Icons.playlist_add_check, size: 36, color: Colors.grey),
                const SizedBox(height: 8),
                const Text(
                  'Chưa có kỹ năng nào được khai báo',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                ),
                const SizedBox(height: 4),
                const Text(
                  'Khai báo kỹ năng Bậc 1 (Tự khai báo) để nhận đề xuất công việc ngay lập tức theo BR-13!',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Colors.grey, fontSize: 12),
                ),
                const SizedBox(height: 12),
                ElevatedButton.icon(
                  onPressed: () => DeclareSkillsDialog.show(context),
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('TỰ KHAI BÁO NGAY (TIER 1)'),
                  style: ElevatedButton.styleFrom(
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                  ),
                ),
              ],
            ),
          )
        else
          Card(
            elevation: 1,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            child: ListView.separated(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: skills.length,
              separatorBuilder: (context, index) => const Divider(height: 1),
              itemBuilder: (context, index) {
                final item = skills[index];
                final tierBg = _getTierBgColor(item.verificationTier);
                final tierTextColor = _getTierTextColor(item.verificationTier);
                final tierIcon = _getTierIcon(item.verificationTier);

                return ListTile(
                  contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                  leading: CircleAvatar(
                    backgroundColor: tierBg,
                    child: Icon(tierIcon, color: tierTextColor, size: 20),
                  ),
                  title: Row(
                    children: [
                      Expanded(
                        child: Text(
                          item.skillName,
                          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                        decoration: BoxDecoration(
                          color: tierBg,
                          borderRadius: BorderRadius.circular(6),
                          border: Border.all(color: tierTextColor.withOpacity(0.3)),
                        ),
                        child: Text(
                          item.tierBadgeLabel,
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.bold,
                            color: tierTextColor,
                          ),
                        ),
                      ),
                    ],
                  ),
                  subtitle: Padding(
                    padding: const EdgeInsets.only(top: 4.0),
                    child: Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
                          decoration: BoxDecoration(
                            color: Colors.grey.shade100,
                            borderRadius: BorderRadius.circular(4),
                            border: Border.all(color: Colors.grey.shade300),
                          ),
                          child: Text(
                            'Trình độ: ${item.proficiencyLabel}',
                            style: TextStyle(fontSize: 11, color: Colors.grey.shade800),
                          ),
                        ),
                        const SizedBox(width: 8),
                        if (item.spVerifyAwarded > 0)
                          Text(
                            '+${item.spVerifyAwarded} SP',
                            style: const TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.bold,
                              color: Colors.green,
                            ),
                          ),
                        if (item.isExpired) ...[
                          const SizedBox(width: 8),
                          const Text(
                            '(Hết hạn BR-17)',
                            style: TextStyle(fontSize: 11, color: Colors.deepOrange, fontStyle: FontStyle.italic),
                          ),
                        ],
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
      ],
    );
  }
}
