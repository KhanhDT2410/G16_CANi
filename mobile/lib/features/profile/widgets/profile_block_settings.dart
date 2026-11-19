// CANi · tab "Tài khoản" — Block 5 · Settings (Cài đặt & Tùy chỉnh việc làm)
// Đã kết nối WorkerSettingsScreen, WorkerBankSettingsScreen, WeeklyAvailabilityScreen.

import 'package:flutter/material.dart';

import 'package:cani/features/help_center/screens/help_center_screen.dart';
import 'package:cani/features/job_proposals/screens/my_disputes_screen.dart';
import 'package:cani/features/profile/widgets/profile_tokens.dart';
import 'package:cani/features/skill_verification/screens/worker_settings_screen.dart';
import 'package:cani/features/worker_groups/screens/worker_groups_screen.dart';

class ProfileSettingsBlock extends StatelessWidget {
  const ProfileSettingsBlock({super.key});

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: t.surface,
        borderRadius: BorderRadius.circular(t.radius),
        border: Border.all(color: t.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.tune_rounded, size: 20, color: t.accentDeep),
              const SizedBox(width: 8),
              Text(
                'Cài đặt & Tùy chỉnh',
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                      color: t.text,
                      fontWeight: FontWeight.bold,
                    ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // 1. TIÊU CHÍ TÌM VIỆC & LỊCH RẢNH NHẬN CA (WorkerSettingsScreen)
          _ClickableRow(
            icon: Icons.tune_rounded,
            label: 'Tiêu chí tìm việc & Lịch nhận ca',
            subtitle: 'Bán kính radar, thù lao mong muốn & lịch rảnh hàng tuần',
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const WorkerSettingsScreen()),
              );
            },
          ),

          Divider(height: 16, color: t.border.withValues(alpha: 0.5)),

          // 2. ĐỘI CỦA TÔI (WorkerGroupsScreen)
          _ClickableRow(
            icon: Icons.groups_rounded,
            label: 'Đội của tôi',
            subtitle: 'Lập đội, mời thành viên cùng nhận ca làm việc',
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const WorkerGroupsScreen()),
              );
            },
          ),

          Divider(height: 16, color: t.border.withValues(alpha: 0.5)),

          // 3. KHIẾU NẠI & TRANH CHẤP (MyDisputesScreen)
          _ClickableRow(
            icon: Icons.gavel_rounded,
            label: 'Khiếu nại & Tranh chấp',
            subtitle: 'Theo dõi tiến độ giải quyết thù lao và quyền lợi ca làm',
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const MyDisputesScreen()),
              );
            },
          ),

          Divider(height: 16, color: t.border.withValues(alpha: 0.5)),

          // 4. TRUNG TÂM TRỢ GIÚP & CÂU HỎI THƯỜNG GẶP (HelpCenterScreen)
          _ClickableRow(
            icon: Icons.help_outline_rounded,
            label: 'Trung tâm Trợ giúp & Câu hỏi thường gặp',
            subtitle: 'Giải đáp thắc mắc, gửi yêu cầu hỗ trợ và liên hệ CSKH',
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const HelpCenterScreen()),
              );
            },
          ),
        ],
      ),
    );
  }
}

class _ClickableRow extends StatelessWidget {
  const _ClickableRow({
    required this.icon,
    required this.label,
    required this.subtitle,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(7),
              decoration: BoxDecoration(
                color: t.accentDeep.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Icon(icon, size: 18, color: t.accentDeep),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    label,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: t.text,
                          fontWeight: FontWeight.w600,
                        ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    subtitle,
                    style: TextStyle(fontSize: 11, color: t.muted),
                  ),
                ],
              ),
            ),
            Icon(Icons.chevron_right, size: 20, color: t.muted),
          ],
        ),
      ),
    );
  }
}
