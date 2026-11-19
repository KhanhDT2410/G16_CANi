// CANi · tab "Tài khoản" — Block 5 · Settings (Cài đặt & Tùy chỉnh việc làm)
// Đã kết nối WorkerSettingsScreen, WorkerBankSettingsScreen, WeeklyAvailabilityScreen.

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/profile/providers/profile_providers.dart';
import 'package:cani/features/profile/widgets/profile_tokens.dart';
import 'package:cani/features/skill_verification/screens/weekly_availability_screen.dart';
import 'package:cani/features/skill_verification/screens/worker_settings_screen.dart';
import 'package:cani/features/worker_groups/screens/worker_groups_screen.dart';

class ProfileSettingsBlock extends ConsumerStatefulWidget {
  const ProfileSettingsBlock({super.key});

  @override
  ConsumerState<ProfileSettingsBlock> createState() => _ProfileSettingsBlockState();
}

class _ProfileSettingsBlockState extends ConsumerState<ProfileSettingsBlock> {
  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;
    final notifEnabled = ref.watch(notificationSettingsProvider);

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

          // 1. TIÊU CHÍ TÌM VIỆC & BÁN KÍNH RADAR (WorkerSettingsScreen)
          _ClickableRow(
            icon: Icons.radar_rounded,
            label: 'Tiêu chí tìm việc & Bán kính radar',
            subtitle: 'Bán kính quét (1-50km), thù lao tối thiểu, khu vực',
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const WorkerSettingsScreen()),
              );
            },
          ),

          Divider(height: 16, color: t.border.withValues(alpha: 0.5)),

          // 2. LỊCH RẢNH CỐ ĐỊNH HÀNG TUẦN (WeeklyAvailabilityScreen)
          _ClickableRow(
            icon: Icons.calendar_month_rounded,
            label: 'Lịch rảnh cố định hàng tuần',
            subtitle: 'Thiết lập khung giờ sẵn sàng nhận ca trong tuần',
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const WeeklyAvailabilityScreen()),
              );
            },
          ),

          Divider(height: 16, color: t.border.withValues(alpha: 0.5)),

          // 4. ĐỘI CỦA TÔI (WorkerGroupsScreen)
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

          // 5. THÔNG BÁO VIỆC LÀM MỚI (Switch)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 4),
            child: Row(
              children: [
                Icon(Icons.notifications_active_outlined, size: 20, color: t.accentDeep),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Thông báo việc làm & Radar matching',
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                              color: t.text,
                              fontWeight: FontWeight.w600,
                            ),
                      ),
                      Text(
                        'Nhận chuông báo khi có ca làm phù hợp gần bạn',
                        style: TextStyle(fontSize: 11, color: t.muted),
                      ),
                    ],
                  ),
                ),
                Switch(
                  value: notifEnabled,
                  onChanged: (val) {
                    ref.read(notificationSettingsProvider.notifier).toggle(val);
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text(val ? 'Đã bật thông báo việc làm mới' : 'Đã tắt thông báo việc làm'),
                        duration: const Duration(seconds: 2),
                      ),
                    );
                  },
                  activeThumbColor: t.accent,
                  inactiveTrackColor: t.skeleton,
                ),
              ],
            ),
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
