// CANi · tab "Tài khoản" — Block 5 · Settings (Cài đặt & Tùy chỉnh việc làm)
// Đã kết nối WorkerSettingsScreen, WorkerBankSettingsScreen, WeeklyAvailabilityScreen.

import 'package:flutter/material.dart';

import 'package:cani/features/profile/screens/worker_bank_settings_screen.dart';
import 'package:cani/features/profile/widgets/profile_tokens.dart';
import 'package:cani/features/skill_verification/screens/weekly_availability_screen.dart';
import 'package:cani/features/skill_verification/screens/worker_settings_screen.dart';

class ProfileSettingsBlock extends StatefulWidget {
  const ProfileSettingsBlock({super.key});

  @override
  State<ProfileSettingsBlock> createState() => _ProfileSettingsBlockState();
}

class _ProfileSettingsBlockState extends State<ProfileSettingsBlock> {
  bool _notifEnabled = true;

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
              Text(
                'Cài đặt & Tùy chỉnh',
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                      color: t.text,
                      fontWeight: FontWeight.bold,
                    ),
              ),
              const Spacer(),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: const Color(0xFFEFF6FF),
                  borderRadius: BorderRadius.circular(99),
                  border: Border.all(color: const Color(0xFFBFDBFE)),
                ),
                child: const Text(
                  'Hoạt động',
                  style: TextStyle(
                    color: Color(0xFF1D4ED8),
                    fontSize: 10.5,
                    fontWeight: FontWeight.w700,
                  ),
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

          const Divider(height: 16, color: Color(0xFFF1F5F9)),

          // 2. TÀI KHOẢN NGÂN HÀNG THỤ HƯỞNG (WorkerBankSettingsScreen)
          _ClickableRow(
            icon: Icons.account_balance_rounded,
            label: 'Tài khoản ngân hàng nhận thù lao',
            subtitle: 'VietQR 247 nhận thanh toán trực tiếp sau ca (Non-Escrow)',
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const WorkerBankSettingsScreen()),
              );
            },
          ),

          const Divider(height: 16, color: Color(0xFFF1F5F9)),

          // 3. LỊCH RẢNH CỐ ĐỊNH HÀNG TUẦN (WeeklyAvailabilityScreen)
          _ClickableRow(
            icon: Icons.calendar_month_rounded,
            label: 'Lịch rảnh cố định hàng tuần',
            subtitle: 'Thiết lập khung giờ sẵn sàng nhận ca trong tuần (UC-26)',
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const WeeklyAvailabilityScreen()),
              );
            },
          ),

          const Divider(height: 16, color: Color(0xFFF1F5F9)),

          // 4. THÔNG BÁO VIỆC LÀM MỚI (Switch)
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
                  value: _notifEnabled,
                  onChanged: (val) {
                    setState(() => _notifEnabled = val);
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

          const Divider(height: 16, color: Color(0xFFF1F5F9)),

          // 5. NGÔN NGỮ
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 4),
            child: Row(
              children: [
                Icon(Icons.language_rounded, size: 20, color: t.accentDeep),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'Ngôn ngữ ứng dụng',
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: t.text,
                          fontWeight: FontWeight.w600,
                        ),
                  ),
                ),
                Text(
                  'Tiếng Việt (Mặc định)',
                  style: TextStyle(fontSize: 12, color: t.muted, fontWeight: FontWeight.w500),
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
