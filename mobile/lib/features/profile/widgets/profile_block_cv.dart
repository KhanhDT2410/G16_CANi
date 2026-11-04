// CANi · tab "Tài khoản" — Block 4 · Hồ Sơ Năng Lực & Kinh Nghiệm
// Kết nối trực tiếp với profileDataProvider (Skill Points, Tier, eKYC status)

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/profile/providers/profile_providers.dart';
import 'package:cani/features/profile/widgets/profile_tokens.dart';
import 'package:cani/features/skill_verification/screens/skill_hub_screen.dart';

class ProfileCvBlock extends ConsumerWidget {
  const ProfileCvBlock({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = Theme.of(context).profileX;
    final data = ref.watch(profileDataProvider);
    final user = data.user;

    // Phân định chính xác 4 trạng thái eKYC
    final ekycStatusText = data.isEkycApproved
        ? 'Đã xác thực hợp lệ'
        : (data.isEkycPending
            ? 'Đang chờ duyệt'
            : (data.isEkycRejected ? 'Cần bổ sung / Bị từ chối' : 'Chưa gửi hồ sơ'));

    final ekycStatusColor = data.isEkycApproved
        ? const Color(0xFF059669)
        : (data.isEkycPending
            ? const Color(0xFFD97706)
            : (data.isEkycRejected ? const Color(0xFFDC2626) : const Color(0xFF64748B)));

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
                'Hồ sơ năng lực & Kinh nghiệm',
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                      color: t.text,
                      fontWeight: FontWeight.bold,
                    ),
              ),
              const Spacer(),
              InkWell(
                onTap: () {
                  Navigator.of(context).push(
                    MaterialPageRoute(builder: (_) => const SkillHubScreen()),
                  );
                },
                child: Text(
                  'Xem Skill Hub →',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: t.accentDeep,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),

          // Lưới thông tin năng lực thực tế
          _InfoRow(
            icon: Icons.military_tech_outlined,
            label: 'Hạng nhân sự & Điểm uy tín',
            value: '${data.tier} • ${data.totalSp} SP',
            isHighlight: true,
          ),
          const Divider(height: 14, color: Color(0xFFF1F5F9)),

          _InfoRow(
            icon: Icons.star_border_rounded,
            label: 'Đánh giá trung bình từ EO',
            value: data.avgRating > 0 ? '${data.avgRating.toStringAsFixed(1)} / 5.0' : 'Chưa có lượt đánh giá',
          ),
          const Divider(height: 14, color: Color(0xFFF1F5F9)),

          _InfoRow(
            icon: Icons.badge_outlined,
            label: 'Định danh CCCD (eKYC)',
            value: ekycStatusText,
            valueColor: ekycStatusColor,
          ),
          const Divider(height: 14, color: Color(0xFFF1F5F9)),

          _InfoRow(
            icon: Icons.phone_outlined,
            label: 'Số điện thoại liên hệ',
            value: user?.phone != null && user!.phone!.isNotEmpty ? user.phone! : 'Chưa liên kết',
          ),
          const Divider(height: 14, color: Color(0xFFF1F5F9)),

          _InfoRow(
            icon: Icons.email_outlined,
            label: 'Email tài khoản',
            value: user?.email ?? 'Chưa cập nhật',
          ),
        ],
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({
    required this.icon,
    required this.label,
    required this.value,
    this.isHighlight = false,
    this.valueColor,
  });

  final IconData icon;
  final String label;
  final String value;
  final bool isHighlight;
  final Color? valueColor;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;

    return Row(
      children: [
        Icon(icon, size: 18, color: t.accentDeep),
        const SizedBox(width: 10),
        Text(
          label,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: t.text,
                fontWeight: FontWeight.w500,
              ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            value,
            textAlign: TextAlign.end,
            overflow: TextOverflow.ellipsis,
            maxLines: 1,
            style: TextStyle(
              fontSize: 12.5,
              fontWeight: isHighlight ? FontWeight.w700 : FontWeight.w600,
              color: valueColor ?? (isHighlight ? const Color(0xFF0066FF) : t.text),
            ),
          ),
        ),
      ],
    );
  }
}
